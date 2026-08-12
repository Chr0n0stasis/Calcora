package dev.libchara.calcora.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalMathFormatterTest {
    @Test
    fun parsesDivisionAsFractionInsteadOfGlyphReplacement() {
        val tree = NaturalMath.parse("(a+b)/(c-d)")
        assertTrue(tree is MathNode.Fraction)
        assertEquals("((a+b))/((c−d))", NaturalMathFormatter.format("(a+b)/(c-d)"))
    }

    @Test
    fun buildsNestedPowerRootAndSubscriptNodes() {
        val power = NaturalMath.parse("sqrt(x_1^2+1)")
        assertTrue(power is MathNode.Root)
        assertEquals("√(x_(1)^(2)+1)", NaturalMathFormatter.format("sqrt(x_1^2+1)"))
    }

    @Test
    fun keepsFractionAsStretchableRootContent() {
        val root = NaturalMath.parse("sqrt(1/2)")
        assertTrue(root is MathNode.Root)
        assertTrue((root as MathNode.Root).radicand is MathNode.Fraction)
    }

    @Test
    fun parsesGiacLatexResults() {
        val tree = NaturalMath.parse("\\frac{x^{2}+1}{\\sqrt{2}}", MathSource.Latex)
        assertTrue(tree is MathNode.Fraction)
        val fraction = tree as MathNode.Fraction
        assertTrue(fraction.denominator is MathNode.Root)
    }

    @Test
    fun recognizesXcasMatricesAsTwoDimensionalStructures() {
        val tree = NaturalMath.parse("[[1,2],[3,4]]")
        assertTrue(tree is MathNode.Matrix)
        assertEquals(2, (tree as MathNode.Matrix).rows.size)
        assertEquals(2, tree.rows.first().size)
    }

    @Test
    fun parsesCalculusFunctionSyntaxAsNaturalStructures() {
        val integral = NaturalMath.parse("integrate(x^2,x,0,1)")
        assertTrue(integral is MathNode.Integral)
        integral as MathNode.Integral
        assertTrue(integral.integrand is MathNode.Script)
        assertEquals("∫_(0)^(1)(x^(2))d(x)", NaturalMathFormatter.format("integrate(x^2,x,0,1)"))

        val sum = NaturalMath.parse("sum(1/k^2,k,1,n)")
        assertTrue(sum is MathNode.Summation)
        sum as MathNode.Summation
        assertTrue(sum.expression is MathNode.Fraction)
        assertEquals("∑_(k=1)^(n)((1)/(k^(2)))", NaturalMathFormatter.format("sum(1/k^2,k,1,n)"))

        val derivative = NaturalMath.parse("diff(sin(x),x,2)")
        assertTrue(derivative is MathNode.Derivative)
        assertEquals("d^(2)/d(x)^(2)(sin(x))", NaturalMathFormatter.format("diff(sin(x),x,2)"))

        val limit = NaturalMath.parse("limit(sin(x)/x,x,0)")
        assertTrue(limit is MathNode.Limit)
        assertEquals("lim_(x→0)((sin(x))/(x))", NaturalMathFormatter.format("limit(sin(x)/x,x,0)"))
        assertEquals("lim_(x→0)((sin(x))/(x))", NaturalMathFormatter.format("limit(sin(x)/x,x=0)"))
        assertEquals(
            "lim_(x→0^(-))(exp((1)/(x)))",
            NaturalMathFormatter.format("limit(exp(1/x),x,0,-1)")
        )
    }

    @Test
    fun parsesLatexLargeOperatorsWithAttachedLimits() {
        val sum = NaturalMath.parse("\\sum_{k=1}^{n} k^2", MathSource.Latex)
        val sumOperator = (sum as MathNode.Row).items.first()
        assertTrue(sumOperator is MathNode.Summation)
        sumOperator as MathNode.Summation
        assertNotNull(sumOperator.lower)
        assertNotNull(sumOperator.upper)

        val integral = NaturalMath.parse("\\int\\limits_{0}^{1} x\\,dx", MathSource.Latex)
        val integralOperator = (integral as MathNode.Row).items.first()
        assertTrue(integralOperator is MathNode.Integral)
    }

    @Test
    fun incompleteNaturalCalculusTemplatesNeverLoseSourceRanges() {
        listOf(
            "integrate(", "integrate(□,x", "int(1/x,x,",
            "sum(", "sum(□,k,1,", "diff(", "diff(□,x,",
            "limit(", "limit(□,x=", "limit(1/x,x,0,"
        ).forEach { source -> assertTreeRanges(NaturalMath.parse(source), source.length) }
    }

    @Test
    fun arrowsSkipHiddenCalculusFunctionSyntaxAndFollowVisibleSlots() {
        val integral = "integrate(x^2,x,0,1)"
        // Left of the integrand is visually the upper limit, not the hidden `(`.
        assertEquals(19, NaturalMathEditing.moveHorizontally(integral, 10, -1).selectionStart)
        // From the differential variable, leave the whole integral at once.
        assertEquals(integral.length, NaturalMathEditing.moveHorizontally(integral, 15, 1).selectionStart)

        val sum = "sum(1/k^2,k,1,n)"
        assertEquals(15, NaturalMathEditing.moveHorizontally(sum, 4, -1).selectionStart)
        val leftExponent = NaturalMathEditing.moveHorizontally(sum, 9, 1)
        assertEquals("sum(1/k^(2),k,1,n)", leftExponent.text)
        assertEquals(11, leftExponent.selectionStart)
        assertEquals(
            leftExponent.text.length,
            NaturalMathEditing.moveHorizontally(leftExponent.text, leftExponent.selectionStart, 1).selectionStart
        )

        val derivative = "diff(sin(x),x,2)"
        assertEquals(13, NaturalMathEditing.moveHorizontally(derivative, 5, -1).selectionStart)
        assertEquals(5, NaturalMathEditing.moveHorizontally(derivative, 13, 1).selectionStart)

        val limit = "limit(sin(x)/x,x,0)"
        assertEquals(18, NaturalMathEditing.moveHorizontally(limit, 6, -1).selectionStart)
        assertEquals(6, NaturalMathEditing.moveHorizontally(limit, 18, 1).selectionStart)
        assertEquals(limit.length, NaturalMathEditing.moveHorizontally(limit, 14, 1).selectionStart)
    }

    @Test
    fun incompleteInputNeverThrows() {
        listOf("1/", "sqrt(", "x^", "((a+b", "a/(b/").forEach { input ->
            val node = NaturalMath.parse(input)
            assertEquals(0, node.start)
            assertTrue(node.end in 0..input.length)
        }
    }

    @Test
    fun normalizesDisplayGlyphsForGiacOnlyAtBoundary() {
        assertEquals("pi/2<=infinity", NaturalMath.toGiac("π÷2≤∞"))
        assertEquals("sqrt(pi)/2", ExpressionFormatter.toEngineInput("sqrt(π)÷2"))
    }

    @Test
    fun engineInputCannotExtendAVisuallyFinishedExponent() {
        assertEquals("10^(-3)*t", ExpressionFormatter.toEngineInput("10^(-3)t"))
        assertEquals("10^-3*t", ExpressionFormatter.toEngineInput("10^-3t"))
        assertEquals("10^(-3)* t", ExpressionFormatter.toEngineInput("10^(-3) t"))
        assertEquals("10^(-3)*t", ExpressionFormatter.toEngineInput("10^(-3)×t"))

        // Here t is visibly and structurally part of the exponent, so its
        // meaning must remain 10^(-3*t).
        assertEquals("10^(-3t)", ExpressionFormatter.toEngineInput("10^(-3t)"))
        assertEquals("2^((x+1))y", NaturalMathFormatter.format("2^(x+1)y"))
        assertEquals("2^(x+1)*y", ExpressionFormatter.toEngineInput("2^(x+1)y"))
        assertEquals("2^(x^2*y)*z", ExpressionFormatter.toEngineInput("2^(x^2y)z"))
    }

    @Test
    fun operatorsTypedInFractionSlotsCreateInvisibleGroups() {
        val denominator = NaturalMathEditing.adjust("a/b", 3, 3, "a/b+", 4)
        assertEquals("a/(b+)", denominator.text)
        assertEquals(5, denominator.selectionStart)
        assertTrue((NaturalMath.parse(denominator.text) as MathNode.Fraction).denominator is MathNode.Delimited)

        val numerator = NaturalMathEditing.adjust("a/b", 1, 1, "a+/b", 2)
        assertEquals("(a+)/b", numerator.text)
        assertEquals(3, numerator.selectionStart)
    }

    @Test
    fun negativeAndDecimalFractionSlotsDoNotAcquireExtraGroups() {
        val negative = NaturalMathEditing.adjust("1/(−.5", 6, 6, "1/(−.5+", 7)
        assertEquals("1/(−.5+", negative.text)
        assertEquals(7, negative.selectionStart)

        val decimal = NaturalMathEditing.adjust("1/2.5", 5, 5, "1/2.5−", 6)
        assertEquals("1/(2.5−)", decimal.text)
        assertEquals(7, decimal.selectionStart)
    }

    @Test
    fun operatorRemainsInGroupedSlotAndArrowCanExitIt() {
        val continued = NaturalMathEditing.adjust("a/(b+)", 5, 5, "a/(b+c)", 6)
        assertEquals("a/(b+c)", continued.text)
        assertEquals(6, continued.selectionStart)

        val exited = NaturalMathEditing.moveHorizontally("a/b", 3, 1)
        assertEquals("a/(b)", exited.text)
        assertEquals(5, exited.selectionStart)
        val outside = NaturalMathEditing.adjust(exited.text, 5, 5, "a/(b)+", 6)
        assertEquals("a/(b)+", outside.text)
    }

    @Test
    fun backspaceRemovesEmptyImplicitGroupAsOneUnit() {
        val edited = NaturalMathEditing.backspace("a/()", 3, 3)
        assertEquals("a/", edited.text)
        assertEquals(2, edited.selectionStart)

        val imeEdited = NaturalMathEditing.adjust("a/()", 3, 3, "a/)", 2)
        assertEquals("a/", imeEdited.text)
        assertEquals(2, imeEdited.selectionStart)
    }

    @Test
    fun arrowsCrossFractionSlotsInOnePressAndSkipHiddenGroups() {
        assertEquals(2, NaturalMathEditing.moveHorizontally("a/b", 1, 1).selectionStart)
        assertEquals(1, NaturalMathEditing.moveHorizontally("a/b", 2, -1).selectionStart)

        val leftFraction = NaturalMathEditing.moveHorizontally("(a/b)", 2, 1)
        assertEquals("(a/b)", leftFraction.text)
        assertEquals(3, leftFraction.selectionStart)

        val exitDenominator = NaturalMathEditing.moveHorizontally("(a/b)", 4, 1)
        assertEquals("(a/(b))", exitDenominator.text)
        assertEquals(6, exitDenominator.selectionStart)

        val reenterDenominator = NaturalMathEditing.moveHorizontally(exitDenominator.text, 6, -1)
        assertEquals(5, reenterDenominator.selectionStart)
    }

    @Test
    fun rightArrowCommitsImplicitClosingDelimiterBeforeLeavingIt() {
        val parenthesis = NaturalMathEditing.moveHorizontally("(1+1", 4, 1)
        assertEquals("(1+1)", parenthesis.text)
        assertEquals(5, parenthesis.selectionStart)

        val inner = NaturalMathEditing.moveHorizontally("([1", 3, 1)
        assertEquals("([1]", inner.text)
        assertEquals(4, inner.selectionStart)

        val outer = NaturalMathEditing.moveHorizontally(inner.text, inner.selectionStart, 1)
        assertEquals("([1])", outer.text)
        assertEquals(5, outer.selectionStart)
    }

    @Test
    fun rightArrowEscapesUnclosedParenthesesAroundNestedFraction() {
        val source = "(1/(2"

        val denominator = NaturalMathEditing.moveHorizontally(source, source.length, 1)
        assertEquals("(1/(2)", denominator.text)
        assertEquals(denominator.text.length, denominator.selectionStart)

        val outer = NaturalMathEditing.moveHorizontally(
            denominator.text,
            denominator.selectionStart,
            1
        )
        assertEquals("(1/(2))", outer.text)
        assertEquals(outer.text.length, outer.selectionStart)
    }

    @Test
    fun unclosedFractionGroupNeverReturnsTheSameCursorPosition() {
        val source = "(−8.341×ln(2.5))/(1/(493)−(1/(473"
        val moved = NaturalMathEditing.moveHorizontally(source, source.length, 1)

        assertEquals("$source)", moved.text)
        assertEquals(source.length + 1, moved.selectionStart)
    }

    @Test
    fun displayOperatorsParticipateInPrecedenceInsteadOfBecomingIdentifiers() {
        val negativeFraction = NaturalMath.parse("−8.341/2")
        assertTrue(negativeFraction is MathNode.Fraction)
        assertEquals("(−8.341)/(2)", NaturalMathFormatter.format("−8.341/2"))

        val multiplicationThenDivision = NaturalMath.parse("a×b/c")
        assertTrue(multiplicationThenDivision is MathNode.Fraction)
        assertEquals("(a×b)/(c)", NaturalMathFormatter.format("a×b/c"))

        assertEquals("a≤b", NaturalMathFormatter.format("a≤b"))
        assertEquals("a≔b", NaturalMathFormatter.format("a≔b"))
    }

    @Test
    fun decimalAndScientificTokensHaveStableBoundaries() {
        assertEquals("(−.5)/(2)", NaturalMathFormatter.format("−.5/2"))
        assertEquals("(1.25)/(−0.5)", NaturalMathFormatter.format("1.25/−0.5"))
        assertEquals("1.2(.3)/(4)", NaturalMathFormatter.format("1.2.3/4"))
        assertEquals("(2e−3)/(4)", NaturalMathFormatter.format("2e−3/4"))
        assertEquals("2e+", NaturalMathFormatter.format("2e+"))
    }

    @Test
    fun unarySignsAndPowersDoNotMisgroupFractions() {
        val signedPower = NaturalMath.parse("−2^2/3")
        assertTrue(signedPower is MathNode.Fraction)
        assertEquals("(−2^(2))/(3)", NaturalMathFormatter.format("−2^2/3"))
        assertEquals("2^(−3)", NaturalMathFormatter.format("2^−3"))
        assertEquals("1+−2", NaturalMathFormatter.format("1+−2"))
    }

    @Test
    fun exponentSlotKeepsLettersAndOperatorsInsideWithoutVisibleParentheses() {
        val firstLetter = NaturalMathEditing.adjust("2^", 2, 2, "2^x", 3)
        assertEquals("2^(x)", firstLetter.text)
        assertEquals(4, firstLetter.selectionStart)

        val secondTerm = NaturalMathEditing.adjust(
            firstLetter.text, firstLetter.selectionStart, firstLetter.selectionEnd,
            "2^(x+1)", 6
        )
        assertEquals("2^(x+1)", secondTerm.text)
        assertEquals(6, secondTerm.selectionStart)

        val ungroupedLegacyInput = NaturalMathEditing.adjust("2^2", 3, 3, "2^2x", 4)
        assertEquals("2^(2x)", ungroupedLegacyInput.text)
        assertEquals("2^((2x))", NaturalMathFormatter.format(ungroupedLegacyInput.text))
    }

    @Test
    fun arrowsEnterAndExitExponentSlotInOnePress() {
        val source = "2^(x+1)"
        assertEquals(3, NaturalMathEditing.moveHorizontally(source, 1, 1).selectionStart)
        assertEquals(1, NaturalMathEditing.moveHorizontally(source, 3, -1).selectionStart)
        assertEquals(source.length, NaturalMathEditing.moveHorizontally(source, 6, 1).selectionStart)
        assertEquals(6, NaturalMathEditing.moveHorizontally(source, source.length, -1).selectionStart)

        val groupedOnExit = NaturalMathEditing.moveHorizontally("2^x", 3, 1)
        assertEquals("2^(x)", groupedOnExit.text)
        assertEquals(groupedOnExit.text.length, groupedOnExit.selectionStart)
    }

    @Test
    fun backspaceRemovesEmptyExponentSlotAsOneUnit() {
        val edited = NaturalMathEditing.backspace("2^()", 3, 3)
        assertEquals("2^", edited.text)
        assertEquals(2, edited.selectionStart)
    }

    @Test
    fun highRiskFractionCorpusKeepsOnlyIntendedTermsInEachSlot() {
        val cases = mapOf(
            "1/−2" to "(1)/(−2)",
            "−1.25/−0.5" to "(−1.25)/(−0.5)",
            "1/2/3" to "((1)/(2))/(3)",
            "1/(2/3)" to "(1)/(((2)/(3)))",
            "−(1/2)" to "−((1)/(2))",
            "1+2/3−4" to "1+(2)/(3)−4",
            "a×b/c+d" to "(a×b)/(c)+d",
            "(−8.341×ln(2.5))/(1/493−(1/473))" to
                "((−8.341×ln(2.5)))/(((1)/(493)−((1)/(473))))"
        )
        cases.forEach { (source, expected) ->
            assertEquals(source, expected, NaturalMathFormatter.format(source))
            assertTreeRanges(NaturalMath.parse(source), source.length)
        }
    }

    @Test
    fun rangeAndRepeatedDotsDoNotMergeUnrelatedNumericObjects() {
        assertEquals("1…2", NaturalMathFormatter.format("1..2"))
        assertEquals("1.2(.3)/(4)", NaturalMathFormatter.format("1.2.3/4"))
    }

    @Test
    fun extremeNestingFallsBackInsteadOfCrashingRenderer() {
        val source = "(".repeat(4_000) + "1"
        val tree = NaturalMath.parse(source)
        assertTreeRanges(tree, source.length)
    }

    @Test
    fun malformedOperatorsRemainLocalDuringLiveInput() {
        listOf("1//2", "1/*2", "1+/2", "−/", "1^^2", "1/−").forEach { source ->
            val tree = NaturalMath.parse(source)
            assertTreeRanges(tree, source.length)
            assertNotNull(NaturalMathFormatter.format(source))
        }
    }

    @Test
    fun latexLeftRightDelimitersBecomeStretchableNodes() {
        val tree = NaturalMath.parse("\\left(\\frac{1}{2}\\right)", MathSource.Latex)
        assertTrue(tree is MathNode.Delimited)
        tree as MathNode.Delimited
        assertEquals("(", tree.left)
        assertEquals(")", tree.right)
        assertTrue(tree.content is MathNode.Fraction)
    }

    @Test
    fun mixedAndIncompleteDelimitersStayStructurallyBounded() {
        listOf("(1/2]", "[1/2)", "{−.5/(2", "(((1/2", "sqrt((1/2)").forEach { source ->
            val tree = NaturalMath.parse(source)
            assertTreeRanges(tree, source.length)
        }
    }

    @Test
    fun outerFunctionParenthesisCannotBecomeInnerSquareBracketCloser() {
        val tree = NaturalMath.parse("fft([)")
        assertEquals("fft([])", NaturalMathFormatter.format("fft([)"))
        assertTrue(tree is MathNode.Row)
        val functionArguments = (tree as MathNode.Row).items.last() as MathNode.Delimited
        assertEquals("(", functionArguments.left)
        assertEquals(")", functionArguments.right)
        val list = functionArguments.content as MathNode.Delimited
        assertEquals("[", list.left)
        assertEquals("]", list.right)
        assertEquals(5, list.end)

        // Moving right first commits the inferred inner bracket without
        // duplicating or stealing the already present outer parenthesis.
        val closed = NaturalMathEditing.moveHorizontally("fft([)", 5, 1)
        assertEquals("fft([])", closed.text)
        assertEquals(6, closed.selectionStart)
    }

    @Test
    fun evaluationCommitsEveryDelimiterAlreadyInferredByTheDisplay() {
        val functionList = NaturalMathEditing.commitInferredDelimiters("fft([1,1)", 8)
        assertEquals("fft([1,1])", functionList.text)
        assertEquals(9, functionList.selectionStart)
        assertEquals("fft([1,1])", ExpressionFormatter.toEngineInput("fft([1,1)"))

        val nested = NaturalMathEditing.commitInferredDelimiters("fft([[1,2)")
        assertEquals("fft([[1,2]])", nested.text)
        assertEquals(nested.text.length, nested.selectionStart)

        val fullyUnclosed = NaturalMathEditing.commitInferredDelimiters("fft([1,1")
        assertEquals("fft([1,1])", fullyUnclosed.text)

        val complete = NaturalMathEditing.commitInferredDelimiters("fft([1,1])", 6)
        assertEquals("fft([1,1])", complete.text)
        assertEquals(6, complete.selectionStart)
    }

    @Test
    fun deterministicFuzzKeepsEveryNodeRangeInsideSource() {
        val alphabet = "0123456789.eE+−-×*/^()[]{}abc_≤≥≠,"
        var state = 0x4c4347
        repeat(2_000) {
            state = state * 1_103_515_245 + 12_345
            val length = ((state ushr 16) and 31)
            val source = buildString(length) {
                repeat(length) {
                    state = state * 1_103_515_245 + 12_345
                    append(alphabet[(state ushr 16).mod(alphabet.length)])
                }
            }
            val tree = NaturalMath.parse(source)
            assertTreeRanges(tree, source.length)
            for (cursor in 0..source.length) {
                for (direction in listOf(-1, 1)) {
                    val moved = NaturalMathEditing.moveHorizontally(source, cursor, direction)
                    assertTrue(moved.selectionStart in 0..moved.text.length)
                    assertTrue(moved.selectionEnd in 0..moved.text.length)
                    assertTreeRanges(NaturalMath.parse(moved.text), moved.text.length)
                }
            }
        }
    }

    private fun assertTreeRanges(node: MathNode, sourceLength: Int) {
        assertTrue("${node::class.simpleName}: start=${node.start}", node.start in 0..sourceLength)
        assertTrue("${node::class.simpleName}: end=${node.end}", node.end in 0..sourceLength)
        assertTrue("${node::class.simpleName}: ${node.start}..${node.end}", node.start <= node.end)
        when (node) {
            is MathNode.Text -> Unit
            is MathNode.Row -> node.items.forEach { assertTreeRanges(it, sourceLength) }
            is MathNode.Fraction -> {
                assertTrue(node.barStart in 0..sourceLength)
                assertTrue(node.barEnd in node.barStart..sourceLength)
                assertTreeRanges(node.numerator, sourceLength)
                assertTreeRanges(node.denominator, sourceLength)
            }
            is MathNode.Script -> {
                assertTreeRanges(node.base, sourceLength)
                node.superscript?.let { assertTreeRanges(it, sourceLength) }
                node.subscript?.let { assertTreeRanges(it, sourceLength) }
            }
            is MathNode.Root -> {
                assertTreeRanges(node.radicand, sourceLength)
                node.index?.let { assertTreeRanges(it, sourceLength) }
            }
            is MathNode.Integral -> {
                node.integrand?.let { assertTreeRanges(it, sourceLength) }
                node.variable?.let { assertTreeRanges(it, sourceLength) }
                node.lower?.let { assertTreeRanges(it, sourceLength) }
                node.upper?.let { assertTreeRanges(it, sourceLength) }
            }
            is MathNode.Summation -> {
                node.expression?.let { assertTreeRanges(it, sourceLength) }
                node.index?.let { assertTreeRanges(it, sourceLength) }
                node.lower?.let { assertTreeRanges(it, sourceLength) }
                node.upper?.let { assertTreeRanges(it, sourceLength) }
            }
            is MathNode.Derivative -> {
                assertTreeRanges(node.expression, sourceLength)
                node.variable?.let { assertTreeRanges(it, sourceLength) }
                node.order?.let { assertTreeRanges(it, sourceLength) }
            }
            is MathNode.Limit -> {
                assertTreeRanges(node.expression, sourceLength)
                node.variable?.let { assertTreeRanges(it, sourceLength) }
                node.point?.let { assertTreeRanges(it, sourceLength) }
                node.direction?.let { assertTreeRanges(it, sourceLength) }
            }
            is MathNode.Delimited -> assertTreeRanges(node.content, sourceLength)
            is MathNode.Matrix -> node.rows.flatten().forEach { assertTreeRanges(it, sourceLength) }
        }
    }
}
