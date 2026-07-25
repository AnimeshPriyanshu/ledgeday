package com.vaultledger.feature.settings

import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var workspaceRepository: FakeWorkspaceRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        workspaceRepository = FakeWorkspaceRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signOut clears auth state`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(authRepository, workspaceRepository)

        vm.signOut()
        advanceUntilIdle()

        val authState = authRepository.observeAuthState().first()
        assertNull(authState)
    }

    @Test
    fun `signOut does not throw when repository errors`() = runTest(testDispatcher) {
        authRepository.throwOnSignOut = true
        val vm = SettingsViewModel(authRepository, workspaceRepository)

        vm.signOut()
        advanceUntilIdle()

        // Should not throw — sign out errors are non-blocking
        // Simply verifying the operation completes without exception
    }
}

class FakeWorkspaceRepository : WorkspaceRepository {
    private val workspaces = mutableListOf<Workspace>()

    override fun getAllWorkspaces(): Flow<List<Workspace>> = flowOf(workspaces.toList())

    override suspend fun getWorkspaceById(id: String): Workspace? = workspaces.find { it.id == id }

    override suspend fun createWorkspace(name: String, description: String): Workspace {
        val ws = Workspace(id = "test-id", name = name, description = description, createdAt = System.currentTimeMillis())
        workspaces.add(ws)
        return ws
    }

    override suspend fun updateWorkspace(workspace: Workspace) {
        val idx = workspaces.indexOfFirst { it.id == workspace.id }
        if (idx >= 0) workspaces[idx] = workspace
    }

    override suspend fun deleteWorkspace(id: String) { workspaces.removeAll { it.id == id } }
}
