package dev.libchara.calcora.engine

/**
 * A small, fault-tolerant mathematical syntax tree shared by the editor and
 * result renderer. Source ranges are UTF-16 offsets, matching TextFieldValue.
 */
sealed interface MathNode {
    val start: Int
    val end: Int

    data class Text(val value: String, override val start: Int, override val end: Int) : MathNode
    data class Row(val items: List<MathNode>, override val start: Int, override val end: Int) : MathNode
    data class Fraction(
        val numerator: MathNode,
        val denominator: MathNode,
        val barStart: Int,
        val barEnd: Int,
        override val start: Int,
        override val end: Int
    ) : MathNode
    data class Script(
        val base: MathNode,
        val superscript: MathNode? = null,
        val subscript: MathNode? = null,
        override val start: Int,
        override val end: Int
    ) : MathNode
    data class Root(
        val radicand: MathNode,
        val index: MathNode? = null,
        override val start: Int,
        override val end: Int
    ) : MathNode
    data class Integral(
        val integrand: MathNode? = null,
        val variable: MathNode? = null,
        val lower: MathNode? = null,
        val upper: MathNode? = null,
        override val start: Int,
        override val end: Int
    ) : MathNode
    data class Summation(
        val expression: MathNode? = null,
        val index: MathNode? = null,
        val lower: MathNode? = null,
        val upper: MathNode? = null,
        override val start: Int,
        override val end: Int
    ) : MathNode
    data class Derivative(
        val expression: MathNode,
        val variable: MathNode? = null,
        val order: MathNode? = null,
        override val start: Int,
        override val end: Int
    ) : MathNode
    data class Limit(
        val expression: MathNode,
        val variable: MathNode? = null,
        val point: MathNode? = null,
        val direction: MathNode? = null,
        override val start: Int,
        override val end: Int
    ) : MathNode
    data class Delimited(
        val left: String,
        val content: MathNode,
        val right: String,
        override val start: Int,
        override val end: Int
    ) : MathNode
    data class Matrix(
        val rows: List<List<MathNode>>,
        override val start: Int,
        override val end: Int
    ) : MathNode
}

enum class MathSource { Xcas, Latex }

object NaturalMath {
    fun parse(source: String, kind: MathSource = MathSource.Xcas): MathNode = try {
        if (kind == MathSource.Latex) LatexParser(source).parse() else XcasParser(source).parse()
    } catch (_: StackOverflowError) {
        MathNode.Text(source, 0, source.length)
    } catch (_: RuntimeException) {
        MathNode.Text(source, 0, source.length)
    }

    /**
     * Converts calculator glyphs to Giac syntax while preserving the meaning
     * shown by the natural-math tree. Giac extends an unseparated power in
     * inputs such as `10^(-3)t`, although the editor lays `t` out beside the
     * power as an implicit factor. Insert the multiplication explicitly at
     * that boundary so display and evaluation can never disagree.
     */
    fun toGiac(source: String): String {
        val productsAfterPowers = implicitProductsAfterPowers(parse(source), source)
        return buildString(source.length + productsAfterPowers.size) {
            source.forEachIndexed { index, ch ->
                append(
                    when (ch) {
                        '×', '·' -> '*'
                        '÷' -> '/'
                        '−' -> '-'
                        'π' -> "pi"
                        '∞' -> "infinity"
                        '≤' -> "<="
                        '≥' -> ">="
                        '≠' -> "!="
                        else -> ch
                    }
                )
                if (index + 1 in productsAfterPowers) append('*')
            }
        }.trim()
    }

    private fun implicitProductsAfterPowers(root: MathNode, source: String): Set<Int> = buildSet {
        fun nextNonSpace(offset: Int): Char? {
            var index = offset
            while (index < source.length && source[index].isWhitespace()) index++
            return source.getOrNull(index)
        }

        fun startsFactor(ch: Char?): Boolean = ch != null && (
            ch.isLetterOrDigit() || ch == '_' || ch == '∞' || ch == 'π' ||
                ch in "([{"
            )

        fun visit(node: MathNode) {
            when (node) {
                is MathNode.Script -> {
                    if (node.superscript != null && startsFactor(nextNonSpace(node.end))) add(node.end)
                    visit(node.base)
                    node.superscript?.let(::visit)
                    node.subscript?.let(::visit)
                }
                is MathNode.Fraction -> {
                    visit(node.numerator)
                    visit(node.denominator)
                }
                is MathNode.Row -> node.items.forEach(::visit)
                is MathNode.Root -> {
                    visit(node.radicand)
                    node.index?.let(::visit)
                }
                is MathNode.Integral -> {
                    node.integrand?.let(::visit)
                    node.variable?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Summation -> {
                    node.expression?.let(::visit)
                    node.index?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Derivative -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.order?.let(::visit)
                }
                is MathNode.Limit -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.point?.let(::visit)
                    node.direction?.let(::visit)
                }
                is MathNode.Delimited -> visit(node.content)
                is MathNode.Matrix -> node.rows.flatten().forEach(::visit)
                is MathNode.Text -> Unit
            }
        }
        visit(root)
    }
}

