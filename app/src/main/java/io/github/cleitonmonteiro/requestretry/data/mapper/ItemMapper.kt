package io.github.cleitonmonteiro.requestretry.data.mapper

import io.github.cleitonmonteiro.requestretry.data.remote.ItemDto
import io.github.cleitonmonteiro.requestretry.domain.model.Item

fun ItemDto.toDomain() = Item(id = item_id, name = item_name)
