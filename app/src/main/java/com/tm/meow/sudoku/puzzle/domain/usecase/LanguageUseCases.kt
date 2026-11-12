package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.language.AppLanguage
import com.tm.meow.sudoku.puzzle.domain.language.AppLanguages
import com.tm.meow.sudoku.puzzle.domain.repository.LanguageRepository

/** Language currently in effect (app choice, else a supported device language, else English). */
class GetAppLanguage(private val repo: LanguageRepository) {
    operator fun invoke(): AppLanguage = AppLanguages.effective(repo.appLanguageTag(), repo.systemLanguageTag())
}

/** Apply [tag] (must be one of [AppLanguages.ALL]); unknown tags are ignored. Returns the applied language or null. */
class SetAppLanguage(private val repo: LanguageRepository) {
    operator fun invoke(tag: String): AppLanguage? {
        val language = AppLanguages.ALL.firstOrNull { it.tag == tag } ?: return null
        repo.apply(language.tag)
        return language
    }
}
