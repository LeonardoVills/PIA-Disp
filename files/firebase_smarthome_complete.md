# 🔥 Firebase Firestore — SmartHome Dashboard
# Arquitecto: Claude | Basado en SmartHomeDashboard (Android/Kotlin) + dispositivos_inteligentes.py

---

## 📐 1. ESTRUCTURA DE FIRESTORE

```
smarthome-db/
│
├── usuarios/
│   └── {uid}/
│       ├── username: string
│       ├── email: string
│       ├── role: "ADMIN" | "USER"
│       ├── passwordHash: string         ← Firebase Auth maneja esto
│       ├── createdAt: timestamp
│       ├── updatedAt: timestamp
│       └── lastLogin: timestamp
│
├── dispositivos/
│   └── {deviceId}/
│       ├── nombre: string
│       ├── categoria: string             ← "Foco LED", "Televisión", etc.
│       ├── tipo: string                  ← DeviceType enum
│       ├── cuarto: string                ← DeviceRoom enum
│       ├── encendido: boolean
│       ├── eficiente: boolean
│       ├── potenciaWatts: number
│       ├── maxWatts: number
│       ├── minutosUsoHoy: number
│       ├── minutosUsosemana: number
│       ├── propietarioUid: string        ← ref a usuarios/
│       ├── createdAt: timestamp
│       └── updatedAt: timestamp
│       │
│       └── lecturas/                     ← subcollection
│           └── {readingId}/
│               ├── timestamp: timestamp
│               ├── wattsConsumed: number
│               ├── periodMinutes: number
│               ├── kWhConsumed: number   ← calculado
│               ├── costoMXN: number      ← kWh * 2.85
│               └── encendido: boolean
│
├── categorias/
│   └── {categoryId}/
│       ├── nombre: string
│       ├── icono: string
│       ├── createdAt: timestamp
│       └── createdBy: string             ← uid
│
├── eventos_seguridad/
│   └── {eventId}/
│       ├── timestamp: timestamp
│       ├── tipoEvento: string            ← SecurityEventType
│       ├── zona: string                  ← SecurityZone
│       ├── descripcion: string
│       ├── nivelAlerta: string           ← "INFO" | "WARNING" | "CRITICAL"
│       ├── reconocido: boolean
│       ├── imagenUrl: string | null
│       ├── dispositivoId: string | null  ← ref a dispositivos/
│       └── createdAt: timestamp
│
└── sesiones/
    └── {uid}/
        ├── username: string
        ├── role: string
        ├── loginAt: timestamp
        └── activa: boolean
```

---

## 📄 2. EJEMPLOS JSON DE DOCUMENTOS

### usuario (ADMIN)
```json
{
  "uid": "abc123xyz",
  "username": "admin",
  "email": "admin@smarthome.mx",
  "role": "ADMIN",
  "createdAt": "2025-01-01T00:00:00Z",
  "updatedAt": "2025-03-23T10:00:00Z",
  "lastLogin": "2025-03-23T10:00:00Z"
}
```

### usuario (USER normal)
```json
{
  "uid": "user456abc",
  "username": "equipo1",
  "email": "equipo1@smarthome.mx",
  "role": "USER",
  "createdAt": "2025-01-15T00:00:00Z",
  "updatedAt": "2025-03-22T08:00:00Z",
  "lastLogin": "2025-03-22T08:00:00Z"
}
```

### dispositivo
```json
{
  "deviceId": "d1",
  "nombre": "Sala - Luz Principal",
  "categoria": "Foco LED",
  "tipo": "LIGHT",
  "cuarto": "LIVING_ROOM",
  "encendido": true,
  "eficiente": true,
  "potenciaWatts": 60.0,
  "maxWatts": 60.0,
  "minutosUsoHoy": 420,
  "minutosUsoSemana": 1800,
  "propietarioUid": "abc123xyz",
  "createdAt": "2025-01-01T00:00:00Z",
  "updatedAt": "2025-03-23T10:05:00Z"
}
```

