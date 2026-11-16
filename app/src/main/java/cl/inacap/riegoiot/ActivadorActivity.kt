package cl.inacap.riegoiot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import cl.inacap.riegoiot.databinding.ActivityActivadorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ActivadorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivadorBinding
    private lateinit var historyAdapter: ActionHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivadorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Sesion.exigirRol(this, Sesion.ADMIN)

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

    // El slider usa stepSize=5: un valor fuera del paso lanza IllegalStateException
    private fun snapToStep(value: Int): Int = ((value.coerceIn(10, 90) + 2) / 5) * 5

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
        refreshHistory()
    }

    private fun refreshHistory() {
        val actions = ActionLogger.getRecentActions(this, 20)
        historyAdapter.updateItems(actions)
    }

    private fun setupSaveButton() {
        binding.btnSaveConfig.setOnClickListener {
            binding.tilDuration.error = null

            val threshold = binding.sliderThreshold.value.toInt()
            val durationStr = binding.etDuration.text?.toString()?.trim().orEmpty()
            val duration = durationStr.toIntOrNull()
            val isAutoMode = binding.switchAutoMode.isChecked

            if (duration == null || duration < 3 || duration > 30) {
                binding.tilDuration.error = getString(R.string.duration_error)
                return@setOnClickListener
            }

            // Fase 5: crear doc en comandos y acciones
            Snackbar.make(binding.root, getString(R.string.send_error), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupWaterNowButton() {
        binding.btnWaterNow.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.water_dialog_title)
                .setMessage(R.string.water_dialog_msg)
                .setPositiveButton(R.string.confirm) { dialog, _ ->
                    dialog.dismiss()
                    // Fase 5: crear doc en comandos y acciones
                    Snackbar.make(binding.root, getString(R.string.send_error), Snackbar.LENGTH_LONG).show()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }
}
