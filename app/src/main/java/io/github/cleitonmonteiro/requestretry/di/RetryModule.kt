package io.github.cleitonmonteiro.requestretry.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.cleitonmonteiro.requestretry.retry.ConservativeRetryDecider
import io.github.cleitonmonteiro.requestretry.retry.DefaultFailureClassifier
import io.github.cleitonmonteiro.requestretry.retry.FailureClassifier
import io.github.cleitonmonteiro.requestretry.data.observability.AndroidDebugLogger
import io.github.cleitonmonteiro.requestretry.data.observability.DebugLogger
import io.github.cleitonmonteiro.requestretry.data.observability.SanitizedRetryObserver
import io.github.cleitonmonteiro.requestretry.retry.RetryDecider
import io.github.cleitonmonteiro.requestretry.retry.RetryExecutor
import io.github.cleitonmonteiro.requestretry.retry.RetryObserver
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
/** Supplies the resilience policies and payload-free retry telemetry observer. */
object RetryModule {
    @Provides
    @Singleton
    fun provideFailureClassifier(): FailureClassifier = DefaultFailureClassifier

    @Provides
    @Singleton
    fun provideRetryDecider(): RetryDecider = ConservativeRetryDecider

    @Provides
    @Singleton
    fun provideRetryObserver(observer: SanitizedRetryObserver): RetryObserver = observer

    @Provides
    @Singleton
    fun provideDebugLogger(logger: AndroidDebugLogger): DebugLogger = logger

    @Provides
    @Singleton
    fun provideRetryExecutor(
        failureClassifier: FailureClassifier,
        retryDecider: RetryDecider,
        observer: RetryObserver,
    ): RetryExecutor = RetryExecutor(failureClassifier, retryDecider, observer)
}