### lectura energética (subcollection)
```json
{
  "readingId": "r_1711187100000",
  "timestamp": "2025-03-23T10:05:00Z",
  "wattsConsumed": 60.0,
  "periodMinutes": 60,
  "kWhConsumed": 0.001,
  "costoMXN": 0.00285,
  "encendido": true
}
```

### categoria dinámica
```json
{
  "categoryId": "cat_clima",
  "nombre": "Clima",
  "icono": "ic_ac",
  "createdAt": "2025-01-01T00:00:00Z",
  "createdBy": "abc123xyz"
}
```

### evento de seguridad
```json
{
  "eventId": "ev1",
  "timestamp": "2025-03-23T09:30:00Z",
  "tipoEvento": "MOTION_DETECTED",
  "zona": "GARDEN",
  "descripcion": "Movimiento detectado en jardín",
  "nivelAlerta": "WARNING",
  "reconocido": false,
  "imagenUrl": null,
  "dispositivoId": "d10",
  "createdAt": "2025-03-23T09:30:00Z"
}
```

---

## 🛡️ 3. REGLAS DE SEGURIDAD FIRESTORE

```javascript
// firestore.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // ── Helper functions ──────────────────────────────────────────
    function isAuthenticated() {
      return request.auth != null;
    }

    function isAdmin() {
      return isAuthenticated() &&
        get(/databases/$(database)/documents/usuarios/$(request.auth.uid)).data.role == 'ADMIN';
    }

    function isOwner(uid) {
      return isAuthenticated() && request.auth.uid == uid;
    }

    // ── Usuarios ─────────────────────────────────────────────────
    match /usuarios/{uid} {
      // Cualquier usuario autenticado puede leer su propio perfil
      allow read: if isOwner(uid) || isAdmin();
      // Solo admin puede crear/modificar usuarios
      allow create: if isAdmin();
      allow update: if isOwner(uid) || isAdmin();
      allow delete: if isAdmin();
    }

    // ── Dispositivos ──────────────────────────────────────────────
    match /dispositivos/{deviceId} {
      // Admin lee todos; usuario solo lee los suyos
      allow read: if isAdmin() ||
        (isAuthenticated() &&
         resource.data.propietarioUid == request.auth.uid);

      // Solo admin puede crear/eliminar dispositivos
      allow create: if isAdmin();
      allow delete: if isAdmin();

      // Admin actualiza todo; usuario solo puede cambiar encendido/apagado
      allow update: if isAdmin() ||
        (isAuthenticated() &&
         resource.data.propietarioUid == request.auth.uid &&
         request.resource.data.diff(resource.data).affectedKeys()
           .hasOnly(['encendido', 'updatedAt']));

      // ── Lecturas (subcollection) ─────────────────────────────
      match /lecturas/{readingId} {
        allow read: if isAdmin() ||
          (isAuthenticated() &&
           get(/databases/$(database)/documents/dispositivos/$(deviceId))
             .data.propietarioUid == request.auth.uid);
        allow create: if isAuthenticated();
        allow update, delete: if isAdmin();
      }
    }

    // ── Categorías ────────────────────────────────────────────────
    match /categorias/{categoryId} {
      allow read: if isAuthenticated();
      allow write: if isAdmin();
    }

    // ── Eventos de seguridad ──────────────────────────────────────
    match /eventos_seguridad/{eventId} {
      allow read: if isAuthenticated();
      allow create: if isAuthenticated();
      // Solo admin puede reconocer o eliminar
      allow update: if isAdmin() ||
        (isAuthenticated() &&
         request.resource.data.diff(resource.data).affectedKeys()
           .hasOnly(['reconocido']));
      allow delete: if isAdmin();
    }

    // ── Sesiones ──────────────────────────────────────────────────
    match /sesiones/{uid} {
      allow read, write: if isOwner(uid) || isAdmin();
    }
  }
}
```

---

## 🔐 4. FIREBASE AUTHENTICATION — CONFIGURACIÓN