data class NaturalMathEdit(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

/**
 * Keeps edits made in visually selected fraction and exponent slots inside
 * those slots.
 * Parentheses inserted here are structural and are suppressed by the 2-D
 * fraction layout, so the user never has to manage them manually.
 */
object NaturalMathEditing {
    private enum class SlotRole { Numerator, Denominator }
    private data class ExponentSlot(val node: MathNode, val script: MathNode.Script) {
        private val closedGroup: MathNode.Delimited?
            get() = (node as? MathNode.Delimited)?.takeIf {
                it.left == "(" && it.right == ")" && it.end > it.content.end
            }
        val visibleStart: Int get() = closedGroup?.content?.start ?: node.start
        val visibleEnd: Int get() = closedGroup?.content?.end ?: node.end
        val grouped: Boolean get() = closedGroup != null
    }
    private data class FractionSlot(val node: MathNode, val role: SlotRole, val fraction: MathNode.Fraction) {
        private val closedGroup: MathNode.Delimited?
            get() = (node as? MathNode.Delimited)?.takeIf {
                it.left == "(" && it.right == ")" && it.end > it.content.end
            }
        val visibleStart: Int get() = closedGroup?.content?.start ?: node.start
        val visibleEnd: Int get() = closedGroup?.content?.end ?: node.end
        val grouped: Boolean get() = closedGroup != null
    }

    fun adjust(
        oldText: String,
        oldSelectionStart: Int,
        oldSelectionEnd: Int,
        newText: String,
        newSelectionStart: Int,
        newSelectionEnd: Int = newSelectionStart
    ): NaturalMathEdit {
        val selectionStart = minOf(oldSelectionStart, oldSelectionEnd).coerceIn(0, oldText.length)
        val selectionEnd = maxOf(oldSelectionStart, oldSelectionEnd).coerceIn(0, oldText.length)
        if (
            selectionStart == selectionEnd &&
            oldText.getOrNull(selectionStart - 1) == '(' &&
            oldText.getOrNull(selectionStart) == ')' &&
            newText == oldText.removeRange(selectionStart - 1, selectionStart)
        ) {
            return backspace(oldText, selectionStart, selectionEnd)
        }
        val inserted = insertedText(oldText, selectionStart, selectionEnd, newText) ?: return NaturalMathEdit(newText, newSelectionStart, newSelectionEnd)
        if (inserted.isEmpty()) return NaturalMathEdit(newText, newSelectionStart, newSelectionEnd)

        val exponent = exponentSlots(NaturalMath.parse(oldText))
            .filter { selectionStart >= it.visibleStart && selectionEnd <= it.visibleEnd }
            .minByOrNull { it.node.end - it.node.start }
        if (exponent != null) {
            if (exponent.grouped) return NaturalMathEdit(newText, newSelectionStart, newSelectionEnd)
            val delta = newText.length - oldText.length
            return groupSlot(
                newText,
                exponent.node.start,
                exponent.node.end + delta,
                newSelectionStart,
                newSelectionEnd
            )
        }

        if (inserted.none(::isSlotExtendingOperator)) return NaturalMathEdit(newText, newSelectionStart, newSelectionEnd)

        val slot = fractionSlots(NaturalMath.parse(oldText))
            .filter { selectionStart >= it.node.start && selectionEnd <= it.node.end }
            .minByOrNull { it.node.end - it.node.start }
            ?: return NaturalMathEdit(newText, newSelectionStart, newSelectionEnd)
        if (slot.node is MathNode.Delimited && slot.node.left == "(" && slot.node.right == ")") {
            return NaturalMathEdit(newText, newSelectionStart, newSelectionEnd)
        }

        return groupSlot(
            newText,
            slot.node.start,
            slot.node.end + newText.length - oldText.length,
            newSelectionStart,
            newSelectionEnd
        )
    }

    /**
     * Materializes every closing delimiter that the renderer currently
     * infers. Evaluation and history must use the same syntax the user sees.
     */
    fun commitInferredDelimiters(
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart
    ): NaturalMathEdit {
        val insertions = inferredDelimiterInsertions(NaturalMath.parse(text))
        if (insertions.isEmpty()) {
            return NaturalMathEdit(
                text,
                selectionStart.coerceIn(0, text.length),
                selectionEnd.coerceIn(0, text.length)
            )
        }

        val grouped = insertions.groupBy({ it.first }, { it.second })
            .mapValues { (_, closers) -> closers.joinToString("") }
        var completed = text
        grouped.keys.sortedDescending().forEach { rawOffset ->
            val offset = rawOffset.coerceIn(0, completed.length)
            completed = completed.substring(0, offset) + grouped.getValue(rawOffset) + completed.substring(offset)
        }

        fun shifted(rawOffset: Int): Int {
            val safe = rawOffset.coerceIn(0, text.length)
            val shift = grouped.entries.sumOf { (offset, closers) ->
                if (offset <= safe) closers.length else 0
            }
            return safe + shift
        }
        return NaturalMathEdit(completed, shifted(selectionStart), shifted(selectionEnd))
    }

    /** Moves between visible math positions, skipping the invisible grouping syntax. */
    fun moveHorizontally(text: String, cursor: Int, direction: Int): NaturalMathEdit {
        val safeCursor = cursor.coerceIn(0, text.length)
        if (direction == 0) return NaturalMathEdit(text, safeCursor)
        val root = NaturalMath.parse(text)
        val slots = fractionSlots(root)
            .sortedBy { it.fraction.end - it.fraction.start }
        val exponents = exponentSlots(root).sortedBy { it.script.end - it.script.start }

        // An inferred closing delimiter has no source offset outside it. Give
        // it a real closing token before fraction-slot navigation; otherwise
        // an unclosed parenthesized denominator is mistaken for an invisible
        // group and repeatedly returns the same cursor position.
        if (direction > 0) {
            val delimiter = unclosedDelimiters(root)
                .filter { it.end == safeCursor }
                .maxByOrNull { it.start }
            if (delimiter != null) {
                val closing = delimiter.right
                val closedText = text.substring(0, safeCursor) + closing + text.substring(safeCursor)
                return NaturalMathEdit(closedText, safeCursor + closing.length)
            }
        }

        val fractions = slots.groupBy { it.fraction }
        for ((_, pair) in fractions) {
            val numerator = pair.firstOrNull { it.role == SlotRole.Numerator } ?: continue
            val denominator = pair.firstOrNull { it.role == SlotRole.Denominator } ?: continue
            if (direction > 0 && safeCursor == numerator.visibleEnd) {
                return NaturalMathEdit(text, denominator.visibleStart)
            }
            if (direction < 0 && safeCursor == denominator.visibleStart) {
                return NaturalMathEdit(text, numerator.visibleEnd)
            }
        }

        val exponent = exponents.firstOrNull { slot ->
            safeCursor in slot.visibleStart..slot.visibleEnd ||
                (slot.grouped && safeCursor == slot.node.end) ||
                safeCursor == slot.script.base.end
        }
        if (exponent != null) {
            if (direction > 0 && safeCursor == exponent.script.base.end) {
                return NaturalMathEdit(text, exponent.visibleStart)
            }
            if (direction < 0 && safeCursor == exponent.visibleStart) {
                return NaturalMathEdit(text, exponent.script.base.end)
            }
            if (exponent.grouped) {
                if (direction > 0 && safeCursor == exponent.visibleEnd) {
                    return NaturalMathEdit(text, exponent.node.end)
                }
                if (direction < 0 && safeCursor == exponent.node.end) {
                    return NaturalMathEdit(text, exponent.visibleEnd)
                }
            } else if (direction > 0 && safeCursor == exponent.visibleEnd) {
                val grouped = text.substring(0, exponent.node.start) + "(" +
                    text.substring(exponent.node.start, exponent.node.end) + ")" +
                    text.substring(exponent.node.end)
                return NaturalMathEdit(grouped, exponent.node.end + 2)
            }
        }

        // Calculus function syntax is intentionally hidden and its arguments
        // are rearranged into conventional mathematical order. Move through
        // the visible slots rather than stopping on `integrate`, commas and
        // parentheses that the user cannot see.
        val calculus = calculusSlots(root)
            .filter { (node, _) -> safeCursor in node.start..node.end }
            .minByOrNull { it.first.end - it.first.start }
        if (calculus != null) {
            val (node, visible) = calculus
            if (visible.isNotEmpty()) {
                if (direction > 0 && safeCursor == node.start) {
                    return NaturalMathEdit(text, visible.first().start)
                }
                if (direction < 0 && safeCursor == node.end) {
                    return NaturalMathEdit(text, visible.last().end)
                }
                visible.zipWithNext().forEach { (left, right) ->
                    if (direction > 0 && safeCursor == left.end) {
                        return NaturalMathEdit(text, right.start)
                    }
                    if (direction < 0 && safeCursor == right.start) {
                        return NaturalMathEdit(text, left.end)
                    }
                }
                if (direction > 0 && safeCursor == visible.last().end && node.end > safeCursor) {
                    return NaturalMathEdit(text, node.end)
                }
                if (direction < 0 && safeCursor == visible.first().start && node.start < safeCursor) {
                    return NaturalMathEdit(text, node.start)
                }
            }
        }

        val exiting = slots.firstOrNull { slot ->
            (direction < 0 && slot.role == SlotRole.Numerator && safeCursor == slot.visibleStart) ||
                (direction > 0 && slot.role == SlotRole.Denominator && safeCursor == slot.visibleEnd)
        }
        if (exiting != null) {
            if (exiting.grouped) {
                return NaturalMathEdit(text, if (direction < 0) exiting.node.start else exiting.node.end)
            }
            val start = exiting.node.start
            val end = exiting.node.end
            val groupedText = text.substring(0, start) + "(" + text.substring(start, end) + ")" + text.substring(end)
            return NaturalMathEdit(groupedText, if (direction < 0) start else end + 2)
        }

        var next = (safeCursor + direction).coerceIn(0, text.length)
        val hiddenStops = (
            slots.filter { it.grouped }.flatMap { listOf(it.node.start, it.node.end) } +
                exponents.filter { it.grouped }.flatMap { listOf(it.node.start, it.node.end) }
            ).toSet()
        while (next in hiddenStops && next != 0 && next != text.length) {
            next = (next + direction).coerceIn(0, text.length)
        }
        return NaturalMathEdit(text, next)
    }

    fun backspace(text: String, selectionStart: Int, selectionEnd: Int): NaturalMathEdit {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        if (start != end) return NaturalMathEdit(text.removeRange(start, end), start)
        if (start == 0) return NaturalMathEdit(text, 0)

        // Empty whole-slot parentheses are an implementation detail. Remove
        // them together instead of exposing an unmatched delimiter.
        if (text.getOrNull(start - 1) == '(' && text.getOrNull(start) == ')') {
            return NaturalMathEdit(text.removeRange(start - 1, start + 1), start - 1)
        }
        return NaturalMathEdit(text.removeRange(start - 1, start), start - 1)
    }

    private fun insertedText(oldText: String, start: Int, end: Int, newText: String): String? {
        val prefix = oldText.substring(0, start)
        val suffix = oldText.substring(end)
        if (!newText.startsWith(prefix) || !newText.endsWith(suffix)) return null
        val insertedEnd = newText.length - suffix.length
        if (insertedEnd < start) return null
        return newText.substring(start, insertedEnd)
    }

    private fun shiftIntoGroup(offset: Int, groupStart: Int): Int = if (offset >= groupStart) offset + 1 else offset

    private fun groupSlot(
        text: String,
        rawStart: Int,
        rawEnd: Int,
        selectionStart: Int,
        selectionEnd: Int
    ): NaturalMathEdit {
        val start = rawStart.coerceIn(0, text.length)
        val end = rawEnd.coerceIn(start, text.length)
        val grouped = text.substring(0, start) + "(" + text.substring(start, end) + ")" + text.substring(end)
        return NaturalMathEdit(
            grouped,
            shiftIntoGroup(selectionStart, start),
            shiftIntoGroup(selectionEnd, start)
        )
    }

    private fun isSlotExtendingOperator(ch: Char): Boolean = ch in "+-−*×·/÷%^=<>≤≥≠"

    /** Post-order traversal keeps same-offset closers in inner-to-outer order. */
    private fun inferredDelimiterInsertions(root: MathNode): List<Pair<Int, String>> = buildList {
        fun visit(node: MathNode) {
            when (node) {
                is MathNode.Delimited -> {
                    visit(node.content)
                    if (node.end == node.content.end && node.right in setOf(")", "]", "}")) {
                        add(node.end to node.right)
                    }
                }
                is MathNode.Fraction -> {
                    visit(node.numerator)
                    visit(node.denominator)
                }
                is MathNode.Row -> node.items.forEach(::visit)
                is MathNode.Script -> {
                    visit(node.base)
                    node.superscript?.let(::visit)
                    node.subscript?.let(::visit)
                }
                is MathNode.Root -> {
                    visit(node.radicand)
                    node.index?.let(::visit)
                }
                is MathNode.Integral -> {
                    node.integrand?.let(::visit)
                    node.variable?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Summation -> {
                    node.expression?.let(::visit)
                    node.index?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Derivative -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.order?.let(::visit)
                }
                is MathNode.Limit -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.point?.let(::visit)
                    node.direction?.let(::visit)
                }
                is MathNode.Matrix -> node.rows.flatten().forEach(::visit)
                is MathNode.Text -> Unit
            }
        }
        visit(root)
    }

    private fun exponentSlots(root: MathNode): List<ExponentSlot> = buildList {
        fun visit(node: MathNode) {
            when (node) {
                is MathNode.Script -> {
                    node.superscript?.let {
                        add(ExponentSlot(it, node))
                        visit(it)
                    }
                    visit(node.base)
                    node.subscript?.let(::visit)
                }
                is MathNode.Fraction -> {
                    visit(node.numerator)
                    visit(node.denominator)
                }
                is MathNode.Row -> node.items.forEach(::visit)
                is MathNode.Root -> {
                    visit(node.radicand)
                    node.index?.let(::visit)
                }
                is MathNode.Integral -> {
                    node.integrand?.let(::visit)
                    node.variable?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Summation -> {
                    node.expression?.let(::visit)
                    node.index?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Derivative -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.order?.let(::visit)
                }
                is MathNode.Limit -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.point?.let(::visit)
                    node.direction?.let(::visit)
                }
                is MathNode.Delimited -> visit(node.content)
                is MathNode.Matrix -> node.rows.flatten().forEach(::visit)
                is MathNode.Text -> Unit
            }
        }
        visit(root)
    }

    private fun calculusSlots(root: MathNode): List<Pair<MathNode, List<MathNode>>> = buildList {
        fun visit(node: MathNode) {
            when (node) {
                is MathNode.Integral -> {
                    val visible = listOfNotNull(node.lower, node.upper, node.integrand, node.variable)
                    add(node to visible)
                    visible.forEach(::visit)
                }
                is MathNode.Summation -> {
                    val visible = listOfNotNull(node.index, node.lower, node.upper, node.expression)
                    add(node to visible)
                    visible.forEach(::visit)
                }
                is MathNode.Derivative -> {
                    val visible = listOfNotNull(node.order, node.variable, node.expression)
                    add(node to visible)
                    visible.forEach(::visit)
                }
                is MathNode.Limit -> {
                    val visible = listOfNotNull(node.variable, node.point, node.direction, node.expression)
                    add(node to visible)
                    visible.forEach(::visit)
                }
                is MathNode.Fraction -> {
                    visit(node.numerator)
                    visit(node.denominator)
                }
                is MathNode.Row -> node.items.forEach(::visit)
                is MathNode.Script -> {
                    visit(node.base)
                    node.superscript?.let(::visit)
                    node.subscript?.let(::visit)
                }
                is MathNode.Root -> {
                    visit(node.radicand)
                    node.index?.let(::visit)
                }
                is MathNode.Delimited -> visit(node.content)
                is MathNode.Matrix -> node.rows.flatten().forEach(::visit)
                is MathNode.Text -> Unit
            }
        }
        visit(root)
    }

    private fun fractionSlots(root: MathNode): List<FractionSlot> = buildList {
        fun visit(node: MathNode) {
            when (node) {
                is MathNode.Fraction -> {
                    add(FractionSlot(node.numerator, SlotRole.Numerator, node))
                    add(FractionSlot(node.denominator, SlotRole.Denominator, node))
                    visit(node.numerator)
                    visit(node.denominator)
                }
                is MathNode.Row -> node.items.forEach(::visit)
                is MathNode.Script -> {
                    visit(node.base)
                    node.superscript?.let(::visit)
                    node.subscript?.let(::visit)
                }
                is MathNode.Root -> {
                    visit(node.radicand)
                    node.index?.let(::visit)
                }
                is MathNode.Integral -> {
                    node.integrand?.let(::visit)
                    node.variable?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Summation -> {
                    node.expression?.let(::visit)
                    node.index?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Derivative -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.order?.let(::visit)
                }
                is MathNode.Limit -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.point?.let(::visit)
                    node.direction?.let(::visit)
                }
                is MathNode.Delimited -> visit(node.content)
                is MathNode.Matrix -> node.rows.flatten().forEach(::visit)
                is MathNode.Text -> Unit
            }
        }
        visit(root)
    }

    private fun unclosedDelimiters(root: MathNode): List<MathNode.Delimited> = buildList {
        fun visit(node: MathNode) {
            when (node) {
                is MathNode.Delimited -> {
                    if (node.end == node.content.end && node.right in setOf(")", "]", "}")) add(node)
                    visit(node.content)
                }
                is MathNode.Fraction -> {
                    visit(node.numerator)
                    visit(node.denominator)
                }
                is MathNode.Row -> node.items.forEach(::visit)
                is MathNode.Script -> {
                    visit(node.base)
                    node.superscript?.let(::visit)
                    node.subscript?.let(::visit)
                }
                is MathNode.Root -> {
                    visit(node.radicand)
                    node.index?.let(::visit)
                }
                is MathNode.Integral -> {
                    node.integrand?.let(::visit)
                    node.variable?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Summation -> {
                    node.expression?.let(::visit)
                    node.index?.let(::visit)
                    node.lower?.let(::visit)
                    node.upper?.let(::visit)
                }
                is MathNode.Derivative -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.order?.let(::visit)
                }
                is MathNode.Limit -> {
                    visit(node.expression)
                    node.variable?.let(::visit)
                    node.point?.let(::visit)
                    node.direction?.let(::visit)
                }
                is MathNode.Matrix -> node.rows.flatten().forEach(::visit)
                is MathNode.Text -> Unit
            }
        }
        visit(root)
    }
}

private class XcasParser(private val source: String) {
    private enum class Type { Number, Identifier, String, Operator, Left, Right, Comma, End }
    private data class Token(val type: Type, val text: String, val start: Int, val end: Int)

    private val tokens = lex()
    private var position = 0
    private val delimiterStack = mutableListOf<String>()

    fun parse(): MathNode {
        if (source.isEmpty()) return MathNode.Text("", 0, 0)
        val rows = mutableListOf<MathNode>()
        while (peek().type != Type.End) {
            val before = position
            rows += parseExpression(0)
            if (position == before) {
                val token = take()
                rows += textNode(token)
            }
            if (peek().type == Type.Comma) rows += textNode(take(), ",")
        }
        return row(rows, 0, source.length)
    }

    private fun parseExpression(minPrecedence: Int): MathNode {
        var left = parsePrefix()
        while (true) {
            val operator = peek()
            val precedence = precedence(operator)
            if (precedence < minPrecedence) break
            take()
            val right = if (!canStartOperand(peek())) {
                MathNode.Text("", operator.end, operator.end)
            } else {
                parseExpression(if (operator.text == "^") precedence else precedence + 1)
            }
            left = when (operator.text) {
                "/", "÷" -> MathNode.Fraction(left, right, operator.start, operator.end, left.start, right.end)
                "^" -> MathNode.Script(left, superscript = right, start = left.start, end = right.end)
                else -> row(listOf(left, textNode(operator, displayOperator(operator.text)), right), left.start, right.end)
            }
        }
        return left
    }

    private fun parsePrefix(): MathNode {
        val token = peek()
        if (token.type == Type.Operator && token.text in setOf("+", "-", "−", "!")) {
            take()
            // Exponentiation binds tighter than a leading sign: -x^2 is
            // -(x^2), while 2^-3 still accepts the signed exponent.
            val operand = parseExpression(6)
            return row(listOf(textNode(token, displayOperator(token.text)), operand), token.start, operand.end)
        }
        var node = parseAtom()
        while (peek().type == Type.Left && peek().text == "(" && node is MathNode.Text) {
            val open = take()
            val arguments = parseDelimited(open, ")") as MathNode.Delimited
            node = when (node.value.lowercase()) {
                "sqrt" -> MathNode.Root(arguments.content, start = node.start, end = arguments.end)
                "abs" -> MathNode.Delimited("∣", arguments.content, "∣", node.start, arguments.end)
                "integrate", "int" -> integral(node.start, arguments)
                "sum" -> summation(node.start, arguments)
                "diff", "derive", "derivative" -> derivative(node.start, arguments)
                "limit", "limite" -> limit(node.start, arguments)
                else -> row(listOf(node, arguments), node.start, arguments.end)
            }
        }
        return node
    }

    private fun integral(start: Int, arguments: MathNode.Delimited): MathNode.Integral {
        val values = splitArguments(arguments)
        return MathNode.Integral(
            integrand = values.getOrNull(0),
            variable = values.getOrNull(1),
            lower = values.getOrNull(2),
            upper = values.getOrNull(3),
            start = start,
            end = arguments.end
        )
    }

    private fun summation(start: Int, arguments: MathNode.Delimited): MathNode.Summation {
        val values = splitArguments(arguments)
        return MathNode.Summation(
            expression = values.getOrNull(0),
            index = values.getOrNull(1),
            lower = values.getOrNull(2),
            upper = values.getOrNull(3),
            start = start,
            end = arguments.end
        )
    }

    private fun derivative(start: Int, arguments: MathNode.Delimited): MathNode.Derivative {
        val values = splitArguments(arguments)
        return MathNode.Derivative(
            expression = values.getOrElse(0) { MathNode.Text("", arguments.content.start, arguments.content.start) },
            variable = values.getOrNull(1),
            order = values.getOrNull(2),
            start = start,
            end = arguments.end
        )
    }

    private fun limit(start: Int, arguments: MathNode.Delimited): MathNode.Limit {
        val values = splitArguments(arguments)
        val expression = values.getOrElse(0) {
            MathNode.Text("", arguments.content.start, arguments.content.start)
        }
        val compactBinding = values.getOrNull(1)?.let(::limitBinding)
        return MathNode.Limit(
            expression = expression,
            variable = compactBinding?.first ?: values.getOrNull(1),
            point = compactBinding?.second ?: values.getOrNull(2),
            direction = if (compactBinding != null) values.getOrNull(2) else values.getOrNull(3),
            start = start,
            end = arguments.end
        )
    }

    private fun limitBinding(node: MathNode): Pair<MathNode, MathNode>? {
        val items = (node as? MathNode.Row)?.items ?: return null
        val equals = items.indexOfFirst { it is MathNode.Text && it.value == "=" }
        if (equals <= 0 || equals >= items.lastIndex) return null
        return row(items.take(equals), node.start, items[equals - 1].end) to
            row(items.drop(equals + 1), items[equals + 1].start, node.end)
    }

    private fun parseAtom(): MathNode {
        val token = take()
        return when (token.type) {
            Type.Left -> parseDelimited(token, matchingRight(token.text))
            Type.Number, Type.Identifier, Type.String -> identifierNode(token)
            Type.End -> MathNode.Text("", token.start, token.end)
            else -> textNode(token, displayOperator(token.text))
        }
    }

    private fun parseDelimited(open: Token, expectedRight: String): MathNode {
        val ancestorClosers = delimiterStack.toSet()
        delimiterStack += expectedRight
        val children = mutableListOf<MathNode>()
        try {
            // A mismatched closer may belong to an ancestor. For example, in
            // `fft([)` the `)` must close the function call while the inner
            // list gets an inferred `]`. A genuinely stray closer still ends
            // this group, retaining the parser's fault-tolerant recovery.
            while (peek().type != Type.End && peek().type != Type.Right) {
                val before = position
                children += parseExpression(0)
                if (position == before) children += textNode(take())
                if (peek().type == Type.Comma) children += textNode(take(), ", ")
            }
        } finally {
            delimiterStack.removeAt(delimiterStack.lastIndex)
        }
        val close = if (
            peek().type == Type.Right &&
            (peek().text == expectedRight || peek().text !in ancestorClosers)
        ) take() else null
        val contentEnd = close?.start ?: (children.lastOrNull()?.end ?: open.end)
        val end = close?.end ?: contentEnd
        val delimited = MathNode.Delimited(
            displayDelimiter(open.text), row(children, open.end, contentEnd),
            displayDelimiter(close?.text ?: expectedRight), open.start, end
        )
        if (open.text == "[") {
            val matrixRows = children.filterNot { it is MathNode.Text && it.value.trim().startsWith(",") }
                .mapNotNull { child ->
                    (child as? MathNode.Delimited)?.takeIf { it.left == "[" && it.right == "]" }?.content?.let(::splitCells)
                }
            if (matrixRows.size >= 2 && matrixRows.size == children.count { it !is MathNode.Text || !it.value.trim().startsWith(",") }) {
                return MathNode.Matrix(matrixRows, open.start, end)
            }
        }
        return delimited
    }

    private fun identifierNode(token: Token): MathNode {
        if (token.type != Type.Identifier) return textNode(token)
        val underscore = token.text.indexOf('_').takeIf { it > 0 && it < token.text.lastIndex }
        if (underscore != null) {
            val baseEnd = token.start + underscore
            return MathNode.Script(
                MathNode.Text(identifierDisplay(token.text.substring(0, underscore)), token.start, baseEnd),
                subscript = MathNode.Text(identifierDisplay(token.text.substring(underscore + 1)), baseEnd + 1, token.end),
                start = token.start,
                end = token.end
            )
        }
        return textNode(token, identifierDisplay(token.text))
    }

    private fun precedence(token: Token): Int {
        if (token.type != Type.Operator) return -1
        return when (token.text) {
            ";" -> 0
            ":=", "->", "≔", "→" -> 1
            "=", "==", "!=", "<", ">", "<=", ">=", "≤", "≥", "≠" -> 2
            "+", "-", "−" -> 3
            "*", "×", "·", "/", "÷", "%" -> 4
            "^" -> 6
            else -> -1
        }
    }

    private fun canStartOperand(token: Token): Boolean = when (token.type) {
        Type.Number, Type.Identifier, Type.String, Type.Left -> true
        Type.Operator -> token.text in setOf("+", "-", "−", "!")
        else -> false
    }

    private fun lex(): List<Token> {
        val result = mutableListOf<Token>()
        var i = 0
        while (i < source.length) {
            if (source[i].isWhitespace()) { i++; continue }
            val start = i
            when {
                source[i].isDigit() || (source[i] == '.' && source.getOrNull(i + 1)?.isDigit() == true) -> {
                    if (source[i] == '.') {
                        i++
                        while (i < source.length && source[i].isDigit()) i++
                    } else {
                        while (i < source.length && source[i].isDigit()) i++
                        if (source.getOrNull(i) == '.' && source.getOrNull(i + 1) != '.') {
                            i++
                            while (i < source.length && source[i].isDigit()) i++
                        }
                    }
                    // Only absorb an exponent when it already has digits.
                    // During live input, `2e+` must remain `2`, `e`, `+`
                    // instead of becoming one malformed numeric object that
                    // swallows an operator and changes fraction grouping.
                    if (source.getOrNull(i) in listOf('e', 'E')) {
                        var exponentEnd = i + 1
                        if (source.getOrNull(exponentEnd) in listOf('+', '-', '−')) exponentEnd++
                        val digitsStart = exponentEnd
                        while (source.getOrNull(exponentEnd)?.isDigit() == true) exponentEnd++
                        if (exponentEnd > digitsStart) i = exponentEnd
                    }
                    result += Token(Type.Number, source.substring(start, i), start, i)
                }
                source[i] in "−×·÷≤≥≠≔→" -> {
                    result += Token(Type.Operator, source[i].toString(), i, ++i)
                }
                source[i].isLetter() || source[i] == '_' || source[i] == '∞' -> {
                    i++
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    result += Token(Type.Identifier, source.substring(start, i), start, i)
                }
                source[i] == '"' || source[i] == '\'' -> {
                    val quote = source[i++]
                    while (i < source.length) {
                        val ch = source[i++]
                        if (ch == quote && (i < 2 || source[i - 2] != '\\')) break
                    }
                    result += Token(Type.String, source.substring(start, i), start, i)
                }
                source[i] in "([{\u007b" -> result.add(Token(Type.Left, source[i].toString(), i, ++i))
                source[i] in ")]\u007d" -> result.add(Token(Type.Right, source[i].toString(), i, ++i))
                source[i] == ',' -> result.add(Token(Type.Comma, ",", i, ++i))
                else -> {
                    val pair = source.substring(start, (start + 2).coerceAtMost(source.length))
                    if (pair in setOf("<=", ">=", "!=", "==", ":=", "->", "..", "&&", "||")) i += 2 else i++
                    result += Token(Type.Operator, source.substring(start, i), start, i)
                }
            }
        }
        result += Token(Type.End, "", source.length, source.length)
        return result
    }

    private fun peek() = tokens[position]
    private fun take() = tokens[position++].also { position = position.coerceAtMost(tokens.lastIndex) }
    private fun textNode(token: Token, value: String = token.text) = MathNode.Text(value, token.start, token.end)
}

private class LatexParser(private val source: String) {
    private var position = 0

    fun parse(): MathNode = parseRow(null, stopAtRightCommand = false)

    private fun parseRow(terminator: Char?, stopAtRightCommand: Boolean = false): MathNode {
        val start = position
        val nodes = mutableListOf<MathNode>()
        while (position < source.length && source[position] != terminator) {
            if (stopAtRightCommand && source.startsWith("\\right", position)) break
            val node = parseAtom()
            if (node != null) {
                if (node is MathNode.Integral || node is MathNode.Summation) {
                    while (position < source.length && source[position].isWhitespace()) position++
                    when {
                        source.startsWith("\\nolimits", position) -> position += "\\nolimits".length
                        source.startsWith("\\limits", position) -> position += "\\limits".length
                    }
                    while (position < source.length && source[position].isWhitespace()) position++
                }
                if (position < source.length && source[position] in "^_") {
                    var sup: MathNode? = null
                    var sub: MathNode? = null
                    while (position < source.length && source[position] in "^_") {
                        val marker = source[position++]
                        val script = parseGroupOrAtom()
                        if (marker == '^') sup = script else sub = script
                    }
                    val end = maxOf(node.end, sup?.end ?: 0, sub?.end ?: 0)
                    nodes += when (node) {
                        is MathNode.Integral -> node.copy(lower = sub, upper = sup, end = end)
                        is MathNode.Summation -> node.copy(lower = sub, upper = sup, end = end)
                        else -> MathNode.Script(node, sup, sub, node.start, end)
                    }
                } else nodes += node
            }
        }
        if (terminator != null && position < source.length) position++
        return row(nodes, start, position)
    }

    private fun parseAtom(): MathNode? {
        val start = position
        return when (val ch = source[position++]) {
            '{' -> parseRow('}')
            '}' -> null
            '(' -> MathNode.Delimited("(", parseRow(')'), ")", start, position)
            '[' -> MathNode.Delimited("[", parseRow(']'), "]", start, position)
            '\\' -> parseCommand(start)
            ' ' -> null
            else -> MathNode.Text(ch.toString(), start, position)
        }
    }

    private fun parseCommand(start: Int): MathNode? {
        val nameStart = position
        while (position < source.length && source[position].isLetter()) position++
        val name = source.substring(nameStart, position)
        return when (name) {
            "frac", "dfrac", "tfrac" -> {
                val numerator = parseGroupOrAtom()
                val denominator = parseGroupOrAtom()
                MathNode.Fraction(numerator, denominator, start, nameStart, start, denominator.end)
            }
            "sqrt" -> {
                val index = if (position < source.length && source[position] == '[') {
                    position++
                    val value = parseUntil(']')
                    if (position < source.length) position++
                    value
                } else null
                val radicand = parseGroupOrAtom()
                MathNode.Root(radicand, index, start, radicand.end)
            }
            "left" -> parseLeftRight(start)
            "right", "displaystyle", "limits", "nolimits" -> null
            "operatorname", "mathrm", "mathbf", "mathit", "text" -> parseGroupOrAtom()
            "cdot", "times" -> MathNode.Text("×", start, position)
            "div" -> MathNode.Text("÷", start, position)
            "le", "leq" -> MathNode.Text("≤", start, position)
            "ge", "geq" -> MathNode.Text("≥", start, position)
            "ne", "neq" -> MathNode.Text("≠", start, position)
            "to", "rightarrow" -> MathNode.Text("→", start, position)
            "infty" -> MathNode.Text("∞", start, position)
            ",", ";", "!", "quad", "qquad" -> MathNode.Text(" ", start, position)
            "sum" -> MathNode.Summation(start = start, end = position)
            "prod" -> MathNode.Text("∏", start, position)
            "int" -> MathNode.Integral(start = start, end = position)
            "partial" -> MathNode.Text("∂", start, position)
            else -> MathNode.Text(commandDisplay(name), start, position)
        }
    }

    private fun parseGroupOrAtom(): MathNode {
        while (position < source.length && source[position].isWhitespace()) position++
        if (position >= source.length) return MathNode.Text("", position, position)
        return if (source[position] == '{') {
            position++
            parseRow('}')
        } else parseAtom() ?: MathNode.Text("", position, position)
    }

    private fun parseLeftRight(start: Int): MathNode {
        skipLatexSpace()
        val left = parseLatexDelimiter()
        val contentStart = position
        val content = parseRow(null, stopAtRightCommand = true)
        var right = matchingRight(displayLatexDelimiter(left))
        if (source.startsWith("\\right", position)) {
            position += "\\right".length
            skipLatexSpace()
            right = parseLatexDelimiter()
        }
        return MathNode.Delimited(
            displayLatexDelimiter(left),
            content,
            displayLatexDelimiter(right),
            start,
            position.coerceAtLeast(contentStart)
        )
    }

    private fun skipLatexSpace() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun parseLatexDelimiter(): String {
        if (position >= source.length) return "."
        if (source[position] != '\\') return source[position++].toString()
        position++
        val start = position
        if (position < source.length && source[position].isLetter()) {
            while (position < source.length && source[position].isLetter()) position++
        } else if (position < source.length) {
            position++
        }
        return "\\" + source.substring(start, position)
    }

    private fun parseUntil(end: Char): MathNode {
        val start = position
        while (position < source.length && source[position] != end) position++
        return MathNode.Text(source.substring(start, position), start, position)
    }
}

private fun displayLatexDelimiter(value: String): String = when (value) {
    "\\lbrace", "\\{" -> "{"
    "\\rbrace", "\\}" -> "}"
    "\\lbrack" -> "["
    "\\rbrack" -> "]"
    "\\|", "\\vert", "\\lvert", "\\rvert", "\\mid" -> "∣"
    "." -> ""
    else -> value
}

private fun row(items: List<MathNode>, start: Int, end: Int): MathNode =
    when (items.size) {
        0 -> MathNode.Text("", start, end)
        1 -> items[0]
        else -> MathNode.Row(items, start, end)
    }

private fun splitCells(node: MathNode): List<MathNode> {
    val items = (node as? MathNode.Row)?.items ?: return listOf(node)
    val cells = mutableListOf<MathNode>()
    val current = mutableListOf<MathNode>()
    fun flush() {
        if (current.isNotEmpty()) {
            cells += row(current.toList(), current.first().start, current.last().end)
            current.clear()
        }
    }
    items.forEach {
        if (it is MathNode.Text && it.value.trim().startsWith(",")) flush() else current += it
    }
    flush()
    return cells
}

/** Splits only commas at the function call's top level and preserves empty slots. */
private fun splitArguments(arguments: MathNode.Delimited): List<MathNode> {
    val contentStart = (arguments.start + 1).coerceAtMost(arguments.content.end)
    val contentEnd = arguments.content.end
    val items = (arguments.content as? MathNode.Row)?.items ?: listOf(arguments.content)
    val values = mutableListOf<MathNode>()
    val current = mutableListOf<MathNode>()
    var slotStart = contentStart

    fun flush(slotEnd: Int) {
        values += if (current.isEmpty()) {
            MathNode.Text("", slotStart.coerceAtMost(slotEnd), slotStart.coerceAtMost(slotEnd))
        } else {
            row(current.toList(), slotStart, slotEnd)
        }
        current.clear()
    }

    items.forEach { item ->
        if (item is MathNode.Text && item.value.trim().startsWith(",")) {
            flush(item.start)
            slotStart = item.end
        } else if (!(item is MathNode.Text && item.value.isEmpty() && item.start == item.end)) {
            current += item
        }
    }
    flush(contentEnd)
    return values
}

private fun matchingRight(left: String) = when (left) { "[" -> "]"; "{" -> "}"; else -> ")" }
private fun displayDelimiter(value: String) = value

private fun identifierDisplay(value: String): String = when (value.lowercase()) {
    "pi" -> "π"
    "infinity", "inf" -> "∞"
    else -> commandDisplay(value)
}

private fun commandDisplay(value: String): String = when (value.lowercase()) {
    "alpha" -> "α"; "beta" -> "β"; "gamma" -> "γ"; "delta" -> "δ"
    "epsilon" -> "ε"; "theta" -> "θ"; "lambda" -> "λ"; "mu" -> "μ"
    "rho" -> "ρ"; "sigma" -> "σ"; "tau" -> "τ"; "phi" -> "φ"
    "omega" -> "ω"; "Gamma" -> "Γ"; "Delta" -> "Δ"; "Theta" -> "Θ"
    else -> value
}

private fun displayOperator(value: String): String = when (value) {
    "*", "·" -> "×"
    "-" -> "−"
    "<=" -> "≤"; ">=" -> "≥"; "!=" -> "≠"; "==" -> "="
    ":=" -> "≔"; "->" -> "→"; ".." -> "…"
    else -> value
}
