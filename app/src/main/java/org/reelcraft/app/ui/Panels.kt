@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.reelcraft.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.reelcraft.app.CaptionStyle
import org.reelcraft.app.EditorViewModel
import org.reelcraft.app.ExportState
import org.reelcraft.app.Framing
import org.reelcraft.app.Speech
import org.reelcraft.app.formatTime
import java.util.Locale

// ---------- shared bits ----------

@Composable
private fun Heading(title: String, lede: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Fg)
        Text(lede, fontSize = 14.sp, color = Palette.Muted, lineHeight = 20.sp)
    }
}

@Composable
private fun Label(text: String, value: String? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
        Spacer(Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Palette.Fg)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 13.sp, color = Palette.Muted, lineHeight = 18.sp)
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, display: String, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit, onDone: (() -> Unit)? = null,
) {
    Column {
        Label(label, display)
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(thumbColor = Palette.Amber, activeTrackColor = Palette.Amber, inactiveTrackColor = Palette.Line),
        )
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Palette.Raise,
            selectedLabelColor = Palette.Amber,
            labelColor = Palette.Fg,
        ),
    )
}

private fun secs(ms: Long) = String.format(Locale.US, "%.2fs", ms / 1000.0)

// ---------- Edit ----------

@Composable
fun EditPanel(vm: EditorViewModel) {
    Heading("Trim and arrange", "Tap a piece on the timeline to select it. Trim its ends, split it at the playhead, or move it.")
    val p = vm.selectedPiece()
    val src = p?.let { vm.source(it.sourceId) }
    if (p != null && src != null) {
        val index = vm.pieces.indexOfFirst { it.id == p.id }
        Column {
            Label("Piece ${index + 1} of ${vm.pieces.size}", "${formatTime(p.startMs)} – ${formatTime(p.endMs)}")
            RangeSlider(
                value = p.startMs.toFloat()..p.endMs.toFloat(),
                onValueChange = { r -> vm.updateTrim(p.id, r.start.toLong(), r.endInclusive.toLong()) },
                onValueChangeFinished = { vm.commitTrim(p.id) },
                valueRange = 0f..src.durationMs.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Palette.Amber, activeTrackColor = Palette.Amber, inactiveTrackColor = Palette.Line),
            )
            Text(src.name, fontSize = 12.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.splitAtPlayhead() }) { Text("Split at playhead") }
            OutlinedButton(onClick = { vm.moveSelected(-1) }) { Text("Move earlier") }
            OutlinedButton(onClick = { vm.moveSelected(1) }) { Text("Move later") }
            OutlinedButton(onClick = { vm.deleteSelected() }) { Text("Delete", color = Palette.Danger) }
        }
    } else {
        Note("No piece selected.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("Framing")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Framing.entries.forEach { f -> Choice(f.label, vm.framing == f) { vm.framing = f } }
        }
        Note("Fill screen crops landscape videos to cover the whole vertical frame. Fit whole video keeps everything and adds black bars.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Clips in this project")
        vm.sources.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.Raise).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp, 64.dp).clip(RoundedCornerShape(5.dp)).background(Palette.Track)) {
                    s.thumb?.let { Image(it, null, Modifier.size(36.dp, 64.dp), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = Palette.Fg)
                    Text(formatTime(s.durationMs), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Palette.Muted)
                }
                TextButton(onClick = { vm.addAgain(s.id) }) { Text("Add again") }
                TextButton(onClick = { vm.removeSource(s.id) }) { Text("Remove", color = Palette.Danger) }
            }
        }
    }
}

// ---------- Auto-cut ----------

