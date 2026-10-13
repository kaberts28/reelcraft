@file:OptIn(ExperimentalMaterial3Api::class)

package org.reelcraft.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import org.reelcraft.app.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.reelcraft.app.CaptionLook
import org.reelcraft.app.CaptionStyle
import org.reelcraft.app.EditorViewModel
import org.reelcraft.app.Framing
import org.reelcraft.app.Piece
import org.reelcraft.app.formatTime

private val TABS = listOf("Edit", "Auto-cut", "Captions", "Audio", "Export")

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val pickVideos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        vm.addVideos(uris)
    }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setMusic(uri)
    }
    val addClips = {
        pickVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }
    val snackbar = remember { SnackbarHostState() }
    // Show the message first and clear it only after it's dismissed. Clearing it earlier
    // changes this effect's key, which cancels the snackbar before anyone can read it.
    LaunchedEffect(vm.message) {
        val m = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(m, withDismissAction = true)
        if (vm.message == m) vm.message = null
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Palette.Ink,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            val screenH = maxHeight
            Column(Modifier.fillMaxSize()) {
                TopBar(onAdd = addClips, loading = vm.loading)
                if (vm.pieces.isEmpty()) {
                    EmptyState(onAdd = addClips, loading = vm.loading)
                } else {
                    PreviewStage(vm, Modifier.fillMaxWidth().height(screenH * 0.40f))
                    Transport(vm)
                    Timeline(vm, onAdd = addClips)
                    ScrollableTabRow(
                        selectedTabIndex = tab,
                        containerColor = Palette.Ink,
                        contentColor = Palette.Fg,
                        edgePadding = 8.dp,
                    ) {
                        TABS.forEachIndexed { i, label ->
                            Tab(
                                selected = tab == i,
                                onClick = { tab = i },
                                text = { Text(label, fontWeight = FontWeight.SemiBold) },
                                selectedContentColor = Palette.Amber,
                                unselectedContentColor = Palette.Muted,
                            )
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (tab) {
                            0 -> EditPanel(vm)
                            1 -> CutPanel(vm)
                            2 -> CaptionsPanel(vm)
                            3 -> AudioPanel(vm, onPickMusic = { pickMusic.launch("audio/*") })
                            else -> ExportPanel(vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onAdd: () -> Unit, loading: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(Palette.Amber),
            contentAlignment = Alignment.Center,
        ) { PlayGlyph(Palette.AmberInk, 9.dp) }
        Spacer(Modifier.width(10.dp))
        Text("ReelCraft", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Fg)
        Spacer(Modifier.weight(1f))
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Palette.Amber)
            Spacer(Modifier.width(12.dp))
        }
        Button(onClick = onAdd) { Text("Add clips", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, loading: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Make your first reel", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Fg, lineHeight = 34.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Pick one or more videos from your phone. ReelCraft cuts out the pauses, adds captions and music, " +
                "and saves a vertical video for TikTok, Reels, Shorts or WhatsApp Status. Free, no watermark, works offline.",
            color = Palette.Muted, fontSize = 16.sp, lineHeight = 23.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, enabled = !loading) {
            Text(if (loading) "Opening…" else "Choose videos", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PreviewStage(vm: EditorViewModel, modifier: Modifier) {
    BoxWithConstraints(modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        var h = maxHeight
        var w = h * 9f / 16f
        if (w > maxWidth) { w = maxWidth; h = w * 16f / 9f }
        val density = LocalDensity.current
        Box(Modifier.size(w, h).background(Color.Black).border(1.dp, Palette.Line)) {
            AndroidView(
                factory = { c ->
                    (LayoutInflater.from(c).inflate(R.layout.player_view, null) as PlayerView).apply {
                        player = vm.player
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { pv ->
                    pv.resizeMode = if (vm.framing == Framing.FILL)
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    val src = vm.pieces.getOrNull(vm.currentIndex)?.let { vm.source(it.sourceId) }
                    val z = src?.zoom ?: 1f
                    val px = src?.panX ?: 0f
                    val py = src?.panY ?: 0f
                    val applyZoom = {
                        pv.videoSurfaceView?.apply {
                            scaleX = z
                            scaleY = z
                            translationX = -px * (z - 1f) * pv.width / 2f
                            translationY = -py * (z - 1f) * pv.height / 2f
                        }
                    }
                    applyZoom()
                    if (pv.width == 0) pv.post { applyZoom() }
                },
                modifier = Modifier.fillMaxSize(),
            )
            val capSize = with(density) { (h * CaptionLook.SIZE_OF_HEIGHT * vm.captionSize).toSp() }
            val hookSize = with(density) { (h * CaptionLook.HOOK_SIZE_OF_HEIGHT).toSp() }
            if (vm.hookText.isNotBlank() && vm.currentIndex == 0) {
                CaptionText(
                    vm.hookText.uppercase(), Color(CaptionLook.AMBER_INK), Color(CaptionLook.AMBER), hookSize,
                    Modifier.align(BiasAlignment(0f, 2f * CaptionLook.HOOK_POSITION - 1f)),
                )
            }
            val piece = vm.pieces.getOrNull(vm.currentIndex)
            if (piece != null && piece.caption.isNotBlank()) {
                val (fg, bg) = CaptionLook.colors(vm.captionStyle)
                CaptionText(
                    piece.caption, Color(fg), Color(bg), capSize,
                    Modifier.align(BiasAlignment(0f, 2f * vm.captionPos - 1f)),
                )
            }
        }
    }
}

@Composable
fun CaptionText(text: String, fg: Color, bg: Color, size: TextUnit, modifier: Modifier = Modifier) {
    val lines = CaptionLook.wrap(text)
    Text(
        buildAnnotatedString {
            lines.forEachIndexed { i, l ->
                if (i > 0) append("\n")
                withStyle(SpanStyle(color = fg, background = bg)) { append(" $l ") }
            }
        },
        modifier = modifier.padding(horizontal = 8.dp),
        fontSize = size,
        lineHeight = size * 1.3f,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Transport(vm: EditorViewModel) {
    val total = vm.totalMs
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Palette.Amber).clickable { vm.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            if (vm.isPlaying) PauseGlyph(Palette.AmberInk, 16.dp) else PlayGlyph(Palette.AmberInk, 16.dp)
        }
        Spacer(Modifier.width(10.dp))
        Slider(
            value = vm.timelineMs.coerceAtMost(total).toFloat(),
            onValueChange = { vm.seekTimeline(it.toLong()) },
            valueRange = 0f..maxOf(1f, total.toFloat()),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Palette.Fg, activeTrackColor = Palette.Amber, inactiveTrackColor = Palette.Line),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${formatTime(vm.timelineMs)} / ${formatTime(total)}",
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.Muted,
        )
    }
}

@Composable
private fun Timeline(vm: EditorViewModel, onAdd: () -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(vm.currentIndex, vm.isPlaying) {
        if (vm.isPlaying) list.animateScrollToItem(maxOf(0, vm.currentIndex - 1))
    }
    LazyRow(
        state = list,
        modifier = Modifier.fillMaxWidth().height(84.dp).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(vm.pieces, key = { _, p -> p.id }) { i, p ->
            PieceTile(vm, p, i)
        }
        item {
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(64.dp, 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, Palette.Line, RoundedCornerShape(8.dp))
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center,
            ) { Text("+ Add", color = Palette.Muted, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun PieceTile(vm: EditorViewModel, p: Piece, index: Int) {
    val src = vm.source(p.sourceId)
    val selected = p.id == vm.selectedId
    val width = maxOf(28f, p.durMs / 1000f * 30f).dp
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clip(shape)
            .background(Palette.Track)
            .then(if (selected) Modifier.border(2.dp, Palette.Amber, shape) else Modifier)
            .clickable { vm.selectAndSeek(p) },
    ) {
        src?.thumb?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.5f)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(5.dp)) {
            if (p.caption.isNotBlank()) {
                Text(p.caption, fontSize = 10.sp, color = Palette.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            }
            Text(
                String.format(java.util.Locale.US, "%.1fs", p.durMs / 1000f),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = if (selected) Palette.Amber else Palette.Muted,
            )
        }
        if (index == vm.currentIndex) {
            val frac = ((vm.timelineMs - vm.startOf(index)).toFloat() / maxOf(1L, p.durMs)).coerceIn(0f, 1f)
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth(frac).height(3.dp).background(Palette.Fg))
        }
    }
}

@Composable
fun PlayGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.15f, 0f)
            lineTo(this@Canvas.size.width, this@Canvas.size.height / 2f)
            lineTo(this@Canvas.size.width * 0.15f, this@Canvas.size.height)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun PauseGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val bw = this.size.width * 0.32f
        drawRect(color, topLeft = Offset(this.size.width * 0.08f, 0f), size = androidx.compose.ui.geometry.Size(bw, this.size.height))
        drawRect(color, topLeft = Offset(this.size.width * 0.60f, 0f), size = androidx.compose.ui.geometry.Size(bw, this.size.height))
    }
}
