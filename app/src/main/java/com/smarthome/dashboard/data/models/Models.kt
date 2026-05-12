package com.smarthome.dashboard.data.models

import java.util.Date

// ==================== DEVICE MODELS ====================

enum class DeviceType(val displayName: String) {
    LIGHT("Luz"), 
    AIR_CONDITIONER("Aire Acondicionado"), 
    TV("Televisión"), 
    WASHING_MACHINE("Lavadora"), 
    REFRIGERATOR("Refrigerador"),
    SECURITY_CAMERA("Cámara de Seguridad"), 
    DOOR_SENSOR("Sensor de Puerta"), 
    MOTION_SENSOR("Sensor de Movimiento"), 
    THERMOSTAT("Termostato"), 
    ROUTER("Router")
}

data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val room: String, // Cambiado de Enum a String para permitir áreas personalizadas
    var isActive: Boolean = false,
    var powerConsumptionWatts: Double = 0.0,
    var maxWatts: Double = 100.0,
    var usageMinutesToday: Double = 0.0,
    var usageMinutesThisWeek: Double = 0.0
)

// ==================== ENERGY MODELS ====================

data class EnergyReading(
    val deviceId: String,
    val timestamp: Long,
    val wattsConsumed: Double,
    val periodMinutes: Int = 60
) {
    val kWhConsumed: Double get() = (wattsConsumed * periodMinutes) / 60000.0
    val costMXN: Double get() = kWhConsumed * 2.85 
}

data class HourlyConsumption(
    val hour: Int,
    val totalKwh: Double,
    val totalCostMXN: Double
)

data class DailyConsumption(
    val dayLabel: String,
    val totalKwh: Double,
    val totalCostMXN: Double
)

data class MonthlyConsumption(
    val monthLabel: String,
    val totalKwh: Double,
    val totalCostMXN: Double
)

data class DeviceUsageStat(
    val device: Device,
    val totalMinutes: Double,
    val percentageOfTotal: Float,
    val peakHour: Int
)

// ==================== SECURITY MODELS ====================

enum class SecurityEventType {
    DOOR_OPENED, DOOR_CLOSED, MOTION_DETECTED, ALARM_TRIGGERED,
    CAMERA_ACTIVATED, UNAUTHORIZED_ACCESS, WINDOW_OPENED, WINDOW_CLOSED
}

enum class SecurityZone {
    FRONT_DOOR, BACK_DOOR, GARAGE_DOOR, LIVING_ROOM, BEDROOM,
    KITCHEN, GARDEN, HALLWAY
}

enum class AlertLevel {
    INFO, WARNING, CRITICAL
}

data class SecurityEvent(
    val id: String,
    val timestamp: Long,
    val eventType: SecurityEventType,
    val zone: SecurityZone,
    val description: String,
    val alertLevel: AlertLevel,
    val isAcknowledged: Boolean = false,
    val imageUrl: String? = null
) {
    val formattedTime: String
        get() {
            val date = Date(timestamp)
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale("es", "MX"))
            return sdf.format(date)
        }

    val timeAgo: String
        get() {
            val diff = System.currentTimeMillis() - timestamp
            val minutes = diff / 60000
            val hours = minutes / 60
            val days = hours / 24
            return when {
                days > 0 -> "hace ${days}d"
                hours > 0 -> "hace ${hours}h"
                minutes > 0 -> "hace ${minutes}m"
                else -> "ahora mismo"
            }
        }
}

// ==================== DASHBOARD SUMMARY ====================

data class DashboardSummary(
    val activeDevices: Int,
    val totalDevices: Int,
    val currentWatts: Double,
    val todayKwh: Double,
    val todayCostMXN: Double,
    val monthKwh: Double,
    val monthCostMXN: Double,
    val pendingSecurityAlerts: Int,
    val lastSecurityEvent: SecurityEvent?
)
