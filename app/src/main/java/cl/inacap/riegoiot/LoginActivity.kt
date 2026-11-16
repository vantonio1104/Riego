package cl.inacap.riegoiot

import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.riegoiot.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar

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
    }

    private fun attemptLogin() {
        if (LoginGuard.isLocked()) return

        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        binding.tilUsername.error = null
        binding.tilPassword.error = null

        var valid = true
        if (username.isEmpty()) {
            binding.tilUsername.error = getString(R.string.login_empty_error)
            valid = false
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.login_empty_error)
            valid = false
        }

        if (!valid) return

        // Fase 2: Firebase Auth + rol. Mientras tanto no se da acceso.
        Snackbar.make(binding.root, R.string.login_pendiente, Snackbar.LENGTH_LONG).show()
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
