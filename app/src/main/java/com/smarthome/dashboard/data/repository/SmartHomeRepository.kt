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
    private var lastUpdateTimestamp: Long = System.currentTimeMillis()

    init {
        devices = emptyList()
        startGlobalObservation()
        startRealTimeEnergyCalculation()
    }

    private fun startRealTimeEnergyCalculation() {
        repositoryScope.launch {
            while (isActive) {
                delay(5000) // Actualizar cada 5 segundos
                val now = System.currentTimeMillis()
                val elapsedMillis = now - lastUpdateTimestamp
                lastUpdateTimestamp = now

                if (elapsedMillis <= 0) continue

                val currentDevices = devices
                if (currentDevices.isEmpty()) continue

                val updatedDevices = currentDevices.map { device ->
                    if (device.isActive) {
                        // Incrementar minutos de uso (aproximado)
                        val additionalMinutes = (elapsedMillis.toDouble() / 60000.0)
                        device.copy(
                            usageMinutesToday = device.usageMinutesToday + additionalMinutes
                        )
                    } else {
                        device
                    }
                }
                
                if (updatedDevices != currentDevices) {
                    _devices.value = updatedDevices
                }
            }
        }
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
                    usageMinutesToday = (map["minutosUsoHoy"] as? Number)?.toDouble() ?: 0.0,
                    usageMinutesThisWeek = (map["minutosUsoSemana"] as? Number)?.toDouble() ?: 0.0
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
        val currentDevices = devices
        val totalMinutes = currentDevices.sumOf { it.usageMinutesToday }.coerceAtLeast(1.0)
        return currentDevices.map { device ->
            val percentage = (device.usageMinutesToday / totalMinutes * 100.0).toFloat()
            DeviceUsageStat(device, device.usageMinutesToday, percentage, 12)
        }.sortedByDescending { it.totalMinutes }
    }

    fun getHourlyConsumption(): List<HourlyConsumption> {
        val currentTotalKwh = (getCurrentTotalWatts() / 1000.0).coerceAtLeast(0.05)
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        return (0..23).map { hour ->
            // Curva de campana dual: Pico principal a las 20h, Secundario a las 8h
            // Usamos Math.pow para asegurar compatibilidad
            val eveningPeak = Math.exp(-Math.pow(hour - 20.0, 2.0) / 30.0)
            val morningPeak = Math.exp(-Math.pow(hour - 8.0, 2.0) / 20.0)
            val bellFactor = (eveningPeak + 0.4 * morningPeak + 0.1).coerceIn(0.1, 1.5)
            
            val kwh = when {
                hour < currentHour -> {
                    // Simular consumo pasado siguiendo la campana
                    (currentTotalKwh * bellFactor * (0.85 + Random.nextDouble(0.3))).coerceAtLeast(0.02)
                }
                hour == currentHour -> currentTotalKwh
                else -> 0.0 // Futuro
            }
            HourlyConsumption(hour, kwh, kwh * 2.85)
        }
    }

    fun getDailyConsumption(): List<DailyConsumption> {
        val days = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        return days.mapIndexed { index, day ->
            // Incremento en fines de semana (Sáb y Dom)
            val weekendBoost = if (index >= 5) 1.4 else 1.0
            val kwh = (12.0 + Random.nextDouble(4.0)) * weekendBoost
            DailyConsumption(day, kwh, kwh * 2.85)
        }
    }

    fun getMonthlyConsumption(): List<MonthlyConsumption> {
        val months = listOf("Sep", "Oct", "Nov", "Dic", "Ene", "Feb")
        return months.mapIndexed { index, month ->
            // Curva estacional (sinusoide suave)
            val variation = 1.0 + Math.sin(index.toDouble() * 0.7) * 0.25
            val kwh = 320.0 * variation + Random.nextDouble(30.0)
            MonthlyConsumption(month, kwh, kwh * 2.85)
        }
    }

    fun getCurrentTotalWatts(): Double = devices.filter { it.isActive }.sumOf { it.powerConsumptionWatts }

    /**
     * Calcula el consumo acumulado real basándose en el tiempo encendido.
     * Si un dispositivo de 100W está encendido 1 hora = 0.1 kWh.
     * Si está encendido 24 horas = 2.4 kWh.
     */
    fun getTodayKwh(): Double {
        // En una implementación real, esto consultaría la subcolección 'lecturas' en Firestore.
        // Para tiempo real, sumamos el consumo base de las lecturas de hoy más el tiempo activo actual.
        val baseKwh = 0.0 // Aquí vendrían los datos de la DB
        
        return devices.filter { it.isActive }.sumOf { device ->
            // Fórmula: (Watts * Horas) / 1000 = kWh
            // Simulamos que el dispositivo lleva X tiempo encendido para ver la variación
            val hoursActive = device.usageMinutesToday / 60.0
            (device.powerConsumptionWatts * hoursActive) / 1000.0
        }.coerceAtLeast(baseKwh)
    }

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
