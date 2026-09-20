package io.github.cleitonmonteiro.requestretry.data.remote

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single-flight guard for a future authenticated transport plugin. */
@Singleton
class AuthRefreshCoordinator @Inject constructor() {
    private val mutex = Mutex()
    private val generation = AtomicLong(0L)

    fun snapshot(): Long = generation.get()

    suspend fun refreshIfCurrent(observedGeneration: Long, refresh: suspend () -> Boolean): Boolean =
        mutex.withLock {
            if (generation.get() != observedGeneration) return@withLock true
            refresh().also { succeeded -> if (succeeded) generation.incrementAndGet() }
        }
}
