package io.github.cleitonmonteiro.requestretry.data.mapper

import io.github.cleitonmonteiro.requestretry.data.remote.OrderDto
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderMapperTest {

    @Test
    fun `toDomain maps fields and parses the total amount into a double`() {
        // Arrange
        val dto = OrderDto(orderId = "A-1001", itemName = "Mechanical keyboard", totalAmount = "89.90")

        // Act
        val order = dto.toDomain()

        // Assert
        assertEquals(Order(id = "A-1001", item = "Mechanical keyboard", total = 89.90), order)
    }
}
