@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRefreshCoordinatorTest {
    @Test
    fun `concurrent requests sharing one token generation refresh only once`() = runTest {
        val coordinator = AuthRefreshCoordinator()
        val generation = coordinator.snapshot()
        var refreshes = 0

        val results = List(20) {
            async {
                coordinator.refreshIfCurrent(generation) {
                    refreshes++
                    true
                }
            }
        }.map { it.await() }

        assertTrue(results.all { it })
        assertEquals(1, refreshes)
    }
}
