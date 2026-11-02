package com.fixmycity.app.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixmycity.app.data.model.AppNotification
import com.fixmycity.app.data.repository.FixMyCityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0
)

class NotificationsViewModel(
    private val repository: FixMyCityRepository = FixMyCityRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.notifications.collect { notifs ->
                val list = if (notifs.isNotEmpty()) notifs else com.fixmycity.app.data.mock.MockData.mockNotifications
                _uiState.value = NotificationsUiState(
                    notifications = list,
                    unreadCount = list.count { !it.isRead }
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.fetchNotifications()
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead()
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
        }
    }
}
