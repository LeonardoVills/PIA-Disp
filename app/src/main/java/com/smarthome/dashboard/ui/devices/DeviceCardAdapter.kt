package com.smarthome.dashboard.ui.devices

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.models.Device
import com.smarthome.dashboard.data.models.DeviceType
import com.smarthome.dashboard.databinding.ItemDeviceCardBinding

class DeviceCardAdapter(
    private val devices: MutableList<Device>,
    private val onToggle: (String) -> Unit,
    private val onDelete: (Device) -> Unit // Nueva función para borrar
) : RecyclerView.Adapter<DeviceCardAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemDeviceCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        val ctx = holder.itemView.context

        holder.binding.apply {
            tvDeviceName.text = device.name
            tvDeviceRoom.text = device.room 
            tvDeviceWatts.text = if (device.isActive) "%.0f W".format(device.powerConsumptionWatts) else "Apagado"
            ivDeviceIcon.setImageResource(getDeviceIcon(device.type))

            val bgColor = if (device.isActive) R.color.card_active else R.color.card_inactive
            cardDevice.setCardBackgroundColor(ContextCompat.getColor(ctx, bgColor))

            val iconTint = if (device.isActive) R.color.primary else R.color.text_hint
            ivDeviceIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, iconTint)
            )

            switchDevice.setOnCheckedChangeListener(null) 
            switchDevice.isChecked = device.isActive
            switchDevice.setOnCheckedChangeListener { _, isChecked -> 
                if (isChecked != device.isActive) {
                    onToggle(device.id)
                }
            }

            cardDevice.setOnClickListener { 
                animateCard(holder)
                onToggle(device.id) 
            }

            // Opcion de borrar con click largo
            cardDevice.setOnLongClickListener {
                onDelete(device)
                true
            }

            val isHighConsumer = device.isActive && device.powerConsumptionWatts > 500
            tvEnergyAlert.text = "Alto Consumo"
            tvEnergyAlert.visibility = if (isHighConsumer) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun animateCard(holder: ViewHolder) {
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(holder.binding.cardDevice, "scaleX", 1f, 0.95f),
                ObjectAnimator.ofFloat(holder.binding.cardDevice, "scaleY", 1f, 0.95f)
            )
            duration = 100
        }
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(holder.binding.cardDevice, "scaleX", 0.95f, 1f),
                ObjectAnimator.ofFloat(holder.binding.cardDevice, "scaleY", 0.95f, 1f)
            )
            duration = 100
        }
        scaleDown.start()
        scaleDown.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { scaleUp.start() }
        })
    }

    override fun getItemCount() = devices.size

    fun updateDevices(newDevices: List<Device>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    private fun getDeviceIcon(type: DeviceType): Int = when (type) {
        DeviceType.LIGHT -> R.drawable.ic_lightbulb
        DeviceType.AIR_CONDITIONER -> R.drawable.ic_ac
        DeviceType.TV -> R.drawable.ic_tv
        DeviceType.REFRIGERATOR -> R.drawable.ic_fridge
        DeviceType.WASHING_MACHINE -> R.drawable.ic_washer
        DeviceType.SECURITY_CAMERA -> R.drawable.ic_camera
        DeviceType.DOOR_SENSOR -> R.drawable.ic_door
        DeviceType.MOTION_SENSOR -> R.drawable.ic_motion
        DeviceType.THERMOSTAT -> R.drawable.ic_thermostat
        DeviceType.ROUTER -> R.drawable.ic_router
    }
}
