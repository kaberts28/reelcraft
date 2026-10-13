package org.reelcraft.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Decodes a clip's sound track and measures how loud it is every 20 ms. Runs fully offline. */
object AudioAnalyzer {
    const val HOP_MS = 20

    fun envelope(context: Context, uri: Uri): Envelope? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i; format = f; break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = max(1, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            var floatPcm = false
            var windowFrames = max(1, sampleRate * HOP_MS / 1000)

            val out = FloatList()
            var acc = 0.0
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleTries = 0

            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val ii = codec.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val buf = codec.getInputBuffer(ii)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        oi >= 0 -> {
                            idleTries = 0
                            val buf = codec.getOutputBuffer(oi)
                            if (buf != null && info.size > 0) {
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                buf.order(ByteOrder.nativeOrder())
                                if (floatPcm) {
                                    val fb = buf.asFloatBuffer()
                                    while (fb.remaining() >= channels) {
                                        var s = 0f
                                        for (c in 0 until channels) s += fb.get()
                                        s /= channels
                                        acc += (s * s).toDouble(); count++
                                        if (count >= windowFrames) {
                                            out.add(toDb(acc / count)); acc = 0.0; count = 0
                                        }
                                    }
                                } else {
                                    val sb = buf.asShortBuffer()
                                    while (sb.remaining() >= channels) {
                                        var s = 0f
                                        for (c in 0 until channels) s += sb.get() / 32768f
                                        s /= channels
                                        acc += (s * s).toDouble(); count++
                                        if (count >= windowFrames) {
                                            out.add(toDb(acc / count)); acc = 0.0; count = 0
                                        }
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(oi, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                        oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = codec.outputFormat
                            sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = max(1, of.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                            floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                            windowFrames = max(1, sampleRate * HOP_MS / 1000)
                        }
                        oi == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone -> {
                            if (++idleTries > 300) outputDone = true // some decoders never signal the end
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            if (count > 0) out.add(toDb(acc / count))
            if (out.size == 0) return null
            return Envelope(HOP_MS, out.toArray())
        } catch (e: Exception) {
            return null
        } finally {
            extractor.release()
        }
    }

    private fun toDb(meanSquare: Double): Float = (20 * log10(sqrt(meanSquare) + 1e-6)).toFloat()

    private class FloatList {
        private var data = FloatArray(4096)
        var size = 0; private set
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }
}

/** Finds the spoken parts of a clip so the silent gaps between them can be removed. */
object Speech {
    class Levels(val floor: Float, val peak: Float, val threshold: Float, val flat: Boolean)

    fun levels(env: Envelope, threshold: Float): Levels {
        val sorted = env.db.copyOf().also { it.sort() }
        val n = sorted.size
        val floor = sorted[min(n - 1, (n * 0.10).toInt())]
        val peak = sorted[min(n - 1, (n * 0.97).toInt())]
        return Levels(floor, peak, floor + (peak - floor) * threshold, peak - floor < 6f)
    }

    /** Returns kept regions in ms, or null when the clip has no clear quiet/loud contrast. */
    fun regions(env: Envelope, durationMs: Long, s: CutSettings): List<Pair<Long, Long>>? {
        if (env.db.isEmpty()) return null
        val lv = levels(env, s.threshold)
        if (lv.flat) return null
        val hop = env.hopMs.toLong()
        val raw = ArrayList<LongArray>()
        var start = -1
        for (i in 0..env.db.size) {
            val loud = i < env.db.size && env.db[i] > lv.threshold
            if (loud && start < 0) start = i
            if (!loud && start >= 0) {
                raw.add(longArrayOf(start * hop, i * hop)); start = -1
            }
        }
        val merged = ArrayList<LongArray>()
        for (r in raw) {
            val last = merged.lastOrNull()
            if (last != null && r[0] - last[1] < s.minPauseMs) last[1] = r[1] else merged.add(r.copyOf())
        }
        val padded = merged
            .filter { it[1] - it[0] >= 200 }
            .map { longArrayOf(max(0L, it[0] - s.padMs), min(durationMs, it[1] + s.padMs)) }
        val out = ArrayList<LongArray>()
        for (r in padded) {
            val last = out.lastOrNull()
            if (last != null && r[0] <= last[1]) last[1] = max(last[1], r[1]) else out.add(r)
        }
        return out.map { it[0] to it[1] }
    }
}
