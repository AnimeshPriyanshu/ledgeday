package com.vaultledger.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class UiOperation<T>(
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var savedProvider: (suspend () -> Flow<T>)? = null
    private var savedMap: ((T) -> UiState<T>)? = null

    fun observe(
        provide: suspend () -> Flow<T>,
        map: (T) -> UiState<T> = { UiState.Success(it) },
    ) {
        savedProvider = provide
        savedMap = map
        restartObserve()
    }

    fun launch(
        block: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Operation failed")
            }
        }
    }

    fun retry() {
        if (savedProvider != null) {
            restartObserve()
        }
    }

    private fun restartObserve() {
        observeJob?.cancel()
        observeJob = scope.launch {
            _state.value = UiState.Loading
            val provider = savedProvider ?: return@launch
            val mapper = savedMap ?: return@launch
            try {
                provider()
                    .map { data -> mapper(data) }
                    .catch { e ->
                        _state.value = UiState.Error(e.message ?: "An error occurred")
                    }
                    .collect { uiState ->
                        _state.value = uiState
                    }
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "An error occurred")
            }
        }
    }
}
