"""
run_once_setup.py
=================
Ejecuta UNA VEZ para crear usuarios iniciales, categorías
y dispositivos base en Firebase.

  python run_once_setup.py
"""

from firebase_config import db
import firebase_admin
from firebase_admin import auth
from datetime import datetime, timezone


def crear_usuarios():
    print("\n👤 Creando usuarios...")

    # Admin
    try:
        admin = auth.create_user(
            email="admin@smarthome.mx",
            password="Admin2025!",
            display_name="Administrador"
        )
        db.collection("usuarios").document(admin.uid).set({
            "uid":       admin.uid,
            "username":  "admin",
            "email":     "admin@smarthome.mx",
            "role":      "ADMIN",
            "createdAt": datetime.now(timezone.utc),
            "updatedAt": datetime.now(timezone.utc),
            "lastLogin": datetime.now(timezone.utc),
        })
        print(f"  ✅ Admin creado: UID = {admin.uid}")
    except Exception as e:
        print(f"  ⚠️  Admin ya existe o error: {e}")

    # Usuario normal
    try:
        user1 = auth.create_user(
            email="equipo1@smarthome.mx",
            password="Equipo2025!",
            display_name="Equipo 1"
        )
        db.collection("usuarios").document(user1.uid).set({
            "uid":       user1.uid,
            "username":  "equipo1",
            "email":     "equipo1@smarthome.mx",
            "role":      "USER",
            "createdAt": datetime.now(timezone.utc),
            "updatedAt": datetime.now(timezone.utc),
            "lastLogin": datetime.now(timezone.utc),
        })
        print(f"  ✅ User creado: UID = {user1.uid}")
    except Exception as e:
        print(f"  ⚠️  User ya existe o error: {e}")


def crear_categorias():
    print("\n📂 Creando categorías...")
    categorias = [
        {"nombre": "Foco LED",      "icono": "ic_lightbulb"},
        {"nombre": "Televisión",    "icono": "ic_tv"},
        {"nombre": "Clima",         "icono": "ic_ac"},
        {"nombre": "Refrigerador",  "icono": "ic_fridge"},
        {"nombre": "Lavadora",      "icono": "ic_washer"},
        {"nombre": "Cámara",        "icono": "ic_camera"},
        {"nombre": "Sensor Puerta", "icono": "ic_door"},
        {"nombre": "Termostato",    "icono": "ic_thermostat"},
        {"nombre": "Router",        "icono": "ic_router"},
    ]
    for cat in categorias:
        cat["createdAt"] = datetime.now(timezone.utc)
        db.collection("categorias").add(cat)
        print(f"  ✅ Categoría: {cat['nombre']}")


def crear_dispositivos_demo(propietario_uid: str):
    print(f"\n📱 Creando dispositivos demo para UID {propietario_uid}...")
    dispositivos = [
        {"nombre": "Sala - Luz Principal",       "categoria": "Foco LED",   "potenciaWatts": 60,   "maxWatts": 60,   "cuarto": "LIVING_ROOM"},
        {"nombre": "Recámara - Luz",             "categoria": "Foco LED",   "potenciaWatts": 0,    "maxWatts": 40,   "cuarto": "BEDROOM"},
        {"nombre": "Sala - Aire Acondicionado",  "categoria": "Clima",      "potenciaWatts": 1200, "maxWatts": 1500, "cuarto": "LIVING_ROOM"},
        {"nombre": "Sala - Televisión",          "categoria": "Televisión", "potenciaWatts": 150,  "maxWatts": 150,  "cuarto": "LIVING_ROOM"},
        {"nombre": "Cocina - Refrigerador",      "categoria": "Refrigerador","potenciaWatts": 180,  "maxWatts": 200,  "cuarto": "KITCHEN"},
    ]
    for idx, d in enumerate(dispositivos, start=1):
        d.update({
            "encendido":        d["potenciaWatts"] > 0,
            "eficiente":        True,
            "minutosUsoHoy":    0,
            "minutosUsoSemana": 0,
            "consumoTotalWh":   0.0,
            "propietarioUid":   propietario_uid,
            "createdAt":        datetime.now(timezone.utc),
            "updatedAt":        datetime.now(timezone.utc),
        })
        doc_ref = db.collection("dispositivos").document(f"d{idx}")
        doc_ref.set(d)
        print(f"  ✅ {d['nombre']}")


if __name__ == "__main__":
    print("🔥 SmartHome Dashboard — Setup Inicial Firebase\n")
    crear_usuarios()
    crear_categorias()

    # Reemplaza con el UID del admin que se acaba de crear
    # (se imprime arriba al ejecutar)
    ADMIN_UID = input("\n📋 Pega el UID del admin para crear dispositivos demo: ").strip()
    if ADMIN_UID:
        crear_dispositivos_demo(ADMIN_UID)

    print("\n🎉 Setup completo. ¡Ya puedes usar la app!")
