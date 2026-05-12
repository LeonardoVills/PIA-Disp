package com.smarthome.dashboard.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.smarthome.dashboard.data.models.*
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * FirebaseRepository.kt
 * ─────────────────────
 * CRUD completo para Firestore — SmartHome Dashboard
 */
object FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid
    val currentUserEmail: String? get() = auth.currentUser?.email
    val currentUserPhone: String? get() = auth.currentUser?.phoneNumber

    // ════════════════════════════════════════════════════════════
    // AUTENTICACIÓN
    // ════════════════════════════════════════════════════════════

    /** Inicia sesión con email y contraseña */
    suspend fun iniciarSesion(email: String, password: String): Result<String> = try {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        Result.success(result.user!!.uid)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Inicia sesión con credenciales de teléfono (OTP) */
    suspend fun iniciarSesionConTelefono(credential: PhoneAuthCredential): Result<String> = try {
        val result = auth.signInWithCredential(credential).await()
        val uid = result.user!!.uid
        
        // Verificar si el usuario ya existe en Firestore, si no, crearlo con datos básicos
        val doc = db.collection("usuarios").document(uid).get().await()
        if (!doc.exists()) {
            val syncId = uid.takeLast(4).uppercase()
            db.collection("usuarios").document(uid).set(
                hashMapOf(
                    "uid"       to uid,
                    "syncId"    to syncId,
                    "username"  to (result.user?.phoneNumber ?: "Usuario"),
                    "email"     to (result.user?.email ?: ""),
                    "phone"     to (result.user?.phoneNumber ?: ""),
                    "role"      to UserRole.USER.name,
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now(),
                    "lastLogin" to Timestamp.now()
                )
            ).await()
            
            db.collection("sync_mappings").document(syncId).set(
                mapOf("uid" to uid)
            ).await()
        } else {
            // Actualizar último login
            db.collection("usuarios").document(uid).update("lastLogin", Timestamp.now()).await()
        }

        Result.success(uid)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Cierra sesión */
    fun cerrarSesion() {
        auth.signOut()
    }

    // ════════════════════════════════════════════════════════════
    // USUARIOS
    // ════════════════════════════════════════════════════════════

    /** Verifica si un número de teléfono ya está registrado */
    suspend fun telefonoExiste(phone: String): Boolean = try {
        val query = db.collection("usuarios")
            .whereEqualTo("phone", phone)
            .get().await()
        !query.isEmpty
    } catch (e: Exception) { false }

    /** Registra usuario en Firebase Auth + Firestore */
    suspend fun crearUsuario(
        email: String,
        password: String,
        username: String,
        phone: String,
        role: UserRole,
        adminId: String? = null
    ): Result<String> = try {
        // Verificar si el teléfono ya existe antes de crear en Auth
        if (telefonoExiste(phone)) {
            throw Exception("El número de teléfono ya está registrado.")
        }

        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user!!.uid
        
        // Generar un SyncID de 4 caracteres
        val syncId = uid.takeLast(4).uppercase()

        db.collection("usuarios").document(uid).set(
            hashMapOf(
                "uid"       to uid,
                "syncId"    to syncId,
                "username"  to username,
                "email"     to email,
                "phone"     to phone,
                "role"      to role.name,
                "adminId"   to adminId,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now(),
                "lastLogin" to Timestamp.now()
            )
        ).await()
        
        // Mapear el SyncID al UID real
        db.collection("sync_mappings").document(syncId).set(
            mapOf("uid" to uid)
        ).await()

        Result.success(uid)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Lee datos de usuario por UID y asegura que tenga un SyncID */
    suspend fun leerUsuario(uid: String): Map<String, Any>? = try {
        val doc = db.collection("usuarios").document(uid).get().await()
        val data = doc.data
        
        if (data != null && data["syncId"] == null) {
            // Si el usuario no tiene syncId (usuarios viejos), se lo generamos ahora
            val syncId = uid.takeLast(4).uppercase()
            
            // Actualizar usuario
            db.collection("usuarios").document(uid).update("syncId", syncId).await()
            
            // Crear mapeo
            db.collection("sync_mappings").document(syncId).set(mapOf("uid" to uid)).await()
            
            data.toMutableMap().apply { put("syncId", syncId) }
        } else {
            data
        }
    } catch (e: Exception) { null }

    /** Busca el UID real a partir de un SyncID de 4 caracteres */
    suspend fun getUidFromSyncId(syncId: String): String? = try {
        db.collection("sync_mappings").document(syncId.uppercase()).get().await().getString("uid")
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
            "cuarto"           to device.room, 
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

    /** Lee dispositivos del usuario actual (o de su administrador si es USER) */
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

    /** Elimina un evento de seguridad */
    suspend fun eliminarEventoSeguridad(eventId: String): Boolean = try {
        db.collection("eventos_seguridad").document(eventId).delete().await()
        true
    } catch (e: Exception) { false }

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
