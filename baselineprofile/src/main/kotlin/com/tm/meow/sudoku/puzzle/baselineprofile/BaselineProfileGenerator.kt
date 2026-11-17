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
 * Journey: cold start -> splash (a Google TEST App Open ad may show for up to ~15 s; closed here) -> (first run only: Accept terms, notification "Not Now", "Skip Tutorial") -> Home (Levels map)
 *   -> Shop and back -> Settings and back -> Levels game (current node -> sheet Play -> board tap) -> back -> Sudoku tab -> New Game ->
 *   Sudoku board tap -> back.
 * Selectors are accessibility text / content descriptions that exist in the app (res/values/strings.xml). Every step
 * after launch is optional so a missing screen never fails the run. Every build type uses Google TEST ad ids, so on a
 * clean install the splash shows a test App Open ad (and UMP may show in EEA); [awaitHome] closes the ad (close button,
 * else Back) and waits long enough for Home. Ad SDK code is therefore partly covered by the profile.
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

        // First-run screens (only present right after install / data clear). Strings: tutorial_skip. Home then asks POST_NOTIFICATIONS (system dialog).
        // UMP consent form (EEA/regulated geographies only; no-op elsewhere). Test device should be non-EEA or form accepted once.
        // (These steps come AFTER the splash ad, so they are handled inside [awaitHome]'s polling loop.)

        // Home: Levels map visible (tabs "Levels" / "Sudoku", Store icon desc "Store").
        // The splash can hold up to ~15 s for the test App Open ad: wait up to 40 s, closing the ad when it appears.
        if (!awaitHome()) return@collect

        // Store and back (home_shop = "Store", shop_back = "Back").
        if (clickIfPresent(By.desc("Store"), 2_000)) {
            device.wait(Until.hasObject(By.text("Restore")), 3_000) // shop_restore (Remove ads card is hidden before Lv 10)
            device.pressBack()
            device.wait(Until.hasObject(By.text("Sudoku")), 3_000)
        }

        // Settings and back (home_settings = "Settings", settings_back = "Back").
        if (clickIfPresent(By.desc("Settings"), 2_000)) {
            device.wait(Until.hasObject(By.text("Sound & feel")), 3_000)
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

    /** Wait for the Home tabs, dismissing a splash App Open test ad on the way. False = Home never appeared. */
    private fun MacrobenchmarkScope.awaitHome(): Boolean {
        val deadline = System.currentTimeMillis() + HOME_WAIT_MS
        var adSeenAt = 0L
        while (System.currentTimeMillis() < deadline) {
            if (device.wait(Until.hasObject(By.text("Sudoku")), 1_000)) return true
            // First-run steps (only right after install / data clear): UMP form (EEA only), notification system dialog, tutorial.
            if (FIRST_RUN_SELECTORS.any { sel -> device.findObject(sel)?.let { it.click(); true } ?: false }) {
                device.waitForIdle()
                continue
            }
            // Ad close affordances (test ad creative / SDK chrome). Content descriptions vary by SDK version.
            val closed = AD_CLOSE_SELECTORS.any { sel ->
                device.findObject(sel)?.let { it.click(); true } ?: false
            }
            if (closed) {
                adSeenAt = 0L
                device.waitForIdle()
            } else if (device.hasObject(By.textContains("Test Ad"))) {
                // Ad on screen but no close button yet (countdown): give it a few seconds, then Back.
                if (adSeenAt == 0L) adSeenAt = System.currentTimeMillis()
                if (System.currentTimeMillis() - adSeenAt > 6_000) {
                    device.pressBack()
                    adSeenAt = 0L
                }
            }
        }
        return device.hasObject(By.text("Sudoku"))
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

        /** Splash hold (15 s ad wait) + consent + first-run steps. */
        private const val HOME_WAIT_MS = 40_000L

        private val FIRST_RUN_SELECTORS = listOf(
            By.text("Consent"),
            By.text("Agree"),
            By.text("Don't allow"),
            By.text("Skip Tutorial"),
        )

        private val AD_CLOSE_SELECTORS = listOf(
            By.desc("Close"),
            By.desc("Close ad"),
            By.descContains("close"),
            By.text("Close"),
            By.text("Skip"),
        )
    }
}
