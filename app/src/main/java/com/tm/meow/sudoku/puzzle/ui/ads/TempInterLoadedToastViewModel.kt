package com.tm.meow.sudoku.puzzle.ui.ads

import androidx.lifecycle.ViewModel
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// TEMP-TOAST: remove before release
@HiltViewModel
class TempInterLoadedToastViewModel @Inject constructor(ads: AdsRepository) : ViewModel() {
    val loaded: Flow<Unit> = ads.interstitialLoadedEvents
    val notes: Flow<String> = ads.debugNotes // TEMP-TOAST: remove before release
}
