package com.smarthome.dashboard.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.smarthome.dashboard.data.models.*
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * FirebaseRepository.kt
 * ─────────────────────
 * CRUD completo para Firestore — SmartHome Dashboard
 * Requiere dependencias en build.gradle:
 *   implementation platform('com.google.firebase:firebase-bom:32.7.2')
 *   implementation 'com.google.firebase:firebase-auth-ktx'
 *   implementation 'com.google.firebase:firebase-firestore-ktx'
 */
object FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid

    // ════════════════════════════════════════════════════════════
    // USUARIOS
    // ════════════════════════════════════════════════════════════

    /** Registra usuario en Firebase Auth + Firestore */
    suspend fun crearUsuario(
        email: String,
        password: String,
        username: String,
        role: UserRole
    ): Result<String> = try {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user!!.uid
        db.collection("usuarios").document(uid).set(
            hashMapOf(
                "uid"       to uid,
                "username"  to username,
                "email"     to email,
                "role"      to role.name,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now(),
                "lastLogin" to Timestamp.now()
            )
        ).await()
        Result.success(uid)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Lee datos de usuario por UID */
    suspend fun leerUsuario(uid: String): Map<String, Any>? = try {
        db.collection("usuarios").document(uid).get().await().data
    } catch (e: Exception) { null }

    /** Lista todos los usuarios (admin only) */
    suspend fun listarUsuarios(): List<Map<String, Any>> = try {
        db.collection("usuarios").get().await()
            .documents.mapNotNull { it.data?.plus("id" to it.id) }
    } catch (e: Exception) { emptyList() }

    /** Actualiza campos del usuario */
    suspend fun actualizarUsuario(uid: String, campos: Map<String, Any>): Boolean = try {
        db.collection("usuarios").document(uid)
            .update(campos + ("updatedAt" to Timestamp.now())).await()
        true
    } catch (e: Exception) { false }

    /** Elimina usuario de Firestore (Auth requiere Admin SDK) */
    suspend fun eliminarUsuario(uid: String): Boolean = try {
        db.collection("usuarios").document(uid).delete().await()
        true
    } catch (e: Exception) { false }

    // ════════════════════════════════════════════════════════════
    // DISPOSITIVOS
    // ════════════════════════════════════════════════════════════

    /** Crea un dispositivo nuevo */
    suspend fun crearDispositivo(device: Device, propietarioUid: String): Result<String> = try {
        val doc = hashMapOf(
            "nombre"           to device.name,
            "categoria"        to device.type.name,
            "tipo"             to device.type.name,
            "cuarto"           to device.room.name,
            "encendido"        to device.isActive,
            "eficiente"        to true,
            "potenciaWatts"    to device.powerConsumptionWatts,
            "maxWatts"         to device.maxWatts,
            "minutosUsoHoy"    to device.usageMinutesToday,
            "minutosUsoSemana" to device.usageMinutesThisWeek,
            "propietarioUid"   to propietarioUid,
            "createdAt"        to Timestamp.now(),
            "updatedAt"        to Timestamp.now()
        )
        val ref = db.collection("dispositivos").add(doc).await()
        Result.success(ref.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Lee dispositivos del usuario actual */
    suspend fun leerDispositivosUsuario(uid: String): List<Map<String, Any>> = try {
        db.collection("dispositivos")
            .whereEqualTo("propietarioUid", uid)
            .get().await()
            .documents.mapNotNull { it.data?.plus("id" to it.id) }
    } catch (e: Exception) { emptyList() }

    /** Lee TODOS los dispositivos (admin) */
    suspend fun leerTodosDispositivos(): List<Map<String, Any>> = try {
        db.collection("dispositivos").get().await()
            .documents.mapNotNull { it.data?.plus("id" to it.id) }
    } catch (e: Exception) { emptyList() }

    /** Cambia estado encendido/apagado */
    suspend fun toggleDispositivo(deviceId: String, encendido: Boolean, potencia: Double = 0.0): Boolean = try {
        db.collection("dispositivos").document(deviceId).update(
            mapOf(
                "encendido"     to encendido,
                "potenciaWatts" to if (encendido) potencia else 0.0,
                "updatedAt"     to Timestamp.now()
            )
        ).await()
        true
    } catch (e: Exception) { false }

    /** Actualiza dispositivo con cualquier campo */
    suspend fun actualizarDispositivo(deviceId: String, campos: Map<String, Any>): Boolean = try {
        db.collection("dispositivos").document(deviceId)
            .update(campos + ("updatedAt" to Timestamp.now())).await()
        true
    } catch (e: Exception) { false }

    /** Elimina dispositivo */
    suspend fun eliminarDispositivo(deviceId: String): Boolean = try {
        db.collection("dispositivos").document(deviceId).delete().await()
        true
    } catch (e: Exception) { false }

    // ════════════════════════════════════════════════════════════
    // LECTURAS ENERGÉTICAS (subcollection)
    // ════════════════════════════════════════════════════════════

    /** Guarda lectura de consumo */
    suspend fun guardarLectura(
        deviceId: String,
        watts: Double,
        minutos: Int,
        encendido: Boolean
    ): Boolean = try {
        val kwh   = (watts * minutos) / 60000.0
        val costo = kwh * 2.85
        db.collection("dispositivos").document(deviceId)
            .collection("lecturas").add(
                hashMapOf(
                    "timestamp"     to Timestamp.now(),
                    "wattsConsumed" to watts,
                    "periodMinutes" to minutos,
                    "kWhConsumed"   to kwh,
                    "costoMXN"      to costo,
                    "encendido"     to encendido
                )
            ).await()
        true
    } catch (e: Exception) { false }

    /** Lee lecturas de hoy para un dispositivo */
    suspend fun leerLecturasHoy(deviceId: String): List<Map<String, Any>> = try {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.time
        db.collection("dispositivos").document(deviceId)
            .collection("lecturas")
            .whereGreaterThan("timestamp", Timestamp(startOfDay))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get().await()
            .documents.mapNotNull { it.data }
    } catch (e: Exception) { emptyList() }

    // ════════════════════════════════════════════════════════════
    // CATEGORÍAS
    // ════════════════════════════════════════════════════════════

    suspend fun crearCategoria(nombre: String, icono: String): Boolean = try {
        db.collection("categorias").add(
            hashMapOf(
                "nombre"    to nombre,
                "icono"     to icono,
                "createdAt" to Timestamp.now(),
                "createdBy" to (currentUid ?: "")
            )
        ).await()
        true
    } catch (e: Exception) { false }

    suspend fun leerCategorias(): List<Map<String, Any>> = try {
        db.collection("categorias").get().await()
            .documents.mapNotNull { it.data?.plus("id" to it.id) }
    } catch (e: Exception) { emptyList() }

    // ════════════════════════════════════════════════════════════
    // EVENTOS DE SEGURIDAD
    // ════════════════════════════════════════════════════════════

    suspend fun crearEventoSeguridad(evento: SecurityEvent, dispositivoId: String? = null): Boolean = try {
        db.collection("eventos_seguridad").document(evento.id).set(
            hashMapOf(
                "timestamp"     to Timestamp(Date(evento.timestamp)),
                "tipoEvento"    to evento.eventType.name,
                "zona"          to evento.zone.name,
                "descripcion"   to evento.description,
                "nivelAlerta"   to evento.alertLevel.name,
                "reconocido"    to evento.isAcknowledged,
                "imagenUrl"     to evento.imageUrl,
                "dispositivoId" to dispositivoId,
                "createdAt"     to Timestamp.now()
            )
        ).await()
        true
    } catch (e: Exception) { false }

    suspend fun reconocerEvento(eventId: String): Boolean = try {
        db.collection("eventos_seguridad").document(eventId)
            .update("reconocido", true).await()
        true
    } catch (e: Exception) { false }

    suspend fun reconocerTodosEventos(): Boolean = try {
        val batch = db.batch()
        db.collection("eventos_seguridad")
            .whereEqualTo("reconocido", false).get().await()
            .documents.forEach { doc ->
                batch.update(doc.reference, "reconocido", true)
            }
        batch.commit().await()
        true
    } catch (e: Exception) { false }

    suspend fun leerEventosSeguridad(
        soloNoReconocidos: Boolean = false,
        limite: Long = 50
    ): List<Map<String, Any>> = try {
        var query: Query = db.collection("eventos_seguridad")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limite)
        if (soloNoReconocidos) query = query.whereEqualTo("reconocido", false)
        query.get().await().documents.mapNotNull { it.data?.plus("id" to it.id) }
    } catch (e: Exception) { emptyList() }

    // ════════════════════════════════════════════════════════════
    // TIEMPO REAL — Listeners
    // ════════════════════════════════════════════════════════════

    /** Escucha cambios en dispositivos del usuario en tiempo real */
    fun listenDispositivosUsuario(uid: String, onUpdate: (List<Map<String, Any>>) -> Unit) =
        db.collection("dispositivos")
            .whereEqualTo("propietarioUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                onUpdate(snapshot.documents.mapNotNull { it.data?.plus("id" to it.id) })
            }

    /** Escucha eventos de seguridad en tiempo real */
    fun listenEventosSeguridad(onUpdate: (List<Map<String, Any>>) -> Unit) =
        db.collection("eventos_seguridad")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                onUpdate(snapshot.documents.mapNotNull { it.data?.plus("id" to it.id) })
            }
}
