package org.reelcraft.app

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/** Shared caption rules so the in-app preview and the exported video look the same. */
object CaptionLook {
    const val SIZE_OF_HEIGHT = 0.035f   // caption text height as a share of video height
    const val HOOK_SIZE_OF_HEIGHT = 0.042f
    const val HOOK_POSITION = 0.12f       // share of height from the top

    const val WHITE = 0xFFFFFFFF.toInt()
    const val YELLOW = 0xFFFFD23F.toInt()
    const val DARK_BOX = 0xD90A0A0C.toInt()
    const val AMBER = 0xFFF4B53F.toInt()
    const val AMBER_INK = 0xFF1E1606.toInt()

    fun colors(style: CaptionStyle): Pair<Int, Int> = when (style) {
        CaptionStyle.BOX -> WHITE to DARK_BOX
        CaptionStyle.HIGHLIGHT -> YELLOW to DARK_BOX
    }

    /** Splits a caption into short lines that fit a vertical screen. */
    fun wrap(text: String, maxChars: Int = 22): List<String> {
        val lines = ArrayList<String>()
        var cur = StringBuilder()
        for (word in text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            if (cur.isNotEmpty() && cur.length + 1 + word.length > maxChars) {
                lines.add(cur.toString()); cur = StringBuilder()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(word)
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }

    /** Text for the exported video overlay. */
    fun spannable(text: String, fg: Int, bg: Int, sizePx: Int): SpannableString {
        val body = wrap(text).joinToString("\n") { " $it " }
        val s = SpannableString(body)
        val all = Spanned.SPAN_INCLUSIVE_INCLUSIVE
        s.setSpan(AbsoluteSizeSpan(sizePx), 0, body.length, all)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, body.length, all)
        s.setSpan(ForegroundColorSpan(fg), 0, body.length, all)
        s.setSpan(BackgroundColorSpan(bg), 0, body.length, all)
        s.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, body.length, all)
        return s
    }
}
