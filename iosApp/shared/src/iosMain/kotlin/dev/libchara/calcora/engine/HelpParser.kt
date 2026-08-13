package dev.libchara.calcora.engine

import androidx.compose.runtime.mutableStateOf

data class HelpEntry(
    val name: String,
    val description: String,
    val related: List<String>,
    val examples: String,
    val signature: String = ""
) {
    val exampleLines get() = examples.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    val syntax get() = if (signature.isBlank()) "$name(…)" else "$name($signature)"
}

object HelpParser {
    internal var loaded = false
    val isReady = mutableStateOf(false)
    private var preferredLang = 2
    private var source = ""
    private val helpMap = mutableMapOf<String, HelpEntry>()
    private val allNames = mutableListOf<String>()
    private val indexedNames = mutableListOf<IndexedName>()
    private val sortedNames = mutableListOf<String>()
    private val searchCache = mutableMapOf<String, List<Scored>>()

    fun loadFromString(text: String) {
        source = text
        parse(text)
    }

    fun reloadForLanguage(newLang: Int) {
        if (preferredLang == newLang && loaded) return
        preferredLang = newLang
        if (source.isNotBlank()) parse(source)
    }

    private fun parse(text: String) {
        loaded = false
        isReady.value = false
        helpMap.clear(); allNames.clear(); indexedNames.clear(); sortedNames.clear(); searchCache.clear()
        var currentName = ""
        var description = ""
        var signature = ""
        val related = mutableListOf<String>()
        val examples = StringBuilder()
        var inBody = false

        fun saveCurrent() {
            if (currentName.isBlank()) return
            val aliases = currentName.split(Regex("\\s+")).filter(String::isNotBlank)
            val entry = HelpEntry(
                aliases.first(), description.trim(), related.distinct(),
                examples.toString().trim(), signature.trim()
            )
            aliases.forEach { alias ->
                helpMap[alias] = entry.copy(name = alias)
                if (alias !in allNames) allNames += alias
            }
        }

        text.lineSequence().forEach { line ->
            when {
                line.startsWith("# ") -> {
                    saveCurrent()
                    currentName = line.removePrefix("# ").trim()
                    description = ""; signature = ""; related.clear(); examples.clear(); inBody = true
                }
                inBody && line.length > 1 && line[0] in '1'..'9' && line[1] == ' ' -> {
                    val content = line.dropWhile { it != ' ' }.trim()
                    val code = line[0].digitToIntOrNull() ?: 0
                    if (code == preferredLang || code == 2 || description.isEmpty()) description = content
                }
                line.startsWith("-") -> {
                    val name = line.trimStart('-').dropWhile(Char::isDigit).trim()
                        .substringBefore(' ').trim()
                    if (name.isNotBlank() && name.all { it.isLetterOrDigit() || it == '_' }) related += name
                }
                line.startsWith("0 ") -> signature = line.removePrefix("0 ").trim()
                inBody && line.isNotBlank() && !line.startsWith("#") && !line.startsWith("-") &&
                    !line.startsWith("0 ") && line.firstOrNull()?.isDigit() != true ->
                    examples.append(line.trim()).append('\n')
            }
        }
        saveCurrent()
        indexedNames += allNames.map { IndexedName(it, it.lowercase()) }
        sortedNames += allNames.distinct().sortedBy(String::lowercase)
        loaded = true
        isReady.value = true
    }

    fun lookup(name: String): HelpEntry? {
        if (!loaded) return null
        val normalized = name.trim()
        return helpMap[normalized] ?: helpMap[normalized.lowercase()] ?: helpMap[normalized.uppercase()]
    }

    fun getAllNames(): List<String> = allNames.toList()
    data class Scored(val name: String, val score: Int)
    private data class IndexedName(val name: String, val lower: String)
    fun search(query: String) = searchScored(query).map { it.name }

    fun searchScored(query: String): List<Scored> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return sortedNames.map { Scored(it, 0) }
        return searchCache.getOrPut(q) {
            indexedNames.asSequence().map { indexed ->
                val exact = indexed.lower.indexOf(q)
                var score = if (exact >= 0) 8 + (if (exact == 0) 7 else 0) + (q.length * 2).coerceAtMost(10) else 0
                var previous = -1
                q.forEach { character ->
                    val found = indexed.lower.indexOf(character, previous + 1)
                    if (found >= 0) { score += 1 + if (found == previous + 1) 1 else 0; previous = found }
                }
                if (indexed.lower == q) score += 20
                Scored(indexed.name, score)
            }.filter { it.score > 1 }
                .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.name })
                .toList()
        }
    }
}
