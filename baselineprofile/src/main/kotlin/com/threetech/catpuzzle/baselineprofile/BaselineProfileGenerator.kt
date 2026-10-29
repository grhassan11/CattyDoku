package com.threetech.catpuzzle.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Generates the baseline profile + startup profile. Run (user, device/emulator API 28+ attached):
 *   ./gradlew :app:generateBaselineProfile
 * Journey: cold start -> (first run only: Terms Accept, notification "Not now", tutorial Skip) -> Home -> Game.
 * Text matching is tolerant: every step is optional, so a missing screen never fails the run. The built variant is
 * release-based, so placeholder ad ids keep ads (and UMP) off during capture.
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
        pressHome()
        startActivityAndWait()

        // First-run screens (only present on the first iteration after install / data clear).
        clickIfPresent("Accept", 4_000)
        clickIfPresent("Not now", 2_000)
        clickIfPresent("Skip", 2_000)

        // Home -> Game: the main CTA reads "Play" (new player) or "Level N".
        val cta = By.text(Pattern.compile("Play|Level \\d+"))
        if (device.wait(Until.hasObject(cta), 10_000)) {
            device.findObject(cta)?.click()
            device.waitForIdle()
            // Game screen: let the board compose, then a tap on the board centre (cell mark path).
            Thread.sleep(1_500)
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            device.waitForIdle()
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.clickIfPresent(text: String, timeoutMs: Long) {
        val selector = By.text(text)
        if (device.wait(Until.hasObject(selector), timeoutMs)) {
            device.findObject(selector)?.click()
            device.waitForIdle()
        }
    }

    companion object {
        // Placeholder package: keep in sync with :app applicationId (rename together).
        const val TARGET_PACKAGE = "com.threetech.catpuzzle"
    }
}
