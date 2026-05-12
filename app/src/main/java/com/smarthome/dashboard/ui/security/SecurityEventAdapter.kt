package com.smarthome.dashboard.ui.security

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.models.AlertLevel
import com.smarthome.dashboard.data.models.SecurityEvent
import com.smarthome.dashboard.data.models.SecurityEventType
import com.smarthome.dashboard.databinding.ItemSecurityEventBinding

class SecurityEventAdapter(
    private var events: List<SecurityEvent>,
    private val onAcknowledge: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<SecurityEventAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemSecurityEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSecurityEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        val ctx = holder.itemView.context

        holder.binding.apply {
            tvEventDescription.text = event.description
            tvEventTime.text = event.formattedTime
            tvEventTimeAgo.text = event.timeAgo
            tvEventZone.text = event.zone.name.replace("_", " ")
            tvEventType.text = formatEventType(event.eventType)
            ivEventIcon.setImageResource(getEventIcon(event.eventType))

            // Alert level indicator
            val (colorRes, labelRes) = when (event.alertLevel) {
                AlertLevel.CRITICAL -> Pair(R.color.alert_critical, "CRÍTICO")
                AlertLevel.WARNING -> Pair(R.color.alert_warning, "AVISO")
                AlertLevel.INFO -> Pair(R.color.alert_info, "INFO")
            }
            viewAlertStripe.setBackgroundColor(ContextCompat.getColor(ctx, colorRes))
            tvAlertLevel.text = labelRes
            tvAlertLevel.setTextColor(ContextCompat.getColor(ctx, colorRes))

            // Lógica de visualización de Advertencia/Aviso
            val isWarning = event.alertLevel == AlertLevel.WARNING
            viewDivider.visibility = if (isWarning) View.VISIBLE else View.GONE
            
            // Lógica del Switch "Terminado" para Avisos y Críticos
            if (event.alertLevel != AlertLevel.INFO) {
                if (event.isAcknowledged) {
                    switchFinished.visibility = View.GONE
                    tvAcknowledged.visibility = View.VISIBLE
                    root.alpha = 0.6f
                    
                    // Permitir borrar si ya está terminado (click largo o podemos añadir un icono)
                    root.setOnLongClickListener {
                        onDelete(event.id)
                        true
                    }
                } else {
                    switchFinished.visibility = View.VISIBLE
                    tvAcknowledged.visibility = View.GONE
                    switchFinished.setOnCheckedChangeListener(null)
                    switchFinished.isChecked = false
                    switchFinished.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) onAcknowledge(event.id)
                    }
                    root.alpha = 1.0f
                    root.setOnLongClickListener(null)
                }
            } else {
                switchFinished.visibility = View.GONE
                tvAcknowledged.visibility = if (event.isAcknowledged) View.VISIBLE else View.GONE
                root.alpha = if (event.isAcknowledged) 0.6f else 1.0f
                
                // Borrar info también si se desea
                root.setOnLongClickListener {
                    onDelete(event.id)
                    true
                }
            }

            // Highlight unacknowledged critical events
            if (!event.isAcknowledged && event.alertLevel == AlertLevel.CRITICAL) {
                cardEvent.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.card_critical_light))
            } else {
                cardEvent.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
            }
        }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<SecurityEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }

    private fun formatEventType(type: SecurityEventType): String = when (type) {
        SecurityEventType.DOOR_OPENED -> "Puerta Abierta"
        SecurityEventType.DOOR_CLOSED -> "Puerta Cerrada"
        SecurityEventType.MOTION_DETECTED -> "Movimiento Detectado"
        SecurityEventType.ALARM_TRIGGERED -> "Alarma Activada"
        SecurityEventType.CAMERA_ACTIVATED -> "Cámara Activada"
        SecurityEventType.UNAUTHORIZED_ACCESS -> "Acceso No Autorizado"
        SecurityEventType.WINDOW_OPENED -> "Ventana Abierta"
        SecurityEventType.WINDOW_CLOSED -> "Ventana Cerrada"
    }

    private fun getEventIcon(type: SecurityEventType): Int = when (type) {
        SecurityEventType.DOOR_OPENED, SecurityEventType.DOOR_CLOSED -> R.drawable.ic_door
        SecurityEventType.MOTION_DETECTED -> R.drawable.ic_motion
        SecurityEventType.ALARM_TRIGGERED -> R.drawable.ic_alarm
        SecurityEventType.CAMERA_ACTIVATED -> R.drawable.ic_camera
        SecurityEventType.UNAUTHORIZED_ACCESS -> R.drawable.ic_shield_alert
        SecurityEventType.WINDOW_OPENED, SecurityEventType.WINDOW_CLOSED -> R.drawable.ic_window
    }
}