### 4a. Pasos en Firebase Console
```
1. Firebase Console → Authentication → Sign-in method
2. Habilitar: Email/Password
3. Habilitar (opcional): Google Sign-In
4. En Firestore: crear documento en usuarios/ al registrar
```

### 4b. Kotlin — Login Activity (reemplaza el hardcoded)
```kotlin
// build.gradle (app) — agrega estas dependencias
implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
implementation("com.google.firebase:firebase-firestore-ktx:24.10.3")
implementation("com.google.firebase:firebase-bom:32.7.2")

// ──────────────────────────────────────────────────────────────
// LoginActivity.kt — versión Firebase
// ──────────────────────────────────────────────────────────────
package com.smarthome.dashboard.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smarthome.dashboard.MainActivity
import com.smarthome.dashboard.data.models.UserRole
import com.smarthome.dashboard.data.session.SessionManager
import com.smarthome.dashboard.databinding.ActivityLoginBinding
import com.smarthome.dashboard.ui.user.UserActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Si ya hay sesión activa, redirigir
        val currentUser = auth.currentUser
        if (currentUser != null) navigateByRole(currentUser.uid)

        binding.btnLogin.setOnClickListener {
            val email    = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginWithFirebase(email, password)
        }
    }

    private fun loginWithFirebase(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                navigateByRole(uid)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateByRole(uid: String) {
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role") ?: "USER"
                val username = doc.getString("username") ?: ""
                SessionManager.saveSession(this,
                    com.smarthome.dashboard.data.models.User(username, UserRole.valueOf(role)))
                if (role == "ADMIN") startActivity(Intent(this, MainActivity::class.java))
                else startActivity(Intent(this, UserActivity::class.java))
                finish()
            }
    }
}
```

---

## 📱 5. CRUD ANDROID (Kotlin) — COMPLETO

