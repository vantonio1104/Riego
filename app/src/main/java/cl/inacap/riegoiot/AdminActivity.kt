package cl.inacap.riegoiot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.inacap.riegoiot.databinding.ActivityMonitoreoBinding
import com.google.android.material.snackbar.Snackbar

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitoreoBinding

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    companion object {
        private const val ALERT_CHANNEL_ID = "alertas_riego"
        private const val ALERT_NOTIFICATION_ID = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitoreoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Sesion.exigirRol(this, Sesion.ADMIN)

        setupToolbar()
        setupAdminButton()
        setupNotifications()
        // Fase 5: datos de estacion/riego01 desde Firestore
    }

    private fun setupToolbar() {
        binding.toolbarMonitoreo.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_logout) {
                Sesion.cerrar(this)
                true
            } else {
                false
            }
        }
    }

    private fun setupAdminButton() {
        binding.btnGoToActivador.visibility = View.VISIBLE
        binding.btnGoToActivador.setOnClickListener {
            startActivity(Intent(this, ActivadorActivity::class.java))
        }
    }

    private fun setupNotifications() {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun showDryAlert() {
        Snackbar.make(binding.root, getString(R.string.alert_dry_text), Snackbar.LENGTH_LONG).show()
        if (!canPostNotifications()) return

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.alert_dry_title))
            .setContentText(getString(R.string.alert_dry_text))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(ALERT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // El usuario revoco el permiso: basta con el Snackbar
        }
    }

    private fun updateHumidityUI(humidity: Int, threshold: Int) {
        binding.progressHumidity.progress = humidity
        binding.tvHumidityValue.text = getString(R.string.humidity_unit, humidity)

        val statusText = when {
            humidity < threshold -> getString(R.string.plant_status_seca)
            humidity > 85 -> getString(R.string.plant_status_saturada)
            else -> getString(R.string.plant_status_optima)
        }
        binding.tvPlantStatus.text = "${getString(R.string.plant_status_prefix)}$statusText"
    }
}
