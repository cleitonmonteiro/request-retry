package io.github.cleitonmonteiro.requestretry.data.mapper

import io.github.cleitonmonteiro.requestretry.data.remote.ActionDto
import io.github.cleitonmonteiro.requestretry.data.remote.ActionTypeDto
import io.github.cleitonmonteiro.requestretry.domain.model.Action
import io.github.cleitonmonteiro.requestretry.domain.model.ActionType

fun ActionDto.toDomain() = Action(type = actionType.toDomain(), target = target)

/**
 * No `else` branch: adding a new [ActionTypeDto] wire case without adding it here is a compile
 * error, not a silent runtime gap — the same guarantee an untranslated `when` gives you for any
 * sealed/enum mapping between layers.
 */
private fun ActionTypeDto.toDomain(): ActionType = when (this) {
    ActionTypeDto.DEEPLINK -> ActionType.DEEPLINK
    ActionTypeDto.EXTERNAL_LINK -> ActionType.EXTERNAL_LINK
    ActionTypeDto.CLOSE -> ActionType.CLOSE
}
