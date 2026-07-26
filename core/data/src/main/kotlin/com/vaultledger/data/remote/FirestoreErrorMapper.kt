package com.vaultledger.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.vaultledger.data.repository.exception.FirestoreTimeoutException
import com.vaultledger.data.repository.exception.OfflineException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.min

private const val TAG = "FirestoreRetry"

object FirestoreErrorMapper {

    data class MappedError(
        val userMessage: String,
        val isRetryable: Boolean,
    )

    private data class ErrorPattern(
        val prefix: String? = null,
        val code: FirebaseFirestoreException.Code? = null,
        val userMessage: String,
        val isRetryable: Boolean = false,
    )

    private val patterns by lazy { listOf(
        ErrorPattern(
            code = FirebaseFirestoreException.Code.PERMISSION_DENIED,
            userMessage = "You don't have permission to perform this action.",
        ),
        ErrorPattern(
            code = FirebaseFirestoreException.Code.NOT_FOUND,
            userMessage = "The requested resource was not found. It may have been removed.",
        ),
        ErrorPattern(
            code = FirebaseFirestoreException.Code.UNAVAILABLE,
            userMessage = "Unable to reach the server. Please check your connection and try again.",
            isRetryable = true,
        ),
        ErrorPattern(
            code = FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            userMessage = "The request timed out. Please try again.",
            isRetryable = true,
        ),
        ErrorPattern(
            code = FirebaseFirestoreException.Code.CANCELLED,
            userMessage = "The request was cancelled. Please try again.",
            isRetryable = true,
        ),
        ErrorPattern(
            code = FirebaseFirestoreException.Code.FAILED_PRECONDITION,
            userMessage = "This action cannot be completed right now.",
        ),
        ErrorPattern(
            prefix = "Invite not found",
            userMessage = "This invite code is invalid or has been removed.",
        ),
        ErrorPattern(
            prefix = "Invite is",
            userMessage = "This invite has already been accepted or expired.",
        ),
        ErrorPattern(
            prefix = "Invite has expired",
            userMessage = "This invite has expired. Ask your partner to generate a new one.",
        ),
        ErrorPattern(
            prefix = "Cannot accept your own invite",
            userMessage = "You cannot accept your own invite. Share the code with your partner.",
        ),
    ) }

    fun map(throwable: Throwable): MappedError {
        return when (throwable) {
            is OfflineException -> MappedError(
                userMessage = "Internet connection required to generate an invite.",
                isRetryable = true,
            )
            is FirestoreTimeoutException -> MappedError(
                userMessage = "Unable to reach the server. Please try again.",
                isRetryable = true,
            )
            else -> mapFirestore(throwable)
        }
    }

    private fun mapFirestore(throwable: Throwable): MappedError {
        val firestoreException = unwrap(throwable)
        if (firestoreException != null) {
            for (pattern in patterns) {
                if (pattern.prefix != null && (firestoreException.message?.startsWith(pattern.prefix) == true)) {
                    return MappedError(pattern.userMessage, pattern.isRetryable)
                }
                if (pattern.code != null && firestoreException.code == pattern.code) {
                    val message = firestoreException.message ?: ""
                    if (pattern.prefix == null || message.startsWith(pattern.prefix)) {
                        return MappedError(pattern.userMessage, pattern.isRetryable)
                    }
                }
            }
        }
        return MappedError(
            userMessage = "Something went wrong. Please try again.",
            isRetryable = true,
        )
    }

    private fun unwrap(throwable: Throwable): FirebaseFirestoreException? {
        return when (throwable) {
            is FirebaseFirestoreException -> throwable
            else -> null
        }
    }
}

fun isTransientFirestoreError(throwable: Throwable): Boolean {
    val firestoreException = unwrapFirestoreException(throwable) ?: return true
    return firestoreException.code != FirebaseFirestoreException.Code.PERMISSION_DENIED &&
        firestoreException.code != FirebaseFirestoreException.Code.NOT_FOUND &&
        firestoreException.code != FirebaseFirestoreException.Code.INVALID_ARGUMENT &&
        firestoreException.code != FirebaseFirestoreException.Code.FAILED_PRECONDITION &&
        firestoreException.code != FirebaseFirestoreException.Code.ALREADY_EXISTS &&
        firestoreException.code != FirebaseFirestoreException.Code.UNAUTHENTICATED
}

fun unwrapFirestoreException(throwable: Throwable): FirebaseFirestoreException? {
    return when (throwable) {
        is FirebaseFirestoreException -> throwable
        else -> null
    }
}

fun <T> Flow<T>.retryFirestoreTransient(maxRetries: Int = 5): Flow<T> = retryWhen { cause, attempt ->
    if (!isTransientFirestoreError(cause) || attempt >= maxRetries) {
        Log.w(TAG, "Not retrying: isTransient=${isTransientFirestoreError(cause)}, " +
            "attempt=$attempt, maxRetries=$maxRetries, error=${cause.message}")
        false
    } else {
        val delayMs = min(1000L * (1L shl attempt.toInt()), 30000L)
        Log.w(TAG, "Retrying (attempt ${attempt + 1}/$maxRetries) in ${delayMs}ms: ${cause.message}")
        if (cause is FirebaseFirestoreException) {
            Log.w(TAG, "Firestore error code: ${cause.code}")
        }
        delay(delayMs)
        true
    }
}
