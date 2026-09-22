package io.github.cleitonmonteiro.requestretry.data.observability

import android.util.Log
import io.github.cleitonmonteiro.requestretry.BuildConfig
import javax.inject.Inject

/** Debug-only logging sink. Callers must only pass sanitized, payload-free messages. */
interface DebugLogger {
    fun d(tag: String, message: String)
}

/** No-ops outside debug builds so production never emits these logs. */
class AndroidDebugLogger @Inject constructor() : DebugLogger {
    override fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }
}
