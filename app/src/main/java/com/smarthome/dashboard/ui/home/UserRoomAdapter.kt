package com.smarthome.dashboard.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smarthome.dashboard.R
import com.smarthome.dashboard.databinding.ItemUserRoomCardBinding

data class RoomSummary(
    val name: String,
    val deviceCount: Int,
    val iconResId: Int,
    val colorResId: Int
)

class UserRoomAdapter(
    private var rooms: List<RoomSummary>,
    private val onRoomClick: (String) -> Unit
) : RecyclerView.Adapter<UserRoomAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemUserRoomCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserRoomCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val room = rooms[position]
        holder.binding.apply {
            tvRoomName.text = room.name
            tvDeviceCount.text = "${room.deviceCount} dispositivos"
            ivRoomIcon.setImageResource(room.iconResId)
            
            // Efecto visual de color
            viewAccent.backgroundTintList = android.content.res.ColorStateList.valueOf(
                holder.itemView.context.getColor(room.colorResId)
            )
            
            cardRoom.setOnClickListener { onRoomClick(room.name) }
        }
    }

    override fun getItemCount() = rooms.size

    fun updateRooms(newRooms: List<RoomSummary>) {
        rooms = newRooms
        notifyDataSetChanged()
    }
}
