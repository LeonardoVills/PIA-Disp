# 🏠 Hogar Inteligente — Smart Home Dashboard
### Aplicación Android para Android Studio Panda 2025.3.1

---

## 📋 DESCRIPCIÓN

Dashboard completo de domótica con 4 módulos principales:

| Módulo | Función |
|--------|---------|
| **🏠 Inicio** | Resumen general: dispositivos activos, consumo actual, alertas de seguridad |
| **⚡ Energía** | Gráficas de consumo por hora/día/mes, costo CFE, dispositivos más usados |
| **💡 Dispositivos** | Control de 14 dispositivos del hogar con filtro por habitación |
| **🔒 Seguridad** | Log de eventos con niveles crítico/advertencia/info, sistema de confirmación |

---

## 🚀 CÓMO IMPORTAR EN ANDROID STUDIO PANDA

### Paso 1: Descomprimir
```
Descomprime SmartHomeDashboard.zip en una carpeta local
```

### Paso 2: Abrir en Android Studio
```
File → Open → Selecciona la carpeta SmartHomeDashboard
```

### Paso 3: Sincronizar Gradle
```
File → Sync Project with Gradle Files
(O haz clic en el botón "Sync Now" que aparece arriba)
```

### Paso 4: Ejecutar
```
Run → Run 'app'  (Shift+F10)
Selecciona un emulador API 26+ o dispositivo físico
```

---

## 📦 DEPENDENCIAS PRINCIPALES

```gradle
// Gráficas (bar charts energía)
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

// Material Design 3
implementation 'com.google.android.material:material:1.12.0'

// Navigation Component
implementation 'androidx.navigation:navigation-fragment-ktx:2.7.7'

// ViewModel + LiveData
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3'
```

> **IMPORTANTE:** El archivo `settings.gradle` ya incluye el repositorio de JitPack para MPAndroidChart:
> ```gradle
> maven { url 'https://jitpack.io' }
> ```

---

## 🏗️ ESTRUCTURA DEL PROYECTO

```
app/src/main/
├── java/com/smarthome/dashboard/
│   ├── MainActivity.kt                     # Actividad principal + BottomNav
│   ├── data/
│   │   ├── models/Models.kt                # Data classes: Device, Energy, Security
│   │   └── repository/SmartHomeRepository.kt  # Fuente de datos (mock)
│   └── ui/
│       ├── dashboard/DashboardFragment.kt  # Pantalla inicio
│       ├── energy/
│       │   ├── EnergyFragment.kt           # Gráficas de consumo
│       │   └── DeviceUsageAdapter.kt       # Lista de uso por dispositivo
│       ├── devices/
│       │   ├── DevicesFragment.kt          # Grid de dispositivos
│       │   └── DeviceCardAdapter.kt        # Tarjetas con toggle
│       └── security/
│           ├── SecurityFragment.kt         # Log de eventos
│           └── SecurityEventAdapter.kt     # Items del log
└── res/
    ├── layout/                             # XML de pantallas y items
    ├── drawable/                           # 16 iconos vectoriales
    ├── values/colors.xml                   # Paleta de colores
    └── menu/bottom_nav_menu.xml            # Navegación inferior
```

---

## ✨ FUNCIONALIDADES

### 📊 Reporte de Consumo Energético
- **3 vistas**: Por hora (24h), por día (semana), por mes (6 meses)
- **Gráfica de barras** con MPAndroidChart — barra pico resaltada en naranja
- **Cálculo de costo** basado en tarifa CFE (~$2.85 MXN/kWh)
- **Estadísticas**: Total kWh, costo estimado, hora/día pico

### 📱 Reporte de Dispositivos Más Usados
- Lista de dispositivos ordenada por tiempo de uso diario
- Barra de progreso de uso relativo
- Hora pico de uso de cada dispositivo
- Estimación de energía consumida en kWh

### 🔐 Reporte de Seguridad — Log de Eventos
- **15 eventos** precargados con timestamps reales
- **3 niveles de alerta**: Crítico (rojo), Advertencia (amarillo), Info (azul)
- Tipos de evento: Puerta abierta/cerrada, Movimiento, Acceso no autorizado, Alarma, Cámara activada
- **Filtros** por nivel de alerta
- Sistema de **confirmación de alertas** individual y masivo
- Badge en la barra de navegación con conteo de alertas pendientes

### 💡 Control de Dispositivos
- **14 dispositivos** en 6 habitaciones
- Toggle encendido/apagado con animación
- Filtro por habitación (Sala, Recámara, Cocina, Garage, Jardín)
- Indicador de consumo en watts por dispositivo
- Alerta visual para dispositivos de alto consumo (>500W)

---

## 🎨 DISEÑO

| Característica | Valor |
|---------------|-------|
| Tema | Light, azul corporativo |
| Color primario | `#2563EB` (Azul) |
| Color acento | `#F59E0B` (Ámbar) |
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 (Android 15) |

---

## 📝 NOTA SOBRE LOS DATOS

Los datos son **simulados** (mock data) con patrones realistas:
- Consumo horario sigue curva típica de hogar mexicano
- Eventos de seguridad con timestamps calculados hacia atrás desde "ahora"
- Costo basado en tarifa CFE doméstica básica (~$2.85 MXN/kWh)

Para conectar con datos reales, modifica `SmartHomeRepository.kt` para consumir tu API o base de datos local (Room).
# PIA-Disp
