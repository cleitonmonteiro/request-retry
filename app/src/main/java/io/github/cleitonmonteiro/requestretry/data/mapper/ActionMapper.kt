package io.github.cleitonmonteiro.requestretry.data.mapper

import io.github.cleitonmonteiro.requestretry.data.remote.ActionDto
import io.github.cleitonmonteiro.requestretry.data.remote.ActionTypeDto
import io.github.cleitonmonteiro.requestretry.domain.model.Action

/**
 * No `else` branch: adding a new [ActionTypeDto] wire case without adding it here is a compile
 * error, not a silent runtime gap — the same guarantee an untranslated `when` gives you for any
 * sealed/enum mapping between layers. This is also where two different failure modes are told
 * apart: an *unrecognized* action type degrades to [Action.Unknown] (see [ActionTypeDto.UNKNOWN]'s
 * doc), but a *recognized* type with a missing required field ([requireTarget]) throws — that's a
 * server bug, not a forward-compatibility gap, and surfaces as a typed protocol failure instead of
 * being silently swallowed.
 */
fun ActionDto.toDomain(): Action = when (actionType) {
    ActionTypeDto.DEEPLINK -> Action.Deeplink(uri = requireTarget(), label = label ?: "Open")
    ActionTypeDto.EXTERNAL_LINK -> Action.ExternalLink(url = requireTarget(), label = label ?: "Open link")
    ActionTypeDto.CLOSE -> Action.Close(label = label ?: "Close")
    ActionTypeDto.UNKNOWN -> Action.Unknown
}

private fun ActionDto.requireTarget(): String =
    requireNotNull(target) { "$actionType action is missing its target" }
