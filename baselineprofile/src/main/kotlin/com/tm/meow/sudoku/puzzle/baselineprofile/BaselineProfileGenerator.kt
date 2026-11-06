package com.tm.meow.sudoku.puzzle.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile + startup profile. Run (owner, device/emulator API 28+ attached, rooted or emulator
 * with a Google APIs/AOSP image):
 *   ./gradlew :app:generateBaselineProfile
 * Output lands in app/src/main/generated/baselineProfiles/ (baseline-prof.txt + startup-prof.txt); commit both.
 *
 * Journey: cold start -> (first run only: Accept terms, notification "Not Now", "Skip Tutorial") -> Home (Levels map)
 *   -> Shop and back -> Levels game (current node -> sheet Play -> board tap) -> back -> Sudoku tab -> New Game ->
 *   Sudoku board tap -> back.
 * Selectors are accessibility text / content descriptions that exist in the app (res/values/strings.xml). Every step
 * after launch is optional so a missing screen never fails the run. The built variant is release-based, so the
 * placeholder ad ids keep ads (and UMP) off during capture: ad code is not covered by the profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
        maxIterations = 5,
        stableIterations = 3,
    ) {
        // Cold start (also the startup-profile path).
        pressHome()
        startActivityAndWait()

        // First-run screens (only present right after install / data clear). Strings: welcome_accept, notif_not_now, tutorial_skip.
        clickIfPresent(By.text("Accept"), 4_000)
        clickIfPresent(By.text("Not Now"), 2_000)
        clickIfPresent(By.text("Skip Tutorial"), 2_000)

        // Home: Levels map visible (tabs "Levels" / "Sudoku", Store icon desc "Store").
        if (!device.wait(Until.hasObject(By.text("Sudoku")), 10_000)) return@collect

        // Store and back (home_shop = "Store", shop_back = "Back").
        if (clickIfPresent(By.desc("Store"), 2_000)) {
            device.wait(Until.hasObject(By.text("Restore")), 3_000) // shop_restore (Remove ads card is hidden before Lv 10)
            device.pressBack()
            device.wait(Until.hasObject(By.text("Sudoku")), 3_000)
        }

        // Levels game: current map node (world_node_current: "Level N, current level. Tap for details") -> sheet "Play"/"Replay".
        if (clickIfPresent(By.descContains("current level"), 3_000)) {
            if (clickIfPresent(By.text("Play"), 3_000) || clickIfPresent(By.text("Replay"), 500)) {
                // board_description = "Puzzle board, N by N".
                if (device.wait(Until.hasObject(By.descStartsWith("Puzzle board")), 10_000)) {
                    device.findObject(By.descStartsWith("Puzzle board"))?.click()
                    device.waitForIdle()
                }
                device.pressBack()
                device.wait(Until.hasObject(By.text("Sudoku")), 3_000)
            }
        }

        // Sudoku: tab -> entry -> New Game -> board.
        if (clickIfPresent(By.text("Sudoku"), 2_000)) {
            if (device.wait(Until.hasObject(By.text("New Game")), 5_000)) {
                device.findObject(By.text("New Game"))?.click()
                device.waitForIdle()
                // Confirm dialog (only when a saved game exists): sudoku_new_game_confirm_yes = "New Game".
                clickIfPresent(By.text("New Game"), 1_000)
                // Sudoku tutorial prompt (first Sudoku entry): sudoku_tut_skip_all.
                clickIfPresent(By.text("I Know How to Play Sudoku"), 1_500)
                // sudoku_cell_description = "Row r, column c, ..."
                if (device.wait(Until.hasObject(By.descStartsWith("Row 1, column 1")), 10_000)) {
                    device.findObject(By.descStartsWith("Row 1, column 1"))?.click()
                    device.waitForIdle()
                }
                device.pressBack()
                device.waitForIdle()
            }
        }
    }

    private fun MacrobenchmarkScope.clickIfPresent(selector: BySelector, timeoutMs: Long): Boolean {
        if (!device.wait(Until.hasObject(selector), timeoutMs)) return false
        device.findObject(selector)?.click() ?: return false
        device.waitForIdle()
        return true
    }

    companion object {
        // Keep in sync with :app applicationId (NOT the namespace).
        const val TARGET_PACKAGE = "com.tm.meow.sudoku.puzzle"
    }
}
