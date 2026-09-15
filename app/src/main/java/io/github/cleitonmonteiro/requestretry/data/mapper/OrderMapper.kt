package io.github.cleitonmonteiro.requestretry.data.mapper

import io.github.cleitonmonteiro.requestretry.data.remote.OrderDto
import io.github.cleitonmonteiro.requestretry.domain.model.Order

fun OrderDto.toDomain() = Order(id = orderId, item = itemName, total = totalAmount.toDouble())
