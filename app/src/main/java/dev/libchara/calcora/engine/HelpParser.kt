package dev.libchara.calcora.engine

import androidx.compose.runtime.mutableStateOf
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

data class HelpEntry(val name: String, val description: String, val related: List<String>, val examples: String)

object HelpParser {
    internal var loaded = false
    val isReady = mutableStateOf(false)
    private var preferredLang = 2 // 2=en, 8=zh
    private val helpMap = mutableMapOf<String, HelpEntry>()
    private val allNames = mutableListOf<String>()
    private val indexedNames = mutableListOf<IndexedName>()
    private val sortedNames = mutableListOf<String>()
    private val searchCache = object : LinkedHashMap<String, List<Scored>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Scored>>): Boolean = size > 96
    }

    @Synchronized
    fun reloadForLanguage(newLang: Int) {
        if (preferredLang == newLang && loaded) return
        preferredLang = newLang
        loaded = false
        isReady.value = false
        helpMap.clear()
        allNames.clear()
        indexedNames.clear()
        sortedNames.clear()
        searchCache.clear()
    }

    @Synchronized
    fun loadFromStream(inputStream: java.io.InputStream) {
        if (loaded) return
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var currentName = ""
                var desc = ""
                val related = mutableListOf<String>()
                val examples = StringBuilder()
                var inBody = false

                fun saveCurrent() {
                    if (currentName.isBlank()) return
                    val parts = currentName.split(" ", limit = 2)
                    val name = parts[0]
                    val entry = HelpEntry(name, desc.trim(), related.toList(), examples.toString().trim())
                    helpMap[name] = entry
                    if (parts.size > 1) helpMap[parts[1]] = entry.copy(name = parts[1])
                    allNames.add(name)
                }

                reader.forEachLine { line ->
                    when {
                        line.startsWith("# ") -> {
                            saveCurrent()
                            currentName = line.removePrefix("# ").trim()
                            desc = ""; related.clear(); examples.clear()
                            inBody = true
                        }
                        inBody && line.isNotEmpty() && line[0] in '1'..'9' && line.length > 1 && line[1] == ' ' -> {
                            val content = line.removePrefix(line.takeWhile { it != ' ' }).trim()
                            val langCode = line[0].toString().toIntOrNull() ?: 0
                            if (langCode == preferredLang) desc = content
                            else if (langCode == 2) desc = content
                            else if (desc.isEmpty() && langCode != preferredLang) desc = content
                        }
                        line.startsWith("-") -> {
                            var rest = line.trimStart('-')
                            while (rest.isNotEmpty() && rest[0].isDigit()) rest = rest.drop(1)
                            rest = rest.trim()
                            val name = rest.split(" ", limit = 2).firstOrNull()?.trim() ?: ""
                            if (name.isNotBlank() && name.all { it.isLetterOrDigit() || it == '_' }) related.add(name)
                        }
                        line.startsWith("0 ") -> Unit
                        inBody && line.startsWith("3 ") -> Unit
                        inBody && line.startsWith("4 ") -> Unit
                        inBody && line.startsWith("8 ") -> Unit
                        inBody && line.isNotBlank() && !line.startsWith("#") &&
                            !line.startsWith("-") && !line.startsWith("0 ") &&
                            !(line.isNotEmpty() && line[0] in '1'..'9') -> {
                            examples.append(line.trim()).append("\n")
                        }
                        else -> { /* skip empty/unrecognized */ }
                    }
                }
                saveCurrent()
            }
            indexedNames.addAll(allNames.map { IndexedName(it, it.lowercase(Locale.ROOT)) })
            sortedNames.addAll(allNames.sorted())
            loaded = true; isReady.value = true
        } catch (_: Exception) {}
    }

    @Synchronized
    fun lookup(name: String): HelpEntry? {
        if (!loaded) return null
        val n = name.trim()
        return helpMap[n] ?: helpMap[n.lowercase(Locale.ROOT)] ?: helpMap[n.uppercase(Locale.ROOT)]
    }

    @Synchronized
    fun getAllNames(): List<String> = allNames.toList()

    data class Scored(val name: String, val score: Int)
    private data class IndexedName(val name: String, val lower: String)

    fun search(query: String): List<String> = searchScored(query).map { it.name }

    @Synchronized
    fun searchScored(query: String): List<Scored> {
        val q = query.lowercase(Locale.ROOT).trim()
        if (q.isEmpty()) return sortedNames.map { Scored(it, 0) }
        searchCache[q]?.let { return it }
        val result = indexedNames.asSequence().map { indexed ->
            val lower = indexed.lower
            var score = 0
            val exactIndex = lower.indexOf(q)
            if (exactIndex >= 0) {
                score += 8
                if (exactIndex == 0) score += 7
                score += (q.length * 2).coerceAtMost(10)
            }
            var lastIndex = -1
            for (ch in q) {
                val idx = lower.indexOf(ch, startIndex = lastIndex + 1)
                if (idx >= 0) {
                    score += 1
                    if (idx == lastIndex + 1) score += 1
                    lastIndex = idx
                }
            }
            if (lower == q) score += 20
            Scored(indexed.name, score)
        }.filter { it.score > 1 }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.name })
            .toList()
        searchCache[q] = result
        return result
    }
}
