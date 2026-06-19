package dev.libchara.calcora.data

import android.content.Context
import dev.libchara.calcora.engine.CalcResult
import dev.libchara.calcora.engine.EvalMode
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

data class HistoryEntry(
    val id: Long,
    val expression: String,
    val result: String,
    val numeric: String,
    val mode: EvalMode,
    val timestamp: Long,
    val isPlot: Boolean = false,
    val plotData: String = "",
    
) {
    val formattedTime: String by lazy(LazyThreadSafetyMode.NONE) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    }
}

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    HistoryEntry(
                        id = item.optLong("id"),
                        expression = item.optString("expression"),
                        result = item.optString("result"),
                        numeric = item.optString("numeric"),
                        mode = EvalMode.fromName(item.optString("mode")),
                        timestamp = item.optLong("timestamp"),
                        isPlot = item.optBoolean("isPlot", false),
                        plotData = item.optString("plotData", ""),
                        
                    )
                )
            }
        }
    }

    fun add(result: CalcResult, maxItems: Int = 64): List<HistoryEntry> {
        if (result.input.isBlank()) return load()
        val now = System.currentTimeMillis()
        val next = HistoryEntry(
            id = now,
            expression = result.input,
            result = result.primary,
            numeric = result.numeric,
            mode = result.mode,
            timestamp = now,
            isPlot = result.isPlot,
            plotData = result.plotData,
            
        )
        val updated = listOf(next) + load().filterNot { it.expression == result.input && it.result == result.primary }
        val limited = updated.take(maxItems)
        save(limited)
        return limited
    }

    fun remove(entry: HistoryEntry): List<HistoryEntry> {
        val updated = load().filterNot { it.id == entry.id }
        save(updated)
        return updated
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(items: List<HistoryEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("expression", item.expression)
                    .put("result", item.result)
                    .put("numeric", item.numeric)
                    .put("mode", item.mode.name)
                    .put("timestamp", item.timestamp)
                    .put("isPlot", item.isPlot)
                    .put("plotData", item.plotData)
                    
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
