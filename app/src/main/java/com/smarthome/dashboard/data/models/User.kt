package com.smarthome.dashboard.data.models

enum class UserRole {
    ADMIN,
    USER
}

data class User(
    val username: String,
    val role: UserRole,
    val syncId: String? = null,
    val adminId: String? = null // ID del administrador al que pertenece este usuario
)
