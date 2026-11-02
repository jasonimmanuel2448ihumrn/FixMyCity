package com.fixmycity.app.data.model

data class Report(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val date: String,
    val status: ReportStatus,
    val imageResId: Int? = null,
    val imageUrl: String? = null,
    val userId: String = "",          // Firebase Auth uid
    val userName: String = "Citizen", // Display name of the reporter
    val timelineSteps: List<TimelineStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null
)

enum class ReportStatus {
    SUBMITTED,
    UNDER_REVIEW,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    REJECTED
}

data class TimelineStep(
    val status: ReportStatus,
    val label: String,
    val date: String?,
    val isCompleted: Boolean
)