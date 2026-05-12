package com.smarthome.dashboard.data.repository

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smarthome.dashboard.data.models.Device
import com.smarthome.dashboard.data.models.DeviceType
import com.smarthome.dashboard.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EnergyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Obtenemos el UID directamente de Firebase Auth ya que SessionManager no lo guarda
            val uid = FirebaseRepository.currentUid ?: return@withContext Result.failure()

            // 1. Obtener dispositivos actuales de Firebase
            val fbDevices = FirebaseRepository.leerDispositivosUsuario(uid)
            val devices = fbDevices.map { map ->
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

            // 2. Registrar lectura para cada dispositivo
            devices.forEach { device ->
                // Guardamos una lectura de los últimos 60 minutos
                FirebaseRepository.guardarLectura(
                    deviceId = device.id,
                    watts = if (device.isActive) device.powerConsumptionWatts else 0.0,
                    minutos = 60,
                    encendido = device.isActive
                )

                // 3. Actualizar contadores de tiempo si está encendido
                if (device.isActive) {
                    val nuevosCampos = mapOf(
                        "minutosUsoHoy" to (device.usageMinutesToday + 60.0),
                        "minutosUsoSemana" to (device.usageMinutesThisWeek + 60.0)
                    )
                    FirebaseRepository.actualizarDispositivo(device.id, nuevosCampos)
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
