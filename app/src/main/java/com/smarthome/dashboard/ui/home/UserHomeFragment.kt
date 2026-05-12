package com.smarthome.dashboard.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.repository.SmartHomeRepository
import com.smarthome.dashboard.databinding.FragmentUserHomeBinding
import com.smarthome.dashboard.ui.user.UserRoomActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserHomeFragment : Fragment() {

    private var _binding: FragmentUserHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UserRoomAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeDevices()
    }

    private fun setupRecyclerView() {
        adapter = UserRoomAdapter(emptyList()) { roomName ->
            openRoom(roomName)
        }
        binding.rvUserRooms.layoutManager = LinearLayoutManager(context)
        binding.rvUserRooms.adapter = adapter
    }

    private fun observeDevices() {
        viewLifecycleOwner.lifecycleScope.launch {
            SmartHomeRepository.devicesFlow.collectLatest { devices ->
                // 1. Obtener solo las áreas que tienen dispositivos del admin
                val roomGroups = devices.groupBy { it.room }
                
                val roomSummaries = roomGroups.map { (roomName, deviceList) ->
                    RoomSummary(
                        name = roomName,
                        deviceCount = deviceList.size,
                        iconResId = getRoomIcon(roomName),
                        colorResId = getRoomColor(roomName)
                    )
                }.sortedBy { it.name }

                // 2. Actualizar UI
                if (roomSummaries.isEmpty()) {
                    binding.rvUserRooms.visibility = View.GONE
                    binding.layoutEmptyState.visibility = View.VISIBLE
                } else {
                    binding.rvUserRooms.visibility = View.VISIBLE
                    binding.layoutEmptyState.visibility = View.GONE
                    adapter.updateRooms(roomSummaries)
                }
            }
        }
    }

    private fun getRoomIcon(roomName: String): Int {
        val lower = roomName.lowercase()
        return when {
            lower.contains("recámara") || lower.contains("bedroom") || lower.contains("cuarto") -> R.drawable.ic_home
            lower.contains("sala") || lower.contains("living") -> R.drawable.ic_tv
            lower.contains("cocina") || lower.contains("kitchen") -> R.drawable.ic_fridge
            lower.contains("baño") || lower.contains("bathroom") -> R.drawable.ic_washer
            lower.contains("exterior") || lower.contains("jardín") || lower.contains("patio") -> R.drawable.ic_camera
            lower.contains("entrada") || lower.contains("garage") -> R.drawable.ic_door
            else -> R.drawable.ic_home
        }
    }

    private fun getRoomColor(roomName: String): Int {
        val lower = roomName.lowercase()
        return when {
            lower.contains("recámara") -> android.R.color.holo_blue_light
            lower.contains("sala") -> android.R.color.holo_orange_light
            lower.contains("cocina") -> android.R.color.holo_green_light
            lower.contains("baño") -> android.R.color.holo_purple
            else -> R.color.primary
        }
    }

    private fun openRoom(roomName: String) {
        val intent = Intent(requireContext(), UserRoomActivity::class.java).apply {
            putExtra("ROOM_NAME", roomName)
            putExtra("ROOM_ID", roomName)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
