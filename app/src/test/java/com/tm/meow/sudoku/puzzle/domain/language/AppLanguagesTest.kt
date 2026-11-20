package com.tm.meow.sudoku.puzzle.domain.language

import com.tm.meow.sudoku.puzzle.domain.repository.LanguageRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.GetAppLanguage
import com.tm.meow.sudoku.puzzle.domain.usecase.SetAppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguagesTest {

    private class FakeLanguageRepository(var app: String? = null, var system: String? = null) : LanguageRepository {
        override fun appLanguageTag() = app
        override fun systemLanguageTag() = system
        override fun apply(tag: String) {
            app = tag
        }
    }

    @Test
    fun listOrderAndShape() {
        assertEquals(
            listOf("en", "ja", "zh-TW", "nl", "de", "ko", "it", "es", "fr", "ru", "tr", "pt"),
            AppLanguages.ALL.map { it.tag },
        )
        assertEquals(AppLanguages.ENGLISH, AppLanguages.ALL[0])
        assertEquals(AppLanguages.ALL.size, AppLanguages.ALL.map { it.tag }.toSet().size)
        assertTrue(AppLanguages.ALL.all { it.nativeName.isNotBlank() && it.englishName.isNotBlank() })
        assertEquals("日本語", AppLanguages.ALL[1].nativeName)
        assertEquals("Traditional Chinese", AppLanguages.ALL[2].englishName)
    }

    @Test
    fun resolveMatchesLanguageAndRegion() {
        assertEquals("de", AppLanguages.resolve("de-AT")?.tag)
        assertEquals("fr", AppLanguages.resolve("fr_CA")?.tag)
        assertEquals("ja", AppLanguages.resolve("JA")?.tag)
        assertEquals("en", AppLanguages.resolve("en-GB")?.tag)
        assertEquals("pt", AppLanguages.resolve("pt-BR")?.tag)
        assertNull(AppLanguages.resolve("sv-SE"))
        assertNull(AppLanguages.resolve(""))
        assertNull(AppLanguages.resolve(null))
    }

    @Test
    fun resolveChineseOnlyTraditional() {
        assertEquals("zh-TW", AppLanguages.resolve("zh-TW")?.tag)
        assertEquals("zh-TW", AppLanguages.resolve("zh-Hant")?.tag)
        assertEquals("zh-TW", AppLanguages.resolve("zh-Hant-HK")?.tag)
        assertEquals("zh-TW", AppLanguages.resolve("zh-HK")?.tag)
        assertNull(AppLanguages.resolve("zh"))
        assertNull(AppLanguages.resolve("zh-CN"))
        assertNull(AppLanguages.resolve("zh-Hans-CN"))
    }

    @Test
    fun effectivePrefersAppThenSystemThenEnglish() {
        assertEquals("ko", AppLanguages.effective("ko", "de").tag)
        assertEquals("de", AppLanguages.effective(null, "de-DE").tag)
        assertEquals("pt", AppLanguages.effective(null, "pt-BR").tag)
        assertEquals("en", AppLanguages.effective(null, "sv-SE").tag)
        assertEquals("en", AppLanguages.effective(null, null).tag)
        assertEquals("en", AppLanguages.effective("en", "es").tag) // explicit English wins over a Spanish device
    }

    @Test
    fun useCasesReadAndApply() {
        val repo = FakeLanguageRepository(system = "es-MX")
        val get = GetAppLanguage(repo)
        val set = SetAppLanguage(repo)
        assertEquals("es", get().tag)
        assertEquals("en", set("en")?.tag)
        assertEquals("en", repo.app)
        assertEquals("en", get().tag)
        assertNull(set("xx"))
        assertEquals("en", repo.app)
        assertEquals("zh-TW", set("zh-TW")?.tag)
        assertEquals("zh-TW", get().tag)
    }
}
