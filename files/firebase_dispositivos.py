"""
firebase_dispositivos.py — CRUD completo Firestore para dispositivos_inteligentes.py
======================================================================================
Conecta la app Python/tkinter con Firebase Firestore.
"""

from firebase_config import db
from datetime import datetime, timezone


# ══════════════════════════════════════════════════════════════════════
# CREAR / ACTUALIZAR DISPOSITIVO
# ══════════════════════════════════════════════════════════════════════

def guardar_dispositivo(dispositivo, propietario_uid: str) -> str:
    """Crea o actualiza un dispositivo en Firestore. Retorna el doc ID."""
    doc_data = {
        "nombre":           dispositivo.nombre,
        "categoria":        dispositivo.categoria,
        "encendido":        dispositivo.encendido,
        "eficiente":        dispositivo.eficiente,
        "potenciaWatts":    float(dispositivo.potencia),
        "maxWatts":         float(dispositivo.potencia),
        "minutosUsoHoy":    int(dispositivo.tiempo_actual() / 60),
        "consumoTotalWh":   round(dispositivo.consumo_actual(), 4),
        "propietarioUid":   propietario_uid,
        "updatedAt":        datetime.now(timezone.utc),
    }

    doc_ref = db.collection("dispositivos").document(str(dispositivo.id))
    if not doc_ref.get().exists:
        doc_data["createdAt"] = datetime.now(timezone.utc)
        doc_ref.set(doc_data)
        print(f"✅ Creado: {dispositivo.nombre} (ID: {dispositivo.id})")
    else:
        doc_ref.update(doc_data)
        print(f"🔄 Actualizado: {dispositivo.nombre}")

    return str(dispositivo.id)


# ══════════════════════════════════════════════════════════════════════
# LEER DISPOSITIVOS
# ══════════════════════════════════════════════════════════════════════

def leer_dispositivos(propietario_uid: str = None) -> list:
    """Lee dispositivos de Firestore. Filtra por uid si se provee."""
    col = db.collection("dispositivos")
    docs = col.where("propietarioUid", "==", propietario_uid).stream() \
              if propietario_uid else col.stream()

    result = []
    for doc in docs:
        data = doc.to_dict()
        data["firebaseId"] = doc.id
        result.append(data)
        estado = "🟢" if data.get("encendido") else "🔴"
        print(f"  {estado} {data.get('nombre')} | {data.get('potenciaWatts')}W")

    return result


# ══════════════════════════════════════════════════════════════════════
# ACTUALIZAR ESTADO (encendido / apagado)
# ══════════════════════════════════════════════════════════════════════

def actualizar_estado(device_id: str, encendido: bool, potencia_watts: float = 0.0):
    """Actualiza encendido/apagado y potencia en Firestore."""
    db.collection("dispositivos").document(str(device_id)).update({
        "encendido":     encendido,
        "potenciaWatts": potencia_watts if encendido else 0.0,
        "updatedAt":     datetime.now(timezone.utc)
    })
    estado = "🟢 ENCENDIDO" if encendido else "🔴 APAGADO"
    print(f"💡 ID {device_id} → {estado} ({potencia_watts}W)")


# ══════════════════════════════════════════════════════════════════════
# GUARDAR LECTURA DE CONSUMO ENERGÉTICO
# ══════════════════════════════════════════════════════════════════════

def guardar_lectura_consumo(device_id: str, watts: float, minutos: int, encendido: bool):
    """Guarda una lectura de energía en la subcollection lecturas/."""
    kwh   = (watts * minutos) / 60000.0
    costo = kwh * 2.85  # CFE tarifa MXN

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

    print(f"⚡ Lectura | ID {device_id} | {watts}W | {round(kwh,6)} kWh | ${round(costo,4)} MXN")


# ══════════════════════════════════════════════════════════════════════
# ELIMINAR DISPOSITIVO
# ══════════════════════════════════════════════════════════════════════

def eliminar_dispositivo(device_id: str):
    """Elimina dispositivo de Firestore."""
    db.collection("dispositivos").document(str(device_id)).delete()
    print(f"🗑️  Dispositivo {device_id} eliminado")


# ══════════════════════════════════════════════════════════════════════
# ESCUCHAR EN TIEMPO REAL
# ══════════════════════════════════════════════════════════════════════

def escuchar_dispositivos_realtime(propietario_uid: str):
    """
    Escucha cambios en tiempo real.
    Devuelve el watcher — llama watcher.unsubscribe() para detenerlo.
    """
    def on_snapshot(col_snapshot, changes, read_time):
        for change in changes:
            data   = change.document.to_dict()
            nombre = data.get("nombre", "Desconocido")
            estado = "🟢" if data.get("encendido") else "🔴"
            tipo   = change.type.name
            if tipo == "ADDED":
                print(f"➕ Nuevo: {nombre} {estado}")
            elif tipo == "MODIFIED":
                print(f"✏️  Cambio: {nombre} {estado} | {data.get('potenciaWatts')}W")
            elif tipo == "REMOVED":
                print(f"🗑️  Eliminado: {nombre}")

    query   = db.collection("dispositivos").where("propietarioUid", "==", propietario_uid)
    watcher = query.on_snapshot(on_snapshot)
    print("👂 Escuchando en tiempo real (watcher.unsubscribe() para detener)...")
    return watcher
