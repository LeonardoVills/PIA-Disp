package com.smarthome.dashboard.data.repository

import com.smarthome.dashboard.data.models.*
import com.smarthome.dashboard.data.session.SessionManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import kotlin.random.Random

object SmartHomeRepository {

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devicesFlow: StateFlow<List<Device>> = _devices

    var devices: List<Device>
        get() = _devices.value
        set(value) { _devices.value = value }

    private val _securityEvents = MutableStateFlow<List<SecurityEvent>>(emptyList())
    val securityEventsFlow: StateFlow<List<SecurityEvent>> = _securityEvents
    
    private var devicesListener: ListenerRegistration? = null
    private var securityListener: ListenerRegistration? = null
    
    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        devices = emptyList()
        startGlobalObservation()
    }

    private fun startGlobalObservation() {
        securityListener?.remove()
        securityListener = FirebaseRepository.listenEventosSeguridad { list ->
            _securityEvents.value = list.map { map ->
                SecurityEvent(
                    id = map["id"] as? String ?: "",
                    timestamp = (map["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                    eventType = try { SecurityEventType.valueOf(map["tipoEvento"] as? String ?: "INFO") } catch(e: Exception) { SecurityEventType.DOOR_OPENED },
                    zone = try { SecurityZone.valueOf(map["zona"] as? String ?: "LIVING_ROOM") } catch(e: Exception) { SecurityZone.LIVING_ROOM },
                    description = map["descripcion"] as? String ?: "",
                    alertLevel = try { AlertLevel.valueOf(map["nivelAlerta"] as? String ?: "INFO") } catch(e: Exception) { AlertLevel.INFO },
                    isAcknowledged = map["reconocido"] as? Boolean ?: false,
                    imageUrl = map["imagenUrl"] as? String
                )
            }
        }
    }

    fun syncFromFirebase(uid: String) {
        devicesListener?.remove()
        devicesListener = FirebaseRepository.listenDispositivosUsuario(uid) { list ->
            devices = list.map { map ->
                Device(
                    id = map["id"] as? String ?: "",
                    name = map["nombre"] as? String ?: "Dispositivo",
                    type = try { DeviceType.valueOf(map["tipo"] as? String ?: "LIGHT") } catch(e: Exception) { DeviceType.LIGHT },
                    room = map["cuarto"] as? String ?: "General",
                    isActive = map["encendido"] as? Boolean ?: false,
                    powerConsumptionWatts = (map["potenciaWatts"] as? Number)?.toDouble() ?: 0.0,
                    maxWatts = (map["maxWatts"] as? Number)?.toDouble() ?: 100.0,
                    usageMinutesToday = (map["minutosUsoHoy"] as? Number)?.toInt() ?: 0,
                    usageMinutesThisWeek = (map["minutosUsoSemana"] as? Number)?.toInt() ?: 0
                )
            }
        }
    }

    fun toggleDevice(deviceId: String): Boolean {
        if (deviceId.isEmpty()) return false
        val currentDevice = devices.find { it.id == deviceId } ?: return false
        val newState = !currentDevice.isActive
        val newWatts = if (newState) currentDevice.maxWatts * Random.nextDouble(0.7, 1.0) else 0.0
        
        repositoryScope.launch(Dispatchers.IO) {
            FirebaseRepository.toggleDispositivo(deviceId, newState, newWatts)
        }
        return newState
    }

    suspend fun addDevice(name: String, type: DeviceType, room: String, ownerUid: String): Result<String> {
        val newDevice = Device(
            id = "", 
            name = name,
            type = type,
            room = room,
            isActive = false,
            powerConsumptionWatts = 0.0,
            maxWatts = when(type) {
                DeviceType.AIR_CONDITIONER -> 1200.0
                DeviceType.REFRIGERATOR -> 200.0
                DeviceType.WASHING_MACHINE -> 500.0
                DeviceType.TV -> 150.0
                else -> 60.0
            }
        )
        
        val result = FirebaseRepository.crearDispositivo(newDevice, ownerUid)
        if (result.isSuccess) {
            val deviceId = result.getOrNull() ?: ""
            createAutoEvent("Se añadió el dispositivo: $name en $room", AlertLevel.INFO, SecurityEventType.DOOR_CLOSED, deviceId)
            repositoryScope.launch {
                delay(120_000); createAutoEvent("Aviso de optimización para $name: Consumo inicial registrado", AlertLevel.WARNING, SecurityEventType.MOTION_DETECTED, deviceId)
            }
            repositoryScope.launch {
                delay(180_000); createAutoEvent("Alerta de sistema: Verificación de seguridad pendiente para $name", AlertLevel.CRITICAL, SecurityEventType.UNAUTHORIZED_ACCESS, deviceId)
            }
        }
        return result
    }

    private suspend fun createAutoEvent(desc: String, level: AlertLevel, type: SecurityEventType, deviceId: String) {
        val event = SecurityEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            eventType = type,
            zone = SecurityZone.LIVING_ROOM,
            description = desc,
            alertLevel = level,
            isAcknowledged = false
        )
        FirebaseRepository.crearEventoSeguridad(event, deviceId)
    }

    suspend fun deleteDevice(deviceId: String, ownerUid: String): Boolean {
        return FirebaseRepository.eliminarDispositivo(deviceId)
    }

    suspend fun deleteSecurityEvent(eventId: String): Boolean {
        return FirebaseRepository.eliminarEventoSeguridad(eventId)
    }

    fun getSecurityEvents(filterLevel: AlertLevel? = null): List<SecurityEvent> {
        val all = _securityEvents.value
        return if (filterLevel == null) all
        else all.filter { it.alertLevel == filterLevel }
    }

    fun getPendingAlerts(): Int = _securityEvents.value.count { !it.isAcknowledged && it.alertLevel != AlertLevel.INFO }

    fun acknowledgeEvent(eventId: String) {
        repositoryScope.launch { FirebaseRepository.reconocerEvento(eventId) }
    }

    fun acknowledgeAll() {
        repositoryScope.launch { FirebaseRepository.reconocerTodosEventos() }
    }

    fun getDeviceUsageStats(): List<DeviceUsageStat> {
        val totalMinutes = devices.sumOf { it.usageMinutesToday }.coerceAtLeast(1)
        return devices.map { device ->
            DeviceUsageStat(device, device.usageMinutesToday, (device.usageMinutesToday.toFloat() / totalMinutes * 100), 20)
        }.sortedByDescending { it.totalMinutes }
    }

    fun getHourlyConsumption(): List<HourlyConsumption> = (0..23).map { hour ->
        val kwh = Random.nextDouble(0.3, 1.8)
        HourlyConsumption(hour, kwh, kwh * 2.85)
    }

    fun getDailyConsumption(): List<DailyConsumption> {
        val days = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        return days.map { DailyConsumption(it, Random.nextDouble(10.0, 15.0), 30.0) }
    }

    fun getMonthlyConsumption(): List<MonthlyConsumption> {
        val months = listOf("Sep", "Oct", "Nov", "Dic", "Ene", "Feb")
        return months.map { MonthlyConsumption(it, 300.0, 800.0) }
    }

    fun getCurrentTotalWatts(): Double = devices.filter { it.isActive }.sumOf { it.powerConsumptionWatts }

    fun getTodayKwh(): Double = getHourlyConsumption().sumOf { it.totalKwh }

    fun getDashboardSummary(): DashboardSummary {
        val allEvents = _securityEvents.value
        val activeDevices = devices.count { it.isActive }
        val todayKwh = getTodayKwh()
        return DashboardSummary(
            activeDevices = activeDevices,
            totalDevices = devices.size,
            currentWatts = getCurrentTotalWatts(),
            todayKwh = todayKwh,
            todayCostMXN = todayKwh * 2.85,
            monthKwh = 350.0,
            monthCostMXN = 350.0 * 2.85,
            pendingSecurityAlerts = getPendingAlerts(),
            lastSecurityEvent = allEvents.firstOrNull()
        )
    }
}
