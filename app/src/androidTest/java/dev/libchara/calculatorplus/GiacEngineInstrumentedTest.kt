package dev.libchara.calcora

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.libchara.calcora.engine.EvalMode
import dev.libchara.calcora.engine.GiacEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GiacEngineInstrumentedTest {
    @Test
    fun nativeGiacBackendEvaluatesCoreExpressions() {
        assertTrue(GiacEngine.init())
        assertTrue(GiacEngine.version().contains("Giac 2.0.0 native core"))

        val arithmetic = GiacEngine.evaluate("1+2*3", EvalMode.Auto)
        assertNull(arithmetic.error, arithmetic.error)
        assertEquals("7", arithmetic.symbolic)
        assertTrue(arithmetic.backend.contains("giac 2.0.0 native core"))

        val derivative = GiacEngine.evaluateRawXcas("diff(sin(x),x)")
        assertNull(derivative.error, derivative.error)
        assertTrue(derivative.symbolic.contains("cos"))
    }

    @Test
    fun plotCommandsReturnGraphicDataWithoutCrashing() {
        assertTrue(GiacEngine.init())

        listOf(
            "plot(sin(x),x=-2..2)",
            "plotfunc(x^2,x=-2..2)",
            "plotparam([cos(t),sin(t)],t=0..2*pi)",
            "listplot([1,4,2,3])"
        ).forEach { expression ->
            val result = GiacEngine.evaluateRawXcas(expression)
            assertNull("$expression: ${result.error}", result.error)
            assertTrue("$expression was not recognized as a plot", result.isPlot)
            assertTrue("$expression returned no plot data", result.plotData.isNotBlank())
            assertTrue("$expression exposed its sampled points as symbolic text", result.symbolic.isBlank())
        }

        val approximatePlot = GiacEngine.evaluate("plot(cos(x))", EvalMode.Approx)
        assertNull(approximatePlot.error, approximatePlot.error)
        assertTrue(approximatePlot.isPlot)
        assertTrue(approximatePlot.plotData.isNotBlank())
    }
}
