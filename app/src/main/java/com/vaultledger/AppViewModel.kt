package com.vaultledger

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.data.sync.SyncManager
import com.vaultledger.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow<Boolean?>(null)
    val isAuthenticated: StateFlow<Boolean?> = _isAuthenticated.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                val uid = user?.id
                if (uid != null) {
                    Log.d(TAG, "Auth state: user=$uid, calling startSyncing")
                    syncManager.startSyncing(uid)
                } else {
                    Log.d(TAG, "Auth state: null user, calling stopSyncing")
                    syncManager.stopSyncing()
                }
                _isAuthenticated.value = uid != null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared: stopping sync")
        syncManager.stopSyncing()
    }

    companion object {
        private const val TAG = "AppViewModel"
    }
}