@Composable
fun CutPanel(vm: EditorViewModel) {
    Heading(
        "Cut the pauses out",
        "ReelCraft listens to your clip, finds the quiet gaps between sentences and removes them. " +
            "Each spoken part becomes its own piece, ready for a caption.",
    )
    if (vm.source(vm.cutSourceId) == null) vm.cutSourceId = vm.sources.firstOrNull()?.id
    val src = vm.source(vm.cutSourceId) ?: run { Note("Add a clip first."); return }

    if (vm.sources.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.sources.forEach { s ->
                Choice(s.name.take(18), s.id == src.id) { vm.cutSourceId = s.id }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE") val version = vm.envelopeVersion // redraw when analysis finishes
    val env = vm.envelope(src.id)
    Box(
        Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Track),
        contentAlignment = Alignment.Center,
    ) {
        if (env == null) {
            if (vm.cutBusy) CircularProgressIndicator(Modifier.size(24.dp), color = Palette.Amber, strokeWidth = 2.dp)
            else TextButton(onClick = { vm.analyzeIfNeeded(src.id, thenCut = false) }) { Text("Show this clip's sound") }
        } else {
            val kept = vm.pieces.filter { it.sourceId == src.id }
            val lv = Speech.levels(env, vm.cut.threshold)
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val n = env.db.size
                val bw = size.width / n
                val span = maxOf(1f, lv.peak - lv.floor)
                for (i in 0 until n) {
                    val v = ((env.db[i] - lv.floor) / span).coerceIn(0f, 1f)
                    val h = maxOf(1.5f, v * (size.height - 12f))
                    val t = i.toLong() * env.hopMs
                    val on = kept.any { t >= it.startMs && t < it.endMs }
                    drawRect(
                        if (on) Palette.Teal else Palette.Muted.copy(alpha = 0.35f),
                        topLeft = Offset(i * bw, (size.height - h) / 2f),
                        size = Size(maxOf(1f, bw - 0.3f), h),
                    )
                }
                val tv = ((lv.threshold - lv.floor) / span).coerceIn(0f, 1f)
                val y = (size.height - tv * (size.height - 12f)) / 2f
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                drawLine(Palette.Amber, Offset(0f, y), Offset(size.width, y), 2f, pathEffect = dash)
                drawLine(Palette.Amber, Offset(0f, size.height - y), Offset(size.width, size.height - y), 2f, pathEffect = dash)
            }
        }
    }
    Note("Teal is kept, grey is cut. The dashed line is the silence threshold.")

    vm.lastCut?.takeIf { it.sourceId == src.id }?.let { r ->
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat(String.format(Locale.US, "%.1fs", r.removedMs / 1000f), "silence removed")
            Stat("${r.pauses}", "pauses cut")
            Stat(formatTime(r.newLengthMs), "new length")
        }
    }

    LabeledSlider("Silence threshold", vm.cut.threshold, "${(vm.cut.threshold * 100).toInt()}%", 0.12f..0.6f,
        { vm.cut = vm.cut.copy(threshold = it) })
    LabeledSlider("Shortest pause to cut", vm.cut.minPauseMs.toFloat(), secs(vm.cut.minPauseMs.toLong()), 200f..1500f,
        { vm.cut = vm.cut.copy(minPauseMs = it.toInt()) })
    LabeledSlider("Breathing room", vm.cut.padMs.toFloat(), secs(vm.cut.padMs.toLong()), 0f..300f,
        { vm.cut = vm.cut.copy(padMs = it.toInt()) })

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.analyzeIfNeeded(src.id, thenCut = true) }, enabled = !vm.cutBusy) {
            Text(if (vm.cutBusy) "Listening…" else "Auto-cut this clip", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = { vm.restoreSource(src.id) }) { Text("Restore full clip") }
    }
    Note("A higher threshold cuts more. Breathing room keeps a little sound around each sentence so it doesn't feel chopped.")
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Teal)
        Text(label, fontSize = 12.sp, color = Palette.Muted)
    }
}

// ---------- Captions ----------

