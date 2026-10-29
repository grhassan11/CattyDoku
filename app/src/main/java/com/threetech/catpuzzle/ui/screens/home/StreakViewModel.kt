package com.threetech.catpuzzle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.analytics.AnalyticsEvent
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.analytics.NoopAnalyticsLogger
import com.threetech.catpuzzle.domain.usecase.ClaimStreakReward
import com.threetech.catpuzzle.domain.usecase.ObserveSettings
import com.threetech.catpuzzle.domain.usecase.ObserveStreakCalendar
import com.threetech.catpuzzle.domain.usecase.ObserveStreakRewards
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Streak screen state. Reuses [ObserveStreakCalendar] + [HomeUiMapper.calendarUi] (same mapping Home used for the
 * dialog). Logs streak_dialog_open once per screen instance (survives rotation; not re-fired by back).
 * Milestone rewards: [rewards] is the track, [claim] is manual (never auto-granted), the result arrives once on [events].
 */
@HiltViewModel
class StreakViewModel @Inject constructor(
    observeStreakCalendar: ObserveStreakCalendar,
    observeStreakRewards: ObserveStreakRewards,
    private val claimStreakReward: ClaimStreakReward,
    private val observeSettings: ObserveSettings,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    val calendar: StateFlow<StreakCalendarUi?> = observeStreakCalendar()
        .map<_, StreakCalendarUi?> { HomeUiMapper.calendarUi(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rewards: StateFlow<StreakRewardUi> = observeStreakRewards()
        .map { StreakRewardUiMapper.map(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakRewardUi())

    private val eventChannel = Channel<StreakEvent>(Channel.BUFFERED)
    val events: Flow<StreakEvent> = eventChannel.receiveAsFlow()

    private var claiming = false

    init {
        analytics.log(AnalyticsEvent.StreakDialogOpen)
    }

    /** [day] = milestone day (3/7/14/20/30). Ignored while a claim is running; a stale/duplicate tap is a no-op. */
    fun claim(day: Int) {
        if (claiming) return
        claiming = true
        viewModelScope.launch {
            try {
                val claim = claimStreakReward(day) ?: return@launch
                analytics.log(AnalyticsEvent.StreakRewardClaim(claim.day, claim.cycle))
                val sfx = try {
                    observeSettings().first().sfx
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    true
                }
                eventChannel.send(StreakEvent.RewardClaimed(StreakRewardUiMapper.claimed(claim), sfx))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // claim failed (db error): nothing was credited (single transaction); the node stays claimable
            } finally {
                claiming = false
            }
        }
    }
}