```kotlin
// ──────────────────────────────────────────────────────────────
// FirebaseRepository.kt — CRUD completo para la app Android
// ──────────────────────────────────────────────────────────────
package com.smarthome.dashboard.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.smarthome.dashboard.data.models.*
import kotlinx.coroutines.tasks.await

object FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    // ════════════════════════════════════════════════════════════
    // USUARIOS
    // ════════════════════════════════════════════════════════════

    /** Registra usuario en Auth + Firestore */
    suspend fun crearUsuario(email: String, password: String, username: String, role: UserRole): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user!!.uid
            val userDoc = hashMapOf(
                "uid"       to uid,
                "username"  to username,
                "email"     to email,
                "role"      to role.name,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "updatedAt" to com.google.firebase.Timestamp.now(),
                "lastLogin" to com.google.firebase.Timestamp.now()
            )
            db.collection("usuarios").document(uid).set(userDoc).await()
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lee perfil de usuario */
    suspend fun leerUsuario(uid: String): Map<String, Any>? {
        return try {
            db.collection("usuarios").document(uid).get().await().data
        } catch (e: Exception) { null }
    }

    /** Lista todos los usuarios (solo ADMIN) */
    suspend fun listarUsuarios(): List<Map<String, Any>> {
        return try {
            db.collection("usuarios").get().await().documents.mapNotNull { it.data }
        } catch (e: Exception) { emptyList() }
    }

    /** Actualiza datos del usuario */
    suspend fun actualizarUsuario(uid: String, campos: Map<String, Any>): Boolean {
        return try {
            val data = campos.toMutableMap()
            data["updatedAt"] = com.google.firebase.Timestamp.now()
            db.collection("usuarios").document(uid).update(data).await()
            true
        } catch (e: Exception) { false }
    }

    /** Elimina usuario de Auth + Firestore */
    suspend fun eliminarUsuario(uid: String): Boolean {
        return try {
            db.collection("usuarios").document(uid).delete().await()
            // Nota: eliminar de Auth requiere que el usuario esté autenticado
            // o usar Firebase Admin SDK (backend)
            true
        } catch (e: Exception) { false }
    }

    // ════════════════════════════════════════════════════════════
    // DISPOSITIVOS
    // ════════════════════════════════════════════════════════════

    /** Crea un dispositivo nuevo */
    suspend fun crearDispositivo(device: Device, propietarioUid: String): Result<String> {
        return try {
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
                "createdAt"        to com.google.firebase.Timestamp.now(),
                "updatedAt"        to com.google.firebase.Timestamp.now()
            )
            val ref = db.collection("dispositivos").add(doc).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lee todos los dispositivos del usuario actual */
    suspend fun leerDispositivosUsuario(uid: String): List<Map<String, Any>> {
        return try {
            db.collection("dispositivos")
                .whereEqualTo("propietarioUid", uid)
                .get().await()
                .documents.mapNotNull { it.data?.plus("id" to it.id) }
        } catch (e: Exception) { emptyList() }
    }

    /** Lee TODOS los dispositivos (admin) */
    suspend fun leerTodosDispositivos(): List<Map<String, Any>> {
        return try {
            db.collection("dispositivos").get().await()
                .documents.mapNotNull { it.data?.plus("id" to it.id) }
        } catch (e: Exception) { emptyList() }
    }

    /** Cambia estado encendido/apagado */
    suspend fun toggleDispositivo(deviceId: String, encendido: Boolean): Boolean {
        return try {
            db.collection("dispositivos").document(deviceId).update(
                mapOf(
                    "encendido"  to encendido,
                    "updatedAt"  to com.google.firebase.Timestamp.now()
                )
            ).await()
            true
        } catch (e: Exception) { false }
    }

    /** Actualiza dispositivo completo */
    suspend fun actualizarDispositivo(deviceId: String, campos: Map<String, Any>): Boolean {
        return try {
            val data = campos.toMutableMap()
            data["updatedAt"] = com.google.firebase.Timestamp.now()
            db.collection("dispositivos").document(deviceId).update(data).await()
            true
        } catch (e: Exception) { false }
    }

    /** Elimina dispositivo */
    suspend fun eliminarDispositivo(deviceId: String): Boolean {
        return try {
            db.collection("dispositivos").document(deviceId).delete().await()
            true
        } catch (e: Exception) { false }
    }

    // ════════════════════════════════════════════════════════════
    // LECTURAS ENERGÉTICAS (subcollection)
    // ════════════════════════════════════════════════════════════

    /** Guarda lectura de consumo del dispositivo */
    suspend fun guardarLectura(deviceId: String, watts: Double, minutos: Int, encendido: Boolean): Boolean {
        return try {
            val kwh    = (watts * minutos) / 60000.0
            val costo  = kwh * 2.85
            val lectura = hashMapOf(
                "timestamp"      to com.google.firebase.Timestamp.now(),
                "wattsConsumed"  to watts,
                "periodMinutes"  to minutos,
                "kWhConsumed"    to kwh,
                "costoMXN"       to costo,
                "encendido"      to encendido
            )
            db.collection("dispositivos").document(deviceId)
              .collection("lecturas").add(lectura).await()
            true
        } catch (e: Exception) { false }
    }

    /** Lee lecturas de hoy para un dispositivo */
    suspend fun leerLecturasHoy(deviceId: String): List<Map<String, Any>> {
        return try {
            val startOfDay = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            }.time
            db.collection("dispositivos").document(deviceId)
              .collection("lecturas")
              .whereGreaterThan("timestamp",
                  com.google.firebase.Timestamp(startOfDay))
              .orderBy("timestamp", Query.Direction.DESCENDING)
              .get().await()
              .documents.mapNotNull { it.data }
        } catch (e: Exception) { emptyList() }
    }

    // ════════════════════════════════════════════════════════════
    // CATEGORÍAS
    // ════════════════════════════════════════════════════════════

    suspend fun crearCategoria(nombre: String, icono: String, creadoPorUid: String): Boolean {
        return try {
            db.collection("categorias").add(
                hashMapOf(
                    "nombre"    to nombre,
                    "icono"     to icono,
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "createdBy" to creadoPorUid
                )
            ).await()
            true
        } catch (e: Exception) { false }
    }

    suspend fun leerCategorias(): List<Map<String, Any>> {
        return try {
            db.collection("categorias").get().await()
                .documents.mapNotNull { it.data?.plus("id" to it.id) }
        } catch (e: Exception) { emptyList() }
    }

    // ════════════════════════════════════════════════════════════
    // EVENTOS DE SEGURIDAD
    // ════════════════════════════════════════════════════════════

    suspend fun crearEventoSeguridad(evento: SecurityEvent, dispositivoId: String?): Boolean {
        return try {
            db.collection("eventos_seguridad").document(evento.id).set(
                hashMapOf(
                    "timestamp"     to com.google.firebase.Timestamp(java.util.Date(evento.timestamp)),
                    "tipoEvento"    to evento.eventType.name,
                    "zona"          to evento.zone.name,
                    "descripcion"   to evento.description,
                    "nivelAlerta"   to evento.alertLevel.name,
                    "reconocido"    to evento.isAcknowledged,
                    "imagenUrl"     to evento.imageUrl,
                    "dispositivoId" to dispositivoId,
                    "createdAt"     to com.google.firebase.Timestamp.now()
                )
            ).await()
            true
        } catch (e: Exception) { false }
    }

    suspend fun reconocerEvento(eventId: String): Boolean {
        return try {
            db.collection("eventos_seguridad").document(eventId)
              .update("reconocido", true).await()
            true
        } catch (e: Exception) { false }
    }

    suspend fun leerEventosSeguridad(soloNoReconocidos: Boolean = false): List<Map<String, Any>> {
        return try {
            var query: Query = db.collection("eventos_seguridad")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
            if (soloNoReconocidos) query = query.whereEqualTo("reconocido", false)
            query.get().await().documents.mapNotNull { it.data?.plus("id" to it.id) }
        } catch (e: Exception) { emptyList() }
    }

    // ════════════════════════════════════════════════════════════
    // TIEMPO REAL — Listener de dispositivos
    // ════════════════════════════════════════════════════════════

    /** Escucha cambios en tiempo real de todos los dispositivos del usuario */
    fun listenDispositivosUsuario(uid: String, onUpdate: (List<Map<String, Any>>) -> Unit) {
        db.collection("dispositivos")
            .whereEqualTo("propietarioUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val devices = snapshot.documents.mapNotNull {
                    it.data?.plus("id" to it.id)
                }
                onUpdate(devices)
            }
    }
}
```

