package org.reelcraft.app

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.util.Locale

/** A video file the user imported. */
data class Source(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val thumb: ImageBitmap?,
    /** 1 = no zoom. Applies to every piece cut from this clip. */
    val zoom: Float = 1f,
    /** -1..1: where the zoomed frame sits. +x shows more of the right side, +y more of the bottom. */
    val panX: Float = 0f,
    val panY: Float = 0f,
)

/** One stretch of a source placed on the timeline. */
data class Piece(
    val id: Long,
    val sourceId: Long,
    val startMs: Long,
    val endMs: Long,
    val caption: String = "",
) {
    val durMs: Long get() = endMs - startMs
}

/** Loudness of a clip in 20 ms steps, in decibels. */
class Envelope(val hopMs: Int, val db: FloatArray)

enum class CaptionStyle(val label: String) { BOX("Box"), HIGHLIGHT("Highlight") }

enum class Framing(val label: String) { FILL("Fill screen"), FIT("Fit whole video") }

data class CutSettings(
    val threshold: Float = 0.30f,
    val minPauseMs: Int = 450,
    val padMs: Int = 100,
)

data class CutResult(val sourceId: Long, val removedMs: Long, val pauses: Int, val newLengthMs: Long)

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val percent: Int) : ExportState
    data class Done(val file: File, val bytes: Long) : ExportState
    data class Failed(val message: String) : ExportState
}

fun formatTime(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 100 // tenths
    val minutes = t / 600
    val seconds = (t % 600) / 10.0
    return String.format(Locale.US, "%d:%04.1f", minutes, seconds)
}
