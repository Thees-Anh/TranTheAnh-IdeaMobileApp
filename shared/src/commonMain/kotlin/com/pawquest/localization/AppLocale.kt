package com.pawquest.localization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val tag: String, val nativeName: String) {
    VIETNAMESE("vi", "Tiếng Việt"), ENGLISH("en", "English");
    companion object {
        fun fromTag(tag: String?): AppLanguage = if (tag?.lowercase()?.startsWith("vi") == true) VIETNAMESE else ENGLISH
    }
}

/** Installation-wide display preference. Never modifies persisted gameplay/user-authored data. */
object AppLocale {
    var language by mutableStateOf(AppLanguage.ENGLISH)
        private set
    private var persist: (AppLanguage) -> Unit = {}
    fun initialize(language: AppLanguage, persist: (AppLanguage) -> Unit = {}) {
        this.persist = persist
        this.language = language
    }
    fun select(language: AppLanguage) {
        if (this.language == language) return
        persist(language)
        this.language = language
    }
}

/** Translate only system copy at presentation boundaries, not arbitrary database/user text. */
fun tr(text: String): String = translate(text, AppLocale.language)
fun trOrNull(text: String?): String? = text?.let(::tr)

private data class Translation(val en: String, val vi: String) {
    private fun pattern(value: String): Regex {
        val parts = Regex("\\{\\d+\\}").findAll(value).toList()
        var end = 0
        val result = buildString {
            append("^")
            for (part in parts) {
                append(Regex.escape(value.substring(end, part.range.first)))
                append("([\\s\\S]*?)")
                end = part.range.last + 1
            }
            append(Regex.escape(value.substring(end))); append("$")
        }
        return Regex(result)
    }
    private val enPattern by lazy { pattern(en) }
    private val viPattern by lazy { pattern(vi) }
    fun resolve(text: String, language: AppLanguage): String? {
        val source = if (language == AppLanguage.ENGLISH) vi else en
        val target = if (language == AppLanguage.ENGLISH) en else vi
        val match = (if (language == AppLanguage.ENGLISH) viPattern else enPattern).matchEntire(text) ?: return null
        val indexes = Regex("\\{(\\d+)\\}").findAll(source).map { it.groupValues[1] }.toList()
        val values = indexes.zip(match.groupValues.drop(1)).toMap()
        return Regex("\\{(\\d+)\\}").replace(target) { values[it.groupValues[1]] ?: it.value }
    }
}

private val translations = translationRows.trimIndent().lineSequence().filter { it.isNotBlank() }.map {
    val parts = it.split('|', limit = 2)
    require(parts.size == 2) { "Invalid translation row" }
    Translation(parts[0].replace("\\n", "\n"), parts[1].replace("\\n", "\n"))
}.toList()
private val english = translations.associate { it.vi to it.en }
private val vietnamese = translations.associate { it.en to it.vi }
private val templates = translations.filter { "{0}" in it.en || "{0}" in it.vi }
    .sortedByDescending { it.en.replace(Regex("\\{\\d+\\}"), "").length }

fun translate(text: String, language: AppLanguage): String {
    val direct = (if (language == AppLanguage.ENGLISH) english else vietnamese)[text]
    if (direct != null) return direct
    // Already-localized text and user content remain unchanged. Captured arguments are never translated.
    if ((if (language == AppLanguage.ENGLISH) vietnamese else english).containsKey(text)) return text
    return templates.firstNotNullOfOrNull { it.resolve(text, language) } ?: text
}

internal fun translationPairs(): List<Pair<String, String>> = translations.map { it.en to it.vi }