---

## 🐍 6. PYTHON — SDK Firebase Admin (para dispositivos_inteligentes.py)

```bash
# Instalación
pip install firebase-admin
```

```python
# ──────────────────────────────────────────────────────────────
# firebase_config.py
# ──────────────────────────────────────────────────────────────
# INSTRUCCIONES:
# 1. Ve a Firebase Console → Configuración del proyecto → Cuentas de servicio
# 2. Clic en "Generar nueva clave privada" → descarga el JSON
# 3. Renombra el archivo a: serviceAccountKey.json
# 4. Ponlo en la misma carpeta que este script

import firebase_admin
from firebase_admin import credentials, firestore
from datetime import datetime

# ── Inicializar Firebase ───────────────────────────────────────
cred = credentials.Certificate("serviceAccountKey.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

print("✅ Firebase conectado correctamente")
```

```python
# ──────────────────────────────────────────────────────────────
# firebase_dispositivos.py — CRUD completo de Python a Firestore
# ──────────────────────────────────────────────────────────────
from firebase_config import db
from google.cloud import firestore as gc_firestore
from datetime import datetime, timezone
import time

# ════════════════════════════════════════════════════════════════
# GUARDAR DISPOSITIVO EN FIREBASE
# ════════════════════════════════════════════════════════════════

def guardar_dispositivo(dispositivo, propietario_uid: str) -> str:
    """
    Guarda o actualiza un dispositivo en Firestore.
    Retorna el ID del documento.
    """
    doc_data = {
        "nombre":           dispositivo.nombre,
        "categoria":        dispositivo.categoria,
        "tipo":             dispositivo.categoria,
        "encendido":        dispositivo.encendido,
        "eficiente":        dispositivo.eficiente,
        "potenciaWatts":    float(dispositivo.potencia),
        "maxWatts":         float(dispositivo.potencia),
        "minutosUsoHoy":    int(dispositivo.tiempo_actual() / 60),
        "consumoWh":        round(dispositivo.consumo_actual(), 4),
        "propietarioUid":   propietario_uid,
        "updatedAt":        datetime.now(timezone.utc),
    }

    # Usa el id local como document id para consistencia
    doc_ref = db.collection("dispositivos").document(str(dispositivo.id))

    if not doc_ref.get().exists:
        doc_data["createdAt"] = datetime.now(timezone.utc)
        doc_ref.set(doc_data)
        print(f"✅ Dispositivo {dispositivo.nombre} creado con ID {dispositivo.id}")
    else:
        doc_ref.update(doc_data)
        print(f"🔄 Dispositivo {dispositivo.nombre} actualizado")

    return str(dispositivo.id)


# ════════════════════════════════════════════════════════════════
# LEER DISPOSITIVOS DESDE FIREBASE
# ════════════════════════════════════════════════════════════════

def leer_dispositivos(propietario_uid: str = None) -> list:
    """
    Lee dispositivos desde Firestore.
    Si se pasa uid, filtra por propietario.
    """
    col = db.collection("dispositivos")
    if propietario_uid:
        docs = col.where("propietarioUid", "==", propietario_uid).stream()
    else:
        docs = col.stream()

    dispositivos = []
    for doc in docs:
        data = doc.to_dict()
        data["firebaseId"] = doc.id
        dispositivos.append(data)
        print(f"📦 {data.get('nombre')} | {'🟢' if data.get('encendido') else '🔴'} | {data.get('potenciaWatts')}W")

    return dispositivos


# ════════════════════════════════════════════════════════════════
# ACTUALIZAR ESTADO (encendido/apagado)
# ════════════════════════════════════════════════════════════════

def actualizar_estado(device_id: str, encendido: bool, potencia_watts: float = 0.0):
    """
    Actualiza encendido/apagado y potencia actual en Firestore.
    """
    db.collection("dispositivos").document(str(device_id)).update({
        "encendido":     encendido,
        "potenciaWatts": potencia_watts if encendido else 0.0,
        "updatedAt":     datetime.now(timezone.utc)
    })
    estado = "🟢 ENCENDIDO" if encendido else "🔴 APAGADO"
    print(f"💡 Dispositivo {device_id} → {estado} ({potencia_watts}W)")


# ════════════════════════════════════════════════════════════════
# GUARDAR LECTURA DE CONSUMO ENERGÉTICO
# ════════════════════════════════════════════════════════════════

def guardar_lectura_consumo(device_id: str, watts: float, minutos: int, encendido: bool):
    """
    Guarda una lectura de consumo en la subcollection lecturas/.
    """
    kwh   = (watts * minutos) / 60000.0
    costo = kwh * 2.85  # tarifa CFE MXN

    lectura = {
        "timestamp":     datetime.now(timezone.utc),
        "wattsConsumed": watts,
        "periodMinutes": minutos,
        "kWhConsumed":   round(kwh, 6),
        "costoMXN":      round(costo, 4),
        "encendido":     encendido
    }

    db.collection("dispositivos").document(str(device_id)) \
      .collection("lecturas").add(lectura)

    print(f"⚡ Lectura guardada | {device_id} | {watts}W | {round(kwh, 6)} kWh | ${round(costo,4)} MXN")


# ════════════════════════════════════════════════════════════════
# ELIMINAR DISPOSITIVO
# ════════════════════════════════════════════════════════════════

def eliminar_dispositivo(device_id: str):
    db.collection("dispositivos").document(str(device_id)).delete()
    print(f"🗑️ Dispositivo {device_id} eliminado de Firestore")


# ════════════════════════════════════════════════════════════════
# SINCRONIZACIÓN EN TIEMPO REAL
# ════════════════════════════════════════════════════════════════

def escuchar_dispositivos_realtime(propietario_uid: str):
    """
    Escucha cambios en tiempo real en los dispositivos del usuario.
    Ejecutar en un hilo separado o de forma asíncrona.
    """
    def on_snapshot(col_snapshot, changes, read_time):
        for change in changes:
            doc = change.document.to_dict()
            nombre = doc.get("nombre", "Desconocido")
            estado = "🟢" if doc.get("encendido") else "🔴"
            if change.type.name == "ADDED":
                print(f"➕ Nuevo dispositivo: {nombre} {estado}")
            elif change.type.name == "MODIFIED":
                print(f"✏️  Actualizado: {nombre} {estado} | {doc.get('potenciaWatts')}W")
            elif change.type.name == "REMOVED":
                print(f"🗑️  Eliminado: {nombre}")

    query = db.collection("dispositivos").where("propietarioUid", "==", propietario_uid)
    watcher = query.on_snapshot(on_snapshot)
    print("👂 Escuchando cambios en tiempo real... (Ctrl+C para detener)")
    return watcher  # guarda para poder hacer watcher.unsubscribe()
```

