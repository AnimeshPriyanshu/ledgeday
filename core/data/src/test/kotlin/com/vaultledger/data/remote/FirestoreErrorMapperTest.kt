package com.vaultledger.data.remote

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
    fun `retries non-throwable exceptions as transient`() = runTest(testDispatcher) {
        var attempts = 0
        val flow: Flow<Int> = flow<Int> {
            attempts++
            if (attempts <= 2) {
                throw RuntimeException("network error")
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
    fun `stops retrying after max retries`() = runTest(testDispatcher) {
        var attempts = 0
        val flow: Flow<Int> = flow<Int> {
            attempts++
            throw RuntimeException("persistent error")
        }.retryFirestoreTransient(maxRetries = 2)

        try {
            flow.collect { }
            testDispatcher.scheduler.advanceUntilIdle()
        } catch (e: RuntimeException) {
            assertEquals("persistent error", e.message)
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
    fun `isTransientFirestoreError returns true for non-Firestore exception`() {
        val ex = RuntimeException("test")
        assertEquals(true, isTransientFirestoreError(ex))
    }

}
