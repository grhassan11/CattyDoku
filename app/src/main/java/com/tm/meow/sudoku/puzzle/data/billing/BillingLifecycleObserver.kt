package com.tm.meow.sudoku.puzzle.data.billing

import com.tm.meow.sudoku.puzzle.di.MainDispatcher
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tm.meow.sudoku.puzzle.domain.repository.ShopRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-foreground adapter for billing. [start] (from Application.onCreate): begins listening for purchase updates +
 * first purchase sync. Every later background -> foreground return re-queries purchases (a subscription may have
 * lapsed / renewed, a pending purchase may have completed, a refund may have landed). The first ON_START of the process
 * is the cold start: [ShopRepository.start] already synced, so it is skipped.
 */
@Singleton
class BillingLifecycleObserver @Inject constructor(
    private val shop: ShopRepository,
    @MainDispatcher main: CoroutineDispatcher,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + main + CoroutineExceptionHandler { _, _ -> /* never crash on billing */ })
    private var coldStartPending = true

    /** Call once from `Application.onCreate` (main thread). */
    fun start() {
        shop.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (coldStartPending) {
            coldStartPending = false
            return
        }
        scope.launch { shop.refresh() }
    }
}