```python
# ──────────────────────────────────────────────────────────────
# integracion_dispositivos_inteligentes.py
# Conecta el código tkinter original con Firebase
# ──────────────────────────────────────────────────────────────
# Agrega esto al final de dispositivos_inteligentes.py
# o impórtalo desde un módulo separado

from firebase_dispositivos import (
    guardar_dispositivo,
    guardar_lectura_consumo,
    actualizar_estado,
    eliminar_dispositivo,
    leer_dispositivos
)

PROPIETARIO_UID = "abc123xyz"  # ← reemplaza con el UID real del usuario

# En la clase App, modifica los métodos así:

def agregar_con_firebase(self):
    """Sobreescribe agregar() para también guardar en Firebase"""
    d = Dispositivo(
        self.next_id,
        self.entry_nombre.get(),
        self.combo_categoria.get(),
        self.var_eficiente.get()
    )
    self.dispositivos.append(d)
    self.next_id += 1
    guardar_dispositivo(d, PROPIETARIO_UID)  # ← Firebase
    self.entry_nombre.delete(0, "end")


def encender_con_firebase(self):
    if self.seleccion_actual:
        d = next(x for x in self.dispositivos if x.id == self.seleccion_actual)
        d.encender()
        actualizar_estado(d.id, True, d.potencia)  # ← Firebase
        guardar_lectura_consumo(d.id, d.potencia, 1, True)  # ← guarda lectura


def apagar_con_firebase(self):
    if self.seleccion_actual:
        d = next(x for x in self.dispositivos if x.id == self.seleccion_actual)
        consumo_final = d.consumo_actual()
        tiempo_total  = d.tiempo_actual()
        d.apagar()
        actualizar_estado(d.id, False, 0.0)  # ← Firebase
        guardar_lectura_consumo(
            d.id,
            d.potencia,
            int(tiempo_total / 60),
            False
        )


def eliminar_con_firebase(self):
    if self.seleccion_actual:
        self.dispositivos = [x for x in self.dispositivos if x.id != self.seleccion_actual]
        eliminar_dispositivo(self.seleccion_actual)  # ← Firebase
        self.seleccion_actual = None


# ── Loop periódico de sincronización ───────────────────────────
def sync_loop_firebase(app, intervalo_segundos=60):
    """Guarda lecturas de consumo de dispositivos encendidos cada N segundos"""
    import threading
    def _sync():
        for d in app.dispositivos:
            if d.encendido:
                guardar_lectura_consumo(d.id, d.potencia, 1, True)
                guardar_dispositivo(d, PROPIETARIO_UID)
        app.root.after(intervalo_segundos * 1000, lambda: sync_loop_firebase(app, intervalo_segundos))
    app.root.after(intervalo_segundos * 1000, _sync)
```