@Composable
fun CaptionsPanel(vm: EditorViewModel) {
    Heading("Captions", "Each piece gets one caption line. Run Auto-cut first so every sentence becomes its own piece.")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("Style")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptionStyle.entries.forEach { s -> Choice(s.label, vm.captionStyle == s) { vm.captionStyle = s } }
        }
    }
    LabeledSlider("Height on screen", vm.captionPos, "${(vm.captionPos * 100).toInt()}%", 0.2f..0.9f, { vm.captionPos = it })
    LabeledSlider("Text size", vm.captionSize, "${(vm.captionSize * 100).toInt()}%", 0.6f..1.6f, { vm.captionSize = it })

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("Hook text", "first piece")
        OutlinedTextField(
            value = vm.hookText,
            onValueChange = { if (it.length <= 60) vm.hookText = it },
            placeholder = { Text("e.g. 3 things nobody tells you") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Note("Shown at the top during the first piece, to make people stop scrolling.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("Lines")
        vm.pieces.forEachIndexed { i, p ->
            val start = vm.startOf(i)
            Column {
                TextButton(onClick = { vm.selectAndSeek(p) }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    Text(
                        "${i + 1}.  ${formatTime(start)} – ${formatTime(start + p.durMs)}",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        color = if (p.id == vm.selectedId) Palette.Amber else Palette.Muted,
                    )
                }
                OutlinedTextField(
                    value = p.caption,
                    onValueChange = { vm.setCaption(p.id, it) },
                    placeholder = { Text("Type what's said here") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Note("Coming next: automatic speech-to-text in English and Swahili, then Luganda, running on your phone with no internet.")
}

// ---------- Audio ----------

@Composable
fun AudioPanel(vm: EditorViewModel, onPickMusic: () -> Unit) {
    Heading("Sound", "Balance your voice against background music. Music loops to fill the whole video.")
    LabeledSlider("Clip volume", vm.clipVolume, "${(vm.clipVolume * 100).toInt()}%", 0f..1f, { vm.updateClipVolume(it) })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Background music")
        if (vm.musicUri != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.Raise).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(vm.musicName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onPickMusic) { Text("Change") }
                TextButton(onClick = { vm.removeMusic() }) { Text("Remove", color = Palette.Danger) }
            }
        } else {
            OutlinedButton(onClick = onPickMusic) { Text("Choose a music file") }
        }
    }
    LabeledSlider("Music volume", vm.musicVolume, "${(vm.musicVolume * 100).toInt()}%", 0f..1f, { vm.updateMusicVolume(it) })
    Note("Use music you own or have permission to use. TikTok and Instagram may mute videos with copyrighted songs.")
}

// ---------- Export ----------

@Composable
fun ExportPanel(vm: EditorViewModel) {
    val context = LocalContext.current
    Heading("Export", "Saves a vertical MP4 with no watermark, ready for TikTok, Reels, Shorts and WhatsApp Status.")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("Quality")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("720p · smaller file", vm.resolution == 1280) { vm.resolution = 1280 }
            Choice("1080p · sharper", vm.resolution == 1920) { vm.resolution = 1920 }
        }
    }
    val total = vm.totalMs
    Note("Length ${formatTime(total)}" + if (total > 60_000) ". WhatsApp Status works best under a minute." else ".")

    when (val st = vm.exportState) {
        is ExportState.Running -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Exporting", "${st.percent}%")
                LinearProgressIndicator(
                    progress = { st.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Palette.Amber, trackColor = Palette.Track,
                )
                OutlinedButton(onClick = { vm.cancelExport() }) { Text("Cancel") }
            }
        }
        else -> {
            Button(onClick = { vm.export() }, enabled = total > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Export video", fontWeight = FontWeight.SemiBold)
            }
            if (st is ExportState.Done) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Raise).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Your video is ready", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Palette.Fg)
                    Text(String.format(Locale.US, "%.1f MB · %s", st.bytes / 1_048_576.0, st.file.name), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Palette.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { context.startActivity(vm.shareIntent(st.file)) }) { Text("Share to TikTok, WhatsApp…") }
                        if (vm.canSaveToGallery) OutlinedButton(onClick = { vm.saveToGallery(st.file) }) { Text("Save to gallery") }
                    }
                }
            }
            if (st is ExportState.Failed) {
                Text("Export failed: ${st.message}", color = Palette.Danger, fontSize = 14.sp)
                Note("Try 720p, or remove music. If it keeps failing, please report it on GitHub with your phone model.")
            }
        }
    }
}
