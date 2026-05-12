package com.smarthome.dashboard.ui.energy

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.models.DeviceType
import com.smarthome.dashboard.data.models.DeviceUsageStat
import com.smarthome.dashboard.databinding.ItemDeviceUsageBinding

class DeviceUsageAdapter(private var stats: List<DeviceUsageStat>) :
    RecyclerView.Adapter<DeviceUsageAdapter.ViewHolder>() {

    fun updateStats(newStats: List<DeviceUsageStat>) {
        stats = newStats
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemDeviceUsageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = stats[position]
        val ctx = holder.itemView.context

        holder.binding.apply {
            tvDeviceName.text = stat.device.name
            tvUsageTime.text = formatMinutes(stat.totalMinutes)
            tvUsagePercent.text = "%.1f%%".format(stat.percentageOfTotal)
            tvPeakHour.text = "Pico: ${stat.peakHour}:00 h"
            progressUsage.progress = stat.percentageOfTotal.toInt()

            ivDeviceIcon.setImageResource(getDeviceIcon(stat.device.type))

            val energyKwh = (stat.device.maxWatts * stat.totalMinutes) / 60000.0
            tvEnergyUsed.text = "%.2f kWh".format(energyKwh)

            val colorRes = when {
                stat.percentageOfTotal > 25 -> R.color.consumption_high
                stat.percentageOfTotal > 15 -> R.color.consumption_medium
                else -> R.color.consumption_low
            }
            progressUsage.progressTintList =
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes))
        }
    }

    override fun getItemCount() = stats.size

    private fun formatMinutes(minutes: Double): String {
        val totalSecs = (minutes * 60).toInt()
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
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
