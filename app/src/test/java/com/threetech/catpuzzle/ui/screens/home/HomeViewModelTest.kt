package com.threetech.catpuzzle.ui.screens.home

import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.model.SavedGame
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.usecase.FakeProgressRepository
import com.threetech.catpuzzle.domain.usecase.FakeRemoteConfigRepository
import com.threetech.catpuzzle.domain.usecase.FakeStreakRewardRepository
import com.threetech.catpuzzle.domain.usecase.FakeSavedGameRepository
import com.threetech.catpuzzle.domain.usecase.FakeStreakRepository
import com.threetech.catpuzzle.domain.usecase.FakeTimeProvider
import com.threetech.catpuzzle.domain.usecase.ObserveSettings
import com.threetech.catpuzzle.domain.usecase.ObserveCompletedDays
import com.threetech.catpuzzle.domain.usecase.ObserveDailyStatus
import com.threetech.catpuzzle.domain.usecase.ObserveLevelDetail
import com.threetech.catpuzzle.domain.usecase.ObserveStreakCalendar
import com.threetech.catpuzzle.domain.usecase.ObserveWorldMap
import com.threetech.catpuzzle.domain.usecase.ObserveStreakRewards
import com.threetech.catpuzzle.domain.usecase.ResolveStartLevel
import com.threetech.catpuzzle.domain.usecase.UpdateSettings
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import com.threetech.catpuzzle.ui.screens.game.FakeSettingsRepository
import com.threetech.catpuzzle.ui.screens.game.SettingToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private class Rig(flags: AppFlags = AppFlags(), settings: Settings = Settings()) {
        val flags = FakeAppFlagsRepository(flags)
        val saved = FakeSavedGameRepository()
        val progress = FakeProgressRepository()
        val streak = FakeStreakRepository()
        val settingsRepo = FakeSettingsRepository(settings)
        val time = FakeTimeProvider(now = 1_000L, today = 20_000L)
        private val completed = ObserveCompletedDays(progress)

        fun viewModel() = HomeViewModel(
            resolveStartLevel = ResolveStartLevel(saved, this.flags, progress),
            observeStreakCalendar = ObserveStreakCalendar(streak, completed, time),
            observeDailyStatus = ObserveDailyStatus(progress, completed, time),
            observeStreakRewards = ObserveStreakRewards(
                streak, FakeStreakRewardRepository(), this.flags, time, FakeRemoteConfigRepository(),
            ),
            observeWorldMap = ObserveWorldMap(progress),
            observeLevelDetail = ObserveLevelDetail(progress),
            observeSettings = ObserveSettings(settingsRepo),
            updateSettings = UpdateSettings(settingsRepo),
        )
    }

    private fun vmTest(
        rig: Rig = Rig(),
        block: suspend TestScope.(Rig, HomeViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block(rig, rig.viewModel())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun won(level: Int) = LevelProgress(level, 100, 1_000L, 1L, 0, 0)

    @Test
    fun freshInstallShowsLevelOne() = vmTest { _, vm ->
        assertEquals(1, vm.state.value.levelNumber)
    }

    @Test
    fun lastPlayedUnwonShowsSameLevel() = vmTest(Rig(AppFlags(lastPlayedLevel = 3))) { _, vm ->
        assertEquals(3, vm.state.value.levelNumber)
    }

    @Test
    fun lastPlayedWonShowsNextLevel() = vmTest(Rig(AppFlags(lastPlayedLevel = 3))) { rig, _ ->
        rig.progress.save(won(3))
        assertEquals(4, rig.viewModel().state.value.levelNumber)
    }

    @Test
    fun autosaveWinsOverLastPlayed() = vmTest(Rig(AppFlags(lastPlayedLevel = 3))) { rig, _ ->
        rig.saved.save(SavedGame(levelIndex = 7, state = GameState.initial(4), elapsedMs = 0L, savedAtMs = 5L))
        assertEquals(7, rig.viewModel().state.value.levelNumber)
    }

    @Test
    fun refreshPicksUpProgressMadeInGame() = vmTest(Rig(AppFlags(lastPlayedLevel = 2))) { rig, vm ->
        assertEquals(2, vm.state.value.levelNumber)
        rig.progress.save(won(2))
        vm.refresh()
        assertEquals(3, vm.state.value.levelNumber)
    }

    @Test
    fun streakStartsAtZero() = vmTest { _, vm ->
        assertEquals(0, vm.state.value.streak)
    }

    @Test
    fun streakCountsConsecutiveDays() = vmTest { rig, vm ->
        rig.streak.recordPlayed(19_999L)
        rig.streak.recordPlayed(20_000L)
        assertEquals(2, vm.state.value.streak)
    }

    @Test
    fun settingToggleIsPersistedAndReflected() = vmTest { rig, vm ->
        assertTrue(vm.state.value.settings.music)
        vm.onSettingChanged(SettingToggle.MUSIC, false)
        assertFalse(rig.settingsRepo.settings.first().music)
        assertFalse(vm.state.value.settings.music)

        vm.onSettingChanged(SettingToggle.SFX, false)
        assertFalse(rig.settingsRepo.settings.first().sfx)
        assertFalse(vm.state.value.settings.sfx)
        // others untouched
        assertFalse(rig.settingsRepo.settings.first().music)
    }

    @Test
    fun dailyIsLockedUntilLevelFiveWon() = vmTest { rig, vm ->
        assertEquals(DailyUi.Locked(DailyRules.UNLOCK_LEVEL), vm.state.value.daily)
        for (l in 1..4) rig.progress.save(won(l))
        assertEquals(DailyUi.Locked(5), vm.state.value.daily)
        rig.progress.save(won(5))
        assertEquals(DailyUi.Available, vm.state.value.daily)
    }

    @Test
    fun dailyBecomesDoneAfterTodaysCompletion() = vmTest { rig, vm ->
        for (l in 1..5) rig.progress.save(won(l))
        assertEquals(DailyUi.Available, vm.state.value.daily)
        rig.progress.save(won(DailyRules.keyForDay(20_000L)))
        assertEquals(DailyUi.Done, vm.state.value.daily)
    }

    @Test
    fun refreshAfterMidnightReopensTheDaily() = vmTest { rig, vm ->
        for (l in 1..5) rig.progress.save(won(l))
        rig.progress.save(won(DailyRules.keyForDay(20_000L)))
        assertEquals(DailyUi.Done, vm.state.value.daily)
        rig.time.today = 20_001L
        vm.refresh()
        assertEquals(DailyUi.Available, vm.state.value.daily)
    }

    @Test
    fun dailyDoesNotMoveTheLevelButton() = vmTest(Rig(AppFlags(lastPlayedLevel = 6))) { rig, vm ->
        rig.progress.save(won(DailyRules.keyForDay(20_000L)))
        vm.refresh()
        assertEquals(6, vm.state.value.levelNumber)
    }

    @Test
    fun calendarShowsStreakAndDailyDays() = vmTest { rig, vm ->
        assertNull(vm.state.value.calendar?.takeIf { it.current > 0 })
        rig.streak.recordPlayed(19_999L)
        rig.streak.recordPlayed(20_000L)
        rig.progress.save(won(DailyRules.keyForDay(20_000L)))
        val cal = vm.state.value.calendar!!
        assertEquals(2, cal.current)
        assertEquals(1, cal.dailyTotal)
        assertEquals(30, cal.recent.size)
        assertEquals(DayDotState.DAILY_DONE, cal.recent.last().state)
        assertEquals(DayDotState.PLAYED, cal.recent[28].state)
        assertEquals(7, cal.week.size)
    }

    @Test
    fun worldMapStartsAtLevelOneWithSixWorlds() = vmTest { _, vm ->
        val map = vm.state.value.worldMap
        assertTrue(map.loaded)
        assertEquals(1, map.currentLevel)
        assertEquals(60, map.visibleLevels)
    }

    @Test
    fun worldMapFollowsWinsAndCarriesStars() = vmTest { rig, vm ->
        rig.progress.save(LevelProgress(1, 100, 1_000L, 1L, 0, 0))
        rig.progress.save(LevelProgress(2, 100, 1_000L, 1L, 1, 0))
        val map = vm.state.value.worldMap
        assertEquals(3, map.currentLevel)
        assertEquals(mapOf(1 to 3, 2 to 2), map.starsByLevel)
    }

    @Test
    fun worldMapOpensThreeMoreWorldsAtLevel51() = vmTest { rig, vm ->
        for (l in 1..49) rig.progress.save(won(l))
        assertEquals(60, vm.state.value.worldMap.visibleLevels)
        rig.progress.save(won(50))
        assertEquals(51, vm.state.value.worldMap.currentLevel)
        assertEquals(90, vm.state.value.worldMap.visibleLevels)
    }

    @Test
    fun sheetIsClosedUntilANodeIsSelected() = vmTest { _, vm ->
        assertNull(vm.state.value.levelSheet)
    }

    @Test
    fun selectingCurrentLevelOpensSheetWithoutResults() = vmTest { _, vm ->
        vm.onNodeSelected(1)
        val sheet = vm.state.value.levelSheet!!
        assertEquals(1, sheet.level)
        assertEquals(4, sheet.boardSize)
        assertFalse(sheet.completed)
        assertNull(sheet.bestScore)
        assertNull(sheet.bestTimeMs)
    }

    @Test
    fun selectingDoneLevelShowsStarsScoreAndTime() = vmTest { rig, vm ->
        rig.progress.save(LevelProgress(1, 250, 65_000L, 1L, 1, 0))
        vm.onNodeSelected(1)
        val sheet = vm.state.value.levelSheet!!
        assertTrue(sheet.completed)
        assertEquals(2, sheet.stars)
        assertEquals(250, sheet.bestScore)
        assertEquals(65_000L, sheet.bestTimeMs)
    }

    @Test
    fun dismissingClosesTheSheet() = vmTest { _, vm ->
        vm.onNodeSelected(2)
        assertEquals(2, vm.state.value.levelSheet?.level)
        vm.onSheetDismissed()
        assertNull(vm.state.value.levelSheet)
    }

    @Test
    fun initialUiStateBeforeAnyData() {
        val ui = HomeUiMapper.map(null, null, null, Settings())
        assertNull(ui.levelNumber)
        assertEquals(DailyUi.Locked(DailyRules.UNLOCK_LEVEL), ui.daily)
        assertNull(ui.calendar)
    }
}
