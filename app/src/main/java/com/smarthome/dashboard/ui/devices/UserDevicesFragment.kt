package com.smarthome.dashboard.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smarthome.dashboard.data.models.Device
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.repository.SmartHomeRepository
import com.smarthome.dashboard.databinding.FragmentUserDevicesBinding
import com.smarthome.dashboard.ui.adapters.UserDeviceAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserDevicesFragment : Fragment() {

    private var _binding: FragmentUserDevicesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UserDeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = UserDeviceAdapter(
            SmartHomeRepository.devices,
            onToggle = { deviceId ->
                SmartHomeRepository.toggleDevice(deviceId)
            },
            onDelete = { device ->
                showDeleteDeviceDialog(device)
            }
        )

        binding.rvUserDevices.layoutManager = LinearLayoutManager(context)
        binding.rvUserDevices.adapter = adapter

        // Observar cambios en los dispositivos para actualizar la lista
        viewLifecycleOwner.lifecycleScope.launch {
            SmartHomeRepository.devicesFlow.collectLatest { devices ->
                adapter.updateList(devices)
            }
        }
    }

    private fun showDeleteDeviceDialog(device: Device) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Dispositivo")
            .setMessage("¿Estás seguro de que deseas eliminar '${device.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                val uid = FirebaseRepository.currentUid
                if (uid != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val success = SmartHomeRepository.deleteDevice(device.id, uid)
                        if (success) {
                            Toast.makeText(requireContext(), "Dispositivo eliminado", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Error al eliminar", Toast.LENGTH_SHORT).show()
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
