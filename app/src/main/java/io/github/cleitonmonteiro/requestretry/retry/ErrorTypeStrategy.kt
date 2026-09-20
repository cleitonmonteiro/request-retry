package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestException

data class FeedbackErrorData(
    val title: String,
    val description: String,
    val buttonLabel: String,
)

enum class ErrorType {
    CONNECTION,
    GENERIC,
    UNPROCESSABLE_ENTITY,
}

fun interface ErrorTypeStrategy {
    fun classify(error: Throwable): ErrorType
}

object DefaultErrorTypeStrategy : ErrorTypeStrategy {
    override fun classify(error: Throwable): ErrorType = when (error) {
        is RequestException.Connection -> ErrorType.CONNECTION
        is RequestException.Http -> if (error.statusCode == 422) {
            ErrorType.UNPROCESSABLE_ENTITY
        } else {
            ErrorType.GENERIC
        }
        else -> ErrorType.GENERIC
    }
}

fun ErrorType.feedback(canRetry: Boolean): FeedbackErrorData = when (this) {
    ErrorType.CONNECTION -> FeedbackErrorData(
        title = "No connection",
        description = "Check your internet connection and try again",
        buttonLabel = if (canRetry) "Try again" else "Go back",
    )
    ErrorType.GENERIC -> FeedbackErrorData(
        title = "Something went wrong",
        description = "We couldn’t complete your request. Try again.",
        buttonLabel = if (canRetry) "Try again" else "Go back",
    )
    ErrorType.UNPROCESSABLE_ENTITY -> FeedbackErrorData(
        title = "Request couldn’t be processed",
        description = "Check your information and try again.",
        buttonLabel = "Go back",
    )
}
