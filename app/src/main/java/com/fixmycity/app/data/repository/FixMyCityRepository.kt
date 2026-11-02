package com.fixmycity.app.data.repository

import android.util.Log
import com.fixmycity.app.data.mock.MockData
import com.fixmycity.app.data.model.AppNotification
import com.fixmycity.app.data.model.Category
import com.fixmycity.app.data.model.NotificationType
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.data.model.ReportStatus
import com.fixmycity.app.data.model.TimelineStep
import com.fixmycity.app.data.model.User
import com.fixmycity.app.domain.model.ImpactStats
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FixMyCityRepository private constructor() {

    // ── Firebase instances ────────────────────────────────────────────────────
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Reactive State ────────────────────────────────────────────────────────
    private val _currentUser = MutableStateFlow(MockData.mockUser)
    val currentUser: StateFlow<User> = _currentUser.asStateFlow()

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _impactStats = MutableStateFlow(ImpactStats(0, 0, 0))
    val impactStats: StateFlow<ImpactStats> = _impactStats.asStateFlow()

    private val _isBackendConnected = MutableStateFlow(false)
    val isBackendConnected: StateFlow<Boolean> = _isBackendConnected.asStateFlow()

    private val _categories = MutableStateFlow(MockData.mockCategories)
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    init {
        // Restore session if Firebase already has a logged-in user
        val currentFirebaseUser: FirebaseUser? = firebaseAuth.currentUser
        if (currentFirebaseUser != null) {
            val uid: String = currentFirebaseUser.uid
            scope.launch {
                try {
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    if (userDoc.exists()) {
                        _currentUser.value = documentToUser(uid, userDoc.data ?: emptyMap())
                    }
                    _isBackendConnected.value = true
                    fetchCategories()
                    fetchReports()
                    fetchNotifications()
                } catch (e: Exception) {
                    Log.w(TAG, "Session restore failed: ${e.message}")
                    _isBackendConnected.value = false
                }
            }
        } else {
            // Also pre-fetch public categories & real reports from Firestore
            scope.launch {
                fetchCategories()
                fetchReports()
            }
        }
    }

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        try {
            fetchCategories()
            fetchReports()
            fetchNotifications()
            _isBackendConnected.value = true
        } catch (e: Exception) {
            Log.w(TAG, "Refresh failed: ${e.message}")
            _isBackendConnected.value = false
        }
    }

    // ── Helper: format date string ────────────────────────────────────────────
    private fun formatCurrentDate(): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
        return sdf.format(Date())
    }

    // ── Reports ───────────────────────────────────────────────────────────────
    suspend fun fetchReports(): List<Report> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            val liveReports = snapshot.documents.mapNotNull { doc ->
                documentToReport(doc.id, doc.data ?: return@mapNotNull null)
            }.filterNot { it.isDummyReport() }
            _reports.value = liveReports
            calculateAndEmitStats(liveReports)
            _isBackendConnected.value = true
            return@withContext _reports.value
        } catch (e: Exception) {
            Log.w(TAG, "fetchReports with orderBy failed: ${e.message}, falling back to plain get...")
            try {
                val snapshot = firestore.collection("reports").get().await()
                val liveReports = snapshot.documents.mapNotNull { doc ->
                    documentToReport(doc.id, doc.data ?: return@mapNotNull null)
                }.filterNot { it.isDummyReport() }.sortedByDescending { it.createdAt }
                _reports.value = liveReports
                calculateAndEmitStats(liveReports)
                _isBackendConnected.value = true
                return@withContext _reports.value
            } catch (e2: Exception) {
                Log.w(TAG, "fetchReports fallback failed: ${e2.message}")
                _isBackendConnected.value = false
            }
        }
        return@withContext _reports.value
    }

    suspend fun fetchMyReports(): List<Report> = withContext(Dispatchers.IO) {
        val uid: String = firebaseAuth.currentUser?.uid ?: return@withContext emptyList()
        try {
            val snapshot = firestore.collection("reports")
                .whereEqualTo("userId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            return@withContext snapshot.documents.mapNotNull { doc ->
                documentToReport(doc.id, doc.data ?: return@mapNotNull null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchMyReports failed: ${e.message}")
            return@withContext _reports.value.filter { it.userId == uid }
        }
    }

    suspend fun getReportById(id: String): Report? = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("reports").document(id).get().await()
            val data = doc.data ?: return@withContext _reports.value.find { it.id == id }
            val report = documentToReport(doc.id, data) ?: return@withContext _reports.value.find { it.id == id }
            _reports.value = _reports.value.map { if (it.id == id) report else it }
                .let { list -> if (list.none { it.id == id }) listOf(report) + list else list }
            return@withContext report
        } catch (e: Exception) {
            Log.w(TAG, "getReportById $id failed: ${e.message}")
            return@withContext _reports.value.find { it.id == id }
        }
    }

    suspend fun createReport(
        category: String,
        description: String,
        location: String,
        title: String? = null,
        imageUrl: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<Report> = withContext(Dispatchers.IO) {
        val uid: String = firebaseAuth.currentUser?.uid ?: "guest_${System.currentTimeMillis()}"
        val authorName: String = _currentUser.value.name.takeIf { it.isNotBlank() } ?: "Citizen"
        val currentDate: String = formatCurrentDate()
        val reportTitle: String = title ?: "$category Issue"

        try {
            // 1. Build timeline steps as raw maps for Firestore
            val timelineStepsData: List<Map<String, Any?>> = listOf(
                mapOf("status" to "SUBMITTED",    "label" to "Submitted",   "date" to currentDate, "isCompleted" to true),
                mapOf("status" to "UNDER_REVIEW", "label" to "Under Review","date" to null,        "isCompleted" to false),
                mapOf("status" to "ASSIGNED",     "label" to "Assigned",    "date" to null,        "isCompleted" to false),
                mapOf("status" to "IN_PROGRESS",  "label" to "In Progress", "date" to null,        "isCompleted" to false),
                mapOf("status" to "RESOLVED",     "label" to "Resolved",    "date" to null,        "isCompleted" to false)
            )

            // 2. Create Firestore document
            val reportRef = firestore.collection("reports").document()
            val reportData: HashMap<String, Any?> = hashMapOf(
                "title" to reportTitle,
                "description" to description.trim(),
                "category" to category,
                "location" to location,
                "date" to currentDate,
                "status" to "SUBMITTED",
                "imageUrl" to imageUrl,
                "userId" to uid,
                "userName" to authorName,
                "timelineSteps" to timelineStepsData,
                "createdAt" to System.currentTimeMillis(),
                "latitude" to latitude,
                "longitude" to longitude
            )
            reportRef.set(reportData).await()

            // 3. Build local Report object from the confirmed data
            val newReport = Report(
                id = reportRef.id,
                title = reportTitle,
                description = description.trim(),
                category = category,
                location = location,
                date = currentDate,
                status = ReportStatus.SUBMITTED,
                imageUrl = imageUrl,
                userId = uid,
                userName = authorName,
                timelineSteps = listOf(
                    TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   currentDate, true),
                    TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review", null, false),
                    TimelineStep(ReportStatus.ASSIGNED,     "Assigned",    null, false),
                    TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress", null, false),
                    TimelineStep(ReportStatus.RESOLVED,     "Resolved",    null, false)
                ),
                createdAt = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude
            )

            // 4. Create notification locally and in Firestore
            val localNotif = AppNotification(
                id = "notif_${System.currentTimeMillis()}_${(100..999).random()}",
                title = "Report Submitted",
                message = "Your report \"$reportTitle\" has been received and submitted for review.",
                time = "Just now",
                isRead = false,
                type = NotificationType.STATUS_UPDATE,
                reportId = reportRef.id,
                userId = uid,
                createdAt = System.currentTimeMillis()
            )
            val muniAlert = AppNotification(
                id = "admin_notif_${System.currentTimeMillis()}_${(100..999).random()}",
                title = "🚨 New Report: $reportTitle",
                message = "Citizen reported $category issue at $location.",
                time = "Just now",
                isRead = false,
                type = NotificationType.NEW_ISSUE,
                reportId = reportRef.id,
                userId = "municipal_officer_001",
                createdAt = System.currentTimeMillis()
            )
            _notifications.value = listOf(localNotif, muniAlert) + _notifications.value

            createNotificationInFirestore(
                uid = uid,
                title = "Report Submitted",
                message = "Your report \"$reportTitle\" has been received and submitted for review.",
                type = "STATUS_UPDATE",
                reportId = reportRef.id
            )

            // 5. Update local state
            _reports.value = listOf(newReport) + _reports.value
            calculateAndEmitStats(_reports.value)
            _currentUser.value = _currentUser.value.copy(
                reportsSubmitted = _currentUser.value.reportsSubmitted + 1
            )
            updateUserStatsInFirestore(uid)
            _isBackendConnected.value = true
            return@withContext Result.success(newReport)
        } catch (e: Exception) {
            Log.e(TAG, "createReport failed: ${e.message}")
            // Offline fallback — add locally only
            val localReport = Report(
                id = "r_${System.currentTimeMillis()}",
                title = reportTitle,
                description = description,
                category = category,
                location = location,
                date = currentDate,
                status = ReportStatus.SUBMITTED,
                imageUrl = imageUrl,
                userId = uid,
                userName = authorName,
                timelineSteps = listOf(
                    TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   currentDate, true),
                    TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review", null, false),
                    TimelineStep(ReportStatus.ASSIGNED,     "Assigned",    null, false),
                    TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress", null, false),
                    TimelineStep(ReportStatus.RESOLVED,     "Resolved",    null, false)
                )
            )
            val localNotif = AppNotification(
                id = "notif_${System.currentTimeMillis()}_${(100..999).random()}",
                title = "Report Submitted",
                message = "Your report \"$reportTitle\" has been received and submitted for review.",
                time = "Just now",
                isRead = false,
                type = NotificationType.STATUS_UPDATE,
                reportId = localReport.id,
                userId = uid,
                createdAt = System.currentTimeMillis()
            )
            val muniAlert = AppNotification(
                id = "admin_notif_${System.currentTimeMillis()}_${(100..999).random()}",
                title = "🚨 New Report: $reportTitle",
                message = "Citizen reported $category issue at $location.",
                time = "Just now",
                isRead = false,
                type = NotificationType.NEW_ISSUE,
                reportId = localReport.id,
                userId = "municipal_officer_001",
                createdAt = System.currentTimeMillis()
            )
            _notifications.value = listOf(localNotif, muniAlert) + _notifications.value
            _reports.value = listOf(localReport) + _reports.value
            calculateAndEmitStats(_reports.value)
            return@withContext Result.success(localReport)
        }
    }

    suspend fun updateReportStatus(
        reportId: String,
        newStatus: ReportStatus,
        note: String? = null
    ): Result<Report> = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        // Security check: Only municipal officers can change report status
        if (!user.role.equals("Municipal", ignoreCase = true) && !user.role.equals("Admin", ignoreCase = true)) {
            Log.w(TAG, "Unauthorized attempt to update report status by user ${user.id} with role ${user.role}")
            return@withContext Result.failure(SecurityException("Only authorized Municipal Corporation personnel can update report status."))
        }

        try {
            val currentReport: Report = getReportById(reportId)
                ?: return@withContext Result.failure(Exception("Report not found"))
            val currentDate: String = formatCurrentDate()

            val statusOrder: List<ReportStatus> = listOf(
                ReportStatus.SUBMITTED, ReportStatus.UNDER_REVIEW,
                ReportStatus.ASSIGNED, ReportStatus.IN_PROGRESS, ReportStatus.RESOLVED
            )
            val statusLabels: Map<ReportStatus, String> = mapOf(
                ReportStatus.SUBMITTED    to "Submitted",
                ReportStatus.UNDER_REVIEW to "Under Review",
                ReportStatus.ASSIGNED     to "Assigned",
                ReportStatus.IN_PROGRESS  to "In Progress",
                ReportStatus.RESOLVED     to "Resolved",
                ReportStatus.REJECTED     to "Rejected"
            )
            val statusLabel = statusLabels[newStatus] ?: newStatus.name.replace('_', ' ')
            val targetIdx: Int = statusOrder.indexOf(newStatus)

            val updatedTimeline: List<TimelineStep>
            val updatedTimelineData: List<Map<String, Any?>>

            if (targetIdx != -1) {
                updatedTimeline = statusOrder.mapIndexed { idx, st ->
                    TimelineStep(
                        status = st,
                        label = statusLabels[st] ?: st.name,
                        date = if (idx <= targetIdx)
                            (currentReport.timelineSteps.getOrNull(idx)?.date ?: currentDate)
                        else null,
                        isCompleted = idx <= targetIdx
                    )
                }
            } else {
                updatedTimeline = currentReport.timelineSteps
            }

            // Generate notification for the user who reported this issue
            val isResolved: Boolean = newStatus == ReportStatus.RESOLVED
            val notifTitle = if (isResolved) "🎉 Issue Resolved!" else "📋 Status Update: $statusLabel"
            val notifMessage = note?.takeIf { it.isNotBlank() }
                ?: "Your report \"${currentReport.title}\" status is now $statusLabel."

            val localNotif = AppNotification(
                id = "notif_${System.currentTimeMillis()}_${(100..999).random()}",
                title = notifTitle,
                message = notifMessage,
                time = "Just now",
                isRead = false,
                type = if (isResolved) NotificationType.RESOLVED else NotificationType.STATUS_UPDATE,
                reportId = reportId,
                userId = currentReport.userId,
                createdAt = System.currentTimeMillis()
            )
            val muniAlert = AppNotification(
                id = "admin_notif_${System.currentTimeMillis()}_${(100..999).random()}",
                title = if (isResolved) "✅ Resolved: ${currentReport.title}" else "🔄 Updated: ${currentReport.title}",
                message = "Status updated to $statusLabel by Officer ${_currentUser.value.name}.",
                time = "Just now",
                isRead = false,
                type = if (isResolved) NotificationType.RESOLVED else NotificationType.STATUS_UPDATE,
                reportId = reportId,
                userId = "municipal_officer_001",
                createdAt = System.currentTimeMillis()
            )
            _notifications.value = listOf(localNotif, muniAlert) + _notifications.value

            updatedTimelineData = updatedTimeline.map { step ->
                mapOf(
                    "status" to step.status.name,
                    "label" to step.label,
                    "date" to step.date,
                    "isCompleted" to step.isCompleted
                )
            }

            // Update Firestore document
            firestore.collection("reports").document(reportId).update(
                mapOf<String, Any?>(
                    "status" to newStatus.name,
                    "timelineSteps" to updatedTimelineData
                )
            ).await()

            val updated: Report = currentReport.copy(status = newStatus, timelineSteps = updatedTimeline)
            _reports.value = _reports.value.map { if (it.id == reportId) updated else it }
            calculateAndEmitStats(_reports.value)

            // Save notification in Firestore for remote sync
            createNotificationInFirestore(
                uid = currentReport.userId,
                title = notifTitle,
                message = notifMessage,
                type = if (isResolved) "RESOLVED" else "STATUS_UPDATE",
                reportId = reportId
            )

            if (isResolved && currentReport.userId.isNotBlank()) {
                updateUserStatsInFirestore(currentReport.userId)
            }
            _isBackendConnected.value = true
            return@withContext Result.success(updated)
        } catch (e: Exception) {
            Log.w(TAG, "updateReportStatus failed: ${e.message}")
            val current: Report? = _reports.value.find { it.id == reportId }
            if (current != null) {
                val updated: Report = current.copy(status = newStatus)
                _reports.value = _reports.value.map { if (it.id == reportId) updated else it }
                calculateAndEmitStats(_reports.value)
                return@withContext Result.success(updated)
            }
            return@withContext Result.failure(e)
        }
    }

    suspend fun deleteReport(reportId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("reports").document(reportId).delete().await()
            _reports.value = _reports.value.filter { it.id != reportId }
            calculateAndEmitStats(_reports.value)
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "deleteReport failed: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    // ── Categories ────────────────────────────────────────────────────────────
    suspend fun fetchCategories(): List<Category> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("categories").get().await()
            val cats: List<Category> = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                try {
                    Category(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        description = data["description"] as? String ?: "",
                        iconName = data["iconName"] as? String ?: ""
                    )
                } catch (e: Exception) { null }
            }
            if (cats.isNotEmpty()) {
                _categories.value = cats
                return@withContext cats
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchCategories failed: ${e.message}")
        }
        return@withContext _categories.value // fallback to MockData
    }

    // ── Notifications ─────────────────────────────────────────────────────────
    suspend fun fetchNotifications(): List<AppNotification> = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        val isMunicipal = user.role.equals("Municipal", ignoreCase = true) || user.role.equals("Admin", ignoreCase = true)
        val defaultNotifs = if (isMunicipal) emptyList() else MockData.mockNotifications
        val uid: String = if (isMunicipal) user.id else (firebaseAuth.currentUser?.uid ?: user.id)

        try {
            // Fetch without composite orderBy("createdAt") to prevent index precondition errors in Firestore
            val snapshot = if (isMunicipal) {
                firestore.collection("notifications").limit(50).get().await()
            } else if (uid.isNotBlank()) {
                firestore.collection("notifications").whereEqualTo("userId", uid).limit(50).get().await()
            } else null

            val notifs: List<AppNotification> = snapshot?.documents?.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                try {
                    AppNotification(
                        id = doc.id,
                        title = data["title"] as? String ?: "",
                        message = data["message"] as? String ?: "",
                        time = data["time"] as? String ?: "Just now",
                        isRead = data["isRead"] as? Boolean ?: false,
                        type = runCatching {
                            NotificationType.valueOf(data["type"] as? String ?: "GENERAL")
                        }.getOrElse { NotificationType.GENERAL },
                        reportId = data["reportId"] as? String,
                        userId = data["userId"] as? String ?: uid,
                        createdAt = data["createdAt"] as? Long ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()

            // Merge remote with current in-memory notifications
            val currentList = _notifications.value
            val baseList = if (currentList.isNotEmpty()) currentList else defaultNotifs
            val combined = (notifs + baseList).distinctBy { it.id }.sortedByDescending { it.createdAt }
            _notifications.value = if (combined.isNotEmpty()) combined else defaultNotifs
            return@withContext _notifications.value
        } catch (e: Exception) {
            Log.w(TAG, "fetchNotifications failed: ${e.message}")
            if (_notifications.value.isEmpty()) {
                _notifications.value = defaultNotifs
            }
            return@withContext _notifications.value
        }
    }

    suspend fun markNotificationRead(id: String) = withContext(Dispatchers.IO) {
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        try {
            firestore.collection("notifications").document(id).update("isRead", true).await()
        } catch (e: Exception) {
            Log.w(TAG, "markNotificationRead failed: ${e.message}")
        }
    }

    suspend fun markAllNotificationsRead() = withContext(Dispatchers.IO) {
        val uid: String = firebaseAuth.currentUser?.uid ?: _currentUser.value.id
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        try {
            val unread = firestore.collection("notifications")
                .whereEqualTo("userId", uid)
                .whereEqualTo("isRead", false)
                .get().await()
            val batch = firestore.batch()
            unread.documents.forEach { doc -> batch.update(doc.reference, "isRead", true) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w(TAG, "markAllNotificationsRead failed: ${e.message}")
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    private fun calculateAndEmitStats(reportList: List<Report>) {
        val submitted: Int = reportList.size
        val inProgress: Int = reportList.count {
            it.status == ReportStatus.IN_PROGRESS ||
            it.status == ReportStatus.UNDER_REVIEW ||
            it.status == ReportStatus.ASSIGNED
        }
        val resolved: Int = reportList.count { it.status == ReportStatus.RESOLVED }
        _impactStats.value = ImpactStats(
            reportsSubmitted = submitted,
            inProgress = inProgress,
            resolved = resolved
        )
    }

    suspend fun fetchStats(): ImpactStats = withContext(Dispatchers.IO) {
        calculateAndEmitStats(_reports.value)
        return@withContext _impactStats.value
    }

    // ── Auth ──────────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()

        // 1. Check for the dedicated Municipal Officer dummy account
        if (cleanEmail == "municipal@fixmycity.org") {
            if (password == "admin123" || password == "municipal123" || password == "admin") {
                val adminUser = MockData.municipalOfficer
                _currentUser.value = adminUser
                _isBackendConnected.value = true
                try {
                    if (firebaseAuth.currentUser == null) {
                        firebaseAuth.signInAnonymously().await()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Municipal anonymous auth: ${e.message}")
                }
                _notifications.value = emptyList()
                refreshAll()
                return@withContext Result.success(adminUser)
            } else {
                return@withContext Result.failure(Exception("Incorrect password for Municipal Official account."))
            }
        }

        try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
            val firebaseUser: FirebaseUser = authResult.user
                ?: return@withContext Result.failure(Exception("Authentication failed"))
            val uid: String = firebaseUser.uid

            val userDoc = firestore.collection("users").document(uid).get().await()
            val user: User = if (userDoc.exists() && userDoc.data != null) {
                documentToUser(uid, userDoc.data!!)
            } else {
                val newUser = User(
                    id = uid,
                    name = (firebaseUser.displayName
                        ?: cleanEmail.substringBefore("@").capitalizeWords()),
                    email = cleanEmail,
                    phone = ""
                )
                firestore.collection("users").document(uid).set(userToMap(newUser)).await()
                newUser
            }
            _currentUser.value = user
            _isBackendConnected.value = true
            if (_notifications.value.isEmpty() || _notifications.value.all { it.userId == "municipal_officer_001" }) {
                _notifications.value = MockData.mockNotifications
            }
            refreshAll()
            return@withContext Result.success(user)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase login failed: ${e.message}")
            return@withContext Result.failure(Exception(toFriendlyMessage(e)))
        }
    }

    suspend fun signup(
        name: String,
        email: String,
        phone: String,
        password: String
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser: FirebaseUser = authResult.user
                ?: return@withContext Result.failure(Exception("Account creation failed"))
            val uid: String = firebaseUser.uid

            val newUser = User(
                id = uid,
                name = name.trim(),
                email = email.trim().lowercase(),
                phone = phone,
                role = "Citizen",
                reportsSubmitted = 0,
                reportsResolved = 0
            )
            firestore.collection("users").document(uid).set(userToMap(newUser)).await()
            _currentUser.value = newUser
            _isBackendConnected.value = true
            if (_notifications.value.isEmpty() || _notifications.value.all { it.userId == "municipal_officer_001" }) {
                _notifications.value = MockData.mockNotifications
            }
            refreshAll()
            return@withContext Result.success(newUser)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase signup failed: ${e.message}")
            return@withContext Result.failure(Exception(toFriendlyMessage(e)))
        }
    }

    /** Logs in as an anonymous guest using Firebase Anonymous Auth */
    suspend fun loginAsGuest(): Result<User> = withContext(Dispatchers.IO) {
        try {
            val authResult = firebaseAuth.signInAnonymously().await()
            val firebaseUser: FirebaseUser = authResult.user
                ?: return@withContext Result.failure(Exception("Anonymous sign-in failed"))
            val uid: String = firebaseUser.uid

            val guestUser = User(
                id = uid,
                name = "Guest",
                email = "guest@fixmycity.org",
                phone = "",
                role = "Guest"
            )
            firestore.collection("users").document(uid)
                .set(userToMap(guestUser), SetOptions.merge())
                .await()
            _currentUser.value = guestUser
            _isBackendConnected.value = true
            if (_notifications.value.isEmpty() || _notifications.value.all { it.userId == "municipal_officer_001" }) {
                _notifications.value = MockData.mockNotifications
            }
            refreshAll()
            return@withContext Result.success(guestUser)
        } catch (e: Exception) {
            Log.w(TAG, "Guest login failed: ${e.message}")
            _currentUser.value = MockData.guestUser
            if (_notifications.value.isEmpty() || _notifications.value.all { it.userId == "municipal_officer_001" }) {
                _notifications.value = MockData.mockNotifications
            }
            return@withContext Result.success(MockData.guestUser)
        }
    }

    /** Signs in using Google Authentication credentials / ID Token */
    suspend fun loginWithGoogle(
        idToken: String? = null,
        displayName: String? = null,
        email: String? = null,
        photoUrl: String? = null
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            if (!idToken.isNullOrBlank()) {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()
                val firebaseUser: FirebaseUser = authResult.user
                    ?: return@withContext Result.failure(Exception("Google authentication failed"))
                val uid = firebaseUser.uid

                val userDoc = firestore.collection("users").document(uid).get().await()
                val user: User = if (userDoc.exists() && userDoc.data != null) {
                    documentToUser(uid, userDoc.data!!)
                } else {
                    val name = firebaseUser.displayName ?: displayName ?: email?.substringBefore("@")?.capitalizeWords() ?: "Google User"
                    val userEmail = firebaseUser.email ?: email ?: ""
                    val avatar = firebaseUser.photoUrl?.toString() ?: photoUrl
                    val newUser = User(
                        id = uid,
                        name = name,
                        email = userEmail,
                        phone = "",
                        avatarUrl = avatar,
                        role = "Citizen",
                        reportsSubmitted = 0,
                        reportsResolved = 0
                    )
                    firestore.collection("users").document(uid).set(userToMap(newUser)).await()
                    newUser
                }
                _currentUser.value = user
                _isBackendConnected.value = true
                if (_notifications.value.isEmpty() || _notifications.value.all { it.userId == "municipal_officer_001" }) {
                    _notifications.value = MockData.mockNotifications
                }
                refreshAll()
                return@withContext Result.success(user)
            } else {
                // Offline / Direct Account fallback
                val uid = "google_${System.currentTimeMillis()}"
                val name = displayName ?: email?.substringBefore("@")?.capitalizeWords() ?: "Google User"
                val googleUser = User(
                    id = uid,
                    name = name,
                    email = email ?: "user@gmail.com",
                    phone = "",
                    avatarUrl = photoUrl,
                    role = "Citizen",
                    reportsSubmitted = 0,
                    reportsResolved = 0
                )
                _currentUser.value = googleUser
                _isBackendConnected.value = true
                if (_notifications.value.isEmpty() || _notifications.value.all { it.userId == "municipal_officer_001" }) {
                    _notifications.value = MockData.mockNotifications
                }
                return@withContext Result.success(googleUser)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google login failed: ${e.message}")
            // Graceful fallback for local development
            val name = displayName ?: email?.substringBefore("@")?.capitalizeWords() ?: "Google User"
            val fallbackUser = User(
                id = "google_${System.currentTimeMillis()}",
                name = name,
                email = email ?: "user@gmail.com",
                phone = "",
                avatarUrl = photoUrl,
                role = "Citizen"
            )
            _currentUser.value = fallbackUser
            if (_notifications.value.isEmpty() || _notifications.value.all { it.userId == "municipal_officer_001" }) {
                _notifications.value = MockData.mockNotifications
            }
            return@withContext Result.success(fallbackUser)
        }
    }

    suspend fun fetchCurrentUser(): User = withContext(Dispatchers.IO) {
        val uid: String = firebaseAuth.currentUser?.uid ?: return@withContext _currentUser.value
        try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists() && doc.data != null) {
                val user = documentToUser(uid, doc.data!!)
                _currentUser.value = user
                return@withContext user
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchCurrentUser failed: ${e.message}")
        }
        return@withContext _currentUser.value
    }

    suspend fun fetchAllUsers(): List<User> = withContext(Dispatchers.IO) {
        val userMap = mutableMapOf<String, User>()

        // 1. Fetch real users & guests from Firestore "users" collection
        try {
            val snapshot = firestore.collection("users").get().await()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                val u = documentToUser(doc.id, data)
                if (!u.isDummyUser()) {
                    userMap[u.id] = u
                }
            }
            Log.d(TAG, "fetchAllUsers: Found ${userMap.size} users in 'users' collection")
        } catch (e: Exception) {
            Log.w(TAG, "fetchAllUsers from 'users' collection failed: ${e.message}")
        }

        // 2. Cross-reference real users & guests who submitted reports in Firestore
        try {
            val reportsSnapshot = firestore.collection("reports").get().await()
            for (doc in reportsSnapshot.documents) {
                val uid = doc.getString("userId") ?: continue
                val uName = doc.getString("userName")?.takeIf { it.isNotBlank() } ?: "Citizen"
                val dummyUserIds = setOf("user_1", "user_2", "user_3", "user_4", "user_5", "user_6", "user_7", "user_8", "municipal_officer_001")
                val dummyNames = setOf(
                    "Rahul Sharma", "Priya Mehta", "Amit Patil", "Sneha Joshi",
                    "Vikram Deshmukh", "Kavita Nair", "Rohan Kulkarni", "Meera Iyer",
                    "Amit Verma", "Sneha Patel", "Rajesh Kumar", "Officer Rajesh Patil"
                )
                if (uid in dummyUserIds || uName in dummyNames || uid.isBlank()) continue

                val isGuest = uid.startsWith("guest") || uName.contains("guest", ignoreCase = true)
                if (!userMap.containsKey(uid)) {
                    userMap[uid] = User(
                        id = uid,
                        name = if (isGuest) "Guest Citizen" else uName,
                        email = if (uid.contains("@")) uid else if (isGuest) "guest@fixmycity.org" else "user_${uid.take(6)}@fixmycity.org",
                        phone = "",
                        role = if (isGuest) "Guest" else "Citizen",
                        reportsSubmitted = 0,
                        reportsResolved = 0
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAllUsers from 'reports' collection failed: ${e.message}")
        }

        // 3. Compute real-time submitted & resolved report counts for each real user / guest
        val liveReports = _reports.value.filterNot { it.isDummyReport() }
        val resultList = userMap.values.map { user ->
            val submittedCount = liveReports.count { it.userId == user.id }
            val resolvedCount = liveReports.count { it.userId == user.id && it.status == ReportStatus.RESOLVED }
            user.copy(
                reportsSubmitted = maxOf(user.reportsSubmitted, submittedCount),
                reportsResolved = maxOf(user.reportsResolved, resolvedCount)
            )
        }.sortedByDescending { it.reportsSubmitted }

        return@withContext resultList
    }

    suspend fun updateProfile(name: String, phone: String): Result<User> = withContext(Dispatchers.IO) {
        val uid: String = firebaseAuth.currentUser?.uid
            ?: return@withContext Result.failure(Exception("Not logged in"))
        try {
            firestore.collection("users").document(uid).update(
                mapOf<String, Any?>("name" to name.trim(), "phone" to phone)
            ).await()
            val updated = _currentUser.value.copy(name = name.trim(), phone = phone)
            _currentUser.value = updated
            return@withContext Result.success(updated)
        } catch (e: Exception) {
            Log.w(TAG, "updateProfile failed: ${e.message}")
            val updated = _currentUser.value.copy(name = name.trim(), phone = phone)
            _currentUser.value = updated
            return@withContext Result.success(updated)
        }
    }

    fun logout() {
        firebaseAuth.signOut()
        _currentUser.value = MockData.mockUser
        _reports.value = emptyList()
        _notifications.value = emptyList()
        _impactStats.value = ImpactStats(0, 0, 0)
        _isBackendConnected.value = false
    }

    // ── Private Helpers ───────────────────────────────────────────────────────
    private suspend fun createNotificationInFirestore(
        uid: String,
        title: String,
        message: String,
        type: String,
        reportId: String?
    ) {
        try {
            val notifData: HashMap<String, Any?> = hashMapOf(
                "title" to title,
                "message" to message,
                "time" to "Just now",
                "isRead" to false,
                "type" to type,
                "reportId" to reportId,
                "userId" to uid,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("notifications").document().set(notifData).await()
        } catch (e: Exception) {
            Log.w(TAG, "createNotification failed: ${e.message}")
        }
    }

    private suspend fun updateUserStatsInFirestore(uid: String) {
        try {
            val userReports = firestore.collection("reports")
                .whereEqualTo("userId", uid)
                .get().await()
            val total: Int = userReports.size()
            val resolved: Int = userReports.documents.count { doc ->
                doc.getString("status") == "RESOLVED"
            }
            firestore.collection("users").document(uid).update(
                mapOf<String, Any?>("reportsSubmitted" to total, "reportsResolved" to resolved)
            ).await()
            _currentUser.value = _currentUser.value.copy(
                reportsSubmitted = total,
                reportsResolved = resolved
            )
        } catch (e: Exception) {
            Log.w(TAG, "updateUserStats failed: ${e.message}")
        }
    }

    /** Convert raw Firestore data map to Report model */
    private fun documentToReport(docId: String, data: Map<String, Any?>): Report? {
        return try {
            val statusStr: String = data["status"] as? String ?: "SUBMITTED"
            val status: ReportStatus = runCatching { ReportStatus.valueOf(statusStr) }
                .getOrElse { ReportStatus.SUBMITTED }

            @Suppress("UNCHECKED_CAST")
            val stepsRaw: List<Map<String, Any?>> =
                data["timelineSteps"] as? List<Map<String, Any?>> ?: emptyList()
            val timelineSteps: List<TimelineStep> = stepsRaw.mapNotNull { step ->
                val stepStatusStr: String = step["status"] as? String ?: return@mapNotNull null
                val stepStatus: ReportStatus = runCatching { ReportStatus.valueOf(stepStatusStr) }
                    .getOrElse { ReportStatus.SUBMITTED }
                TimelineStep(
                    status = stepStatus,
                    label = step["label"] as? String ?: stepStatusStr,
                    date = step["date"] as? String,
                    isCompleted = step["isCompleted"] as? Boolean ?: false
                )
            }

            Report(
                id = docId,
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                category = data["category"] as? String ?: "",
                location = data["location"] as? String ?: "",
                date = data["date"] as? String ?: "",
                status = status,
                imageUrl = data["imageUrl"] as? String,
                userId = data["userId"] as? String ?: "",
                userName = data["userName"] as? String ?: "Citizen",
                timelineSteps = timelineSteps,
                createdAt = data["createdAt"] as? Long ?: 0L,
                latitude = (data["latitude"] as? Number)?.toDouble(),
                longitude = (data["longitude"] as? Number)?.toDouble()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse report $docId: ${e.message}")
            null
        }
    }

    /** Convert raw Firestore data map to User model */
    private fun documentToUser(uid: String, data: Map<String, Any?>): User {
        return User(
            id = uid,
            name = data["name"] as? String ?: "",
            email = data["email"] as? String ?: "",
            phone = data["phone"] as? String ?: "",
            avatarUrl = data["avatarUrl"] as? String,
            role = data["role"] as? String ?: "Citizen",
            reportsSubmitted = (data["reportsSubmitted"] as? Long)?.toInt() ?: 0,
            reportsResolved = (data["reportsResolved"] as? Long)?.toInt() ?: 0,
            createdAt = data["createdAt"] as? Long ?: System.currentTimeMillis()
        )
    }

    /** Serialize User to a Firestore-compatible map */
    private fun userToMap(user: User): Map<String, Any?> = mapOf(
        "name" to user.name,
        "email" to user.email,
        "phone" to user.phone,
        "avatarUrl" to user.avatarUrl,
        "role" to user.role,
        "reportsSubmitted" to user.reportsSubmitted,
        "reportsResolved" to user.reportsResolved,
        "createdAt" to user.createdAt
    )

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    /** Map common Firebase Auth exceptions to user-friendly messages */
    private fun toFriendlyMessage(e: Exception): String {
        val msg: String = e.message ?: ""
        return when {
            msg.contains("INVALID_LOGIN_CREDENTIALS") ||
            msg.contains("wrong-password") ||
            msg.contains("user-not-found") ->
                "Invalid email or password. Please check your credentials."
            msg.contains("email-already-in-use") ->
                "This email is already registered. Please log in instead."
            msg.contains("weak-password") ->
                "Password is too weak. Please use at least 6 characters."
            msg.contains("network") ->
                "No internet connection. Please check your network and try again."
            else -> msg.ifBlank { "An unexpected error occurred." }
        }
    }

    companion object {
        private const val TAG = "FixMyCityRepo"

        @Volatile
        private var INSTANCE: FixMyCityRepository? = null

        fun getInstance(): FixMyCityRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FixMyCityRepository().also { INSTANCE = it }
            }
        }
    }
}

fun Report.isDummyReport(): Boolean {
    val dummyReportIds = setOf("r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8")
    val dummyUserIds = setOf("user_1", "user_2", "user_3", "user_4", "user_5", "user_6", "user_7", "user_8", "u1", "u2", "u3", "u4", "u5")
    val dummyNames = setOf(
        "Rahul Sharma", "Priya Mehta", "Amit Patil", "Sneha Joshi",
        "Vikram Deshmukh", "Kavita Nair", "Rohan Kulkarni", "Meera Iyer",
        "Amit Verma", "Sneha Patel", "Rajesh Kumar"
    )
    return id in dummyReportIds || userId in dummyUserIds || userName in dummyNames
}

fun User.isDummyUser(): Boolean {
    val dummyUserIds = setOf("user_1", "user_2", "user_3", "user_4", "user_5", "user_6", "user_7", "user_8", "u1", "u2", "u3", "u4", "u5", "municipal_officer_001")
    val dummyNames = setOf(
        "Rahul Sharma", "Priya Mehta", "Amit Patil", "Sneha Joshi",
        "Vikram Deshmukh", "Kavita Nair", "Rohan Kulkarni", "Meera Iyer",
        "Amit Verma", "Sneha Patel", "Rajesh Kumar", "Officer Rajesh Patil"
    )
    return id in dummyUserIds || name in dummyNames || email.equals("municipal@fixmycity.org", ignoreCase = true)
}
