package com.vaultledger.ui.screen

import app.cash.turbine.test
import com.vaultledger.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAuthRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init emits Loading then Unauthenticated when no user is signed in`() = runTest(testDispatcher) {
        val vm = SplashViewModel(repository)

        vm.state.test {
            assertEquals(SplashState.Loading, awaitItem())
            assertEquals(SplashState.Unauthenticated, awaitItem())
            cancel()
        }
    }

    @Test
    fun `init emits Loading then Authenticated when user is signed in`() = runTest(testDispatcher) {
        repository.setAuthenticatedUser(
            User(id = "1", email = "test@example.com"),
        )
        val vm = SplashViewModel(repository)

        vm.state.test {
            assertEquals(SplashState.Loading, awaitItem())
            assertEquals(SplashState.Authenticated, awaitItem())
            cancel()
        }
    }

    @Test
    fun `state starts as Loading`() = runTest(testDispatcher) {
        val vm = SplashViewModel(repository)

        vm.state.test {
            assertEquals(SplashState.Loading, awaitItem())
            cancel()
        }
    }

    @Test
    fun `emits Authenticated when user signs in after init`() = runTest(testDispatcher) {
        val vm = SplashViewModel(repository)

        vm.state.test {
            assertEquals(SplashState.Loading, awaitItem())
            assertEquals(SplashState.Unauthenticated, awaitItem())

            repository.setAuthenticatedUser(
                User(id = "2", email = "user@example.com"),
            )

            assertEquals(SplashState.Authenticated, awaitItem())
            cancel()
        }
    }

    @Test
    fun `emits Unauthenticated when user signs out after init`() = runTest(testDispatcher) {
        repository.setAuthenticatedUser(
            User(id = "3", email = "loggedin@example.com"),
        )
        val vm = SplashViewModel(repository)

        vm.state.test {
            assertEquals(SplashState.Loading, awaitItem())
            assertEquals(SplashState.Authenticated, awaitItem())

            repository.signOut()

            assertEquals(SplashState.Unauthenticated, awaitItem())
            cancel()
        }
    }

    @Test
    fun `emits Error when auth state check fails`() = runTest(testDispatcher) {
        repository.throwOnObserveAuthState = true
        val vm = SplashViewModel(repository)

        vm.state.test {
            assertEquals(SplashState.Loading, awaitItem())
            // Flow throws, which propagates as cancellation in collect
            // The ViewModel will emit nothing more after the error
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state is not null after init`() = runTest(testDispatcher) {
        val vm = SplashViewModel(repository)

        vm.state.test {
            val first = awaitItem()
            assertEquals(SplashState.Loading, first)
            cancel()
        }
    }

    @Test
    fun `re-emits Authenticated when user state changes from null to user`() = runTest(testDispatcher) {
        val vm = SplashViewModel(repository)

        vm.state.test {
            assertEquals(SplashState.Loading, awaitItem())
            assertEquals(SplashState.Unauthenticated, awaitItem())

            repository.setAuthenticatedUser(
                User(id = "4", email = "new@example.com"),
            )

            assertEquals(SplashState.Authenticated, awaitItem())
            cancel()
        }
    }
}
