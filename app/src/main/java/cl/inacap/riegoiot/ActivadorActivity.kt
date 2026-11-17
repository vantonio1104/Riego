package cl.inacap.riegoiot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cl.inacap.riegoiot.databinding.ActivityActivadorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ActivadorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivadorBinding
    private lateinit var historyAdapter: ActionHistoryAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivadorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Sesion.exigirRol(this, Sesion.ADMIN)
        deleteFile("actions.txt") // historial local de la U3: ahora vive en la coleccion acciones

        setupToolbar()
        setupPlantProfiles()
        setupSlider()
        setupHistoryRecyclerView()
        setupSaveButton()
        setupWaterNowButton()
    }

    private fun setupToolbar() {
        binding.toolbarActivador.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupPlantProfiles() {
        binding.rgPlantProfile.setOnCheckedChangeListener { _, checkedId ->
            val targetValue = when (checkedId) {
                R.id.rbSucculent -> 20.0f
                R.id.rbNormal -> 30.0f
                R.id.rbTropical -> 60.0f
                else -> return@setOnCheckedChangeListener
            }
            binding.sliderThreshold.value = targetValue
            updateThresholdTitle(targetValue.toInt())
        }
    }

    private fun setupSlider() {
        binding.sliderThreshold.addOnChangeListener { _, value, fromUser ->
            val intVal = value.toInt()
            updateThresholdTitle(intVal)
            if (fromUser) {
                when (intVal) {
                    20 -> binding.rbSucculent.isChecked = true
                    30 -> binding.rbNormal.isChecked = true
                    60 -> binding.rbTropical.isChecked = true
                    else -> binding.rgPlantProfile.clearCheck()
                }
            }
        }
    }

    private fun updateThresholdTitle(value: Int) {
        binding.tvThresholdTitle.text = getString(R.string.threshold_title, value)
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = ActionHistoryAdapter()
        binding.rvActionHistory.layoutManager = LinearLayoutManager(this)
        binding.rvActionHistory.adapter = historyAdapter
        observarHistorial()
    }

    private fun observarHistorial() {
        db.collection("acciones").orderBy("fecha", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener(this) { snap, e ->
                if (e != null) return@addSnapshotListener errorConReintentar(binding.root, e) { observarHistorial() }
                if (snap == null) return@addSnapshotListener
                historyAdapter.updateItems(snap.documents.map {
                    ActionLog(
                        it.getString("accion").orEmpty(),
                        ActionLog.fecha(it.getTimestamp("fecha", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
                    )
                })
            }
    }

    /** Crea el comando (estado pendiente) y su registro en acciones en una sola escritura. */
    private fun enviarComando(comando: Map<String, Any>, accion: String, mensajeOk: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Sesion.cerrar(this)
        val batch = db.batch()
        batch.set(
            db.collection("comandos").document(),
            comando + mapOf("creadoPor" to uid, "fecha" to FieldValue.serverTimestamp(), "estado" to "pendiente")
        )
        batch.set(
            db.collection("acciones").document(),
            mapOf("uid" to uid, "accion" to accion, "fecha" to FieldValue.serverTimestamp())
        )
        val tarea = batch.commit()
        // Sin red queda en cache y se sube al reconectar
        if (!hayRed()) Snackbar.make(binding.root, R.string.pendiente_sin_red, Snackbar.LENGTH_LONG).show()
        lifecycleScope.launch {
            val mensaje = try {
                tarea.await()
                mensajeOk
            } catch (e: Exception) {
                mensajeError(e)
            }
            Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveConfig.setOnClickListener {
            binding.tilDuration.error = null

            val threshold = binding.sliderThreshold.value.toInt()
            val durationStr = binding.etDuration.text?.toString()?.trim().orEmpty()
            val duration = durationStr.toIntOrNull()
            val isAutoMode = binding.switchAutoMode.isChecked

            if (duration == null || Protocolo.cmdConfig(threshold, duration, isAutoMode) == null) {
                binding.tilDuration.error = getString(R.string.duration_error)
                return@setOnClickListener
            }

            val modeStr = if (isAutoMode) "AUTO" else "MANUAL"
            enviarComando(
                mapOf("tipo" to "CFG", "umbral" to threshold, "duracion" to duration, "auto" to isAutoMode),
                getString(R.string.action_config_saved, threshold, duration, modeStr),
                R.string.config_saved_success
            )
        }
    }

    private fun setupWaterNowButton() {
        binding.btnWaterNow.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.water_dialog_title)
                .setMessage(R.string.water_dialog_msg)
                .setPositiveButton(R.string.confirm) { dialog, _ ->
                    dialog.dismiss()
                    enviarComando(
                        mapOf("tipo" to Protocolo.CMD_REGAR),
                        getString(R.string.action_manual_water),
                        R.string.water_requested_success
                    )
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }
}
