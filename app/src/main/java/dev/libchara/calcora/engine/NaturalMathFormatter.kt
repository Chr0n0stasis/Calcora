package dev.libchara.calcora.engine

data class NaturalMathDisplay(
    val text: String,
    val originalToTransformed: IntArray,
    val transformedToOriginal: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NaturalMathDisplay) return false
        return text == other.text &&
            originalToTransformed.contentEquals(other.originalToTransformed) &&
            transformedToOriginal.contentEquals(other.transformedToOriginal)
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + originalToTransformed.contentHashCode()
        result = 31 * result + transformedToOriginal.contentHashCode()
        return result
    }
}

object NaturalMathFormatter {
    fun format(input: String): String = formatWithOffsets(input).text

    fun formatWithOffsets(input: String): NaturalMathDisplay {
        val out = StringBuilder(input.length)
        val originalToTransformed = IntArray(input.length + 1)
        val transformedToOriginal = mutableListOf<Int>()

        fun appendReplacement(start: Int, end: Int, replacement: String) {
            val transformedStart = out.length
            originalToTransformed[start] = transformedStart
            out.append(replacement)
            for (offset in start + 1..end) {
                originalToTransformed[offset] = out.length
            }
            repeat(replacement.length) { index ->
                transformedToOriginal.add(if (index == 0) start else end)
            }
        }

        fun appendOriginal(start: Int, end: Int) {
            for (offset in start until end) {
                appendReplacement(offset, offset + 1, input[offset].toString())
            }
        }

        var i = 0
        while (i < input.length) {
            if (input[i] == '"') {
                val start = i
                i++
                while (i < input.length) {
                    val ch = input[i++]
                    if (ch == '"' && (i < 2 || input[i - 2] != '\\')) break
                }
                appendOriginal(start, i)
                continue
            }

            val comparison = comparisonReplacement(input, i)
            if (comparison != null) {
                appendReplacement(i, i + comparison.first, comparison.second)
                i += comparison.first
                continue
            }

            if (input[i] == '^') {
                val exponent = exponentReplacement(input, i)
                if (exponent != null) {
                    appendReplacement(i, exponent.first, exponent.second)
                    i = exponent.first
                    continue
                }
            }

            if (input[i].isLetter() || input[i] == '_') {
                val start = i
                i++
                while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_')) i++
                val word = input.substring(start, i)
                val replacement = identifierReplacement(input, start, i, word)
                if (replacement == null) appendOriginal(start, i) else appendReplacement(start, i, replacement)
                continue
            }

            val replacement = when (input[i]) {
                '*' -> "×"
                '/' -> "÷"
                '-' -> "−"
                else -> input[i].toString()
            }
            appendReplacement(i, i + 1, replacement)
            i++
        }

        transformedToOriginal.add(input.length)
        originalToTransformed[input.length] = out.length
        return NaturalMathDisplay(
            text = out.toString(),
            originalToTransformed = originalToTransformed,
            transformedToOriginal = transformedToOriginal.toIntArray()
        )
    }

    private fun comparisonReplacement(input: String, index: Int): Pair<Int, String>? {
        if (index + 1 >= input.length) return null
        return when (input.substring(index, index + 2)) {
            "<=" -> 2 to "≤"
            ">=" -> 2 to "≥"
            "!=" -> 2 to "≠"
            "==" -> 2 to "="
            ":=" -> 2 to "≔"
            "->" -> 2 to "→"
            ".." -> 2 to "…"
            else -> null
        }
    }

    private fun identifierReplacement(input: String, start: Int, end: Int, word: String): String? {
        val isWordBoundaryBefore = start == 0 || !(input[start - 1].isLetterOrDigit() || input[start - 1] == '_')
        val isWordBoundaryAfter = end == input.length || !(input[end].isLetterOrDigit() || input[end] == '_')
        if (!isWordBoundaryBefore || !isWordBoundaryAfter) return null

        val lower = word.lowercase()
        if (lower == "sqrt" && nextNonSpace(input, end) == '(') return "√"
        return when (lower) {
            "pi" -> "π"
            "infinity", "inf" -> "∞"
            "alpha" -> "α"
            "beta" -> "β"
            "gamma" -> "γ"
            "delta" -> "δ"
            "theta" -> "θ"
            "lambda" -> "λ"
            "mu" -> "μ"
            "sigma" -> "σ"
            "phi" -> "φ"
            "omega" -> "ω"
            else -> null
        }
    }

    private fun nextNonSpace(input: String, start: Int): Char? {
        var index = start
        while (index < input.length && input[index].isWhitespace()) index++
        return input.getOrNull(index)
    }

    private fun exponentReplacement(input: String, caretIndex: Int): Pair<Int, String>? {
        var index = caretIndex + 1
        while (index < input.length && input[index].isWhitespace()) index++
        if (index >= input.length) return null

        if (input[index] == '(') {
            val close = findMatchingParen(input, index) ?: return null
            val inner = input.substring(index + 1, close)
            val superscript = toSuperscript("($inner)") ?: return null
            return close + 1 to superscript
        }

        val start = index
        if (input[index] == '+' || input[index] == '-') index++
        while (index < input.length && (input[index].isLetterOrDigit() || input[index] == '.')) index++
        if (index == start || (index == start + 1 && (input[start] == '+' || input[start] == '-'))) return null
        val superscript = toSuperscript(input.substring(start, index)) ?: return null
        return index to superscript
    }

    private fun findMatchingParen(input: String, openIndex: Int): Int? {
        var depth = 0
        var i = openIndex
        while (i < input.length) {
            when (input[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    private fun toSuperscript(text: String): String? {
        val builder = StringBuilder(text.length)
        for (ch in text) {
            builder.append(SUPERSCRIPT[ch] ?: return null)
        }
        return builder.toString()
    }

    private val SUPERSCRIPT = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        '.' to '·', 'n' to 'ⁿ', 'i' to 'ⁱ',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
        'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'j' to 'ʲ', 'k' to 'ᵏ',
        'l' to 'ˡ', 'm' to 'ᵐ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ',
        's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ',
        'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ'
    )
}
