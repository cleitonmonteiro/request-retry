package io.github.cleitonmonteiro.requestretry.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.cleitonmonteiro.requestretry.domain.model.DurableOperationState
import io.github.cleitonmonteiro.requestretry.domain.model.IdempotencyKey
import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomOrderOperationStoreTest {
    private lateinit var database: RequestRetryDatabase
    private lateinit var store: RoomOrderOperationStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RequestRetryDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RoomOrderOperationStore(database, database.orderOperationDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun operationIsPersistedBeforeSendingAndTerminalStateDoesNotRegress() = runBlocking {
        val request = request()
        store.createIfAbsent(request)
        assertEquals(DurableOperationState.CREATED, store.get(request.operationId)?.state)

        store.markSending(request.operationId)
        assertEquals(DurableOperationState.SENDING, store.get(request.operationId)?.state)
        assertEquals(1, store.get(request.operationId)?.attemptsUsed)

        store.markSucceeded(request.operationId, Order("A-1", "Backpack", 19.99))
        store.markSending(request.operationId)
        assertEquals(DurableOperationState.SUCCEEDED, store.get(request.operationId)?.state)
        assertEquals(1, store.get(request.operationId)?.attemptsUsed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sameOperationIdentityRejectsDifferentPayload() = runBlocking {
        val request = request()
        store.createIfAbsent(request)
        store.createIfAbsent(request.copy(quantity = 99))
    }

    private fun request() = NewOrderRequest(
        itemName = "Backpack",
        quantity = 1,
        customerName = "Ada",
        operationId = OperationId("operation"),
        idempotencyKey = IdempotencyKey("key"),
    )
}
