package com.smarthome.dashboard.ui.user

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smarthome.dashboard.data.models.UserRole
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.session.SessionManager
import com.smarthome.dashboard.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var _binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(_binding.root)

        val activeUser = SessionManager.getActiveUser(this)
        val isAdminCreatingUser = activeUser?.role == UserRole.ADMIN

        supportActionBar?.apply {
            title = if (isAdminCreatingUser) "Registrar Nuevo Usuario" else "Registro de Administrador"
            setDisplayHomeAsUpEnabled(true)
        }

        if (isAdminCreatingUser) {
            _binding.tvTitle.text = "Añadir Integrante al Hogar"
        }

        _binding.btnSendOtpRegister.text = "Registrar Cuenta"
        _binding.btnSendOtpRegister.setOnClickListener {
            val username = _binding.etUsername.text.toString().trim()
            val phone = _binding.etPhone.text.toString().trim()
            val email = _binding.etEmail.text.toString().trim()
            val password = _binding.etPassword.text.toString().trim()

            if (username.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            createFinalAccount(username, phone, email, password, isAdminCreatingUser)
        }
    }

    private fun createFinalAccount(
        username: String, 
        phone: String, 
        email: String, 
        pass: String, 
        isAdminCreating: Boolean
    ) {
        _binding.progressBar.visibility = View.VISIBLE
        _binding.btnSendOtpRegister.isEnabled = false

        val role = if (isAdminCreating) UserRole.USER else UserRole.ADMIN
        val adminId = if (isAdminCreating) FirebaseRepository.currentUid else null
        
        val finalPhone = if (!phone.startsWith("+")) "+52$phone" else phone

        lifecycleScope.launch {
            if (FirebaseRepository.telefonoExiste(finalPhone)) {
                _binding.progressBar.visibility = View.GONE
                _binding.btnSendOtpRegister.isEnabled = true
                Toast.makeText(this@RegisterActivity, "El teléfono ya está registrado", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val result: Result<String> = FirebaseRepository.crearUsuario(
                email, pass, username, finalPhone, role, adminId
            )
            
            _binding.progressBar.visibility = View.GONE
            _binding.btnSendOtpRegister.isEnabled = true

            result.onSuccess { uid: String ->
                Toast.makeText(this@RegisterActivity, "Cuenta creada exitosamente", Toast.LENGTH_LONG).show()
                finish()
            }.onFailure { e: Throwable ->
                Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
