package io.github.cleitonmonteiro.requestretry.data.mapper

import io.github.cleitonmonteiro.requestretry.data.remote.ActionDto
import io.github.cleitonmonteiro.requestretry.data.remote.ActionTypeDto
import io.github.cleitonmonteiro.requestretry.domain.model.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionMapperTest {

    @Test
    fun `toDomain maps a deeplink to its sealed case with the server label`() {
        // Arrange
        val dto = ActionDto(actionType = ActionTypeDto.DEEPLINK, target = "requestretry://orders", label = "View orders")

        // Act
        val action = dto.toDomain()

        // Assert
        assertEquals(Action.Deeplink(uri = "requestretry://orders", label = "View orders"), action)
    }

    @Test
    fun `toDomain maps an external link to its sealed case`() {
        // Arrange
        val dto = ActionDto(actionType = ActionTypeDto.EXTERNAL_LINK, target = "https://example.com", label = "Track shipment")

        // Act
        val action = dto.toDomain()

        // Assert
        assertEquals(Action.ExternalLink(url = "https://example.com", label = "Track shipment"), action)
    }

    @Test
    fun `toDomain maps close without requiring a target`() {
        // Arrange
        val dto = ActionDto(actionType = ActionTypeDto.CLOSE, label = "Done")

        // Act
        val action = dto.toDomain()

        // Assert
        assertEquals(Action.Close(label = "Done"), action)
    }

    @Test
    fun `toDomain falls back to a default label when the server omits one`() {
        // Arrange
        val dto = ActionDto(actionType = ActionTypeDto.CLOSE, label = null)

        // Act
        val action = dto.toDomain()

        // Assert
        assertEquals(Action.Close(label = "Close"), action)
    }

    @Test
    fun `toDomain degrades an unrecognized action type to Unknown instead of throwing`() {
        // Arrange
        val dto = ActionDto(actionType = ActionTypeDto.UNKNOWN)

        // Act
        val action = dto.toDomain()

        // Assert
        assertEquals(Action.Unknown, action)
    }

    @Test
    fun `toDomain throws when a deeplink is missing its target`() {
        // Arrange
        val dto = ActionDto(actionType = ActionTypeDto.DEEPLINK, target = null)

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) { dto.toDomain() }
    }
}
