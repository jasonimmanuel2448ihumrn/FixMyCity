package com.fixmycity.app.utils

object ValidationUtils {
    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }

    fun isValidPhone(phone: String): Boolean {
        return phone.replace(" ", "").replace("-", "").replace("+", "").length >= 10
    }

    fun isValidName(name: String): Boolean {
        return name.trim().length >= 2
    }

    fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "Email is required"
            !isValidEmail(email) -> "Please enter a valid email address"
            else -> null
        }
    }

    fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> "Password is required"
            !isValidPassword(password) -> "Password must be at least 8 characters"
            else -> null
        }
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): String? {
        return when {
            confirmPassword.isBlank() -> "Please confirm your password"
            password != confirmPassword -> "Passwords do not match"
            else -> null
        }
    }

    fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "Full name is required"
            !isValidName(name) -> "Please enter your full name"
            else -> null
        }
    }

    fun validatePhone(phone: String): String? {
        return when {
            phone.isBlank() -> "Phone number is required"
            !isValidPhone(phone) -> "Please enter a valid phone number"
            else -> null
        }
    }

    fun validateDescription(desc: String): String? {
        return when {
            desc.isBlank() -> "Description is required"
            desc.trim().length < 10 -> "Description must be at least 10 characters"
            else -> null
        }
    }
}