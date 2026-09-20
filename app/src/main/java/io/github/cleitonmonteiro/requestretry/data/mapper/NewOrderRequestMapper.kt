package io.github.cleitonmonteiro.requestretry.data.mapper

import io.github.cleitonmonteiro.requestretry.data.remote.NewOrderRequestDto
import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest

/** The first domain -> DTO mapper in the app: every other request is param-free or a bare path segment. */
fun NewOrderRequest.toDto() = NewOrderRequestDto(itemName = itemName, quantity = quantity, customerName = customerName)