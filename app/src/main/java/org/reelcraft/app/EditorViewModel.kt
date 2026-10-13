package org.reelcraft.app

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    private var nextId = 1L
    private fun newId() = nextId++

    // ---- project ----
    val sources = mutableStateListOf<Source>()
    val pieces = mutableStateListOf<Piece>()
    private val envelopes = HashMap<Long, Envelope>()
    var envelopeVersion by mutableIntStateOf(0); private set

    var selectedId by mutableStateOf<Long?>(null)
    var timelineMs by mutableLongStateOf(0L); private set
    var currentIndex by mutableIntStateOf(0); private set
    var isPlaying by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set

    var framing by mutableStateOf(Framing.FILL)
    var captionStyle by mutableStateOf(CaptionStyle.BOX)
    var captionPos by mutableFloatStateOf(0.72f)
    var captionSize by mutableFloatStateOf(1f)
    var hookText by mutableStateOf("")
    var hookSecs by mutableFloatStateOf(3f)

    var clipVolume by mutableFloatStateOf(1f); private set
    var musicUri by mutableStateOf<Uri?>(null); private set
    var musicName by mutableStateOf(""); private set
    var musicVolume by mutableFloatStateOf(0.35f); private set

    var cut by mutableStateOf(CutSettings())
    var cutSourceId by mutableStateOf<Long?>(null)
    var cutBusy by mutableStateOf(false); private set
    var lastCut by mutableStateOf<CutResult?>(null); private set

    var resolution by mutableIntStateOf(1280)
    var exportState by mutableStateOf<ExportState>(ExportState.Idle); private set

    var message by mutableStateOf<String?>(null)

    // ---- playback ----
    val player: ExoPlayer = ExoPlayer.Builder(app).build()
    private val musicPlayer: ExoPlayer = ExoPlayer.Builder(app).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        volume = musicVolume
    }
    private var transformer: Transformer? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) musicPlayer.pause()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    timelineMs = totalMs
                    musicPlayer.pause()
                }
            }
        })
        viewModelScope.launch {
            while (isActive) {
                if (pieces.isNotEmpty() && player.mediaItemCount == pieces.size &&
                    player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE
                ) {
                    val i = player.currentMediaItemIndex.coerceIn(0, pieces.size - 1)
                    currentIndex = i
                    if (player.isPlaying) timelineMs = startOf(i) + player.currentPosition.coerceAtLeast(0)
                }
                delay(40)
            }
        }
    }

    val totalMs: Long get() = pieces.sumOf { it.durMs }
    fun startOf(index: Int): Long {
        var a = 0L
        for (k in 0 until min(index, pieces.size)) a += pieces[k].durMs
        return a
    }
    fun source(id: Long?): Source? = sources.firstOrNull { it.id == id }
    fun selectedPiece(): Piece? = pieces.firstOrNull { it.id == selectedId }
    fun envelope(id: Long?): Envelope? = id?.let { envelopes[it] }

    private fun locate(ms: Long): Pair<Int, Long>? {
        if (pieces.isEmpty()) return null
        var acc = 0L
        pieces.forEachIndexed { i, p ->
            if (ms < acc + p.durMs || i == pieces.lastIndex) return i to (ms - acc).coerceIn(0, max(0, p.durMs - 1))
            acc += p.durMs
        }
        return null
    }

    private fun rebuildPlaylist(seekMs: Long = timelineMs) {
        val items = pieces.map { p ->
            MediaItem.Builder()
                .setUri(source(p.sourceId)!!.uri)
                .setMediaId(p.id.toString())
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(p.startMs)
                        .setEndPositionMs(p.endMs)
                        .build()
                )
                .build()
        }
        val wasPlaying = player.isPlaying
        player.setMediaItems(items)
        player.prepare()
        if (items.isNotEmpty()) seekTimeline(seekMs.coerceIn(0, max(0, totalMs - 1)))
        else { timelineMs = 0; currentIndex = 0 }
        if (wasPlaying && items.isNotEmpty()) player.play()
    }

    fun seekTimeline(ms: Long) {
        val (i, off) = locate(ms) ?: return
        player.seekTo(i, off)
        timelineMs = startOf(i) + off
        currentIndex = i
        syncMusic()
    }

    fun togglePlay() {
        if (pieces.isEmpty()) return
        if (player.isPlaying) { pauseAll(); return }
        if (player.playbackState == Player.STATE_ENDED || timelineMs >= totalMs - 50) seekTimeline(0)
        player.play()
        syncMusic(start = true)
    }

    fun pauseAll() {
        player.pause()
        musicPlayer.pause()
    }

    private fun syncMusic(start: Boolean = false) {
        if (musicUri == null) return
        val d = musicPlayer.duration
        if (d > 0) musicPlayer.seekTo(timelineMs % d)
        if (start || player.isPlaying) musicPlayer.play()
    }

    fun selectAndSeek(piece: Piece) {
        selectedId = piece.id
        val i = pieces.indexOfFirst { it.id == piece.id }
        if (i >= 0) seekTimeline(startOf(i))
    }

    // ---- importing ----
    fun addVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        loading = true
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { uris.mapNotNull { loadSource(it) } }
            loading = false
            if (loaded.isEmpty()) {
                message = "Couldn't open those files. Try MP4 videos from your gallery."
                return@launch
            }
            pauseAll()
            var first: Piece? = null
            loaded.forEach { s ->
                sources += s
                val p = Piece(newId(), s.id, 0, s.durationMs)
                pieces += p
                if (first == null) first = p
            }
            if (cutSourceId == null) cutSourceId = loaded.first().id
            val firstPiece = first!!
            selectedId = firstPiece.id
            rebuildPlaylist(startOf(pieces.indexOf(firstPiece)))
            if (loaded.size < uris.size) message = "Some files couldn't be opened."
        }
    }

    private fun loadSource(uri: Uri): Source? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            if (dur < 300) return null
            val frame = r.getFrameAtTime(min(500_000L, dur * 1000 / 3), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val thumb = frame?.let {
                val h = 160
                val w = max(1, (it.width * h / it.height.toFloat()).toInt())
                Bitmap.createScaledBitmap(it, w, h, true).asImageBitmap()
            }
            val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Clip"
            Source(newId(), uri, name, dur, thumb)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    fun removeSource(id: Long) {
        pauseAll()
        pieces.removeAll { it.sourceId == id }
        sources.removeAll { it.id == id }
        envelopes.remove(id)
        if (cutSourceId == id) cutSourceId = sources.firstOrNull()?.id
        if (lastCut?.sourceId == id) lastCut = null
        if (selectedPiece() == null) selectedId = pieces.firstOrNull()?.id
        rebuildPlaylist(0)
    }

    fun addAgain(id: Long) {
        val s = source(id) ?: return
        val p = Piece(newId(), s.id, 0, s.durationMs)
        pieces += p
        selectedId = p.id
        rebuildPlaylist(startOf(pieces.lastIndex))
    }

    // ---- editing ----
    fun updateTrim(id: Long, startMs: Long, endMs: Long) {
        val i = pieces.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = pieces[i]
        var s = startMs
        var e = endMs
        if (e - s < 100) { if (s != p.startMs) s = e - 100 else e = s + 100 }
        pieces[i] = p.copy(startMs = s.coerceAtLeast(0), endMs = e)
    }

    fun commitTrim(id: Long) {
        val i = pieces.indexOfFirst { it.id == id }
        if (i >= 0) rebuildPlaylist(startOf(i))
    }

    fun splitAtPlayhead() {
        val (i, off) = locate(timelineMs) ?: return
        val p = pieces[i]
        if (off < 100 || off > p.durMs - 100) {
            message = "Move the playhead inside a piece first."
            return
        }
        val cutAt = p.startMs + off
        pieces[i] = p.copy(endMs = cutAt)
        val np = Piece(newId(), p.sourceId, cutAt, p.endMs)
        pieces.add(i + 1, np)
        selectedId = np.id
        rebuildPlaylist(startOf(i + 1))
        message = "Split into two pieces."
    }

    fun moveSelected(delta: Int) {
        val i = pieces.indexOfFirst { it.id == selectedId }
        val j = i + delta
        if (i < 0 || j < 0 || j > pieces.lastIndex) return
        val a = pieces[i]; pieces[i] = pieces[j]; pieces[j] = a
        rebuildPlaylist(startOf(j))
    }

    fun deleteSelected() {
        val i = pieces.indexOfFirst { it.id == selectedId }
        if (i < 0) return
        pauseAll()
        pieces.removeAt(i)
        selectedId = pieces.getOrNull(min(i, pieces.lastIndex))?.id
        rebuildPlaylist(startOf(min(i, max(0, pieces.lastIndex))))
    }

    fun setFraming(sourceId: Long, zoom: Float, panX: Float, panY: Float) {
        val i = sources.indexOfFirst { it.id == sourceId }
        if (i < 0) return
        sources[i] = sources[i].copy(
            zoom = zoom.coerceIn(1f, 3.5f),
            panX = panX.coerceIn(-1f, 1f),
            panY = panY.coerceIn(-1f, 1f),
        )
    }

    fun setCaption(id: Long, text: String) {
        val i = pieces.indexOfFirst { it.id == id }
        if (i >= 0) pieces[i] = pieces[i].copy(caption = text)
    }

    // ---- auto-cut ----
    fun analyzeIfNeeded(id: Long, thenCut: Boolean) {
        val s = source(id) ?: return
        envelopes[id]?.let { if (thenCut) applyCut(s, it); return }
        cutBusy = true
        viewModelScope.launch {
            val env = withContext(Dispatchers.Default) { AudioAnalyzer.envelope(ctx, s.uri) }
            cutBusy = false
            if (env == null) {
                message = "Couldn't read this clip's sound. It may have no audio."
                return@launch
            }
            envelopes[id] = env
            envelopeVersion++
            if (thenCut) applyCut(s, env)
        }
    }

    private fun applyCut(s: Source, env: Envelope) {
        val regions = Speech.regions(env, s.durationMs, cut)
        if (regions == null) { message = "This clip has no clear pauses to cut."; return }
        if (regions.isEmpty()) { message = "Nothing loud enough was found. Lower the threshold."; return }
        val before = pieces.filter { it.sourceId == s.id }.sumOf { it.durMs }.takeIf { it > 0 } ?: s.durationMs
        var at = pieces.indexOfFirst { it.sourceId == s.id }
        if (at < 0) at = pieces.size
        pieces.removeAll { it.sourceId == s.id }
        val fresh = regions.map { (a, b) -> Piece(newId(), s.id, a, b) }
        pieces.addAll(at, fresh)
        val after = fresh.sumOf { it.durMs }
        lastCut = CutResult(s.id, max(0, before - after), max(0, fresh.size - 1), after)
        selectedId = fresh.first().id
        pauseAll()
        rebuildPlaylist(startOf(at))
        message = String.format(Locale.US, "Removed %.1fs of silence.", (before - after) / 1000.0)
    }

    fun restoreSource(id: Long) {
        val s = source(id) ?: return
        var at = pieces.indexOfFirst { it.sourceId == id }
        if (at < 0) at = pieces.size
        pieces.removeAll { it.sourceId == id }
        val p = Piece(newId(), id, 0, s.durationMs)
        pieces.add(at, p)
        selectedId = p.id
        if (lastCut?.sourceId == id) lastCut = null
        pauseAll()
        rebuildPlaylist(startOf(at))
        message = "Full clip restored."
    }

    // ---- audio ----
    fun updateClipVolume(v: Float) { clipVolume = v; player.volume = v }
    fun updateMusicVolume(v: Float) { musicVolume = v; musicPlayer.volume = v }

    fun setMusic(uri: Uri) {
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "Music"
        musicUri = uri
        musicName = name
        musicPlayer.setMediaItem(MediaItem.fromUri(uri))
        musicPlayer.prepare()
        if (player.isPlaying) syncMusic(start = true)
        message = "Music added."
    }

    fun removeMusic() {
        musicPlayer.stop()
        musicPlayer.clearMediaItems()
        musicUri = null
        musicName = ""
    }

    // ---- export ----
    fun export() {
        if (pieces.isEmpty() || exportState is ExportState.Running) return
        pauseAll()
        val plan = ExportPlan(
            pieces = pieces.toList(),
            sources = sources.associateBy { it.id },
            height = resolution,
            framing = framing,
            captionStyle = captionStyle,
            captionPos = captionPos,
            captionSize = captionSize,
            hookText = hookText,
            clipVolume = clipVolume,
            musicUri = musicUri,
            musicVolume = musicVolume,
        )
        val composition = try {
            Exporter.composition(plan)
        } catch (e: Exception) {
            exportState = ExportState.Failed("Couldn't prepare the export: ${e.message}")
            return
        }
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest export
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val out = File(dir, "reelcraft-$stamp.mp4")

        val t = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    exportState = ExportState.Done(out, out.length())
                    transformer = null
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    exportState = ExportState.Failed(exportException.message ?: "Export failed.")
                    transformer = null
                }
            })
            .build()
        transformer = t
        exportState = ExportState.Running(0)
        gallerySave = null
        t.start(composition, out.absolutePath)
        viewModelScope.launch {
            val holder = ProgressHolder()
            while (isActive && exportState is ExportState.Running && transformer === t) {
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    exportState = ExportState.Running(holder.progress)
                }
                delay(250)
            }
        }
    }

    fun cancelExport() {
        transformer?.cancel()
        transformer = null
        exportState = ExportState.Idle
        message = "Export cancelled."
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share video")
    }


    /** Android 8 and 9 need the storage permission to write into the shared camera folder. */
    val needsStoragePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Result of the last Save to gallery, shown on the Export screen. */
    var gallerySave by mutableStateOf<GallerySave?>(null); private set
    val savingToGallery: Boolean get() = gallerySave is GallerySave.Saving

    fun saveToGallery(file: File) {
        if (savingToGallery) return
        if (!file.exists() || file.length() == 0L) {
            gallerySave = GallerySave.Failed("The exported video is missing. Please export again.")
            return
        }
        gallerySave = GallerySave.Saving
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(file) else saveLegacy(file)
                }
            }
            gallerySave = result.fold(
                onSuccess = { it },
                onFailure = { e ->
                    GallerySave.Failed(
                        "${e.message ?: e.javaClass.simpleName} (Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL})"
                    )
                },
            )
            message = when (val g = gallerySave) {
                is GallerySave.Saved -> "Saved to ${g.where}."
                is GallerySave.Failed -> "Couldn't save to the gallery."
                else -> null
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(file: File): GallerySave.Saved {
        // Camera folder first (every gallery shows it), then Movies if a phone refuses DCIM.
        val folders = listOf(
            Environment.DIRECTORY_DCIM + "/ReelCraft" to "Gallery › Camera › ReelCraft",
            Environment.DIRECTORY_MOVIES + "/ReelCraft" to "Gallery › Movies › ReelCraft",
        )
        var last: Exception? = null
        for ((path, label) in folders) {
            try {
                return GallerySave.Saved(insertVideo(file, path), label)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("unknown error")
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun insertVideo(file: File, relativePath: String): Uri {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("the phone refused to create the file")
        try {
            val out = resolver.openOutputStream(uri) ?: error("couldn't open the gallery file")
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(file: File): GallerySave.Saved {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "ReelCraft")
        if (!dir.exists() && !dir.mkdirs()) error("couldn't create the ReelCraft folder")
        val target = File(dir, file.name)
        file.copyTo(target, overwrite = true)
        android.media.MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf("video/mp4"), null)
        return GallerySave.Saved(null, "Gallery › Camera › ReelCraft")
    }

    fun openSavedIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun onCleared() {
        transformer?.cancel()
        player.release()
        musicPlayer.release()
        super.onCleared()
    }
}
