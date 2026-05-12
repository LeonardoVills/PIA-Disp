package com.smarthome.dashboard.ui.dashboard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.models.AlertLevel
import com.smarthome.dashboard.data.models.DeviceType
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.repository.SmartHomeRepository
import com.smarthome.dashboard.databinding.FragmentDashboardBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 10000) // Actualización visual cada 10s
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader()
        
        // 1. Sincronizar con Firebase nada más entrar
        lifecycleScope.launch {
            FirebaseRepository.currentUid?.let { uid ->
                SmartHomeRepository.syncFromFirebase(uid)
            }
        }

        // 2. Observar cambios en tiempo real desde el Repositorio
        lifecycleScope.launch {
            SmartHomeRepository.devicesFlow.collectLatest {
                updateUI()
            }
        }

        handler.post(refreshRunnable)
    }

    private fun setupHeader() {
        val sdf = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("es", "MX"))
        binding.tvDate.text = sdf.format(Date()).replaceFirstChar { it.uppercase() }
    }

    private fun updateUI() {
        val summary = SmartHomeRepository.getDashboardSummary()

        // Active devices
        binding.tvActiveDevices.text = "${summary.activeDevices}"
        binding.tvTotalDevices.text = "/ ${summary.totalDevices} dispositivos"
        if (summary.totalDevices > 0) {
            binding.progressDevices.progress = (summary.activeDevices * 100 / summary.totalDevices)
        }

        // Current consumption
        val watts = summary.currentWatts
        binding.tvCurrentWatts.text = if (watts >= 1000) "%.1f kW".format(watts / 1000) else "%.0f W".format(watts)
        updateWattsIndicator(watts)

        // Today stats
        binding.tvTodayKwh.text = "%.2f kWh".format(summary.todayKwh)
        binding.tvTodayCost.text = "$%.2f MXN".format(summary.todayCostMXN)

        // Month stats
        binding.tvMonthKwh.text = "%.1f kWh".format(summary.monthKwh)
        binding.tvMonthCost.text = "$%.0f MXN".format(summary.monthCostMXN)

        // Security alerts
        val alerts = summary.pendingSecurityAlerts
        binding.tvPendingAlerts.text = if (alerts == 0) "Sin alertas" else "$alerts alerta${if (alerts > 1) "s" else ""} pendiente${if (alerts > 1) "s" else ""}"
        binding.cardSecurityStatus.setCardBackgroundColor(
            ContextCompat.getColor(requireContext(), if (alerts == 0) R.color.card_safe else R.color.card_alert)
        )
        binding.ivSecurityIcon.setImageResource(if (alerts == 0) R.drawable.ic_shield_ok else R.drawable.ic_shield_alert)

        // Last event
        summary.lastSecurityEvent?.let { event ->
            binding.tvLastEvent.text = event.description
            binding.tvLastEventTime.text = event.timeAgo
            binding.tvLastEventZone.text = event.zone.name.replace("_", " ")
            val colorRes = when (event.alertLevel) {
                AlertLevel.CRITICAL -> R.color.alert_critical
                AlertLevel.WARNING -> R.color.alert_warning
                AlertLevel.INFO -> R.color.alert_info
            }
            binding.viewEventIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))
        }

        setupQuickDevices()
    }

    private fun updateWattsIndicator(watts: Double) {
        val maxExpectedWatts = 3000.0
        val percentage = (watts / maxExpectedWatts * 100).toInt().coerceIn(0, 100)
        binding.progressWatts.progress = percentage
        val colorRes = when {
            percentage < 40 -> R.color.consumption_low
            percentage < 70 -> R.color.consumption_medium
            else -> R.color.consumption_high
        }
        binding.tvWattsStatus.text = when {
            percentage < 40 -> "Consumo Bajo"
            percentage < 70 -> "Consumo Moderado"
            else -> "Consumo Alto"
        }
        binding.tvWattsStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun setupQuickDevices() {
        val devices = SmartHomeRepository.devices
        val lights = devices.filter { it.type == DeviceType.LIGHT && it.isActive }.size
        val acs = devices.filter { it.type == DeviceType.AIR_CONDITIONER && it.isActive }.size
        val tvs = devices.filter { it.type == DeviceType.TV && it.isActive }.size

        binding.tvLightsOn.text = "$lights encendidas"
        binding.tvAcsOn.text = "$acs activos"
        binding.tvTvsOn.text = "$tvs activos"
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
