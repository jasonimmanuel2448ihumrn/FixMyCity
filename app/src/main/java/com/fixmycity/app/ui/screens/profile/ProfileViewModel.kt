package com.fixmycity.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixmycity.app.data.model.User
import com.fixmycity.app.data.repository.FixMyCityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User = User("user_1", "Citizen", "citizen@fixmycity.org"),
    val notificationsEnabled: Boolean = true,
    val reportsSubmitted: Int = 0,
    val reportsResolved: Int = 0,
    val inProgress: Int = 0
)

class ProfileViewModel(
    private val repository: FixMyCityRepository = FixMyCityRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.currentUser, repository.impactStats) { user, stats ->
                _uiState.value.copy(
                    user = user,
                    reportsSubmitted = stats.reportsSubmitted,
                    reportsResolved = stats.resolved,
                    inProgress = stats.inProgress
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.fetchCurrentUser()
            repository.fetchStats()
        }
    }

    fun toggleNotifications() {
        _uiState.value = _uiState.value.copy(notificationsEnabled = !_uiState.value.notificationsEnabled)
    }

    fun updateProfile(name: String, phone: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateProfile(name, phone)
            onComplete()
        }
    }
}
