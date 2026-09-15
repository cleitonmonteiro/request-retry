package io.github.cleitonmonteiro.requestretry.retry

import kotlinx.coroutines.flow.Flow

/** A single mocked network call, as a single-shot [Flow]: one emission of [T], or a failure. */
fun interface ApiCall<out T> {
    operator fun invoke(): Flow<T>
}
