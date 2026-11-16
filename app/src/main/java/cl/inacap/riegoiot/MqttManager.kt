package cl.inacap.riegoiot

import android.content.Context
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3ConnAckException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnectionType {
    WIFI_MQTT,
    DISCONNECTED
}

enum class DeviceState {
    ONLINE,
    OFFLINE
}

enum class PumpState {
    REGANDO,
    DETENIDA
}

/**
 * Conexion con la estacion de riego (Arduino UNO R4 WiFi) via broker Mosquitto.
 * - Modo real (por defecto): cliente HiveMQ MQTT 3.1.1, el broker valida usuario/clave y ACL.
 * - Modo simulado (SIMULADO=true en local.properties): la simulacion de la Unidad 2, sin broker.
 * Las pantallas solo observan los StateFlow, asi que no cambian entre modos.
 */
object MqttManager {

    private var client: Mqtt3AsyncClient? = null

    // Solo modo simulado. Solo se guarda el hash SHA-256 de la contrasena ("riego2026").
    private val demoUsers = mapOf(
        "admin" to "fbe73a5e97e2f53354d044d09ab1ff15a85264507e54bc36824d45976fb800fa",
        "observador" to "fbe73a5e97e2f53354d044d09ab1ff15a85264507e54bc36824d45976fb800fa"
    )

    private const val TICK_MS = 2000L          // cada cuanto "publica" la estacion simulada
    private const val DRY_STEP = 1             // % que baja la humedad por tick sin riego
    private const val WATER_STEP_PER_SEC = 3   // % que sube por segundo regando
    private const val COOLDOWN_MS = 10_000L    // espera tras regar (acelerada para la demo)

    private val _connectionType = MutableStateFlow(ConnectionType.DISCONNECTED)
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    private val _deviceState = MutableStateFlow(DeviceState.OFFLINE)
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    private val _pumpState = MutableStateFlow(PumpState.DETENIDA)
    val pumpState: StateFlow<PumpState> = _pumpState.asStateFlow()

    private val _humidity = MutableStateFlow(45)
    val humidity: StateFlow<Int> = _humidity.asStateFlow()

    private val _threshold = MutableStateFlow(30)
    val threshold: StateFlow<Int> = _threshold.asStateFlow()

    private val _durationSeconds = MutableStateFlow(10)
    val durationSeconds: StateFlow<Int> = _durationSeconds.asStateFlow()

    private val _autoMode = MutableStateFlow(true)
    val autoMode: StateFlow<Boolean> = _autoMode.asStateFlow()

    private val _lastUpdate = MutableStateFlow("N/A")
    val lastUpdate: StateFlow<String> = _lastUpdate.asStateFlow()

    private val _alerts = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val alerts: SharedFlow<String> = _alerts.asSharedFlow()

    var currentUsername: String = ""
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var simulationJob: Job? = null
    private var wateringJob: Job? = null
    private var lastWateringEndMs = 0L
    private var dryAlertSent = false

    suspend fun connect(context: Context, username: String, password: String): Result<Unit> {
        if (BuildConfig.SIMULADO) return connectSimulado(username, password)

        val user = username.lowercase()
        val c = MqttClient.builder()
            .useMqttVersion3()
            .identifier("app-$user-${System.currentTimeMillis()}")
            .serverHost(BuildConfig.MQTT_HOST)
            .serverPort(8883)
            // ISO 27002 8.24: TLS; solo se confia en la CA propia del proyecto (res/raw/ca.crt).
            // shortcut: sin verificar el nombre del host para no atar el certificado a la IP del PC;
            // la CA privada ya impide suplantar al broker. Con IP fija, quitar hostnameVerifier.
            .sslConfig()
            .trustManagerFactory(trustOnlyProjectCa(context))
            .hostnameVerifier { _, _ -> true }
            .applySslConfig()
            .addConnectedListener {
                if (client != null) _connectionType.value = ConnectionType.WIFI_MQTT
            }
            .addDisconnectedListener { ctx ->
                _connectionType.value = ConnectionType.DISCONNECTED
                _deviceState.value = DeviceState.OFFLINE
                // Reconecta cada 3 s si la sesion ya estaba abierta y no la cerro el usuario
                if (client != null && ctx.source != MqttDisconnectSource.USER) {
                    ctx.reconnector.reconnect(true).delay(3, TimeUnit.SECONDS)
                }
            }
            .buildAsync()

        return try {
            // El broker valida usuario y contrasena (password_file de Mosquitto)
            c.connectWith()
                .simpleAuth().username(user).password(password.toByteArray(Charsets.UTF_8)).applySimpleAuth()
                .send().await()
            for (topic in listOf(Protocolo.T_ESTADO, Protocolo.T_ALERTA, Protocolo.T_ONLINE)) {
                c.subscribeWith()
                    .topicFilter(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .callback { onMessage(it.topic.toString(), String(it.payloadAsBytes, Charsets.UTF_8)) }
                    .send().await()
            }
            client = c
            currentUsername = user
            _connectionType.value = ConnectionType.WIFI_MQTT
            Result.success(Unit)
        } catch (e: Mqtt3ConnAckException) {
            Result.failure(IllegalArgumentException("Usuario o contraseña incorrectos"))
        } catch (e: Exception) {
            c.disconnect()
            Result.failure(IllegalStateException("No se pudo conectar al broker ${BuildConfig.MQTT_HOST}"))
        }
    }

    private fun trustOnlyProjectCa(context: Context): TrustManagerFactory {
        val ca = context.resources.openRawResource(R.raw.ca).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("ca-riego", ca)
        }
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
    }

