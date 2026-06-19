package dev.libchara.calcora.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalMathFormatterTest {
    @Test
    fun formatsCommonCalculatorSyntax() {
        assertEquals("√(π)+x²÷3", NaturalMathFormatter.format("sqrt(pi)+x^2/3"))
        assertEquals("10⁻³×α", NaturalMathFormatter.format("10^-3*alpha"))
        assertEquals("x⁽ⁿ⁺¹⁾≤∞", NaturalMathFormatter.format("x^(n+1)<=infinity"))
    }

    @Test
    fun doesNotReplaceInsideWordsOrStrings() {
        assertEquals("piecewise+pilot", NaturalMathFormatter.format("piecewise+pilot"))
        assertEquals("\"sqrt(pi)\"+√(π)", NaturalMathFormatter.format("\"sqrt(pi)\"+sqrt(pi)"))
    }

    @Test
    fun offsetMappingStaysInsideTransformedBounds() {
        val display = NaturalMathFormatter.formatWithOffsets("sqrt(pi)+x^(n+1)")

        for (offset in display.originalToTransformed.indices) {
            assertTrue(display.originalToTransformed[offset] in 0..display.text.length)
        }
        for (offset in display.transformedToOriginal.indices) {
            assertTrue(display.transformedToOriginal[offset] in 0.."sqrt(pi)+x^(n+1)".length)
        }
    }
}
