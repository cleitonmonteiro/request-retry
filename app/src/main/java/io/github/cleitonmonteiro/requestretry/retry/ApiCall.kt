package io.github.cleitonmonteiro.requestretry.retry

/** A single mocked network call returning [T], or throwing on failure. */
fun interface ApiCall<out T> {
    suspend operator fun invoke(): T
}
