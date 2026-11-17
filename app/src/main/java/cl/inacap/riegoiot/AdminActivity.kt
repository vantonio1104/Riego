package cl.inacap.riegoiot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import cl.inacap.riegoiot.databinding.ActivityMonitoreoBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitoreoBinding
    private val db = FirebaseFirestore.getInstance()
    private var estabaSeca = false
    private val redCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            runOnUiThread { updateConnectionChip(caps) }
        }

        override fun onLost(network: Network) {
            runOnUiThread { updateConnectionChip(null) }
        }
    }

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
        observarEstacion()
        observarSolicitudes()
    }

    /** Mientras no llegue estacion/riego01 la pantalla muestra "Sin datos" (sin valores inventados). */
    private fun observarEstacion() {
        db.collection("estacion").document("riego01").addSnapshotListener(this) { doc, e ->
            if (e != null) return@addSnapshotListener errorConReintentar(binding.root, e) { observarEstacion() }
            if (doc == null) return@addSnapshotListener
            val humedad = doc.getLong("humedad")?.toInt() ?: return@addSnapshotListener
            val umbral = doc.getLong("umbral")?.toInt() ?: return@addSnapshotListener
            updateHumidityUI(humedad, umbral)

            val regando = doc.getBoolean("bomba") == true
            binding.tvPumpStatus.text = getString(R.string.pump_status_prefix) +
                getString(if (regando) R.string.pump_status_on else R.string.pump_status_off)

            val online = doc.getBoolean("online") == true
            binding.chipDeviceStatus.setText(if (online) R.string.device_online else R.string.device_offline)
            binding.chipDeviceStatus.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(this, if (online) R.color.status_online else R.color.status_offline)
            )
            binding.chipDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.white))

            binding.tvLastUpdate.text =
                getString(R.string.last_update_prefix, ActionLog.fecha(doc.getTimestamp("actualizado")))

            val seca = humedad < umbral
            if (seca && !estabaSeca) showDryAlert()
            estabaSeca = seca
        }
    }

    private fun observarSolicitudes() {
        val adapter = ActionHistoryAdapter()
        binding.rvSolicitudes.layoutManager = LinearLayoutManager(this)
        binding.rvSolicitudes.adapter = adapter
        db.collection("solicitudes").orderBy("fecha", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener(this) { snap, e ->
                if (e != null) return@addSnapshotListener errorConReintentar(binding.root, e) { observarSolicitudes() }
                if (snap == null) return@addSnapshotListener
                adapter.updateItems(snap.documents.map {
                    ActionLog(
                        getString(R.string.solicitud_item, it.getString("prioridad"), it.getString("sector"), it.getString("comentario")),
                        getString(R.string.solicitud_detalle, it.getString("nombre"), ActionLog.fecha(it.getTimestamp("fecha")))
                    )
                })
                binding.tvSinSolicitudes.isVisible = snap.isEmpty
            }
    }

    override fun onStart() {
        super.onStart()
        val cm = getSystemService(ConnectivityManager::class.java)
        updateConnectionChip(cm.getNetworkCapabilities(cm.activeNetwork))
        cm.registerDefaultNetworkCallback(redCallback)
    }

    override fun onStop() {
        super.onStop()
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(redCallback)
    }

    private fun updateConnectionChip(caps: NetworkCapabilities?) {
        val conectado = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        binding.chipConnection.visibility = View.VISIBLE
        binding.chipConnection.setText(
            when {
                !conectado -> R.string.conn_none
                caps!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.string.conn_wifi
                else -> R.string.conn_datos
            }
        )
        binding.chipConnection.chipBackgroundColor = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (conectado) R.color.status_conectado else R.color.status_disconnected)
        )
        binding.chipConnection.setTextColor(ContextCompat.getColor(this, R.color.white))
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
