package com.smarthome.dashboard.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smarthome.dashboard.MainActivity
import com.smarthome.dashboard.R

object NotificationHelper {
    private const val CHANNEL_ID = "sync_channel"
    private const val CHANNEL_NAME = "Sincronizacion"
    const val NOTIFICATION_ID = 1001

    fun showSyncRequestNotification(context: Context, requestId: String, requesterName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notificaciones de sincronizacion de SmartHome"
                enableLights(true)
                enableVibration(true)
                setSound(null, null) // Quitar sonido del canal
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("sync_request_id", requestId)
            putExtra("requester_name", requesterName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, requestId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción para Aceptar directamente desde la notificación
        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("sync_request_id", requestId)
            putExtra("sync_action", "ACCEPT")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context, (requestId + "accept").hashCode(), acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_alert) 
            .setContentTitle("⚠️ Solicitud de Conexion")
            .setContentText("$requesterName solicita acceso a tu hogar.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(500, 500)) // Vibracion corta
            .setSound(null) // Quitar sonido explicitamente
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_shield_ok, "ACEPTAR", acceptPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}
