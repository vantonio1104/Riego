package cl.inacap.riegoiot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import cl.inacap.riegoiot.databinding.ActivityActivadorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ActivadorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivadorBinding
    private lateinit var historyAdapter: ActionHistoryAdapter
    private var username: String = ""

    companion object {
        const val EXTRA_USERNAME = "extra_username"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivadorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = intent.getStringExtra(EXTRA_USERNAME) ?: MqttManager.currentUsername

        setupToolbar()
        loadCurrentConfig()
        setupPlantProfiles()
        setupSlider()
        setupHistoryRecyclerView()
        setupSaveButton()
        setupWaterNowButton()
        observeRemoteThreshold()
    }

    private fun setupToolbar() {
        binding.toolbarActivador.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadCurrentConfig() {
        val current = snapToStep(MqttManager.threshold.value)
        binding.sliderThreshold.value = current.toFloat()
        updateThresholdTitle(current)
        binding.etDuration.setText(MqttManager.durationSeconds.value.toString())
        binding.switchAutoMode.isChecked = MqttManager.autoMode.value
        when (current) {
            20 -> binding.rbSucculent.isChecked = true
            30 -> binding.rbNormal.isChecked = true
            60 -> binding.rbTropical.isChecked = true
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

            if (!MqttManager.publishConfig(threshold, duration, isAutoMode)) {
                Snackbar.make(binding.root, getString(R.string.send_error), Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val modeStr = if (isAutoMode) "AUTO" else "MANUAL"
            val actionText = getString(R.string.action_config_saved, threshold, duration, modeStr)
            ActionLogger.logAction(this, username, actionText, "MQTT")

            Snackbar.make(binding.root, getString(R.string.config_saved_success), Snackbar.LENGTH_SHORT).show()
            refreshHistory()
        }
    }

    private fun setupWaterNowButton() {
        binding.btnWaterNow.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.water_dialog_title)
                .setMessage(R.string.water_dialog_msg)
                .setPositiveButton(R.string.confirm) { dialog, _ ->
                    dialog.dismiss()
                    if (!MqttManager.publishManualWatering(username)) {
                        Snackbar.make(binding.root, getString(R.string.send_error), Snackbar.LENGTH_LONG).show()
                        return@setPositiveButton
                    }

                    val actionText = getString(R.string.action_manual_water)
                    ActionLogger.logAction(this, username, actionText, "MQTT")

                    Snackbar.make(binding.root, getString(R.string.water_requested_success), Snackbar.LENGTH_SHORT).show()
                    refreshHistory()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun observeRemoteThreshold() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MqttManager.threshold.collect { remoteThreshold ->
                    val snapped = snapToStep(remoteThreshold)
                    if (binding.sliderThreshold.value.toInt() != snapped) {
                        binding.sliderThreshold.value = snapped.toFloat()
                        updateThresholdTitle(snapped)
                    }
                }
            }
        }
    }
}
