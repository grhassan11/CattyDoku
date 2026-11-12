package com.tm.meow.sudoku.puzzle.domain.repository

/** Per-app language seam. Tags are BCP-47. Implemented over the platform per-app locale API in the data layer. */
interface LanguageRepository {
    /** Explicit app language tag, or null when the user never picked one (app follows the device language). */
    fun appLanguageTag(): String?

    /** Primary device language tag (ignores the per-app override). */
    fun systemLanguageTag(): String?

    /** Persist + apply [tag]. The platform recreates the visible activity. */
    fun apply(tag: String)
}