---

## 🚀 7. SETUP INICIAL — PASOS PARA CONECTAR TODO

### Paso 1 — Crear proyecto Firebase
```
1. https://console.firebase.google.com
2. Crear proyecto → "SmartHomeDashboard"
3. Habilitar Google Analytics (opcional)
```

### Paso 2 — Configurar Android
```bash
# En Firebase Console:
# Proyecto → Agregar app → Android
# Package name: com.smarthome.dashboard
# Descargar google-services.json → pegar en app/
```

```groovy
// build.gradle (project level)
classpath 'com.google.gms:google-services:4.4.1'

// build.gradle (app level) — al final
apply plugin: 'com.google.gms.google-services'

// dependencias
implementation platform('com.google.firebase:firebase-bom:32.7.2')
implementation 'com.google.firebase:firebase-auth-ktx'
implementation 'com.google.firebase:firebase-firestore-ktx'
```

### Paso 3 — Configurar Python
```bash
pip install firebase-admin
# Descarga serviceAccountKey.json desde:
# Firebase Console → ⚙️ → Cuentas de servicio → Generar clave privada
```

### Paso 4 — Subir reglas de seguridad
```bash
npm install -g firebase-tools
firebase login
firebase init firestore
# Pega las reglas del Paso 3 en firestore.rules
firebase deploy --only firestore:rules
```

