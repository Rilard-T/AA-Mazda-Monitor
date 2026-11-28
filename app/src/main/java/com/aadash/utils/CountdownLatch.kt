package com.aadash.utils

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger

class CountDownLatch(
    initialCount: Int
) {
    private val count = AtomicInteger(initialCount.coerceAtLeast(0))
    private val done = CompletableDeferred<Unit>().apply {
        if (initialCount <= 0) complete(Unit)
    }

    fun countDown() {
        while (true) {
            val cur = count.get()
            if (cur <= 0) return
            if (count.compareAndSet(cur, cur - 1)) {
                if (cur - 1 == 0) {
                    done.complete(Unit)
                }
                return
            }
        }
    }

    suspend fun await() {
        done.await()
    }
}