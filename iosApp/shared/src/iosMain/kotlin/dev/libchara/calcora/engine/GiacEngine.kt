@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.libchara.calcora.engine

import dev.libchara.calcora.generated.resources.Res
import dev.libchara.calcora.native.calcora_engine_evaluate
import dev.libchara.calcora.native.calcora_engine_help
import dev.libchara.calcora.native.calcora_engine_init
import dev.libchara.calcora.native.calcora_engine_interrupt
import dev.libchara.calcora.native.calcora_engine_plot_sample
import dev.libchara.calcora.native.calcora_engine_reset
import dev.libchara.calcora.native.calcora_engine_set_language
import dev.libchara.calcora.native.calcora_engine_version
import kotlinx.cinterop.toKString
import org.json.JSONArray
import org.json.JSONObject

object GiacEngine {
    private var loaded = false
    private var helpSource = ""

    suspend fun initialize(@Suppress("UNUSED_PARAMETER") context: Any? = null) {
        init()
        runCatching {
            helpSource = Res.readBytes("files/aide_cas").decodeToString()
            HelpParser.loadFromString(helpSource)
        }
    }

    fun init(): Boolean {
        if (!loaded) runCatching { calcora_engine_init(); loaded = true }
        return loaded
    }

    fun evaluate(input: String, mode: EvalMode): CalcResult {
        if (!init()) return CalcResult(input = input, error = "Native backend failed to load", mode = mode, backend = "unavailable")
        val normalized = ExpressionFormatter.toEngineInput(input)
        val raw = calcora_engine_evaluate(normalized, mode.name)?.toKString().orEmpty()
        return parseResult(input, mode, raw)
    }

    fun evaluateRawXcas(input: String): CalcResult = evaluate(input, EvalMode.RawXcas)
    fun resetSession() { if (init()) calcora_engine_reset() }
    fun interrupt() { if (init()) calcora_engine_interrupt() }
    fun version(): String = if (init()) calcora_engine_version()?.toKString().orEmpty() else "native unavailable"

    fun help(command: String): String {
        if (!init()) return ""
        HelpParser.lookup(command.trim())?.let { entry ->
            return buildString {
                appendLine("Description: ${entry.description}")
                if (entry.related.isNotEmpty()) appendLine("Related: ${entry.related.joinToString(", ")}")
                if (entry.examples.isNotBlank()) { appendLine("Examples:"); append(entry.examples) }
            }
        }
        val native = calcora_engine_help(command.trim())?.toKString().orEmpty()
        if (native.contains("See also:")) return "NoHelp:" + native.substringAfter("See also:").trim()
        val suggestions = HelpParser.search(command).take(9)
        return if (suggestions.isEmpty()) "" else "NoHelp:" + suggestions.mapIndexed { i, name -> "${i + 1}/ $name" }.joinToString(" ")
    }

    fun helpSearch(query: String) = HelpParser.search(query)
    fun helpSearchScored(query: String) = HelpParser.searchScored(query)

    fun plotSample(
        expr: String, varName: String = "x", xmin: Double = -10.0,
        xmax: Double = 10.0, samples: Int = 500
    ): List<Pair<Double, Double>> {
        if (!init()) return emptyList()
        val raw = calcora_engine_plot_sample(expr, varName, xmin, xmax, samples)
            ?.toKString().orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                array.getJSONArray(index).let { it.getDouble(0) to it.getDouble(1) }
            }
        }.getOrDefault(emptyList())
    }

    fun setLanguage(code: Int) {
        if (init()) calcora_engine_set_language(code)
        HelpParser.reloadForLanguage(if (code == 8) 8 else 2)
    }

    private fun parseResult(input: String, mode: EvalMode, raw: String): CalcResult = runCatching {
        val json = JSONObject(raw)
        CalcResult(
            input = input, symbolic = json.optString("symbolic"), numeric = json.optString("numeric"),
            latex = json.optString("latex"), numericLatex = json.optString("numericLatex"),
            error = json.optString("error").takeIf(String::isNotBlank), mode = mode,
            backend = json.optString("backend", "native"), isPlot = json.optBoolean("isGraphic"),
            plotData = json.optString("plotData")
        )
    }.getOrElse { CalcResult(input = input, symbolic = raw, mode = mode, backend = "native") }
}
