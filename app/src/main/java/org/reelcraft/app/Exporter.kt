package org.reelcraft.app

import android.graphics.Matrix
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.effect.MatrixTransformation
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList

/** Everything the exporter needs, captured at the moment the user taps Export. */
data class ExportPlan(
    val pieces: List<Piece>,
    val sources: Map<Long, Source>,
    val height: Int,
    val framing: Framing,
    val captionStyle: CaptionStyle,
    val captionPos: Float,
    val captionSize: Float,
    val hookText: String,
    val clipVolume: Float,
    val musicUri: Uri?,
    val musicVolume: Float,
) {
    val width: Int get() = height * 9 / 16
}

object Exporter {

    fun composition(plan: ExportPlan): Composition {
        val layout = if (plan.framing == Framing.FILL)
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
        val (fg, bg) = CaptionLook.colors(plan.captionStyle)
        val capPx = (plan.height * CaptionLook.SIZE_OF_HEIGHT * plan.captionSize).toInt()
        val hookPx = (plan.height * CaptionLook.HOOK_SIZE_OF_HEIGHT).toInt()

        val items = plan.pieces.mapIndexed { index, piece ->
            val src = plan.sources.getValue(piece.sourceId)
            val mediaItem = MediaItem.Builder()
                .setUri(src.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(piece.startMs)
                        .setEndPositionMs(piece.endMs)
                        .build()
                )
                .build()

            val overlays = ArrayList<TextureOverlay>()
            if (piece.caption.isNotBlank()) {
                overlays += TextOverlay.createStaticTextOverlay(
                    CaptionLook.spannable(piece.caption, fg, bg, capPx),
                    OverlaySettings.Builder()
                        .setBackgroundFrameAnchor(0f, 1f - 2f * plan.captionPos)
                        .build()
                )
            }
            if (index == 0 && plan.hookText.isNotBlank()) {
                overlays += TextOverlay.createStaticTextOverlay(
                    CaptionLook.spannable(plan.hookText.uppercase(), CaptionLook.AMBER_INK, CaptionLook.AMBER, hookPx),
                    OverlaySettings.Builder()
                        .setBackgroundFrameAnchor(0f, 1f - 2f * CaptionLook.HOOK_POSITION)
                        .build()
                )
            }

            val video = ArrayList<Effect>()
            video += Presentation.createForWidthAndHeight(plan.width, plan.height, layout)
            if (src.zoom > 1.001f) video += zoomEffect(src.zoom, src.panX, src.panY)
            if (overlays.isNotEmpty()) video += OverlayEffect(ImmutableList.copyOf(overlays))

            val audio = ArrayList<AudioProcessor>()
            if (plan.clipVolume < 0.99f) audio += volume(plan.clipVolume)

            EditedMediaItem.Builder(mediaItem).setEffects(Effects(audio, video)).build()
        }

        val sequences = ArrayList<EditedMediaItemSequence>()
        sequences += EditedMediaItemSequence(items)
        plan.musicUri?.let { uri ->
            val music = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setRemoveVideo(true)
                .setEffects(Effects(listOf<AudioProcessor>(volume(plan.musicVolume)), emptyList()))
                .build()
            sequences += EditedMediaItemSequence(listOf(music), /* isLooping= */ true)
        }

        return Composition.Builder(sequences)
            .experimentalSetForceAudioTrack(true)
            .build()
    }

    /**
     * Zooms into the already-vertical frame. Coordinates are -1..1 with y pointing up,
     * so the pan is scaled by (zoom - 1) to keep the frame edges covered.
     */
    private fun zoomEffect(zoom: Float, panX: Float, panY: Float): Effect {
        val m = Matrix()
        m.postScale(zoom, zoom)
        m.postTranslate(-panX * (zoom - 1f), panY * (zoom - 1f))
        return MatrixTransformation { _ -> m }
    }

    private fun volume(gain: Float): AudioProcessor {
        val p = ChannelMixingAudioProcessor()
        for (ch in 1..2) p.putChannelMixingMatrix(ChannelMixingMatrix.create(ch, ch).scaleBy(gain))
        return p
    }
}
