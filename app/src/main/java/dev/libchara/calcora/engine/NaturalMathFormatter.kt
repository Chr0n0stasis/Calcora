package dev.libchara.calcora.engine

/**
 * Plain-text accessibility/export fallback. The visual editor uses [MathNode]
 * and performs real 2-D layout; importantly, this class no longer pretends a
 * slash is a fraction by replacing it with a division glyph.
 */
object NaturalMathFormatter {
    fun format(input: String): String = flatten(NaturalMath.parse(input))

    private fun flatten(node: MathNode): String = when (node) {
        is MathNode.Text -> node.value
        is MathNode.Row -> node.items.joinToString("") { flatten(it) }
        is MathNode.Fraction -> "(${flatten(node.numerator)})/(${flatten(node.denominator)})"
        is MathNode.Script -> buildString {
            append(flatten(node.base))
            node.subscript?.let { append("_(").append(flatten(it)).append(')') }
            node.superscript?.let { append("^(").append(flatten(it)).append(')') }
        }
        is MathNode.Root -> buildString {
            append('√')
            node.index?.let { append('[').append(flatten(it)).append(']') }
            append('(').append(flatten(node.radicand)).append(')')
        }
        is MathNode.Integral -> buildString {
            append('∫')
            node.lower?.let { append("_(").append(flatten(it)).append(')') }
            node.upper?.let { append("^(").append(flatten(it)).append(')') }
            node.integrand?.let { append('(').append(flatten(it)).append(')') }
            node.variable?.let { append('d').append('(').append(flatten(it)).append(')') }
        }
        is MathNode.Summation -> buildString {
            append('∑')
            if (node.index != null || node.lower != null) {
                append("_(")
                node.index?.let { append(flatten(it)) }
                if (node.index != null && node.lower != null) append('=')
                node.lower?.let { append(flatten(it)) }
                append(')')
            }
            node.upper?.let { append("^(").append(flatten(it)).append(')') }
            node.expression?.let { append('(').append(flatten(it)).append(')') }
        }
        is MathNode.Derivative -> buildString {
            append('d')
            node.order?.let { append("^(").append(flatten(it)).append(')') }
            append("/d")
            node.variable?.let { append('(').append(flatten(it)).append(')') }
            node.order?.let { append("^(").append(flatten(it)).append(')') }
            append('(').append(flatten(node.expression)).append(')')
        }
        is MathNode.Limit -> buildString {
            append("lim")
            if (node.variable != null || node.point != null) {
                append("_(")
                node.variable?.let { append(flatten(it)) }
                if (node.variable != null && node.point != null) append('→')
                node.point?.let { append(flatten(it)) }
                node.direction?.let { direction ->
                    val raw = flatten(direction).replace("−", "-")
                    when (raw) {
                        "1", "+1" -> append("^(+)")
                        "-1" -> append("^(-)")
                        else -> append(",").append(raw)
                    }
                }
                append(')')
            }
            append('(').append(flatten(node.expression)).append(')')
        }
        is MathNode.Delimited -> node.left + flatten(node.content) + node.right
        is MathNode.Matrix -> node.rows.joinToString(prefix = "[", postfix = "]") { cells ->
            cells.joinToString(prefix = "[", postfix = "]") { flatten(it) }
        }
    }
}
