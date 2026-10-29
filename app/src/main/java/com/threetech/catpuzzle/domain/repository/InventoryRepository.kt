package com.threetech.catpuzzle.domain.repository

import com.threetech.catpuzzle.domain.model.Booster
import kotlinx.coroutines.flow.Flow

/**
 * Booster stock, STORAGE ONLY. How boosters are earned is an undecided product question and is
 * deliberately not modelled here.
 */
interface InventoryRepository {
    /** Boosters with no row are reported as 0. */
    fun observeAll(): Flow<Map<Booster, Int>>

    fun observe(booster: Booster): Flow<Int>

    suspend fun get(booster: Booster): Int

    /** [amount] must be > 0. */
    suspend fun add(booster: Booster, amount: Int)

    /** Atomically take [amount] (> 0). Returns false and changes nothing when stock is insufficient. */
    suspend fun consume(booster: Booster, amount: Int = 1): Boolean
}
