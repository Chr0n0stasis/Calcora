package dev.libchara.calcora.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libchara.calcora.R
import dev.libchara.calcora.engine.MathNode
import dev.libchara.calcora.engine.MathSource
import dev.libchara.calcora.engine.NaturalMath
import dev.libchara.calcora.engine.NaturalMathEditing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.sin

/** A genuine 2-D math editor: the backing value remains lossless Xcas text. */
@Composable
fun NaturalMathEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Float = 25f,
    syntaxHighlighting: Boolean = true,
    onDone: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val context = LocalContext.current
    val fontPx = with(density) { fontSize.sp.toPx() }
    val mathTypeface = remember(context) { context.resources.getFont(R.font.ibm_3270_regular) }
    val layout = remember(value.text, fontPx, colors.onSurface, colors.primary, colors.secondary, syntaxHighlighting, mathTypeface) {
        MathTypesetter(
            colors.onSurface,
            if (syntaxHighlighting) colors.primary else colors.onSurface,
            if (syntaxHighlighting) colors.secondary else colors.onSurface,
            mathTypeface
        ).layout(
            NaturalMath.parse(value.text), fontPx
        )
    }
    val focusRequester = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val horizontalPaddingPx = with(density) { 10.dp.toPx() }
    val contentWidth = with(density) { (layout.width + horizontalPaddingPx * 2).toDp() }.coerceAtLeast(1.dp)
    val contentHeight = with(density) { (layout.height + 12.dp.toPx()).toDp() }.coerceAtLeast(52.dp)

    LaunchedEffect(value.selection.end, layout.width) {
        val caret = layout.carets.minByOrNull { abs(it.offset - value.selection.end) }
        if (caret != null) scroll.animateScrollTo((caret.x - 160f).coerceAtLeast(0f).toInt())
    }

    Box(modifier = modifier.height(contentHeight), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier.fillMaxWidth().height(contentHeight).horizontalScroll(scroll),
            contentAlignment = Alignment.CenterEnd
        ) {
            Canvas(
                Modifier.width(contentWidth).height(contentHeight).pointerInput(layout, value.text) {
                    detectTapGestures { point ->
                        val local = Offset(point.x - horizontalPaddingPx, point.y - (size.height - layout.height) / 2f)
                        val nearest = layout.carets.minByOrNull {
                            abs(it.x - local.x) + if (local.y in it.top..it.bottom) 0f else 2f * minOf(abs(local.y - it.top), abs(local.y - it.bottom))
                        }
                        onValueChange(value.copy(selection = TextRange(nearest?.offset ?: value.text.length), composition = null))
                        focusRequester.requestFocus()
                    }
                }
            ) {
                val originX = horizontalPaddingPx
                val originY = (size.height - layout.height) / 2f
                val selectionStart = value.selection.min
                val selectionEnd = value.selection.max
                if (selectionStart != selectionEnd) {
                    layout.carets.zipWithNext().forEach { (a, b) ->
                        if (a.offset in selectionStart until selectionEnd) {
                            drawRect(
                                colors.primary.copy(alpha = 0.18f),
                                topLeft = Offset(originX + a.x, originY + minOf(a.top, b.top)),
                                size = androidx.compose.ui.geometry.Size(max(2f, b.x - a.x), max(a.bottom, b.bottom) - minOf(a.top, b.top))
                            )
                        }
                    }
                }
                drawIntoCanvas { composeCanvas ->
                    val canvas = composeCanvas.nativeCanvas
                    layout.texts.forEach { item ->
                        item.paint.color = item.color.toArgbCompat()
                        canvas.drawText(item.text, originX + item.x, originY + item.baseline, item.paint)
                    }
                    layout.lines.forEach { line ->
                        line.paint.color = line.color.toArgbCompat()
                        canvas.drawLine(originX + line.x1, originY + line.y1, originX + line.x2, originY + line.y2, line.paint)
                    }
                }
                if (value.selection.collapsed) {
                    layout.carets.minByOrNull { abs(it.offset - value.selection.end) }?.let { caret ->
                        drawLine(
                            colors.primary,
                            Offset(originX + caret.x, originY + caret.top),
                            Offset(originX + caret.x, originY + caret.bottom),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
        }
        // This one-pixel field owns the IME; all visible editing and hit testing is structural.
        BasicTextField(
            value = value,
            onValueChange = { proposed ->
                if (
                    proposed.text == value.text && value.selection.collapsed && proposed.selection.collapsed &&
                    abs(proposed.selection.start - value.selection.start) == 1
                ) {
                    val direction = if (proposed.selection.start > value.selection.start) 1 else -1
                    val moved = NaturalMathEditing.moveHorizontally(value.text, value.selection.start, direction)
                    onValueChange(value.copy(text = moved.text, selection = TextRange(moved.selectionStart), composition = null))
                    return@BasicTextField
                }
                val adjusted = NaturalMathEditing.adjust(
                    value.text, value.selection.start, value.selection.end,
                    proposed.text, proposed.selection.start, proposed.selection.end
                )
                onValueChange(
                    proposed.copy(
                        text = adjusted.text,
                        selection = TextRange(adjusted.selectionStart, adjusted.selectionEnd),
                        composition = null
                    )
                )
            },
            modifier = Modifier.size(1.dp).align(Alignment.BottomEnd).focusRequester(focusRequester),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            singleLine = true
        )
    }
}

@Composable
fun NaturalMathView(
    source: String,
    modifier: Modifier = Modifier,
    sourceKind: MathSource = MathSource.Xcas,
    fontSize: Float = 20f,
    color: Color = Color.Unspecified,
    minHeight: Dp = 34.dp
) {
    val resolvedColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    val density = LocalDensity.current
    val context = LocalContext.current
    val fontPx = with(density) { fontSize.sp.toPx() }
    val mathTypeface = remember(context) { context.resources.getFont(R.font.ibm_3270_regular) }
    val layout = remember(source, sourceKind, fontPx, resolvedColor, mathTypeface) {
        MathTypesetter(resolvedColor, resolvedColor, resolvedColor, mathTypeface)
            .layout(NaturalMath.parse(source, sourceKind), fontPx)
    }
    val width = with(density) { (layout.width + 8.dp.toPx()).toDp() }.coerceAtLeast(1.dp)
    val height = with(density) { (layout.height + 8.dp.toPx()).toDp() }.coerceAtLeast(minHeight)
    val scroll = rememberScrollState()
    Box(modifier.height(height).horizontalScroll(scroll), contentAlignment = Alignment.CenterEnd) {
        Canvas(Modifier.width(width).height(height)) {
            val ox = 4.dp.toPx()
            val oy = (size.height - layout.height) / 2f
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                layout.texts.forEach { item ->
                    item.paint.color = item.color.toArgbCompat()
                    canvas.drawText(item.text, ox + item.x, oy + item.baseline, item.paint)
                }
                layout.lines.forEach { line ->
                    line.paint.color = line.color.toArgbCompat()
                    canvas.drawLine(ox + line.x1, oy + line.y1, ox + line.x2, oy + line.y2, line.paint)
                }
            }
        }
    }
}

private data class MathLayout(
    val width: Float,
    val height: Float,
    val baseline: Float,
    val texts: List<DrawText>,
    val lines: List<DrawLine>,
    val carets: List<Caret>
)
private data class DrawText(val text: String, val x: Float, val baseline: Float, val paint: Paint, val color: Color)
private data class DrawLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val paint: Paint, val color: Color)
private data class Caret(val offset: Int, val x: Float, val top: Float, val bottom: Float)

