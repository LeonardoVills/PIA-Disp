package com.smarthome.dashboard.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smarthome.dashboard.MainActivity
import com.smarthome.dashboard.data.models.User
import com.smarthome.dashboard.data.models.UserRole
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.session.SessionManager
import com.smarthome.dashboard.databinding.ActivityLoginBinding
import com.smarthome.dashboard.ui.user.RegisterActivity
import com.smarthome.dashboard.ui.user.UserActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var _binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(_binding.root)

        val activeUser = SessionManager.getActiveUser(this)
        if (activeUser != null && FirebaseRepository.currentUid != null) {
            navigateToMain(activeUser.role)
            return
        }

        _binding.btnLoginEmail.setOnClickListener {
            val email = _binding.etEmailLogin.text.toString().trim()
            val pass = _binding.etPasswordLogin.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginWithEmail(email, pass)
        }

        _binding.btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        
        _binding.btnSendOtp.setOnClickListener {
            Toast.makeText(this, "Usa el Registro para crear una cuenta con Email", Toast.LENGTH_LONG).show()
        }
    }

    private fun loginWithEmail(email: String, pass: String) {
        _binding.progressBar.visibility = View.VISIBLE
        _binding.btnLoginEmail.isEnabled = false

        lifecycleScope.launch {
            val result: Result<String> = FirebaseRepository.iniciarSesion(email, pass)
            
            _binding.progressBar.visibility = View.GONE
            _binding.btnLoginEmail.isEnabled = true

            result.onSuccess { uid: String ->
                val userData = FirebaseRepository.leerUsuario(uid)
                if (userData != null) {
                    val roleStr = userData["role"] as? String ?: "USER"
                    val username = userData["username"] as? String ?: "Usuario"
                    val syncId = userData["syncId"] as? String ?: uid.takeLast(4).uppercase()
                    val adminId = userData["adminId"] as? String
                    val role = try { UserRole.valueOf(roleStr) } catch(e: Exception) { UserRole.USER }
                    
                    val user = User(username, role, syncId, adminId)
                    SessionManager.saveSession(this@LoginActivity, user)
                    
                    navigateToMain(role)
                } else {
                    Toast.makeText(this@LoginActivity, "Error al obtener datos", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e: Throwable ->
                Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain(role: UserRole) {
        val intent = if (role == UserRole.ADMIN) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, UserActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
