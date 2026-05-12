package com.smarthome.dashboard.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.models.Device
import com.smarthome.dashboard.data.models.DeviceType
import com.smarthome.dashboard.databinding.ItemUserDeviceBinding

class UserDeviceAdapter(
    private var devices: List<Device>,
    private val onToggle: (String) -> Unit,
    private val onDelete: (Device) -> Unit
) : RecyclerView.Adapter<UserDeviceAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemUserDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.binding.apply {
            tvDeviceName.text = device.name
            tvDeviceRoom.text = device.room 
            switchDevice.isChecked = device.isActive

            val icon = when (device.type) {
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
            ivDeviceIcon.setImageResource(icon)

            switchDevice.setOnClickListener {
                onToggle(device.id)
            }

            // Click largo para borrar
            root.setOnLongClickListener {
                onDelete(device)
                true
            }
        }
    }

    override fun getItemCount() = devices.size

    fun updateList(newList: List<Device>) {
        devices = newList
        notifyDataSetChanged()
    }
}