    /** Recibe lo que publica el Arduino. Mensajes con formato invalido se descartan. */
    private fun onMessage(topic: String, payload: String) {
        when (topic) {
            Protocolo.T_ONLINE ->
                _deviceState.value = if (payload == "1") DeviceState.ONLINE else DeviceState.OFFLINE
            Protocolo.T_ALERTA -> if (payload == "SECO") _alerts.tryEmit("SECO")
            Protocolo.T_ESTADO -> Protocolo.parseEstado(payload)?.let {
                _humidity.value = it.humedad
                _pumpState.value = if (it.regando) PumpState.REGANDO else PumpState.DETENIDA
                _threshold.value = it.umbral
                _durationSeconds.value = it.duracion
                _autoMode.value = it.auto
                updateTimestamp()
            }
        }
    }

    /** Envia un comando al Arduino. Devuelve false si no hay conexion. */
    private fun publish(cmd: String): Boolean {
        val c = client ?: return false
        if (_connectionType.value != ConnectionType.WIFI_MQTT) return false
        c.publishWith()
            .topic(Protocolo.T_CMD)
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload(cmd.toByteArray(Charsets.UTF_8))
            .send()
        return true
    }

    private suspend fun connectSimulado(username: String, password: String): Result<Unit> {
        delay(800) // simula el tiempo de conexion con el broker
        val expectedHash = demoUsers[username.lowercase()]
        if (expectedHash == null || expectedHash != sha256(password)) {
            return Result.failure(IllegalArgumentException("Usuario o contraseña incorrectos"))
        }
        currentUsername = username.lowercase()
        _connectionType.value = ConnectionType.WIFI_MQTT
        _deviceState.value = DeviceState.ONLINE
        updateTimestamp()
        startSimulation()
        return Result.success(Unit)
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        simulationJob?.cancel()
        wateringJob?.cancel()
        _pumpState.value = PumpState.DETENIDA
        _connectionType.value = ConnectionType.DISCONNECTED
        _deviceState.value = DeviceState.OFFLINE
        currentUsername = ""
    }

    /** Devuelve false si el comando no se pudo enviar. */
    fun publishConfig(threshold: Int, durationSeconds: Int, autoMode: Boolean): Boolean {
        if (!BuildConfig.SIMULADO) {
            // El estado real llega de vuelta por riego/estado cuando el Arduino lo aplica
            val cmd = Protocolo.cmdConfig(threshold, durationSeconds, autoMode) ?: return false
            return publish(cmd)
        }
        _threshold.value = threshold.coerceIn(10, 90)
        _durationSeconds.value = durationSeconds.coerceIn(3, 30)
        _autoMode.value = autoMode
        updateTimestamp()
        return true
    }

    /** Devuelve false si el comando no se pudo enviar. */
    fun publishManualWatering(username: String): Boolean {
        if (!BuildConfig.SIMULADO) return publish(Protocolo.CMD_REGAR)
        startWatering()
        return true
    }

    private fun startSimulation() {
        if (simulationJob?.isActive == true) return
        simulationJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (_pumpState.value == PumpState.DETENIDA) {
                    _humidity.value = (_humidity.value - DRY_STEP).coerceAtLeast(5)
                }
                updateTimestamp()
                evaluate()
            }
        }
    }

    private fun evaluate() {
        val hum = _humidity.value
        val thr = _threshold.value
        if (hum < thr) {
            if (!dryAlertSent) {
                _alerts.tryEmit("SECO")
                dryAlertSent = true
            }
            val cooledDown = System.currentTimeMillis() - lastWateringEndMs > COOLDOWN_MS
            if (_autoMode.value && _pumpState.value == PumpState.DETENIDA && cooledDown) {
                startWatering()
            }
        } else {
            dryAlertSent = false
        }
    }

    private fun startWatering() {
        if (_connectionType.value == ConnectionType.DISCONNECTED) return
        if (wateringJob?.isActive == true) return
        wateringJob = scope.launch {
            _pumpState.value = PumpState.REGANDO
            updateTimestamp()
            val stopAt = _threshold.value + 10
            for (second in 1.._durationSeconds.value) {
                delay(1000)
                _humidity.value = (_humidity.value + WATER_STEP_PER_SEC).coerceAtMost(95)
                updateTimestamp()
                if (_humidity.value >= stopAt) break
            }
            _pumpState.value = PumpState.DETENIDA
            lastWateringEndMs = System.currentTimeMillis()
            updateTimestamp()
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun updateTimestamp() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        _lastUpdate.value = sdf.format(Date())
    }
}
