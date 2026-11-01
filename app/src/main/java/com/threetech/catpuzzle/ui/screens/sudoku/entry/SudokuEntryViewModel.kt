package com.threetech.catpuzzle.ui.screens.sudoku.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import com.threetech.catpuzzle.domain.sudoku.usecase.GetActiveSudokuElapsed
import com.threetech.catpuzzle.domain.sudoku.usecase.ObserveSudokuProgress
import com.threetech.catpuzzle.domain.sudoku.usecase.ResetActiveSudokuGame
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sudoku entry (difficulty picker). Selection lives in [SavedStateHandle] (survives rotation + process death).
 * A locked tap never changes the selection; it emits [SudokuEntryEvent.Locked]. The saved timer of the selected tier is
 * re-read on every emission and on [refresh] (Room may not re-emit when only the save's elapsed time changed).
 */
@HiltViewModel
class SudokuEntryViewModel @Inject constructor(
    observeProgress: ObserveSudokuProgress,
    private val resetActiveGame: ResetActiveSudokuGame,
    private val activeElapsed: GetActiveSudokuElapsed,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)
    private val confirmNew = MutableStateFlow(false)
    private val eventChannel = Channel<SudokuEntryEvent>(Channel.BUFFERED)
    val events: Flow<SudokuEntryEvent> = eventChannel.receiveAsFlow()

    private var latest: SudokuEntryUiState = SudokuEntryUiMapper.map(
        SudokuEntryUiMapper.emptyStatuses(), SudokuTier.EASY, null,
    )

    val state: StateFlow<SudokuEntryUiState> = combine(
        observeProgress(),
        savedState.getStateFlow(KEY_TIER, SudokuTier.EASY.name),
        refreshTick,
        confirmNew,
    ) { statuses, requested, _, confirm ->
        val selected = SudokuEntryUiMapper.resolveSelected(statuses, SudokuTier.entries.firstOrNull { it.name == requested })
        val hasSave = statuses.firstOrNull { it.tier == selected }?.hasActiveGame == true
        val elapsed = if (hasSave) elapsedOf(selected) else null
        SudokuEntryUiMapper.map(statuses, selected, elapsed).copy(confirmNewGame = confirm && hasSave)
    }
        .map { it.also { s -> latest = s } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), latest)

    private suspend fun elapsedOf(tier: SudokuTier): Long? = try {
        activeElapsed(tier)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        null
    }

    /** Call on screen start: re-read the saved timer after returning from a game. */
    fun refresh() {
        refreshTick.value += 1
    }

    fun onSelect(tier: SudokuTier) {
        val row = latest.rows.firstOrNull { it.tier == tier } ?: return
        val required = row.requiredTier
        if (row.locked && required != null) {
            eventChannel.trySend(SudokuEntryEvent.Locked(tier, required, row.winsNeeded, row.winsHave))
            return
        }
        savedState[KEY_TIER] = tier.name
    }

    fun onContinue() {
        if (!latest.canContinue) return
        eventChannel.trySend(SudokuEntryEvent.OpenGame(latest.selected, resume = true))
    }

    fun onNewGame() {
        if (latest.canContinue) {
            confirmNew.value = true
        } else {
            eventChannel.trySend(SudokuEntryEvent.OpenGame(latest.selected, resume = false))
        }
    }

    fun onConfirmNewGame() {
        val tier = latest.selected
        confirmNew.value = false
        viewModelScope.launch {
            try {
                resetActiveGame(tier)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                return@launch // clear failed: keep the save, stay on the picker
            }
            eventChannel.send(SudokuEntryEvent.OpenGame(tier, resume = false))
        }
    }

    fun onDismissConfirm() {
        confirmNew.value = false
    }

    private companion object {
        const val KEY_TIER = "sudoku_entry_tier"
    }
}
