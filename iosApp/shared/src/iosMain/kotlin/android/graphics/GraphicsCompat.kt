package android.graphics

import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

class Typeface internal constructor(internal val native: org.jetbrains.skia.Typeface) {
    companion object {
        const val NORMAL = 0
        fun create(name: String, @Suppress("UNUSED_PARAMETER") style: Int): Typeface =
            Typeface(FontMgr.default.matchFamilyStyle(name, FontStyle.NORMAL))
    }
}

class Paint(@Suppress("UNUSED_PARAMETER") flags: Int = 0) {
    enum class Cap { SQUARE, ROUND }
    var textSize: Float = 16f
    var typeface: Typeface = Typeface.create("Menlo", Typeface.NORMAL)
    var strokeCap: Cap = Cap.SQUARE
    var strokeWidth: Float = 1f
    var color: Int = 0xFF000000.toInt()

    internal fun font() = Font(typeface.native, textSize)
    fun measureText(text: String): Float = font().measureTextWidth(text)
    val fontMetrics: FontMetrics
        get() = font().metrics.let { FontMetrics(it.ascent, it.descent) }

    data class FontMetrics(val ascent: Float, val descent: Float)
    companion object { const val ANTI_ALIAS_FLAG = 1 }
}

object Color {
    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha and 0xff) shl 24) or ((red and 0xff) shl 16) or
            ((green and 0xff) shl 8) or (blue and 0xff)
}
