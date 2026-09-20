package io.github.cleitonmonteiro.requestretry.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.cleitonmonteiro.requestretry.retry.Bulkhead
import io.github.cleitonmonteiro.requestretry.retry.CircuitBreaker
import io.github.cleitonmonteiro.requestretry.retry.ConservativeRetryDecider
import io.github.cleitonmonteiro.requestretry.retry.DefaultFailureClassifier
import io.github.cleitonmonteiro.requestretry.retry.FailureClassifier
import io.github.cleitonmonteiro.requestretry.data.observability.SanitizedRetryObserver
import io.github.cleitonmonteiro.requestretry.retry.RetryBudget
import io.github.cleitonmonteiro.requestretry.retry.RetryDecider
import io.github.cleitonmonteiro.requestretry.retry.RetryObserver
import io.github.cleitonmonteiro.requestretry.retry.SlidingWindowRetryBudget
import io.github.cleitonmonteiro.requestretry.retry.ThresholdCircuitBreaker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RetryModule {
    @Provides
    @Singleton
    fun provideFailureClassifier(): FailureClassifier = DefaultFailureClassifier

    @Provides
    @Singleton
    fun provideRetryDecider(): RetryDecider = ConservativeRetryDecider

    @Provides
    @Singleton
    fun provideRetryBudget(): RetryBudget = SlidingWindowRetryBudget()

    @Provides
    @Singleton
    fun provideCircuitBreaker(): CircuitBreaker = ThresholdCircuitBreaker(failureThreshold = 10)

    @Provides
    @Singleton
    fun provideBulkhead(): Bulkhead = Bulkhead(maxConcurrentRequests = 8)

    @Provides
    @Singleton
    fun provideRetryObserver(observer: SanitizedRetryObserver): RetryObserver = observer
}
