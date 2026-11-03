package com.fixmycity.app.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.fixmycity.app.R
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.data.model.ReportStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ServerConfig {
    private const val TAG = "ServerConfig"
    private const val PREFS_NAME = "fixmycity_server_prefs"
    private const val KEY_SERVER_HOST = "custom_server_host"
    private const val KEY_SERVER_PORT = "custom_server_port"

    const val DEFAULT_PORT = 8080
    const val DEFAULT_EMULATOR_HOST = "10.0.2.2"
    const val DEFAULT_LAN_HOST = "192.168.0.135"

    /**
     * Determines whether the app is executing inside an Android Virtual Device / Emulator.
     */
    fun isRunningOnEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()

        return fp.startsWith("generic") ||
                fp.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for x86") ||
                manufacturer.contains("genymotion") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                product.contains("sdk_gphone") ||
                product.contains("sdk")
    }

    /**
     * Gets the currently active laptop server host (e.g. "10.0.2.2" or "192.168.0.135").
     */
    fun getServerHost(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SERVER_HOST, null)
        if (!saved.isNullOrBlank()) {
            return saved.trim()
        }
        return if (isRunningOnEmulator()) DEFAULT_EMULATOR_HOST else DEFAULT_LAN_HOST
    }

    /**
     * Saves a user-configured server host IP.
     */
    fun setServerHost(context: Context, host: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_HOST, host.trim()).apply()
    }

    /**
     * Resets the server host to automatic detection.
     */
    fun resetServerHost(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SERVER_HOST).apply()
    }

    /**
     * Gets the server base URL without trailing slash (e.g. "http://10.0.2.2:8080").
     */
    fun getBaseUrl(context: Context): String {
        val host = getServerHost(context)
        return "http://$host:$DEFAULT_PORT"
    }

    /**
     * Returns the stock image filename for a given civic category.
     */
    fun getStockImageForCategory(category: String?): String {
        return when (category?.trim()?.lowercase()) {
            "pothole" -> "stock_pothole.jpg"
            "garbage", "waste", "sanitation" -> "stock_garbage.jpg"
            "streetlight", "street light", "lighting" -> "stock_streetlight.jpg"
            "water leakage", "water", "pipeline" -> "stock_water.jpg"
            "drainage", "sewage" -> "stock_drainage.jpg"
            "road damage", "road", "pavement" -> "stock_road.jpg"
            "public property", "property", "park" -> "stock_public_property.jpg"
            "traffic", "traffic sign", "signal" -> "stock_traffic.jpg"
            else -> "stock_pothole.jpg"
        }
    }

    /**
     * Returns the offline local drawable resource fallback for a category.
     */
    fun getStockDrawableForCategory(category: String?): Int {
        return when (category?.trim()?.lowercase()) {
            "pothole" -> R.drawable.stock_pothole
            "garbage", "waste", "sanitation" -> R.drawable.stock_garbage
            "streetlight", "street light", "lighting" -> R.drawable.stock_streetlight
            "water leakage", "water", "pipeline" -> R.drawable.stock_water
            "drainage", "sewage" -> R.drawable.stock_drainage
            "road damage", "road", "pavement" -> R.drawable.stock_road
            "public property", "property", "park" -> R.drawable.stock_public_property
            "traffic", "traffic sign", "signal" -> R.drawable.stock_traffic
            else -> R.drawable.stock_pothole
        }
    }

    /**
     * Returns the full server URL for a category's stock image on the laptop.
     */
    fun getCategoryImageUrl(context: Context, category: String?): String {
        val stockFile = getStockImageForCategory(category)
        return "${getBaseUrl(context)}/uploads/$stockFile"
    }

    /**
     * Resolves an imageUrl intelligently across emulators, physical phones, and offline state:
     * - If null or blank: returns the laptop server stock image URL for the category.
     * - If local file path (/data/...): checks if file exists; if not, falls back to category stock image.
     * - If contains /uploads/: dynamically rewrites the host to the active laptop server host.
     * - If external HTTP/HTTPS URL: returns as is.
     */
    fun resolveImageUrl(context: Context, rawUrl: String?, category: String?): Any {
        val stockFileName = getStockImageForCategory(category)
        val defaultServerStockUrl = "${getBaseUrl(context)}/uploads/$stockFileName"

        if (rawUrl.isNullOrBlank()) {
            return defaultServerStockUrl
        }

        val trimmed = rawUrl.trim()

        // 1. Local disk path (/data/user/0/... or /storage/...)
        if (trimmed.startsWith("/")) {
            val localFile = File(trimmed)
            if (localFile.exists() && localFile.length() > 0) {
                return localFile
            }
            // File doesn't exist on this device (e.g. report created on another phone), fallback to stock
            return defaultServerStockUrl
        }

        // 2. Relative or laptop server uploads path
        if (trimmed.startsWith("/uploads/")) {
            val filename = trimmed.removePrefix("/uploads/")
            return "${getBaseUrl(context)}/uploads/$filename"
        }

        // 3. Absolute URL containing /uploads/ (e.g. from emulator "http://10.0.2.2:8080/uploads/...")
        if (trimmed.contains("/uploads/")) {
            val filename = trimmed.substringAfter("/uploads/")
            // Dynamically rewrite to current reachable server host
            return "${getBaseUrl(context)}/uploads/$filename"
        }

        // 4. Remote web URL (e.g. Unsplash or Cloud Storage)
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        return defaultServerStockUrl
    }

    /**
     * Uploads an image file to the laptop server.
     * Tries the configured host, then falls back to emulator and LAN hosts.
     * Returns the permanent laptop server URL on success, or null on failure.
     */
    suspend fun uploadImage(context: Context, file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "uploadImage: file does not exist or is empty")
            return@withContext null
        }

        val candidates = buildCandidateEndpoints(context)

        for (endpoint in candidates) {
            try {
                Log.d(TAG, "Attempting upload to $endpoint...")
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "image/jpeg")
                    setRequestProperty("Accept", "application/json")
                }

                conn.outputStream.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val filename = json.optString("filename")
                    if (filename.isNotBlank()) {
                        val activeHost = url.host
                        val finalUrl = "http://$activeHost:$DEFAULT_PORT/uploads/$filename"
                        Log.i(TAG, "Image successfully uploaded to laptop: $finalUrl")
                        return@withContext finalUrl
                    }
                } else {
                    Log.w(TAG, "Upload endpoint $endpoint returned code $responseCode")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upload to $endpoint failed: ${e.message}")
            }
        }

        Log.e(TAG, "All upload endpoints failed. Laptop server might be offline.")
        null
    }

    /**
     * Pings the laptop server's /health endpoint to verify connectivity.
     */
    suspend fun pingServer(context: Context, testHost: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val host = testHost?.trim()?.ifBlank { null } ?: getServerHost(context)
        val endpoint = "http://$host:$DEFAULT_PORT/health"
        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val totalImages = json.optInt("total_images", 0)
                Result.success("Online • $totalImages images stored on PC")
            } else {
                Result.failure(Exception("HTTP error ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildCandidateEndpoints(context: Context, path: String = "upload"): List<String> {
        val list = mutableListOf<String>()
        val configured = getServerHost(context)
        list.add("http://$configured:$DEFAULT_PORT/$path")

        if (isRunningOnEmulator()) {
            if (configured != DEFAULT_EMULATOR_HOST) {
                list.add("http://$DEFAULT_EMULATOR_HOST:$DEFAULT_PORT/$path")
            }
        } else {
            if (configured != DEFAULT_LAN_HOST) {
                list.add("http://$DEFAULT_LAN_HOST:$DEFAULT_PORT/$path")
            }
        }
        list.add("http://127.0.0.1:$DEFAULT_PORT/$path")
        return list.distinct()
    }

    /**
     * Communicates with the laptop AI backend (Ollama RAG) or falls back to on-device analytics.
     */
    suspend fun queryAiChat(
        context: Context,
        query: String,
        userId: String,
        userName: String,
        reports: List<Report>
    ): AiChatResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("message", query)
            put("userId", userId)
            put("userName", userName)
            val reportsArr = JSONArray()
            reports.take(35).forEach { r ->
                val rObj = JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("category", r.category)
                    put("location", r.location)
                    put("status", r.status.name)
                    put("description", r.description)
                }
                reportsArr.put(rObj)
            }
            put("reports", reportsArr)
        }

        val endpoints = buildCandidateEndpoints(context, "api/chat")
        for (ep in endpoints) {
            try {
                Log.d(TAG, "Querying AI endpoint: $ep")
                val url = URL(ep)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3500
                    readTimeout = 18000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                conn.outputStream.use { out ->
                    out.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode in 200..299) {
                    val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respStr)
                    val reply = json.optString("response").trim()
                    if (reply.isNotBlank()) {
                        val source = json.optString("source", "ollama")
                        val model = json.optString("model", "Ollama")
                        val actionType = json.optString("actionType", "NONE")
                        val actionLabel = if (json.has("actionLabel") && !json.isNull("actionLabel")) {
                            json.getString("actionLabel").takeIf { it.isNotBlank() && it != "null" }
                        } else null
                        val actionPayload = if (json.has("actionPayload") && !json.isNull("actionPayload")) {
                            json.getString("actionPayload").takeIf { it.isNotBlank() && it != "null" }
                        } else null

                        Log.i(TAG, "AI response received from $source ($model)")
                        return@withContext AiChatResult(
                            text = reply,
                            source = source,
                            model = model,
                            actionType = actionType,
                            actionLabel = actionLabel,
                            actionPayload = actionPayload
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI request to $ep failed: ${e.message}")
            }
        }

        // On-device fallback when laptop server / Ollama is not reached
        Log.i(TAG, "Using on-device civic intelligence engine fallback")
        return@withContext generateOnDeviceCivicSummary(query, reports, userId, userName)
    }

    private fun generateOnDeviceCivicSummary(
        query: String,
        reports: List<Report>,
        userId: String,
        userName: String
    ): AiChatResult {
        val lower = query.lowercase().trim()

        val isSummaryRequest = listOf(
            "summar", "overview", "breakdown", "stats", "statistics", "how many",
            "all reports", "list reports", "what are the reports", "what are the issues", "what issues", "what problems"
        ).any { lower.contains(it) }

        val isReportIntent = listOf(
            "report", "file", "submit", "lodge", "log", "register", "new issue"
        ).any { lower.contains(it) } && !isSummaryRequest

        // 1. Reporting an Issue (Direct Guidance without Unwanted Summaries)
        if (isReportIntent && !lower.contains("draft") && !lower.contains("write")) {
            val matchedCat = when {
                lower.contains("pothole") -> "Pothole"
                lower.contains("garbage") || lower.contains("trash") || lower.contains("waste") || lower.contains("dump") -> "Garbage"
                lower.contains("streetlight") || lower.contains("light") || lower.contains("lamp") -> "Streetlight"
                lower.contains("water") || lower.contains("leak") || lower.contains("pipe") -> "Water Leakage"
                lower.contains("drain") || lower.contains("sewage") || lower.contains("gutter") -> "Drainage"
                lower.contains("road") || lower.contains("divider") -> "Road Damage"
                lower.contains("property") || lower.contains("park") || lower.contains("bench") -> "Public Property"
                else -> null
            }

            return if (matchedCat != null) {
                AiChatResult(
                    text = "I'm ready to help you report this **$matchedCat** issue! 🚨\n\nTo submit it directly to municipal field engineers:\n1. Tap the **Report $matchedCat Issue** button below.\n2. Take or upload a photo showing the issue clearly.\n3. Confirm your GPS location and submit.\n\nOnce submitted, you will receive real-time updates as field teams inspect and fix it.",
                    source = "on_device",
                    model = "Civic Intelligence",
                    actionType = "REPORT_ISSUE",
                    actionLabel = "Report $matchedCat Issue",
                    actionPayload = matchedCat
                )
            } else {
                AiChatResult(
                    text = "I'm ready to help you log a civic issue! 🛠️\n\nYou can report any neighborhood problem in 3 simple steps:\n1. Tap the **Report an Issue** button below.\n2. Select the category (Pothole, Garbage, Streetlight, Water Leakage, Drainage, Road Damage, Public Property).\n3. Attach a photo and confirm the location.\n\nYour complaint will be immediately dispatched to the appropriate municipal department with real-time status tracking.",
                    source = "on_device",
                    model = "Civic Intelligence",
                    actionType = "REPORT_ISSUE",
                    actionLabel = "Report an Issue",
                    actionPayload = null
                )
            }
        }

        // 2. Area Summarization (e.g. Vashi, Nerul, Belapur, Sector 17, Koparkhairane, etc. - ONLY when asked)
        val areas = listOf("vashi", "belapur", "nerul", "koparkhairane", "kharghar", "airoli", "sanpada", "ghansoli", "seawoods", "panvel", "sector 17", "sector 10", "sector 5", "sector 20", "sector 3", "sector 9", "palm beach", "market")
        val matchedArea = areas.firstOrNull { lower.contains(it) }

        if (isSummaryRequest && matchedArea != null) {
            val areaReports = reports.filter {
                (it.location + " " + it.title).lowercase().contains(matchedArea)
            }
            val areaTitle = matchedArea.replaceFirstChar { it.uppercase() }

            if (areaReports.isEmpty()) {
                return AiChatResult(
                    text = "📍 **Civic Summary for $areaTitle**\n\nCurrently, there are **0 active complaints** logged specifically in $areaTitle.\n\nIf you notice an infrastructure defect (such as a pothole, broken streetlight, or garbage dump), you can be the first to report it!",
                    source = "on_device",
                    model = "Civic Intelligence",
                    actionType = "REPORT_ISSUE",
                    actionLabel = "Report Issue in $areaTitle",
                    actionPayload = null
                )
            }

            val total = areaReports.size
            val resolved = areaReports.count { it.status == ReportStatus.RESOLVED }
            val inProgress = areaReports.count { it.status == ReportStatus.IN_PROGRESS || it.status == ReportStatus.ASSIGNED }
            val underReview = areaReports.count { it.status == ReportStatus.UNDER_REVIEW || it.status == ReportStatus.SUBMITTED }
            val resRate = if (total > 0) ((resolved.toDouble() / total) * 100).toInt().coerceIn(0, 100) else 0

            val bullets = areaReports.take(4).joinToString("\n") { r ->
                val emoji = when (r.status) {
                    ReportStatus.RESOLVED -> "✅"
                    ReportStatus.IN_PROGRESS -> "🛠️"
                    ReportStatus.ASSIGNED -> "👷"
                    ReportStatus.UNDER_REVIEW -> "👀"
                    else -> "⏳"
                }
                "• **#${r.id.uppercase()}** [${r.category}]: \"${r.title}\" ($emoji ${r.status.name.replace('_', ' ')})\n  _${r.location}_"
            }

            val response = "📊 **Live Grievance Summary for $areaTitle**\n\n" +
                    "Found **$total complaints** in this area:\n" +
                    "• **Resolved**: $resolved ($resRate% resolution rate)\n" +
                    "• **In Progress / Assigned**: $inProgress\n" +
                    "• **Under Review**: $underReview\n\n" +
                    "**Notable Reports:**\n$bullets\n\n" +
                    "💡 _Municipal crews inspect $areaTitle regularly. You can report a new issue or view details._"

            return AiChatResult(
                text = response,
                source = "on_device",
                model = "Civic Intelligence",
                actionType = "REPORT_ISSUE",
                actionLabel = "Report New Issue in $areaTitle",
                actionPayload = null
            )
        }

        // 3. Category Summarization (ONLY when asked)
        val categories = listOf("pothole", "garbage", "streetlight", "water leakage", "drainage", "road damage", "public property")
        val matchedCat = categories.firstOrNull { lower.contains(it) }
        if (isSummaryRequest && matchedCat != null) {
            val catReports = reports.filter { it.category.lowercase().contains(matchedCat) }
            val catName = matchedCat.replaceFirstChar { it.uppercase() }
            val total = catReports.size
            val resolved = catReports.count { it.status == ReportStatus.RESOLVED }
            val active = total - resolved

            val bullets = catReports.take(4).joinToString("\n") {
                "• **#${it.id.uppercase()}** in ${it.location} (${it.status.name.replace('_', ' ')})"
            }

            return AiChatResult(
                text = "🚨 **$catName Reports Overview**\n\n" +
                        "Total logged across the city: **$total reports**\n" +
                        "• Active / Ongoing: **$active**\n" +
                        "• Resolved: **$resolved**\n\n" +
                        "**Recent Incidents:**\n${bullets.ifBlank { "No active records" }}\n\n" +
                        "⏱️ **Department SLA**: Standard resolution turnaround is 24–72 hours.",
                source = "on_device",
                model = "Civic Intelligence",
                actionType = "REPORT_ISSUE",
                actionLabel = "Report $catName Issue",
                actionPayload = catName
            )
        }

        // 3. Complaint Tracking
        if (lower.contains("track") || lower.contains("my report") || lower.contains("my complaint") || lower.contains("status")) {
            val userReports = reports.filter { it.userId == userId || it.userId.isNotBlank() }
            if (userReports.isEmpty()) {
                return AiChatResult(
                    text = "You haven't submitted any complaints yet! If you notice an issue around your neighborhood, you can easily submit one right now.",
                    source = "on_device",
                    model = "Civic Intelligence",
                    actionType = "REPORT_ISSUE",
                    actionLabel = "Report Your First Issue"
                )
            }

            val pending = userReports.count { it.status != ReportStatus.RESOLVED }
            val resolved = userReports.count { it.status == ReportStatus.RESOLVED }
            val summary = userReports.take(3).joinToString("\n") { r ->
                val emoji = if (r.status == ReportStatus.RESOLVED) "✅" else "🛠️"
                "• **${r.title}** ($emoji ${r.status.name.replace('_', ' ')})"
            }

            return AiChatResult(
                text = "Here is your complaint summary:\n\n$summary\n\n📊 Total: **${userReports.size} reports** (${resolved} Resolved, ${pending} Active).\n\nMunicipal crews are assigned to ongoing reports.",
                source = "on_device",
                model = "Civic Intelligence",
                actionType = "VIEW_MY_REPORTS",
                actionLabel = "View My Reports",
                actionPayload = null
            )
        }

        // 4. Complaint Drafting
        if (lower.contains("draft") || lower.contains("write") || lower.contains("complain")) {
            val detectedCat = when {
                lower.contains("garbage") -> "Garbage"
                lower.contains("light") -> "Streetlight"
                lower.contains("water") -> "Water Leakage"
                lower.contains("drain") -> "Drainage"
                lower.contains("road") -> "Road Damage"
                else -> "Pothole"
            }

            return AiChatResult(
                text = "📝 **Draft Complaint Prepared for You**:\n\n" +
                        "• **Category**: $detectedCat\n" +
                        "• **Title**: Severe $detectedCat defect obstructing public safety\n" +
                        "• **Description**: Noticeable infrastructure defect observed on the main roadway causing safety hazards and disruption. Requesting municipal inspection and immediate remediation.\n\n" +
                        "Tap below to submit this draft directly!",
                source = "on_device",
                model = "Civic Intelligence",
                actionType = "REPORT_ISSUE",
                actionLabel = "Submit $detectedCat Report",
                actionPayload = detectedCat
            )
        }

        // 5. Emergency numbers
        if (lower.contains("emergency") || lower.contains("helpline") || lower.contains("contact") || lower.contains("number") || lower.contains("call")) {
            return AiChatResult(
                text = "Official 24x7 Municipal & Emergency Helplines:\n\n" +
                        "🏛️ **City Corporation Helpline**: 1916\n" +
                        "🚨 **Police Control**: 100\n" +
                        "🚒 **Fire Brigade**: 101\n" +
                        "🚑 **Ambulance Emergency**: 108\n" +
                        "🌊 **Disaster Management Cell**: 1077\n\n" +
                        "Tap below to dial the city control room directly.",
                source = "on_device",
                model = "Civic Intelligence",
                actionType = "CALL_HELPLINE",
                actionLabel = "Call City Helpline 1916",
                actionPayload = "1916"
            )
        }

        // 6. SLAs
        if (lower.contains("time") || lower.contains("duration") || lower.contains("how long") || lower.contains("sla")) {
            return AiChatResult(
                text = "Municipal Service Level Agreements (SLAs):\n\n" +
                        "• **Water Pipeline Bursts**: 6 – 12 hours (Emergency Priority)\n" +
                        "• **Streetlight Repairs**: 24 – 48 hours\n" +
                        "• **Garbage Disposal**: 12 – 24 hours\n" +
                        "• **Pothole Patching**: 48 – 72 hours\n" +
                        "• **Drainage Clearance**: 24 – 48 hours\n\n" +
                        "You receive an automated in-app notification whenever your report status is updated!",
                source = "on_device",
                model = "Civic Intelligence",
                actionType = "REPORT_ISSUE",
                actionLabel = "File a Complaint"
            )
        }

        // Default
        return AiChatResult(
            text = "Hello $userName! 👋 I am **FixMyCity Civic AI Copilot**.\n\n" +
                    "I can:\n" +
                    "• **Summarize reports in an area** (e.g. _\"Summarize reports in Vashi\"_)\n" +
                    "• **Check specific categories** (e.g. _\"What are open drainage issues?\"_)\n" +
                    "• **Track your complaints** or check turnaround SLAs\n" +
                    "• **Draft a formal complaint** for quick submission\n\n" +
                    "How can I help your neighborhood today?",
            source = "on_device",
            model = "Civic Intelligence",
            actionType = "REPORT_ISSUE",
            actionLabel = "File a Report Now"
        )
    }
}

data class AiChatResult(
    val text: String,
    val source: String = "local",
    val model: String = "Civic AI",
    val actionType: String = "NONE",
    val actionLabel: String? = null,
    val actionPayload: String? = null
)
