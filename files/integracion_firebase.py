"""
integracion_firebase.py
=======================
Parche para agregar Firebase a dispositivos_inteligentes.py existente.

USO:
  En tu archivo dispositivos_inteligentes.py, al final del __init__ de App agrega:
    from integracion_firebase import FirebaseMixin
    FirebaseMixin.patch(self)

O simplemente copia los métodos de esta clase dentro de tu clase App.
"""

from firebase_dispositivos import (
    guardar_dispositivo,
    guardar_lectura_consumo,
    actualizar_estado,
    eliminar_dispositivo,
    leer_dispositivos,
)

# ── Configura tu UID aquí ─────────────────────────────────────────────
PROPIETARIO_UID = "REEMPLAZA_CON_TU_UID"   # ← Firebase Auth UID del usuario


class FirebaseMixin:
    """Métodos que reemplazan los originales de App con soporte Firebase."""

    @staticmethod
    def patch(app_instance):
        """Reemplaza métodos de App con versiones Firebase-enabled."""
        app = app_instance

        # Guardamos los métodos originales por si los necesitamos
        _original_agregar   = app.agregar
        _original_encender  = app.encender
        _original_apagar    = app.apagar
        _original_eliminar  = app.eliminar

        def agregar_firebase():
            """Agrega dispositivo local + Firestore."""
            _original_agregar()
            if app.dispositivos:
                d = app.dispositivos[-1]
                guardar_dispositivo(d, PROPIETARIO_UID)

        def encender_firebase():
            """Enciende dispositivo local + actualiza Firestore."""
            _original_encender()
            if app.seleccion_actual:
                d = next((x for x in app.dispositivos if x.id == app.seleccion_actual), None)
                if d and d.encendido:
                    actualizar_estado(d.id, True, d.potencia)

        def apagar_firebase():
            """Apaga dispositivo local + guarda lectura + actualiza Firestore."""
            if app.seleccion_actual:
                d = next((x for x in app.dispositivos if x.id == app.seleccion_actual), None)
                if d and d.encendido:
                    minutos_uso = int(d.tiempo_actual() / 60) or 1
                    guardar_lectura_consumo(d.id, d.potencia, minutos_uso, False)
            _original_apagar()
            if app.seleccion_actual:
                d = next((x for x in app.dispositivos if x.id == app.seleccion_actual), None)
                if d:
                    actualizar_estado(d.id, False, 0.0)

        def eliminar_firebase():
            """Elimina dispositivo local + Firestore."""
            if app.seleccion_actual:
                eliminar_dispositivo(app.seleccion_actual)
            _original_eliminar()

        # Parchear métodos
        app.agregar  = agregar_firebase
        app.encender = encender_firebase
        app.apagar   = apagar_firebase
        app.eliminar = eliminar_firebase

        # Iniciar sync periódico cada 60 segundos
        _iniciar_sync_periodico(app, intervalo_segundos=60)

        print("🔥 Firebase integrado correctamente con la app")


def _iniciar_sync_periodico(app, intervalo_segundos=60):
    """Guarda lecturas de todos los dispositivos encendidos cada N segundos."""
    def _sync():
        for d in app.dispositivos:
            if d.encendido:
                guardar_lectura_consumo(d.id, d.potencia, 1, True)
                guardar_dispositivo(d, PROPIETARIO_UID)
        # Re-programar
        app.root.after(intervalo_segundos * 1000, _sync)

    app.root.after(intervalo_segundos * 1000, _sync)
    print(f"🔁 Sync automático cada {intervalo_segundos}s activado")
