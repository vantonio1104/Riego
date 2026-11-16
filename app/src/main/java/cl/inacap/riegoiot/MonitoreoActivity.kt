package cl.inacap.riegoiot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cl.inacap.riegoiot.databinding.ActivityMonitoreoBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MonitoreoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitoreoBinding
    private var username: String = ""
    private var userRole: UserRole = UserRole.OBSERVADOR

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    companion object {
        const val EXTRA_USERNAME = "extra_username"
        private const val ALERT_CHANNEL_ID = "alertas_riego"
        private const val ALERT_NOTIFICATION_ID = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitoreoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = intent.getStringExtra(EXTRA_USERNAME) ?: MqttManager.currentUsername
        userRole = UserRoleManager.getRole(username)

        setupToolbar()
        setupAdminButton()
        setupNotifications()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbarMonitoreo.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_logout) {
                MqttManager.disconnect()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                true
            } else {
                false
            }
        }
    }

    private fun setupAdminButton() {
        if (userRole == UserRole.ADMINISTRADOR) {
            binding.btnGoToActivador.visibility = View.VISIBLE
            binding.btnGoToActivador.setOnClickListener {
                val intent = Intent(this, ActivadorActivity::class.java).apply {
                    putExtra(ActivadorActivity.EXTRA_USERNAME, username)
                }
                startActivity(intent)
            }
        } else {
            binding.btnGoToActivador.visibility = View.GONE
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

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(MqttManager.humidity, MqttManager.threshold) { hum, thresh ->
                        Pair(hum, thresh)
                    }.collect { (hum, thresh) ->
                        updateHumidityUI(hum, thresh)
                    }
                }

                launch {
                    MqttManager.connectionType.collect { connType ->
                        updateConnectionChip(connType)
                    }
                }

                launch {
                    MqttManager.deviceState.collect { devState ->
                        updateDeviceStateChip(devState)
                    }
                }

                launch {
                    MqttManager.pumpState.collect { pState ->
                        updatePumpStatus(pState)
                    }
                }

                launch {
                    MqttManager.lastUpdate.collect { lastUp ->
                        binding.tvLastUpdate.text = getString(R.string.last_update_prefix, lastUp)
                    }
                }

                launch {
                    MqttManager.alerts.collect { alert ->
                        if (alert == "SECO") showDryAlert()
                    }
                }
            }
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

    private fun updateConnectionChip(type: ConnectionType) {
        when (type) {
            ConnectionType.WIFI_MQTT -> {
                binding.chipConnection.text = getString(R.string.conn_mqtt)
                binding.chipConnection.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_mqtt)
                )
                binding.chipConnection.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            ConnectionType.DISCONNECTED -> {
                binding.chipConnection.text = getString(R.string.conn_none)
                binding.chipConnection.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_disconnected)
                )
                binding.chipConnection.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }
    }

    private fun updateDeviceStateChip(state: DeviceState) {
        when (state) {
            DeviceState.ONLINE -> {
                binding.chipDeviceStatus.text = getString(R.string.device_online)
                binding.chipDeviceStatus.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_online)
                )
                binding.chipDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            DeviceState.OFFLINE -> {
                binding.chipDeviceStatus.text = getString(R.string.device_offline)
                binding.chipDeviceStatus.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_offline)
                )
                binding.chipDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }
    }

    private fun updatePumpStatus(state: PumpState) {
        val statusStr = when (state) {
            PumpState.REGANDO -> getString(R.string.pump_status_on)
            PumpState.DETENIDA -> getString(R.string.pump_status_off)
        }
        binding.tvPumpStatus.text = "${getString(R.string.pump_status_prefix)}$statusStr"
    }
}
