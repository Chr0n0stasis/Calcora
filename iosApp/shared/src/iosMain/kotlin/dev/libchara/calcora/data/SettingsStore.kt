package dev.libchara.calcora.data

import dev.libchara.calcora.engine.EvalMode
import platform.Foundation.NSUserDefaults

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }
enum class AppLanguage(val label: String, val giacCode: Int) {
    System("System", -1), English("English", 2), Chinese("中文", 8)
}
enum class AngleUnit(val label: String) { Rad("Rad"), Deg("Deg") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val angleUnit: AngleUnit = AngleUnit.Rad,
    val precision: Int = 12,
    val defaultEvalMode: EvalMode = EvalMode.Auto,
    val language: AppLanguage = AppLanguage.System,
    val autocompleteEnabled: Boolean = true,
    val syntaxHighlighting: Boolean = true,
    val historyLimit: Int = 64
)

class SettingsStore(@Suppress("UNUSED_PARAMETER") context: Any? = null) {
    private val defaults = NSUserDefaults.standardUserDefaults

    fun load(): AppSettings = AppSettings(
        themeMode = enumValueOrDefault(defaults.stringForKey("theme"), ThemeMode.System),
        angleUnit = enumValueOrDefault(defaults.stringForKey("angle"), AngleUnit.Rad),
        precision = defaults.integerForKey("precision").toInt().let { if (it == 0) 12 else it }.coerceIn(4, 20),
        defaultEvalMode = EvalMode.fromName(defaults.stringForKey("mode")),
        language = AppLanguage.entries.firstOrNull { it.name == defaults.stringForKey("lang") } ?: AppLanguage.System,
        autocompleteEnabled = if (defaults.objectForKey("autocomplete") == null) true else defaults.boolForKey("autocomplete"),
        syntaxHighlighting = if (defaults.objectForKey("syntaxHl") == null) true else defaults.boolForKey("syntaxHl"),
        historyLimit = defaults.integerForKey("historyLimit").toInt().let { if (it == 0) 64 else it }.coerceIn(20, 200)
    )

    fun save(settings: AppSettings) = with(defaults) {
        setObject(settings.themeMode.name, "theme")
        setObject(settings.angleUnit.name, "angle")
        setInteger(settings.precision.toLong(), "precision")
        setObject(settings.defaultEvalMode.name, "mode")
        setObject(settings.language.name, "lang")
        setBool(settings.autocompleteEnabled, "autocomplete")
        setBool(settings.syntaxHighlighting, "syntaxHl")
        setInteger(settings.historyLimit.toLong(), "historyLimit")
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
