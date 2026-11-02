package com.fixmycity.app.data.model

data class User(
    val id: String,          // Firebase Auth uid
    val name: String,
    val email: String,
    val phone: String = "",
    val avatarUrl: String? = null,
    val role: String = "Citizen",
    val reportsSubmitted: Int = 0,
    val reportsResolved: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)