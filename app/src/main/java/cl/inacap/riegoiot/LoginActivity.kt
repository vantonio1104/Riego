package cl.inacap.riegoiot

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cl.inacap.riegoiot.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Guarda los intentos fallidos fuera de la Activity para que el bloqueo
 * no se reinicie al girar la pantalla o volver a abrir el login.
 */
private object LoginGuard {
    const val MAX_ATTEMPTS = 5
    const val LOCK_MS = 30_000L
    var failedAttempts = 0
    var lockUntil = 0L // SystemClock.elapsedRealtime()

    fun remainingLockMs(): Long = (lockUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    fun isLocked(): Boolean = remainingLockMs() > 0L
}

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        if (LoginGuard.isLocked()) {
            startLockCountdown(LoginGuard.remainingLockMs())
        }

        // Sesion activa: se confirma el rol antes de entrar
        if (FirebaseAuth.getInstance().currentUser != null) {
            lifecycleScope.launch { abrirSegunRol() }
        }
    }

    private fun attemptLogin() {
        if (LoginGuard.isLocked()) return

        val email = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        binding.tilUsername.error = null
        binding.tilPassword.error = null

        var valid = true
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilUsername.error = getString(R.string.login_email_error)
            valid = false
        }
        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.login_password_error)
            valid = false
        }

        if (!valid) return

        showLoading(true)

        lifecycleScope.launch {
            try {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
            } catch (e: Exception) {
                showLoading(false)
                onLoginError(e)
                return@launch
            }
            LoginGuard.failedAttempts = 0
            abrirSegunRol()
        }
    }

    private fun onLoginError(e: Exception) {
        when (e) {
            is FirebaseNetworkException -> mostrar(getString(R.string.login_no_network))
            is FirebaseTooManyRequestsException -> mostrar(getString(R.string.login_too_many))
            is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> {
                mostrar(getString(R.string.login_bad_credentials))
                LoginGuard.failedAttempts++
                if (LoginGuard.failedAttempts >= LoginGuard.MAX_ATTEMPTS) {
                    LoginGuard.lockUntil = SystemClock.elapsedRealtime() + LoginGuard.LOCK_MS
                    startLockCountdown(LoginGuard.LOCK_MS)
                }
            }
            else -> mostrar(getString(mensajeError(e)))
        }
    }

    /** Lee el rol desde el servidor. Sin rol confirmado no hay acceso. */
    private suspend fun abrirSegunRol() {
        showLoading(true)
        val rol = try {
            Sesion.leerRol(Source.SERVER)
        } catch (e: Exception) {
            FirebaseAuth.getInstance().signOut()
            showLoading(false)
            mostrar(getString(mensajeError(e)))
            return
        }
        val destino = when (rol) {
            Sesion.ADMIN -> AdminActivity::class.java
            Sesion.USUARIO -> UsuarioActivity::class.java
            else -> null
        }
        if (destino == null) {
            FirebaseAuth.getInstance().signOut()
            showLoading(false)
            mostrar(getString(R.string.login_no_role))
            return
        }
        startActivity(Intent(this, destino))
        finish()
    }

    private fun mostrar(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun startLockCountdown(durationMs: Long) {
        binding.btnLogin.isEnabled = false
        binding.tvLoginStatus.visibility = View.VISIBLE

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = ((millisUntilFinished + 999) / 1000).toInt()
                binding.tvLoginStatus.text = getString(R.string.login_blocked, secondsLeft)
            }

            override fun onFinish() {
                LoginGuard.failedAttempts = 0
                LoginGuard.lockUntil = 0L
                binding.btnLogin.isEnabled = true
                binding.tvLoginStatus.visibility = View.GONE
            }
        }.start()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading && !LoginGuard.isLocked()
        binding.etUsername.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
