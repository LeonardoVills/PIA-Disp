package com.smarthome.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.google.android.material.badge.BadgeDrawable
import com.smarthome.dashboard.data.models.UserRole
import com.smarthome.dashboard.data.repository.EnergyWorker
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.repository.SmartHomeRepository
import com.smarthome.dashboard.data.session.SessionManager
import com.smarthome.dashboard.databinding.ActivityMainBinding
import com.smarthome.dashboard.ui.dashboard.DashboardFragment
import com.smarthome.dashboard.ui.devices.DevicesFragment
import com.smarthome.dashboard.ui.energy.EnergyFragment
import com.smarthome.dashboard.ui.login.LoginActivity
import com.smarthome.dashboard.ui.security.SecurityFragment
import com.smarthome.dashboard.ui.user.RegisterActivity
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var securityBadge: BadgeDrawable? = null
    private var syncListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val user = SessionManager.getActiveUser(this)
        val currentUid = FirebaseRepository.currentUid
        
        if (user == null || user.role != UserRole.ADMIN || currentUid == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)

        // Solicitar permiso de notificaciones (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        // Escuchar peticiones de sincronización desde HTML
        syncListener = FirebaseRepository.escucharPeticionesSincronizacion(currentUid) { requestId, syncCode ->
            android.util.Log.d("SmartHomeSync", "Nueva petición recibida: $requestId para código $syncCode")
            android.widget.Toast.makeText(this, "🔔 Nueva solicitud de conexión: $syncCode", android.widget.Toast.LENGTH_LONG).show()
            com.smarthome.dashboard.data.repository.NotificationHelper.showSyncRequestNotification(this, requestId, "Panel Web ($syncCode)")
        }

        // Manejar clic en notificación si viene de una petición
        val requestIdFromIntent = intent?.getStringExtra("sync_request_id")
        val action = intent?.getStringExtra("sync_action")
        
        if (requestIdFromIntent != null) {
            val name = intent.getStringExtra("requester_name") ?: "Dashboard Externo"
            
            // Quitar la notificacion inmediatamente
            val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(com.smarthome.dashboard.data.repository.NotificationHelper.NOTIFICATION_ID)

            if (action == "ACCEPT") {
                lifecycleScope.launch {
                    FirebaseRepository.responderPeticionSincronizacion(requestIdFromIntent, true)
                    android.widget.Toast.makeText(this@MainActivity, "Sincronizacion Aceptada", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                mostrarDialogoSincronizacion(requestIdFromIntent, name)
            }
        }

        // IMPORTANTE: Asegurar que el SyncID esté registrado en Firestore al iniciar
        lifecycleScope.launch {
            FirebaseRepository.leerUsuario(currentUid)
            // IMPORTANTE: Iniciar la sincronización en TIEMPO REAL para el Admin
            SmartHomeRepository.syncFromFirebase(currentUid)
        }

        setupBottomNavigation()
        updateSecurityBadge()
        setupEnergyTracking()

        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }
    }

    private fun setupEnergyTracking() {
        val energyWorkRequest = PeriodicWorkRequestBuilder<EnergyWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "EnergyTrackingWork",
            ExistingPeriodicWorkPolicy.KEEP,
            energyWorkRequest
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_add_user -> {
                startActivity(Intent(this, RegisterActivity::class.java))
                true
            }
            R.id.nav_logout -> {
                SessionManager.clearSession(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.nav_energy -> {
                    loadFragment(EnergyFragment())
                    true
                }
                R.id.nav_devices -> {
                    loadFragment(DevicesFragment())
                    true
                }
                R.id.nav_security -> {
                    loadFragment(SecurityFragment())
                    true
                }
                else -> false
            }
        }

        // Setup security badge
        securityBadge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_security)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun updateSecurityBadge() {
        val pending = SmartHomeRepository.getPendingAlerts()
        if (pending > 0) {
            securityBadge?.apply {
                isVisible = true
                number = pending
                backgroundColor = getColor(R.color.alert_critical)
            }
        } else {
            securityBadge?.isVisible = false
        }
    }

    private fun mostrarDialogoSincronizacion(requestId: String, name: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Peticion de Sincronizacion")
            .setMessage("$name quiere acceder a tu Smart Home. ¿Deseas permitirlo?")
            .setPositiveButton("Aceptar") { _, _ ->
                lifecycleScope.launch {
                    FirebaseRepository.responderPeticionSincronizacion(requestId, true)
                    android.widget.Toast.makeText(this@MainActivity, "Sincronizacion Aceptada", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Rechazar") { _, _ ->
                lifecycleScope.launch {
                    FirebaseRepository.responderPeticionSincronizacion(requestId, false)
                }
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncListener?.remove()
    }
}
