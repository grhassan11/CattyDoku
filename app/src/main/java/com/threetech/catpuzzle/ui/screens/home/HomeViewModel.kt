package com.threetech.catpuzzle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.analytics.AnalyticsEvent
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.analytics.NoopAnalyticsLogger
import com.threetech.catpuzzle.domain.model.DailyStatus
import com.threetech.catpuzzle.domain.model.LevelDetail
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.model.StreakCalendar
import com.threetech.catpuzzle.domain.model.StreakRewardState
import com.threetech.catpuzzle.domain.usecase.ObserveDailyStatus
import com.threetech.catpuzzle.domain.usecase.ObserveLevelDetail
import com.threetech.catpuzzle.domain.usecase.ObserveSettings
import com.threetech.catpuzzle.domain.usecase.ObserveStreakCalendar
import com.threetech.catpuzzle.domain.usecase.ObserveStreakRewards
import com.threetech.catpuzzle.domain.usecase.ObserveWorldMap
import com.threetech.catpuzzle.ui.screens.home.worlds.WorldMapUi
import com.threetech.catpuzzle.domain.usecase.ResolveStartLevel
import com.threetech.catpuzzle.domain.usecase.UpdateSettings
import com.threetech.catpuzzle.ui.screens.game.SettingToggle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home state holder. Level label uses the same rule as the game ([ResolveStartLevel]); streak calendar,
 * daily status and settings are live. [refresh] (call on screen start) re-resolves the level and restarts
 * the streak / daily flows so "today" is re-read after a date change (midnight, timezone or clock change).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val resolveStartLevel: ResolveStartLevel,
    private val observeStreakCalendar: ObserveStreakCalendar,
    private val observeDailyStatus: ObserveDailyStatus,
    private val observeStreakRewards: ObserveStreakRewards,
    private val observeWorldMap: ObserveWorldMap,
    private val observeLevelDetail: ObserveLevelDetail,
    observeSettings: ObserveSettings,
    private val updateSettings: UpdateSettings,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    private val level = MutableStateFlow<Int?>(null)
    private val refreshTick = MutableStateFlow(0)

    private val calendar: Flow<StreakCalendar?> = refreshTick.flatMapLatest {
        observeStreakCalendar().map<StreakCalendar, StreakCalendar?> { c -> c }
    }

    private val daily: Flow<DailyStatus?> = refreshTick.flatMapLatest {
        observeDailyStatus().map<DailyStatus, DailyStatus?> { d -> d }
    }

    /** Only the pending flag reaches Home (dot on the streak chip); the full reward state lives on the Streak screen. */
    private val rewards: Flow<StreakRewardState> = refreshTick.flatMapLatest { observeStreakRewards() }

    /** Live map progress (current level + stars); updates when a level is won, no refresh needed. */
    private val worldMap: Flow<WorldMapUi> = observeWorldMap().map { WorldMapUi.from(it) }

    /** Level whose detail sheet is open (null = closed). Cleared on dismiss and when Play is tapped. */
    private val selectedLevel = MutableStateFlow<Int?>(null)

    private val levelSheet: Flow<LevelSheetUi?> = selectedLevel.flatMapLatest { lvl ->
        if (lvl == null) flowOf(null) else observeLevelDetail(lvl).map<LevelDetail, LevelSheetUi?> { LevelSheetUi.from(it) }
    }

    val state: StateFlow<HomeUiState> =
        combine(
            combine(level, calendar, daily, observeSettings(), rewards) { lvl, cal, dly, settings, rw ->
                HomeUiMapper.map(lvl, cal, dly, settings, rewardPending = rw.pendingCount > 0)
            },
            worldMap,
            levelSheet,
        ) { base, map, sheet -> base.copy(worldMap = map, levelSheet = sheet) }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            HomeUiMapper.map(null, null, null, Settings()),
        )

    init {
        refresh()
    }

    fun refresh() {
        refreshTick.value += 1
        viewModelScope.launch {
            try {
                level.value = resolveStartLevel()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // keep the previous label; the game resolves the level itself
            }
        }
    }

    fun onNodeSelected(level: Int) {
        selectedLevel.value = level.coerceAtLeast(1)
    }

    fun onSheetDismissed() {
        selectedLevel.value = null
    }

    fun onSettingChanged(toggle: SettingToggle, enabled: Boolean) {
        analytics.log(AnalyticsEvent.SettingChange(toggle.name.lowercase(), enabled))
        viewModelScope.launch {
            try {
                updateSettings { it.withToggle(toggle, enabled) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // best effort, same as the game screen
            }
        }
    }
}
