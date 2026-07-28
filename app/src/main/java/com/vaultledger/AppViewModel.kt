package com.vaultledger

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.data.sync.SyncManager
import com.vaultledger.domain.repository.AuthRepository
import com.vaultledger.sync.SyncWorkScheduler
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
    private val application: Application? = null,
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow<Boolean?>(null)
    val isAuthenticated: StateFlow<Boolean?> = _isAuthenticated.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                val uid = user?.id
                if (uid != null) {
                    syncManager.startSyncing(uid)
                } else {
                    syncManager.stopSyncing()
                }
                _isAuthenticated.value = uid != null
                _isAuthenticated.value?.let { isAuth ->
                    application?.let { ctx ->
                        if (isAuth) SyncWorkScheduler.schedule(ctx)
                        else SyncWorkScheduler.cancel(ctx)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncManager.stopSyncing()
    }
}
