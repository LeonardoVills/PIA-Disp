package com.smarthome.dashboard.ui.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.smarthome.dashboard.MainActivity
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.models.AlertLevel
import com.smarthome.dashboard.data.models.SecurityEvent
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.repository.SmartHomeRepository
import com.smarthome.dashboard.data.session.SessionManager
import com.smarthome.dashboard.databinding.FragmentSecurityBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SecurityFragment : Fragment() {

    private var _binding: FragmentSecurityBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SecurityEventAdapter
    private var currentFilter: AlertLevel? = null

    // Lanzador para el escáner de QR/Barras
    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(
        ScanContract()
    ) { result ->
        if (result.contents != null) {
            Toast.makeText(requireContext(), "Correcto", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdminIdHeader()
        setupEventList()
        setupFilters()
        setupAcknowledgeAll()
        setupScannerButton()
        observeSecurityEvents()
    }

    private fun setupScannerButton() {
        binding.btnScanQr.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                setPrompt("Escanea un código de seguridad")
                setCameraId(0) 
                setBeepEnabled(false) // Deshabilitar sonido
                setBarcodeImageEnabled(true)
                setOrientationLocked(false)
            }
            barcodeLauncher.launch(options)
        }
    }

    private fun setupAdminIdHeader() {
        val user = SessionManager.getActiveUser(requireContext())
        val displayId = user?.syncId ?: FirebaseRepository.currentUid?.takeLast(4)?.uppercase() ?: "----"
        
        binding.tvAdminUidLabel.text = "Mi ID: $displayId"
        binding.layoutAdminId.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Sync ID", displayId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "ID corto copiado: $displayId", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEventList() {
        adapter = SecurityEventAdapter(
            emptyList(),
            onAcknowledge = { eventId ->
                SmartHomeRepository.acknowledgeEvent(eventId)
                (activity as? MainActivity)?.updateSecurityBadge()
            },
            onDelete = { eventId ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = SmartHomeRepository.deleteSecurityEvent(eventId)
                    if (success) {
                        Toast.makeText(context, "Evento eliminado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        binding.rvSecurityEvents.layoutManager = LinearLayoutManager(context)
        binding.rvSecurityEvents.adapter = adapter
    }

    private fun observeSecurityEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            SmartHomeRepository.securityEventsFlow.collectLatest { _ ->
                refreshList()
                updateSecuritySummary()
            }
        }
    }

    private fun setupFilters() {
        val filterButtons = mapOf(
            binding.btnAll to null,
            binding.btnCritical to AlertLevel.CRITICAL,
            binding.btnWarning to AlertLevel.WARNING,
            binding.btnInfo to AlertLevel.INFO
        )

        filterButtons.forEach { (button, level) ->
            button.setOnClickListener {
                currentFilter = level
                refreshList()
                filterButtons.forEach { (btn, _) -> 
                    btn.alpha = if (btn == button) 1.0f else 0.5f 
                }
            }
        }
    }

    private fun setupAcknowledgeAll() {
        binding.btnAcknowledgeAll.setOnClickListener {
            SmartHomeRepository.acknowledgeAll()
            (activity as? MainActivity)?.updateSecurityBadge()
        }
    }

    private fun refreshList() {
        val events = SmartHomeRepository.getSecurityEvents(currentFilter)
        adapter.updateEvents(events)
    }

    private fun updateSecuritySummary() {
        if (_binding == null) return
        val all = SmartHomeRepository.getSecurityEvents()
        val critical = all.count { it.alertLevel == AlertLevel.CRITICAL && !it.isAcknowledged }
        val warning = all.count { it.alertLevel == AlertLevel.WARNING && !it.isAcknowledged }
        val pending = SmartHomeRepository.getPendingAlerts()

        binding.tvCriticalCount.text = "$critical"
        binding.tvWarningCount.text = "$warning"
        binding.tvTotalEvents.text = "${all.size} eventos"
        binding.tvPendingCount.text = "$pending pendientes"

        val statusColor = when {
            critical > 0 -> R.color.alert_critical
            warning > 0 -> R.color.alert_warning
            else -> R.color.alert_safe
        }
        binding.tvSecurityStatus.text = when {
            critical > 0 -> "⚠️ ALERTA CRÍTICA"
            warning > 0 -> "⚡ Advertencias activas"
            else -> "✅ Sistema seguro"
        }
        binding.tvSecurityStatus.setTextColor(ContextCompat.getColor(requireContext(), statusColor))
        binding.cardSecurityHeader.setCardBackgroundColor(
            ContextCompat.getColor(requireContext(), if (critical > 0) R.color.card_critical else R.color.surface)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