private class MathTypesetter(
    private val textColor: Color,
    private val accentColor: Color,
    private val numberColor: Color,
    private val mathTypeface: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
) {
    fun layout(node: MathNode, size: Float): MathLayout = box(node, size)

    private fun box(node: MathNode, size: Float): MathLayout = when (node) {
        is MathNode.Text -> text(node, size)
        is MathNode.Row -> row(node, size)
        is MathNode.Fraction -> fraction(node, size)
        is MathNode.Script -> script(node, size)
        is MathNode.Root -> root(node, size)
        is MathNode.Integral -> integral(node, size)
        is MathNode.Summation -> summation(node, size)
        is MathNode.Derivative -> derivative(node, size)
        is MathNode.Delimited -> delimited(node, size)
        is MathNode.Matrix -> matrix(node, size)
    }

    private fun text(node: MathNode.Text, size: Float): MathLayout {
        val color = when {
            node.value.any(Char::isDigit) && node.value.none(Char::isLetter) -> numberColor
            node.value.firstOrNull()?.isLetter() == true && node.value.length > 1 -> accentColor
            else -> textColor
        }
        val paint = paint(size)
        val width = paint.measureText(node.value).coerceAtLeast(if (node.value.isEmpty()) size * .18f else 0f)
        val metrics = paint.fontMetrics
        val height = metrics.descent - metrics.ascent
        val baseline = -metrics.ascent
        val count = (node.end - node.start).coerceAtLeast(1)
        val carets = (0..count).map { index ->
            Caret((node.start + index).coerceAtMost(node.end), width * index / count, 0f, height)
        }
        return MathLayout(width, height, baseline, listOf(DrawText(node.value, 0f, baseline, paint, color)), emptyList(), carets)
    }

    private fun row(node: MathNode.Row, size: Float): MathLayout {
        val boxes = node.items.mapIndexed { index, item ->
            val child = box(item, size)
            val operator = (item as? MathNode.Text)?.value
            val spacing = when (operator) {
                "+", "−", "-", "=", "≠", "≤", "≥", "<", ">", "≔", "→" -> size * .16f
                "×", "·", "*", "%" -> size * .11f
                else -> 0f
            }
            if (spacing > 0f && index in 1 until node.items.lastIndex) {
                horizontalPadding(child, spacing, spacing)
            } else child
        }
        return combine(boxes, size)
    }

    private fun horizontalPadding(layout: MathLayout, left: Float, right: Float): MathLayout = layout.copy(
        width = layout.width + left + right,
        texts = layout.texts.map { it.copy(x = it.x + left) },
        lines = layout.lines.map { it.copy(x1 = it.x1 + left, x2 = it.x2 + left) },
        carets = layout.carets.map { it.copy(x = it.x + left) }
    )

    private fun combine(boxes: List<MathLayout>, size: Float): MathLayout {
        val baseline = boxes.maxOfOrNull { it.baseline } ?: size
        val descent = boxes.maxOfOrNull { it.height - it.baseline } ?: size * .25f
        val height = baseline + descent
        var x = 0f
        val texts = mutableListOf<DrawText>()
        val lines = mutableListOf<DrawLine>()
        val carets = mutableListOf<Caret>()
        boxes.forEach { child ->
            val y = baseline - child.baseline
            texts += child.texts.map { it.copy(x = it.x + x, baseline = it.baseline + y) }
            lines += child.lines.map { it.copy(x1 = it.x1 + x, x2 = it.x2 + x, y1 = it.y1 + y, y2 = it.y2 + y) }
            carets += child.carets.map { it.copy(x = it.x + x, top = it.top + y, bottom = it.bottom + y) }
            x += child.width
        }
        return MathLayout(x, height, baseline, texts, lines, carets)
    }

    private fun fraction(node: MathNode.Fraction, size: Float): MathLayout {
        val numerator = fractionSlot(node.numerator, size * .82f)
        val denominator = fractionSlot(node.denominator, size * .82f)
        val pad = size * .16f
        val gap = size * .11f
        val width = max(numerator.width, denominator.width) + pad * 2
        val lineY = numerator.height + gap
        val denominatorY = lineY + gap
        val height = denominatorY + denominator.height
        val nx = (width - numerator.width) / 2
        val dx = (width - denominator.width) / 2
        val linePaint = paint(max(1.2f, size * .055f)).apply { strokeWidth = max(1.2f, size * .055f) }
        val texts = numerator.texts.map { it.copy(x = it.x + nx) } + denominator.texts.map { it.copy(x = it.x + dx, baseline = it.baseline + denominatorY) }
        val lines = numerator.lines.map { it.copy(x1 = it.x1 + nx, x2 = it.x2 + nx) } +
            denominator.lines.map { it.copy(x1 = it.x1 + dx, x2 = it.x2 + dx, y1 = it.y1 + denominatorY, y2 = it.y2 + denominatorY) } +
            DrawLine(0f, lineY, width, lineY, linePaint, textColor)
        val carets = numerator.carets.map { it.copy(x = it.x + nx) } +
            denominator.carets.map { it.copy(x = it.x + dx, top = it.top + denominatorY, bottom = it.bottom + denominatorY) } +
            listOf(
                Caret(node.barStart, width / 2, lineY - gap, lineY + gap),
                Caret(node.barEnd, dx, denominatorY, height),
                Caret(node.start, 0f, lineY - size * .28f, lineY + size * .28f),
                Caret(node.end, width, lineY - size * .28f, lineY + size * .28f)
            )
        // A text baseline sits below its visual math axis. Expose a baseline
        // whose axis coincides with the fraction bar so neighboring terms are
        // vertically centered instead of floating near the numerator.
        val mathAxisFromBaseline = size * .25f
        return MathLayout(width, height, lineY + mathAxisFromBaseline, texts, lines, carets)
    }

    /** Whole-slot parentheses carry edit scope and are intentionally not drawn. */
    private fun fractionSlot(node: MathNode, size: Float): MathLayout {
        if (
            node !is MathNode.Delimited || node.left != "(" || node.right != ")" ||
            node.end <= node.content.end
        ) return box(node, size)
        val content = box(node.content, size)
        return content.copy(carets = content.carets.distinctBy { it.offset }.sortedBy { it.offset })
    }

    private fun script(node: MathNode.Script, size: Float): MathLayout {
        val base = box(node.base, size)
        val sup = node.superscript?.let { scriptSlot(it, size * .64f) }
        val sub = node.subscript?.let { box(it, size * .64f) }
        val scriptWidth = max(sup?.width ?: 0f, sub?.width ?: 0f)
        val top = sup?.height?.times(.72f) ?: 0f
        val baseY = top
        val supY = 0f
        val subY = baseY + base.baseline + size * .12f
        val height = max(baseY + base.height, subY + (sub?.height ?: 0f))
        val texts = base.texts.map { it.copy(baseline = it.baseline + baseY) } +
            (sup?.texts?.map { it.copy(x = it.x + base.width, baseline = it.baseline + supY) } ?: emptyList()) +
            (sub?.texts?.map { it.copy(x = it.x + base.width, baseline = it.baseline + subY) } ?: emptyList())
        val lines = shiftLines(base.lines, 0f, baseY) + shiftLines(sup?.lines.orEmpty(), base.width, supY) + shiftLines(sub?.lines.orEmpty(), base.width, subY)
        val carets = shiftCarets(base.carets, 0f, baseY) + shiftCarets(sup?.carets.orEmpty(), base.width, supY) + shiftCarets(sub?.carets.orEmpty(), base.width, subY)
        return MathLayout(base.width + scriptWidth, height, baseY + base.baseline, texts, lines, carets)
    }

    /** Parentheses around a whole exponent define its edit scope, not ink. */
    private fun scriptSlot(node: MathNode, size: Float): MathLayout {
        if (
            node !is MathNode.Delimited || node.left != "(" || node.right != ")" ||
            node.end <= node.content.end
        ) return box(node, size)
        val content = box(node.content, size)
        return content.copy(carets = content.carets.distinctBy { it.offset }.sortedBy { it.offset })
    }

    private fun root(node: MathNode.Root, size: Float): MathLayout {
        val radicand = box(node.radicand, size * .88f)
        val index = node.index?.let { box(it, size * .48f) }
        val topGap = size * .14f
        val height = radicand.height + topGap
        val indexWidth = index?.width?.times(.72f) ?: 0f
        val radicalWidth = size * .58f
        val joinX = indexWidth + radicalWidth
        val contentGap = size * .07f
        val radicandX = joinX + contentGap
        val overhang = size * .07f
        val barEnd = radicandX + radicand.width + overhang
        val bottom = height - size * .04f
        val stroke = max(1.35f, size * .055f)
        val radicalPaint = paint(stroke).apply { strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        val overbarPaint = paint(stroke).apply { strokeWidth = stroke; strokeCap = Paint.Cap.BUTT }

        // Draw one continuous radical shape instead of combining the font's
        // √ glyph with a second overbar. The join and overbar share exactly
        // one point, and the diagonal scales with a tall fraction/radicand.
        val leadX = indexWidth + radicalWidth * .02f
        val notchX = indexWidth + radicalWidth * .24f
        val valleyX = indexWidth + radicalWidth * .43f
        val leadY = height * .58f
        val notchY = height * .55f
        val radicalLines = listOf(
            DrawLine(leadX, leadY, notchX, notchY, radicalPaint, textColor),
            DrawLine(notchX, notchY, valleyX, bottom, radicalPaint, textColor),
            DrawLine(valleyX, bottom, joinX, 0f, radicalPaint, textColor),
            DrawLine(joinX, 0f, barEnd, 0f, overbarPaint, textColor)
        )
        val indexY = index?.let { (height * .43f - it.height).coerceAtLeast(0f) } ?: 0f
        val texts = radicand.texts.map { it.copy(x = it.x + radicandX, baseline = it.baseline + topGap) } +
            (index?.texts?.map { it.copy(baseline = it.baseline + indexY) } ?: emptyList())
        val lines = radicalLines + shiftLines(radicand.lines, radicandX, topGap) +
            (index?.let { shiftLines(it.lines, 0f, indexY) } ?: emptyList())
        val carets = shiftCarets(radicand.carets, radicandX, topGap) +
            (index?.let { shiftCarets(it.carets, 0f, indexY) } ?: emptyList()) +
            listOf(Caret(node.start, 0f, 0f, height), Caret(node.end, barEnd, 0f, height))
        return MathLayout(barEnd, height, topGap + radicand.baseline, texts, lines, carets.distinctBy { it.offset })
    }

    private fun integral(node: MathNode.Integral, size: Float): MathLayout {
        val operator = largeOperator("∫", node.start, node.end, node.lower?.let { box(it, size * .58f) }, node.upper?.let { box(it, size * .58f) }, size * 1.52f)
        if (node.integrand == null) return operator.copy(
            carets = (operator.carets + Caret(node.end, operator.width, 0f, operator.height)).distinctBy { it.offset }
        )

        val integrand = horizontalPadding(box(node.integrand, size), size * .18f, size * .13f)
        val differential = node.variable?.let { variable ->
            combine(
                listOf(
                    text(MathNode.Text("d", variable.start, variable.start), size * .84f),
                    box(variable, size * .84f)
                ),
                size
            )
        }
        val result = combine(listOfNotNull(operator, integrand, differential), size)
        return result.copy(carets = (result.carets + listOf(
            Caret(node.start, 0f, 0f, result.height),
            Caret(node.end, result.width, 0f, result.height)
        )).distinctBy { it.offset })
    }

    private fun summation(node: MathNode.Summation, size: Float): MathLayout {
        val lower = when {
            node.index != null && node.lower != null -> combine(
                listOf(
                    box(node.index, size * .58f),
                    text(MathNode.Text("=", node.index.end, node.lower.start), size * .58f),
                    box(node.lower, size * .58f)
                ),
                size * .58f
            )
            node.index != null -> box(node.index, size * .58f)
            else -> node.lower?.let { box(it, size * .58f) }
        }
        val operator = largeOperator("∑", node.start, node.end, lower, node.upper?.let { box(it, size * .58f) }, size * 1.34f)
        if (node.expression == null) return operator.copy(
            carets = (operator.carets + Caret(node.end, operator.width, 0f, operator.height)).distinctBy { it.offset }
        )
        val expression = horizontalPadding(box(node.expression, size), size * .20f, 0f)
        val result = combine(listOf(operator, expression), size)
        return result.copy(carets = (result.carets + listOf(
            Caret(node.start, 0f, 0f, result.height),
            Caret(node.end, result.width, 0f, result.height)
        )).distinctBy { it.offset })
    }

    private fun derivative(node: MathNode.Derivative, size: Float): MathLayout {
        val marker = node.start
        val d = MathNode.Text("d", marker, marker)
        val numerator: MathNode = node.order?.let {
            MathNode.Script(d, superscript = it, start = marker, end = it.end)
        } ?: d
        val denominatorBase: MathNode = node.variable?.let { variable ->
            MathNode.Row(listOf(d, variable), marker, variable.end)
        } ?: d
        val denominator: MathNode = node.order?.let {
            MathNode.Script(denominatorBase, superscript = it, start = marker, end = it.end)
        } ?: denominatorBase
        val operator = fraction(
            MathNode.Fraction(numerator, denominator, marker, marker, node.start, node.variable?.end ?: marker),
            size * .92f
        )
        val expression = horizontalPadding(box(node.expression, size), size * .18f, 0f)
        val result = combine(listOf(operator, expression), size)
        return result.copy(carets = (result.carets + listOf(
            Caret(node.start, 0f, 0f, result.height),
            Caret(node.end, result.width, 0f, result.height)
        )).distinctBy { it.offset })
    }

    private fun largeOperator(
        symbol: String,
        start: Int,
        end: Int,
        lower: MathLayout?,
        upper: MathLayout?,
        glyphSize: Float
    ): MathLayout {
        // Function names and commas are deliberately hidden. Do not spread
        // all of their source offsets across the glyph: argument carets must
        // remain attached to the visible argument that owns each offset.
        val glyph = text(MathNode.Text(symbol, start, start), glyphSize)
        val gap = glyphSize * .045f
        val width = max(glyph.width, max(lower?.width ?: 0f, upper?.width ?: 0f))
        val upperY = 0f
        val glyphY = (upper?.height ?: 0f) + if (upper != null) gap else 0f
        val lowerY = glyphY + glyph.height + if (lower != null) gap else 0f
        val height = lowerY + (lower?.height ?: 0f)
        val glyphX = (width - glyph.width) / 2f
        val upperX = (width - (upper?.width ?: 0f)) / 2f
        val lowerX = (width - (lower?.width ?: 0f)) / 2f
        val texts = glyph.texts.map { it.copy(x = it.x + glyphX, baseline = it.baseline + glyphY) } +
            (upper?.texts?.map { it.copy(x = it.x + upperX, baseline = it.baseline + upperY) } ?: emptyList()) +
            (lower?.texts?.map { it.copy(x = it.x + lowerX, baseline = it.baseline + lowerY) } ?: emptyList())
        val lines = shiftLines(glyph.lines, glyphX, glyphY) + shiftLines(upper?.lines.orEmpty(), upperX, upperY) + shiftLines(lower?.lines.orEmpty(), lowerX, lowerY)
        val carets = shiftCarets(glyph.carets, glyphX, glyphY) +
            shiftCarets(upper?.carets.orEmpty(), upperX, upperY) +
            shiftCarets(lower?.carets.orEmpty(), lowerX, lowerY)
        return MathLayout(width, height, glyphY + glyph.baseline, texts, lines, carets)
    }

    private fun delimited(node: MathNode.Delimited, size: Float): MathLayout {
        val content = box(node.content, size)
        val verticalPadding = if (content.height > size * 1.25f) size * .10f else 0f
        val height = max(content.height + verticalPadding * 2f, size * 1.05f)
        val contentY = (height - content.height) / 2f
        val leftWidth = delimiterWidth(node.left, size)
        val rightWidth = delimiterWidth(node.right, size)
        val leftGap = if (node.left.isEmpty()) 0f else size * .14f
        val rightGap = if (node.right.isEmpty()) 0f else size * .14f
        val contentX = leftWidth + leftGap
        val rightX = contentX + content.width + rightGap
        val totalWidth = rightX + rightWidth
        val stroke = max(1.35f, size * .055f)
        val linePaint = paint(stroke).apply { strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        val lines = mutableListOf<DrawLine>()
        lines += delimiterLines(node.left, left = true, x = 0f, width = leftWidth, height = height, paint = linePaint)
        lines += shiftLines(content.lines, contentX, contentY)
        lines += delimiterLines(node.right, left = false, x = rightX, width = rightWidth, height = height, paint = linePaint)
        val texts = content.texts.map { it.copy(x = it.x + contentX, baseline = it.baseline + contentY) }
        val carets = shiftCarets(content.carets, contentX, contentY) + listOf(
            Caret(node.start, 0f, 0f, height),
            Caret((node.start + 1).coerceAtMost(node.end), contentX, contentY, contentY + content.height),
            Caret((node.end - 1).coerceAtLeast(node.start), contentX + content.width, contentY, contentY + content.height),
            Caret(node.end, totalWidth, 0f, height)
        )
        return MathLayout(totalWidth, height, contentY + content.baseline, texts, lines, carets.distinctBy { it.offset })
    }

    private fun delimiterWidth(symbol: String, size: Float): Float = when (symbol) {
        "" -> 0f
        "|", "∣" -> size * .12f
        "[", "]", "{", "}" -> size * .25f
        else -> size * .38f
    }

    private fun delimiterLines(
        symbol: String,
        left: Boolean,
        x: Float,
        width: Float,
        height: Float,
        paint: Paint
    ): List<DrawLine> {
        if (symbol.isEmpty()) return emptyList()
        if (symbol in setOf("|", "∣")) {
            val px = x + width / 2f
            return listOf(DrawLine(px, 0f, px, height, paint, textColor))
        }
        if (symbol == "[" || symbol == "]") {
            val spine = if (left) x else x + width
            val tip = if (left) x + width else x
            return listOf(
                DrawLine(tip, 0f, spine, 0f, paint, textColor),
                DrawLine(spine, 0f, spine, height, paint, textColor),
                DrawLine(spine, height, tip, height, paint, textColor)
            )
        }
        if (symbol == "{" || symbol == "}") {
            val points = (0..12).map { index ->
                val t = index / 12f
                val wave = if (t <= .5f) sin(t * 2f * PI).toFloat() else -sin((t - .5f) * 2f * PI).toFloat()
                val px = if (left) x + width * (.72f - .55f * wave) else x + width * (.28f + .55f * wave)
                px to height * t
            }
            return points.zipWithNext { a, b -> DrawLine(a.first, a.second, b.first, b.second, paint, textColor) }
        }
        val top = width * .88f to 0f
        val middle = width * .08f to height * .5f
        val bottom = width * .88f to height
        val upper = cubicPoints(
            top,
            width * .42f to height * .08f,
            width * .08f to height * .30f,
            middle
        )
        val lower = cubicPoints(
            middle,
            width * .08f to height * .70f,
            width * .42f to height * .92f,
            bottom
        ).drop(1)
        val points = (upper + lower).map { (localX, localY) ->
            (if (left) x + localX else x + width - localX) to localY
        }
        return points.zipWithNext { a, b -> DrawLine(a.first, a.second, b.first, b.second, paint, textColor) }
    }

    private fun cubicPoints(
        p0: Pair<Float, Float>,
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>
    ): List<Pair<Float, Float>> = (0..12).map { step ->
        val t = step / 12f
        val u = 1f - t
        val a = u * u * u
        val b = 3f * u * u * t
        val c = 3f * u * t * t
        val d = t * t * t
        (a * p0.first + b * p1.first + c * p2.first + d * p3.first) to
            (a * p0.second + b * p1.second + c * p2.second + d * p3.second)
    }

    private fun matrix(node: MathNode.Matrix, size: Float): MathLayout {
        if (node.rows.isEmpty()) return text(MathNode.Text("[]", node.start, node.end), size)
        val boxes = node.rows.map { row -> row.map { box(it, size * .78f) } }
        val columns = boxes.maxOfOrNull { it.size } ?: 0
        val columnWidths = (0 until columns).map { column ->
            boxes.maxOfOrNull { it.getOrNull(column)?.width ?: 0f } ?: 0f
        }
        val rowHeights = boxes.map { row -> row.maxOfOrNull { it.height } ?: size }
        val hGap = size * .45f
        val vGap = size * .20f
        val bracketWidth = size * .28f
        val innerWidth = columnWidths.sum() + hGap * (columns - 1).coerceAtLeast(0)
        val height = rowHeights.sum() + vGap * (boxes.size - 1).coerceAtLeast(0)
        val texts = mutableListOf<DrawText>()
        val lines = mutableListOf<DrawLine>()
        val carets = mutableListOf<Caret>()
        var y = 0f
        boxes.forEachIndexed { rowIndex, row ->
            var x = bracketWidth + hGap * .45f
            row.forEachIndexed { columnIndex, cell ->
                val cellX = x + (columnWidths[columnIndex] - cell.width) / 2f
                val cellY = y + (rowHeights[rowIndex] - cell.height) / 2f
                texts += cell.texts.map { it.copy(x = it.x + cellX, baseline = it.baseline + cellY) }
                lines += shiftLines(cell.lines, cellX, cellY)
                carets += shiftCarets(cell.carets, cellX, cellY)
                x += columnWidths[columnIndex] + hGap
            }
            y += rowHeights[rowIndex] + vGap
        }
        val totalWidth = innerWidth + bracketWidth * 2 + hGap * .9f
        val bracketPaint = paint(max(1.2f, size * .055f)).apply { strokeWidth = max(1.2f, size * .055f) }
        val notch = bracketWidth * .72f
        lines += listOf(
            DrawLine(bracketWidth, 0f, 0f, 0f, bracketPaint, textColor),
            DrawLine(0f, 0f, 0f, height, bracketPaint, textColor),
            DrawLine(0f, height, bracketWidth, height, bracketPaint, textColor),
            DrawLine(totalWidth - bracketWidth, 0f, totalWidth, 0f, bracketPaint, textColor),
            DrawLine(totalWidth, 0f, totalWidth, height, bracketPaint, textColor),
            DrawLine(totalWidth, height, totalWidth - bracketWidth, height, bracketPaint, textColor)
        )
        carets += Caret(node.start, notch, 0f, height)
        carets += Caret(node.end, totalWidth - notch, 0f, height)
        return MathLayout(totalWidth, height, height / 2f + size * .25f, texts, lines, carets)
    }

    private fun paint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = mathTypeface
        strokeCap = Paint.Cap.SQUARE
    }

    private fun shiftLines(lines: List<DrawLine>, x: Float, y: Float) = lines.map { it.copy(x1 = it.x1 + x, x2 = it.x2 + x, y1 = it.y1 + y, y2 = it.y2 + y) }
    private fun shiftCarets(carets: List<Caret>, x: Float, y: Float) = carets.map { it.copy(x = it.x + x, top = it.top + y, bottom = it.bottom + y) }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)
