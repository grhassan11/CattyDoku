package com.tm.meow.sudoku.puzzle.data.language

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tm.meow.sudoku.puzzle.domain.repository.LanguageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app locale over AndroidX AppCompat: API 33+ delegates to the platform LocaleManager, older versions use the
 * AppLocalesMetadataHolderService auto-store (manifest) + AppCompatActivity recreation.
 */
@Singleton
class AppCompatLanguageRepository @Inject constructor() : LanguageRepository {
    override fun appLanguageTag(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales.toLanguageTags().substringBefore(',')
    }

    override fun systemLanguageTag(): String? {
        val locales = Resources.getSystem().configuration.locales
        return if (locales.isEmpty) null else locales[0].toLanguageTag()
    }

    override fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
