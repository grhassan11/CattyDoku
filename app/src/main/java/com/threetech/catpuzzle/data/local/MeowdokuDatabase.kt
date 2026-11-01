package com.threetech.catpuzzle.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.threetech.catpuzzle.data.local.dao.DailyStreakDao
import com.threetech.catpuzzle.data.local.dao.InventoryDao
import com.threetech.catpuzzle.data.local.dao.LevelProgressDao
import com.threetech.catpuzzle.data.local.dao.PuzzleCacheDao
import com.threetech.catpuzzle.data.local.dao.PurchaseGrantDao
import com.threetech.catpuzzle.data.local.dao.SavedGameDao
import com.threetech.catpuzzle.data.local.dao.StreakRewardDao
import com.threetech.catpuzzle.data.local.dao.SudokuProgressDao
import com.threetech.catpuzzle.data.local.dao.SudokuSavedGameDao
import com.threetech.catpuzzle.data.local.entity.DailyStreakEntity
import com.threetech.catpuzzle.data.local.entity.GrantedPurchaseEntity
import com.threetech.catpuzzle.data.local.entity.InventoryEntity
import com.threetech.catpuzzle.data.local.entity.LevelProgressEntity
import com.threetech.catpuzzle.data.local.entity.PuzzleCacheEntity
import com.threetech.catpuzzle.data.local.entity.SavedGameEntity
import com.threetech.catpuzzle.data.local.entity.StreakRewardClaimEntity
import com.threetech.catpuzzle.data.local.entity.SudokuProgressEntity
import com.threetech.catpuzzle.data.local.entity.SudokuSavedGameEntity

/**
 * MIGRATION POLICY (read before bumping [version]):
 *  1. Schemas are exported to app/schemas/<db>/<version>.json and committed. Never edit an exported file.
 *  2. Every version bump ships an explicit Migration in [ALL_MIGRATIONS] (data-preserving ALTER / copy).
 *  3. NO destructive fallback, ever (no fallbackToDestructiveMigration*): level_progress, daily_streak and
 *     inventory are player data, and puzzle_cache / saved_game must survive because a started or
 *     completed level may never be regenerated.
 *  4. Blob layout changes are versioned inside the blobs (see Codecs.kt) and need no Room migration
 *     unless columns change.
 *  5. Each migration needs a MigrationTestHelper test (instrumented; follow-up, not in phase 3).
 *  6. Downgrades are unsupported (Room throws); the app never ships a lower schema version.
 */
@Database(
    entities = [
        LevelProgressEntity::class,
        PuzzleCacheEntity::class,
        SavedGameEntity::class,
        DailyStreakEntity::class,
        InventoryEntity::class,
        GrantedPurchaseEntity::class,
        StreakRewardClaimEntity::class,
        SudokuProgressEntity::class,
        SudokuSavedGameEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MeowdokuDatabase : RoomDatabase() {
    abstract fun levelProgressDao(): LevelProgressDao

    abstract fun puzzleCacheDao(): PuzzleCacheDao

    abstract fun savedGameDao(): SavedGameDao

    abstract fun dailyStreakDao(): DailyStreakDao

    abstract fun inventoryDao(): InventoryDao

    abstract fun purchaseGrantDao(): PurchaseGrantDao

    abstract fun streakRewardDao(): StreakRewardDao

    abstract fun sudokuProgressDao(): SudokuProgressDao

    abstract fun sudokuSavedGameDao(): SudokuSavedGameDao

    companion object {
        const val NAME = "meowdoku.db"

        /**
         * v1 -> v2 (Shop / Play Billing): new idempotency ledger `granted_purchase`. Purely additive; no existing table
         * is touched. The SQL must match Room's generated create statement for GrantedPurchaseEntity exactly.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `granted_purchase` " +
                        "(`token` TEXT NOT NULL, `productId` TEXT NOT NULL, `grantedAtMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`token`))",
                )
            }
        }

        /**
         * v2 -> v3 (streak milestone rewards): new claim ledger `streak_reward_claim`. Purely additive; no existing
         * table is touched. The SQL must match Room's generated create statement for StreakRewardClaimEntity exactly.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `streak_reward_claim` " +
                        "(`claimKey` TEXT NOT NULL, `grantedAtMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`claimKey`))",
                )
            }
        }

        /**
         * v3 -> v4 (Sudoku mode): new tables `sudoku_progress` (per-tier stats) and `sudoku_saved_game` (one active game
         * per tier). Purely additive; no existing table is touched. The SQL must match Room's generated create statements
         * for SudokuProgressEntity / SudokuSavedGameEntity exactly (compare with schemas/.../4.json after the first build).
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sudoku_progress` " +
                        "(`tierId` INTEGER NOT NULL, `wins` INTEGER NOT NULL, `plays` INTEGER NOT NULL, " +
                        "`bestTimeMs` INTEGER, `bestScore` INTEGER NOT NULL, PRIMARY KEY(`tierId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sudoku_saved_game` " +
                        "(`tierId` INTEGER NOT NULL, `seed` INTEGER NOT NULL, `generatorVersion` INTEGER NOT NULL, " +
                        "`rating` INTEGER NOT NULL, `matchedTier` INTEGER NOT NULL, `givens` BLOB NOT NULL, " +
                        "`solution` BLOB NOT NULL, `cellValues` BLOB NOT NULL, `cellNotes` BLOB NOT NULL, " +
                        "`undo` BLOB NOT NULL, `elapsedMs` INTEGER NOT NULL, `hearts` INTEGER NOT NULL, " +
                        "`mistakes` INTEGER NOT NULL, `hintsUsed` INTEGER NOT NULL, `score` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`tierId`))",
                )
            }
        }

        /** Append MIGRATION_4_5 etc. here, in order. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
