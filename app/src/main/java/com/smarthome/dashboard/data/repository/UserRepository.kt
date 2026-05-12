package com.smarthome.dashboard.data.repository

import com.smarthome.dashboard.data.models.User
import com.smarthome.dashboard.data.models.UserRole

object UserRepository {

    private val users = mapOf(
        "admin" to Pair("admin123", User("admin", UserRole.ADMIN)),
        "equipo1" to Pair("123", User("equipo1", UserRole.USER))
    )

    fun authenticate(username: String, password: String): User? {
        val userCredentials = users[username]
        return if (userCredentials != null && userCredentials.first == password) {
            userCredentials.second
        } else {
            null
        }
    }
}
