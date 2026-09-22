package io.github.cleitonmonteiro.requestretry

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
/** Application entry point that initializes Hilt's generated dependency graph. */
class RequestRetryApplication : Application()
