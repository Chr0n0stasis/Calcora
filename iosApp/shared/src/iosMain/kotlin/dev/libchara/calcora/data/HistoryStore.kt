package dev.libchara.calcora.data

import dev.libchara.calcora.engine.CalcResult
import dev.libchara.calcora.engine.EvalMode
import org.json.JSONArray
import org.json.JSONObject
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateWithTimeIntervalSince1970

data class HistoryEntry(
    val id: Long,
    val expression: String,
    val result: String,
    val numeric: String,
    val latex: String = "",
    val numericLatex: String = "",
    val mode: EvalMode,
    val timestamp: Long,
    val isPlot: Boolean = false,
    val plotData: String = ""
) {
    val formattedTime: String by lazy(LazyThreadSafetyMode.NONE) {
        val formatter = NSDateFormatter().apply {
            dateStyle = NSDateFormatterShortStyle
            timeStyle = NSDateFormatterShortStyle
        }
        formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0))
    }
}

class HistoryStore(@Suppress("UNUSED_PARAMETER") context: Any? = null) {
    private val defaults = NSUserDefaults.standardUserDefaults

    fun load(): List<HistoryEntry> {
        val array = runCatching { JSONArray(defaults.stringForKey(KEY) ?: "[]") }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(HistoryEntry(
                    id = item.optLong("id"), expression = item.optString("expression"),
                    result = item.optString("result"), numeric = item.optString("numeric"),
                    latex = item.optString("latex"), numericLatex = item.optString("numericLatex"),
                    mode = EvalMode.fromName(item.optString("mode")), timestamp = item.optLong("timestamp"),
                    isPlot = item.optBoolean("isPlot"), plotData = item.optString("plotData")
                ))
            }
        }
    }

    fun add(result: CalcResult, maxItems: Int = 64): List<HistoryEntry> {
        if (result.input.isBlank()) return load()
        val now = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val next = HistoryEntry(now, result.input, result.primary, result.numeric,
            result.latex, result.numericLatex, result.mode, now, result.isPlot, result.plotData)
        return (listOf(next) + load().filterNot {
            it.expression == result.input && it.result == result.primary
        }).take(maxItems).also(::save)
    }

    fun remove(entry: HistoryEntry): List<HistoryEntry> =
        load().filterNot { it.id == entry.id }.also(::save)

    fun clear() { defaults.removeObjectForKey(KEY) }

    private fun save(items: List<HistoryEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("expression", item.expression)
                .put("result", item.result).put("numeric", item.numeric).put("latex", item.latex)
                .put("numericLatex", item.numericLatex).put("mode", item.mode.name)
                .put("timestamp", item.timestamp).put("isPlot", item.isPlot).put("plotData", item.plotData))
        }
        defaults.setObject(array.toString(), KEY)
    }

    private companion object { const val KEY = "history.items" }
}
