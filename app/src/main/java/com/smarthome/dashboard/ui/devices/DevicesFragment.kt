package com.smarthome.dashboard.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.models.Device
import com.smarthome.dashboard.data.models.DeviceType
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.repository.SmartHomeRepository
import com.smarthome.dashboard.databinding.FragmentDevicesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: DeviceCardAdapter
    private var currentFilter: String = "Todos"
    private var allDevices: List<Device> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        
        binding.fabAddDevice.setOnClickListener {
            showAddDeviceDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            SmartHomeRepository.devicesFlow.collectLatest { devices ->
                allDevices = devices
                updateTabs()
                filterAndRefresh()
                updateHeader(devices.size, devices.count { it.isActive })
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = DeviceCardAdapter(
            mutableListOf(),
            onToggle = { deviceId ->
                SmartHomeRepository.toggleDevice(deviceId)
            },
            onDelete = { device ->
                showDeleteDeviceDialog(device)
            }
        )
        binding.rvDevices.layoutManager = GridLayoutManager(context, 2)
        binding.rvDevices.adapter = adapter
    }

    private fun updateTabs() {
        binding.layoutFilterTabs.removeAllViews()
        
        val rooms = allDevices.map { it.room }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            
        val tabs = listOf("Todos") + rooms

        tabs.forEach { room ->
            val button = Button(requireContext()).apply {
                text = room
                textSize = 12f
                isAllCaps = false
                
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    100 
                )
                params.setMargins(0, 0, 16, 0)
                layoutParams = params
                
                if (room == currentFilter) {
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
                    setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                } else {
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_inactive))
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    alpha = 0.8f
                }

                setOnClickListener {
                    currentFilter = room
                    updateTabs()
                    filterAndRefresh()
                }

                // Click largo para borrar el área (todos los dispositivos en ella)
                if (room != "Todos") {
                    setOnLongClickListener {
                        showDeleteAreaDialog(room)
                        true
                    }
                }
            }
            binding.layoutFilterTabs.addView(button)
        }
    }

    private fun filterAndRefresh() {
        val filteredList = if (currentFilter == "Todos") {
            allDevices
        } else {
            allDevices.filter { it.room == currentFilter }
        }
        
        binding.rvDevices.post {
            adapter.updateDevices(filteredList)
        }
    }

    private fun updateHeader(total: Int, active: Int) {
        binding.tvDeviceCount.text = "$total dispositivos"
        binding.tvActiveSummary.text = "$active encendidos"
        val totalWatts = SmartHomeRepository.getCurrentTotalWatts()
        binding.tvCurrentWatts.text = "%.0f W".format(totalWatts)
    }

    private fun showAddDeviceDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_device, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_device_name)
        val etRoom = dialogView.findViewById<EditText>(R.id.et_device_room)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinner_type)

        val types = DeviceType.values()
        val typeNames = types.map { it.displayName }
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, typeNames)
        spinnerType.adapter = typeAdapter

        AlertDialog.Builder(requireContext())
            .setTitle("Añadir Dispositivo")
            .setView(dialogView)
            .setPositiveButton("Añadir") { _, _ ->
                val name = etName.text.toString()
                val room = etRoom.text.toString().trim()
                val type = types[spinnerType.selectedItemPosition]
                val uid = FirebaseRepository.currentUid

                if (name.isNotEmpty() && room.isNotEmpty() && uid != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        SmartHomeRepository.addDevice(name, type, room, uid)
                    }
                } else {
                    Toast.makeText(requireContext(), "Por favor llena todos los campos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteDeviceDialog(device: Device) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Dispositivo")
            .setMessage("¿Estás seguro de que deseas eliminar el dispositivo '${device.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                val uid = FirebaseRepository.currentUid
                if (uid != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val success = SmartHomeRepository.deleteDevice(device.id, uid)
                        if (success) {
                            Toast.makeText(requireContext(), "Dispositivo eliminado", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Error al eliminar dispositivo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteAreaDialog(roomName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Área")
            .setMessage("¿Deseas eliminar el área '$roomName' y TODOS sus dispositivos asociados?")
            .setPositiveButton("Eliminar Todo") { _, _ ->
                val uid = FirebaseRepository.currentUid
                if (uid != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val devicesToDelete = allDevices.filter { it.room == roomName }
                        var allSuccess = true
                        devicesToDelete.forEach { device ->
                            val success = SmartHomeRepository.deleteDevice(device.id, uid)
                            if (!success) allSuccess = false
                        }
                        
                        if (allSuccess) {
                            if (currentFilter == roomName) currentFilter = "Todos"
                            Toast.makeText(requireContext(), "Área eliminada correctamente", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Hubo problemas eliminando algunos dispositivos", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
