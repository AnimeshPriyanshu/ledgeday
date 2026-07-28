package com.vaultledger.data.remote

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FirestoreErrorMapperTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `retries transient Firestore UNAVAILABLE errors`() = runTest(testDispatcher) {
        var attempts = 0
        val flow: Flow<Int> = flow<Int> {
            attempts++
            if (attempts <= 2) {
                throw FirebaseFirestoreException(
                    "unavailable", FirebaseFirestoreException.Code.UNAVAILABLE,
                )
            }
            emit(99)
        }.retryFirestoreTransient(maxRetries = 5)

        var result: Int? = null
        flow.collect { result = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(99, result)
        assertEquals(3, attempts)
    }

    @Test
    fun `stops retrying after max retries for transient error`() = runTest(testDispatcher) {
        var attempts = 0
        val flow: Flow<Int> = flow<Int> {
            attempts++
            throw FirebaseFirestoreException(
                "persistent", FirebaseFirestoreException.Code.UNAVAILABLE,
            )
        }.retryFirestoreTransient(maxRetries = 2)

        try {
            flow.collect { }
            testDispatcher.scheduler.advanceUntilIdle()
        } catch (e: FirebaseFirestoreException) {
            assertEquals("persistent", e.message)
        }

        assertEquals(3, attempts)
    }

    @Test
    fun `succeeds on first attempt without retry`() = runTest(testDispatcher) {
        var attempts = 0
        val flow: Flow<Int> = flow<Int> {
            attempts++
            emit(42)
        }.retryFirestoreTransient(maxRetries = 5)

        var result: Int? = null
        flow.collect { result = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(42, result)
        assertEquals(1, attempts)
    }

    @Test
    fun `unwrapFirestoreException returns null for non-Firestore exception`() {
        val ex = RuntimeException("test")
        val unwrapped = unwrapFirestoreException(ex)
        assertEquals(null, unwrapped)
    }

    @Test
    fun `isTransientFirestoreError returns false for non-Firestore exception`() {
        val ex = RuntimeException("test")
        assertEquals(false, isTransientFirestoreError(ex))
    }

    @Test
    fun `unwrapFirestoreException unwraps cause chain`() {
        val inner = FirebaseFirestoreException("inner", FirebaseFirestoreException.Code.UNAVAILABLE)
        val outer = RuntimeException("outer", inner)
        val unwrapped = unwrapFirestoreException(outer)
        assertEquals(inner, unwrapped)
    }

    @Test
    fun `retryFirestoreTransient does not retry non-Firestore errors`() = runTest(testDispatcher) {
        var attempts = 0
        val flow: Flow<Int> = flow<Int> {
            attempts++
            throw RuntimeException("non-firestore")
        }.retryFirestoreTransient(maxRetries = 5)

        try {
            flow.collect { }
            testDispatcher.scheduler.advanceUntilIdle()
        } catch (_: RuntimeException) { }

        assertEquals(1, attempts)
    }
}
