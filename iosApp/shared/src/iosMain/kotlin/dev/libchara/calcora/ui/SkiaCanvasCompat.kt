package dev.libchara.calcora.ui

import android.graphics.Paint
import org.jetbrains.skia.PaintMode

fun org.jetbrains.skia.Canvas.drawText(text: String, x: Float, y: Float, paint: Paint) {
    val nativePaint = org.jetbrains.skia.Paint().apply {
        color = paint.color
        isAntiAlias = true
    }
    drawString(text, x, y, paint.font(), nativePaint)
}

fun org.jetbrains.skia.Canvas.drawLine(
    x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint
) {
    val nativePaint = org.jetbrains.skia.Paint().apply {
        color = paint.color
        isAntiAlias = true
        strokeWidth = paint.strokeWidth
        mode = PaintMode.STROKE
        strokeCap = if (paint.strokeCap == Paint.Cap.ROUND)
            org.jetbrains.skia.PaintStrokeCap.ROUND else org.jetbrains.skia.PaintStrokeCap.SQUARE
    }
    drawLine(x1, y1, x2, y2, nativePaint)
}
