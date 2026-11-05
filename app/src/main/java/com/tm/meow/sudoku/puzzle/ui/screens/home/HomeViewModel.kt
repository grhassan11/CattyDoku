package com.tm.meow.sudoku.puzzle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.billing.CatalogState
import com.tm.meow.sudoku.puzzle.domain.billing.ShopProduct
import com.tm.meow.sudoku.puzzle.domain.usecase.BuyProduct
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadShopCatalog
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveAdsRemoved
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveShopCatalog
import com.tm.meow.sudoku.puzzle.domain.model.WorldRules
import com.tm.meow.sudoku.puzzle.domain.model.DailyStatus
import com.tm.meow.sudoku.puzzle.domain.model.LevelDetail
import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.model.StreakCalendar
import com.tm.meow.sudoku.puzzle.domain.model.StreakRewardState
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveDailyStatus
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveLevelDetail
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveStreakCalendar
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveStreakRewards
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveWorldMap
import com.tm.meow.sudoku.puzzle.ui.screens.home.worlds.WorldMapUi
import com.tm.meow.sudoku.puzzle.domain.usecase.ResolveStartLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.UpdateSettings
import com.tm.meow.sudoku.puzzle.ui.screens.game.SettingToggle
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
    observeAdsRemoved: ObserveAdsRemoved,
    private val observeShopCatalog: ObserveShopCatalog,
    private val loadShopCatalog: LoadShopCatalog,
    private val buyProduct: BuyProduct,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    /** Remove-ads badge: only while ads are on AND Play returned a price for the Forever product (live). */
    private val removeAdsBadge: Flow<Boolean> = combine(observeAdsRemoved(), observeShopCatalog()) { removed, catalog ->
        HomeUiMapper.removeAdsBadgeVisible(removed, catalog)
    }

    /** True while the Play sheet launch is in flight; re-taps are ignored. */
    private val buyBusy = MutableStateFlow(false)

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
            removeAdsBadge,
        ) { base, map, sheet, badge -> base.copy(worldMap = map, levelSheet = sheet, removeAdsBadgeVisible = badge) }.stateIn(
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
        ensureCatalog()
    }

    /** Prices are otherwise loaded only when Shop opens; the badge needs the Forever price on Home. Skipped once Ready. */
    private fun ensureCatalog() {
        if (observeShopCatalog().value is CatalogState.Ready) return
        viewModelScope.launch {
            try {
                loadShopCatalog()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // badge simply stays hidden
            }
        }
    }

    /** Remove-ads badge tapped: open the Play sheet for Forever directly. Failure is silent (badge stays; Shop shows errors). */
    fun onRemoveAdsClick() {
        if (!buyBusy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                buyProduct(ShopProduct.REMOVE_ADS_FOREVER.id)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // never crash on a failed launch
            } finally {
                buyBusy.value = false
            }
        }
    }

    fun onNodeSelected(level: Int) {
        val lvl = level.coerceAtLeast(1)
        analytics.log(AnalyticsEvent.LevelSelect(lvl, WorldRules.worldNumber(lvl)))
        selectedLevel.value = lvl
    }

    /** Mode tab tapped on Home ("sudoku" is the only target from here). */
    fun onModeTab(mode: String) {
        analytics.log(AnalyticsEvent.HomeModeTab(mode))
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
