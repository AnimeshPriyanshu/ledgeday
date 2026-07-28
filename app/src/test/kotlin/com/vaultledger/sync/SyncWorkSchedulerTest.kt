package com.vaultledger.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncWorkSchedulerTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config =
            Configuration
                .Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `schedule enqueues unique periodic work`() =
        runTest(testDispatcher) {
            SyncWorkScheduler.schedule(context)

            val workManager = WorkManager.getInstance(context)
            val workInfos = workManager.getWorkInfosForUniqueWork(SyncWorkScheduler.WORK_NAME).get()
            assertNotNull(workInfos)
            assertEquals(1, workInfos.size)
        }

    @Test
    fun `cancel stops the periodic work`() =
        runTest(testDispatcher) {
            SyncWorkScheduler.schedule(context)
            SyncWorkScheduler.cancel(context)

            val workManager = WorkManager.getInstance(context)
            val workInfos = workManager.getWorkInfosForUniqueWork(SyncWorkScheduler.WORK_NAME).get()
            assertTrue(workInfos.isEmpty() || workInfos.all { it.state.isFinished })
        }

    @Test
    fun `multiple schedule calls do not duplicate work`() =
        runTest(testDispatcher) {
            SyncWorkScheduler.schedule(context)
            SyncWorkScheduler.schedule(context)
            SyncWorkScheduler.schedule(context)

            val workManager = WorkManager.getInstance(context)
            val workInfos = workManager.getWorkInfosForUniqueWork(SyncWorkScheduler.WORK_NAME).get()
            assertEquals(1, workInfos.size)
        }

    @Test
    fun `cancel then schedule creates fresh work`() =
        runTest(testDispatcher) {
            SyncWorkScheduler.schedule(context)
            SyncWorkScheduler.cancel(context)
            SyncWorkScheduler.schedule(context)

            val workManager = WorkManager.getInstance(context)
            val workInfos = workManager.getWorkInfosForUniqueWork(SyncWorkScheduler.WORK_NAME).get()
            assertEquals(1, workInfos.size)
            assertTrue(workInfos.first().state == WorkInfo.State.ENQUEUED)
        }
}
