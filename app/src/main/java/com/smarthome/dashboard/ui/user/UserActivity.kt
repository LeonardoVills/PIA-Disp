package com.smarthome.dashboard.ui.user

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.repository.SmartHomeRepository
import com.smarthome.dashboard.data.session.SessionManager
import com.smarthome.dashboard.databinding.ActivityUserBinding
import com.smarthome.dashboard.ui.devices.UserDevicesFragment
import com.smarthome.dashboard.ui.home.UserHomeFragment
import com.smarthome.dashboard.ui.login.LoginActivity
import com.smarthome.dashboard.ui.profile.UserProfileFragment
import kotlinx.coroutines.launch

class UserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val user = SessionManager.getActiveUser(this)
        val currentUid = FirebaseRepository.currentUid
        
        if (user == null || currentUid == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Asegurar que el SyncID esté registrado en Firestore al iniciar para el Usuario Hijo
        lifecycleScope.launch {
            FirebaseRepository.leerUsuario(currentUid)
            
            // IMPORTANTE: Si es un usuario hijo (USER), sincronizamos los dispositivos de su ADMIN
            // Si no tiene adminId, sincronizamos los propios (por si acaso)
            val syncId = user.adminId ?: currentUid
            SmartHomeRepository.syncFromFirebase(syncId)
        }

        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(UserHomeFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.userBottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_user_home -> {
                    loadFragment(UserHomeFragment())
                    true
                }
                R.id.nav_user_devices -> {
                    loadFragment(UserDevicesFragment())
                    true
                }
                R.id.nav_user_profile -> {
                    loadFragment(UserProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.user_fragment_container, fragment)
            .commit()
    }
}
