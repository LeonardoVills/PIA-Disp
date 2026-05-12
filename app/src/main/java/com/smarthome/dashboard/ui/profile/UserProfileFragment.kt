package com.smarthome.dashboard.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.session.SessionManager
import com.smarthome.dashboard.databinding.FragmentUserProfileBinding
import com.smarthome.dashboard.ui.login.LoginActivity

class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = SessionManager.getActiveUser(requireContext())
        val displayId = user?.syncId ?: FirebaseRepository.currentUid?.takeLast(4)?.uppercase() ?: "----"

        binding.tvUsername.text = user?.username
        
        // Mostrar el ID corto para que el usuario pueda sincronizarlo con el Dashboard HTML
        binding.tvUserRole.text = "ID Sincronización: $displayId"
        binding.tvUserRole.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Sync ID", displayId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "ID corto copiado: $displayId", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            SessionManager.clearSession(requireContext())
            FirebaseRepository.cerrarSesion()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            activity?.finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
