package com.fixmycity.app.data.model

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val isRead: Boolean = false,
    val type: NotificationType = NotificationType.STATUS_UPDATE,
    val reportId: String? = null,
    val userId: String = "",          // Firebase Auth uid of the recipient
    val createdAt: Long = System.currentTimeMillis()
)

enum class NotificationType {
    STATUS_UPDATE,
    RESOLVED,
    NEW_ISSUE,
    GENERAL
}