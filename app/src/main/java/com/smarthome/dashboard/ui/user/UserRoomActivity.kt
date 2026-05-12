package com.smarthome.dashboard.ui.user

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smarthome.dashboard.data.models.Device
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.repository.SmartHomeRepository
import com.smarthome.dashboard.databinding.ActivityUserRoomBinding
import com.smarthome.dashboard.ui.adapters.UserDeviceAdapter
import kotlinx.coroutines.launch

class UserRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserRoomBinding
    private lateinit var adapter: UserDeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomName = intent.getStringExtra("ROOM_NAME") ?: "Habitación"
        val roomId = intent.getStringExtra("ROOM_ID") ?: ""

        binding.tvRoomName.text = roomName
        
        setSupportActionBar(binding.toolbarRoom)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarRoom.setNavigationOnClickListener { finish() }

        // Filtrar dispositivos por el ID o nombre de la habitación (String)
        val roomDevices = SmartHomeRepository.devices.filter { 
            it.room.equals(roomId, ignoreCase = true) || it.room.equals(roomName, ignoreCase = true)
        }
        
        adapter = UserDeviceAdapter(
            roomDevices,
            onToggle = { deviceId ->
                SmartHomeRepository.toggleDevice(deviceId)
            },
            onDelete = { device ->
                showDeleteDeviceDialog(device)
            }
        )

        binding.rvRoomDevices.layoutManager = LinearLayoutManager(this)
        binding.rvRoomDevices.adapter = adapter
    }

    private fun showDeleteDeviceDialog(device: Device) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Dispositivo")
            .setMessage("¿Estás seguro de que deseas eliminar '${device.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                val uid = FirebaseRepository.currentUid
                if (uid != null) {
                    lifecycleScope.launch {
                        val success = SmartHomeRepository.deleteDevice(device.id, uid)
                        if (success) {
                            Toast.makeText(this@UserRoomActivity, "Dispositivo eliminado", Toast.LENGTH_SHORT).show()
                            // Actualizar lista local después de borrar
                            val roomName = intent.getStringExtra("ROOM_NAME") ?: "Habitación"
                            val roomId = intent.getStringExtra("ROOM_ID") ?: ""
                            val updatedDevices = SmartHomeRepository.devices.filter { 
                                it.room.equals(roomId, ignoreCase = true) || it.room.equals(roomName, ignoreCase = true)
                            }
                            adapter.updateList(updatedDevices)
                        } else {
                            Toast.makeText(this@UserRoomActivity, "Error al eliminar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
