package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Runs one real HTTP call, mapping any thrown error into the app's stable [io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure] vocabulary. */
class ApiClient @Inject constructor() {
    suspend fun <T> execute(call: suspend () -> T): T = try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Error) {
        throw error
    } catch (error: Throwable) {
        throw error.toRequestFailureException()
    }

    suspend fun <T> executeHttp(call: suspend () -> T): T = execute(call)
}
