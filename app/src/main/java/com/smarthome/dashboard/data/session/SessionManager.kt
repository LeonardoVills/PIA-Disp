package com.smarthome.dashboard.data.session

import android.content.Context
import android.content.SharedPreferences
import com.smarthome.dashboard.data.models.User
import com.smarthome.dashboard.data.models.UserRole

object SessionManager {

    private const val PREF_NAME = "SmartHomeSession"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"
    private const val KEY_SYNC_ID = "sync_id"
    private const val KEY_ADMIN_ID = "admin_id"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(context: Context, user: User) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_USERNAME, user.username)
        editor.putString(KEY_ROLE, user.role.name)
        editor.putString(KEY_SYNC_ID, user.syncId)
        editor.putString(KEY_ADMIN_ID, user.adminId)
        editor.apply()
    }

    fun getActiveUser(context: Context): User? {
        val prefs = getPreferences(context)
        val username = prefs.getString(KEY_USERNAME, null)
        val roleName = prefs.getString(KEY_ROLE, null)
        val syncId = prefs.getString(KEY_SYNC_ID, null)
        val adminId = prefs.getString(KEY_ADMIN_ID, null)

        return if (username != null && roleName != null) {
            User(username, UserRole.valueOf(roleName), syncId, adminId)
        } else {
            null
        }
    }

    fun clearSession(context: Context) {
        val editor = getPreferences(context).edit()
        editor.clear()
        editor.apply()
    }
}
