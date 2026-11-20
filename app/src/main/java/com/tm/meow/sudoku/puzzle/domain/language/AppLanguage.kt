package com.tm.meow.sudoku.puzzle.domain.language

/** One selectable app language. [tag] = BCP-47 tag handed to the per-app locale API (also the res qualifier source). */
data class AppLanguage(val tag: String, val nativeName: String, val englishName: String)

/** The supported languages, in dialog order. Pure Kotlin: the tag -> LocaleListCompat mapping lives in the data layer. */
object AppLanguages {
    val ENGLISH = AppLanguage("en", "English", "English")

    private val TRADITIONAL_CHINESE = AppLanguage("zh-TW", "繁體中文", "Traditional Chinese")

    /** Index 0 = English (default). Keep in sync with res/xml/locales_config.xml and the values-xx folders. */
    val ALL: List<AppLanguage> = listOf(
        ENGLISH,
        AppLanguage("ja", "日本語", "Japanese"),
        // zh-TW <-> values-zh-rTW. zh-HK / zh-MO / zh-Hant device locales resolve here too (see resolve).
        TRADITIONAL_CHINESE,
        AppLanguage("nl", "Nederlands", "Dutch"),
        AppLanguage("de", "Deutsch", "German"),
        AppLanguage("ko", "한국어", "Korean"),
        AppLanguage("it", "Italiano", "Italian"),
        AppLanguage("es", "Español", "Spanish"),
        AppLanguage("fr", "Français", "French"),
        AppLanguage("ru", "Русский", "Russian"),
        AppLanguage("tr", "Türkçe", "Turkish"),
        AppLanguage("pt", "Português", "Portuguese"),
    )

    /**
     * Supported language for any BCP-47 tag (e.g. "de-AT" -> German, "zh-Hant-HK" -> Traditional Chinese), or null when
     * unsupported (including Simplified Chinese: zh, zh-CN, zh-Hans, zh-SG). Underscores are accepted.
     */
    fun resolve(tag: String?): AppLanguage? {
        if (tag.isNullOrBlank()) return null
        val parts = tag.trim().replace('_', '-').split('-').filter { it.isNotEmpty() }
        val lang = parts.firstOrNull()?.lowercase() ?: return null
        if (lang == "zh") {
            val rest = parts.drop(1).map { it.lowercase() }
            val traditional = "hant" in rest || rest.any { it == "tw" || it == "hk" || it == "mo" }
            return if (traditional && "hans" !in rest) TRADITIONAL_CHINESE else null
        }
        return ALL.firstOrNull { it.tag.equals(lang, ignoreCase = true) }
    }

    /**
     * Language to show as selected: the explicit app language if one is set and supported, else the device language if
     * supported, else English.
     */
    fun effective(appTag: String?, systemTag: String?): AppLanguage =
        resolve(appTag) ?: resolve(systemTag) ?: ENGLISH
}
