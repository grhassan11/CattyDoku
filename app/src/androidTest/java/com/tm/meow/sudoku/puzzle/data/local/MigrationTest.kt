package com.tm.meow.sudoku.puzzle.data.local

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Room migration tests (instrumented). Schemas come from app/schemas (wired as androidTest assets in app/build.gradle.kts).
 * Each test creates the old schema, writes player data, runs the REAL migration objects from [MeowdokuDatabase], and checks
 * that old rows survive and the new tables exist. runMigrationsAndValidate compares the result with the exported schema.
 */
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeowdokuDatabase::class.java,
    )

    private fun SupportSQLiteDatabase.seedV1() {
        insert(
            "level_progress",
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("levelIndex", 7)
                put("bestScore", 450)
                put("bestTimeMs", 61_000L)
                put("completedAt", 1_700_000_000_000L)
                put("mistakes", 1)
                put("hintsUsed", 0)
            },
        )
        insert(
            "inventory",
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("boosterType", "cat")
                put("count", 5)
            },
        )
        insert(
            "daily_streak",
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply { put("epochDay", 20_000L) },
        )
    }

    private fun SupportSQLiteDatabase.assertV1DataSurvived() {
        query("SELECT bestScore, mistakes FROM level_progress WHERE levelIndex = 7").use {
            assertTrue(it.moveToFirst())
            assertEquals(450, it.getInt(0))
            assertEquals(1, it.getInt(1))
        }
        query("SELECT count FROM inventory WHERE boosterType = 'cat'").use {
            assertTrue(it.moveToFirst())
            assertEquals(5, it.getInt(0))
        }
        query("SELECT COUNT(*) FROM daily_streak WHERE epochDay = 20000").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    private fun SupportSQLiteDatabase.assertTableExists(name: String) {
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$name'").use {
            assertTrue("table $name must exist", it.moveToFirst())
        }
    }

    @Test
    fun migrate1To2() {
        helper.createDatabase(TEST_DB, 1).apply { seedV1(); close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MeowdokuDatabase.MIGRATION_1_2)
        db.assertV1DataSurvived()
        db.assertTableExists("granted_purchase")
    }

    @Test
    fun migrate2To3() {
        helper.createDatabase(TEST_DB, 2).apply {
            seedV1()
            insert(
                "granted_purchase",
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("token", "tok_1")
                    put("productId", "cat_pack_5")
                    put("grantedAtMs", 1_000L)
                },
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MeowdokuDatabase.MIGRATION_2_3)
        db.assertV1DataSurvived()
        db.query("SELECT productId FROM granted_purchase WHERE token = 'tok_1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("cat_pack_5", it.getString(0))
        }
        db.assertTableExists("streak_reward_claim")
    }

    @Test
    fun migrate3To4() {
        helper.createDatabase(TEST_DB, 3).apply {
            seedV1()
            insert(
                "streak_reward_claim",
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("claimKey", "3:20000")
                    put("grantedAtMs", 2_000L)
                },
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MeowdokuDatabase.MIGRATION_3_4)
        db.assertV1DataSurvived()
        db.query("SELECT COUNT(*) FROM streak_reward_claim WHERE claimKey = '3:20000'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.assertTableExists("sudoku_progress")
        db.assertTableExists("sudoku_saved_game")
    }

    @Test
    fun migrate1To4InOneGo() {
        helper.createDatabase(TEST_DB, 1).apply { seedV1(); close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, *MeowdokuDatabase.ALL_MIGRATIONS)
        db.assertV1DataSurvived()
        for (table in listOf("granted_purchase", "streak_reward_claim", "sudoku_progress", "sudoku_saved_game")) {
            db.assertTableExists(table)
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
