package com.threetech.catpuzzle.data.billing

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.threetech.catpuzzle.domain.repository.ShopRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
