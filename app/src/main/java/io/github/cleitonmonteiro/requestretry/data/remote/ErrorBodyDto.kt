package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.serialization.Serializable

/**
 * The mock server's error body shape — every non-2xx response it sends is `{"error": "..."}`
 * (see `server/index.js`'s `sendJson` calls for 400/404). [ApiClient] decodes this to populate
 * [io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure.Http.message].
 */
@Serializable
data class ErrorBodyDto(val error: String? = null)