### Paso 5 — Crear usuarios iniciales
```python
# run_once_setup.py — ejecutar UNA VEZ para crear usuarios base
from firebase_config import db
import firebase_admin
from firebase_admin import auth

def crear_usuarios_iniciales():
    # Admin
    admin_user = auth.create_user(
        email="admin@smarthome.mx",
        password="admin123",
        display_name="Admin"
    )
    db.collection("usuarios").document(admin_user.uid).set({
        "uid": admin_user.uid, "username": "admin",
        "email": "admin@smarthome.mx", "role": "ADMIN",
        "createdAt": __import__('datetime').datetime.utcnow()
    })

    # Usuario normal
    user1 = auth.create_user(
        email="equipo1@smarthome.mx",
        password="123456",
        display_name="Equipo 1"
    )
    db.collection("usuarios").document(user1.uid).set({
        "uid": user1.uid, "username": "equipo1",
        "email": "equipo1@smarthome.mx", "role": "USER",
        "createdAt": __import__('datetime').datetime.utcnow()
    })

    # Categorías base
    cats = ["Foco LED", "Televisión", "Clima", "Refrigerador", "Lavadora", "Cámara"]
    for cat in cats:
        db.collection("categorias").add({
            "nombre": cat, "createdAt": __import__('datetime').datetime.utcnow()
        })

    print("✅ Setup inicial completo")

crear_usuarios_iniciales()
```

---

## 📋 8. RESUMEN DE ARCHIVOS A CREAR

| Archivo | Propósito |
|---------|-----------|
| `google-services.json` | Config Android (descargar de Firebase) |
| `serviceAccountKey.json` | Config Python Admin SDK (descargar de Firebase) |
| `firebase_config.py` | Inicialización Python |
| `firebase_dispositivos.py` | CRUD Python completo |
| `integracion_dispositivos_inteligentes.py` | Conecta tkinter con Firebase |
| `run_once_setup.py` | Crea usuarios y categorías base |
| `FirebaseRepository.kt` | CRUD Android completo |
| `LoginActivity.kt` (modificado) | Login con Firebase Auth |
| `firestore.rules` | Reglas de seguridad |

---

*Generado para: SmartHomeDashboard (Android Kotlin) + dispositivos_inteligentes.py (Python/tkinter)*
*Base de datos: Firebase Firestore | Auth: Firebase Authentication*
*Tarifa energética: CFE México (2.85 MXN/kWh)*
