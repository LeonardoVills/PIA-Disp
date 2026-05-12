"""
firebase_config.py — Inicialización Firebase Admin SDK
=======================================================
INSTRUCCIONES:
1. Ve a: https://console.firebase.google.com
2. Tu proyecto → ⚙️ Configuración → Cuentas de servicio
3. Clic en "Generar nueva clave privada" → descarga el JSON
4. Renómbralo a: serviceAccountKey.json
5. Colócalo en la misma carpeta que este archivo
"""

import firebase_admin
from firebase_admin import credentials, firestore

# Inicializar solo una vez
if not firebase_admin._apps:
    cred = credentials.Certificate("serviceAccountKey.json")
    firebase_admin.initialize_app(cred)

db = firestore.client()
print("✅ Firebase Firestore conectado correctamente")
