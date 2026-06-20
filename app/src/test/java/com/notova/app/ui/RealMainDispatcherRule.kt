package com.notova.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.Executors

/**
 * Swaps [Dispatchers.Main] for a REAL single-threaded dispatcher (not a virtual-time test
 * dispatcher). Used by ViewModel tests that drive suspend Retrofit calls against a MockWebServer:
 * those calls resume on OkHttp's real background threads, so virtual-time advancement can't await
 * them — the continuation must run on a real dispatcher and the test polls the resulting StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealMainDispatcherRule : TestWatcher() {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        dispatcher.close()
        executor.shutdownNow()
    }
}

/** Busy-waits (real wall-clock) until [predicate] holds for [supplier], or fails after [timeoutMs]. */
fun <T> waitForState(
    timeoutMs: Long = 5_000,
    supplier: () -> T,
    predicate: (T) -> Boolean,
): T {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val value = supplier()
        if (predicate(value)) return value
        Thread.sleep(POLL_INTERVAL_MS)
    }
    val last = supplier()
    if (predicate(last)) return last
    error("waitForState timed out after ${timeoutMs}ms; last value: $last")
}

private const val POLL_INTERVAL_MS = 10L
