package com.smarthome.dashboard.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.smarthome.dashboard.MainActivity
import com.smarthome.dashboard.data.models.User
import com.smarthome.dashboard.data.models.UserRole
import com.smarthome.dashboard.data.repository.FirebaseRepository
import com.smarthome.dashboard.data.session.SessionManager
import com.smarthome.dashboard.databinding.ActivityLoginBinding
import com.smarthome.dashboard.ui.user.RegisterActivity
import com.smarthome.dashboard.ui.user.UserActivity
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val activeUser = SessionManager.getActiveUser(this)
        if (activeUser != null && FirebaseRepository.currentUid != null) {
            navigateToMain(activeUser.role)
            return
        }

        binding.btnSendOtp.setOnClickListener {
            var phone = binding.etPhone.text.toString().trim()
            if (phone.isEmpty()) {
                Toast.makeText(this, "Ingresa un número de teléfono", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Agregar +52 automáticamente si no tiene prefijo
            if (!phone.startsWith("+")) {
                phone = "+52$phone"
            }
            
            sendVerificationCode(phone)
        }

        binding.btnVerifyOtp.setOnClickListener {
            val code = binding.etOtp.text.toString().trim()
            if (code.isEmpty() || verificationId == null) {
                Toast.makeText(this, "Ingresa el código de verificación", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyCode(code)
        }

        binding.btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun sendVerificationCode(phone: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSendOtp.isEnabled = false

        val options = PhoneAuthOptions.newBuilder()
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSendOtp.isEnabled = true
                    // Mostrar mensaje claro del error de Firebase
                    val errorMsg = when(e.message) {
                        "This operation is not allowed" -> "Error: Debes habilitar 'Teléfono' en la consola de Firebase."
                        else -> "Error: ${e.message}"
                    }
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    binding.progressBar.visibility = View.GONE
                    binding.layoutPhone.visibility = View.GONE
                    binding.layoutOtp.visibility = View.VISIBLE
                    Toast.makeText(this@LoginActivity, "Código enviado", Toast.LENGTH_SHORT).show()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyCode(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        signInWithPhoneCredential(credential)
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnVerifyOtp.isEnabled = false

        lifecycleScope.launch {
            val result = FirebaseRepository.iniciarSesionConTelefono(credential)
            
            binding.progressBar.visibility = View.GONE
            binding.btnVerifyOtp.isEnabled = true

            result.onSuccess { uid ->
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
            }.onFailure { e ->
                Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain(role: UserRole) {
        if (role == UserRole.ADMIN) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, UserActivity::class.java))
        }
        finish()
    }
}
