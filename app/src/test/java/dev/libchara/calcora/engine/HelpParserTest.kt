package dev.libchara.calcora.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class HelpParserTest {
    @Test
    fun parsesLocalizedStructuredHelpAndAllAliases() {
        val source = """
            # sin SIN sine
            1 Sinus.
            2 Sine.
            8 正弦函数。
            0 Expr or Opt
            -1 asin
            -2 convert
            sin(0)
            sin(pi/2)

            # solve resoudre
            2 Solves an equation.
            8 求解方程。
            0 Expr,[Var]
            -1 linsolve
            solve(x^2=1,x)
        """.trimIndent()

        HelpParser.reloadForLanguage(8)
        HelpParser.loadFromStream(ByteArrayInputStream(source.toByteArray()))

        val entry = requireNotNull(HelpParser.lookup("sin"))
        assertEquals("正弦函数。", entry.description)
        assertEquals("Expr or Opt", entry.signature)
        assertEquals("sin(Expr or Opt)", entry.syntax)
        assertEquals(listOf("asin", "convert"), entry.related)
        assertEquals(listOf("sin(0)", "sin(pi/2)"), entry.exampleLines)

        assertEquals("SIN", HelpParser.lookup("SIN")?.name)
        assertEquals("sine", HelpParser.lookup("sine")?.name)
        assertTrue(HelpParser.getAllNames().containsAll(listOf("sin", "SIN", "sine")))
        assertEquals("solve", HelpParser.searchScored("solve").first().name)
    }
}
