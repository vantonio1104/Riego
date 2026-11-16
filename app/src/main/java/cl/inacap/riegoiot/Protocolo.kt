package cl.inacap.riegoiot

/** Estado que publica el Arduino en riego/estado. */
data class EstadoEstacion(
    val humedad: Int,
    val regando: Boolean,
    val umbral: Int,
    val duracion: Int,
    val auto: Boolean
)

/**
 * Formato de mensajes MQTT compartido con el sketch riego_u3.ino.
 * Si se cambia aqui, hay que cambiarlo tambien en el Arduino.
 */
object Protocolo {
    const val T_ESTADO = "riego/estado" // Arduino -> app: "humedad;bomba;umbral;duracion;auto"
    const val T_ALERTA = "riego/alerta" // Arduino -> app: "SECO"
    const val T_ONLINE = "riego/online" // Arduino -> app (retenido + LWT): "1" | "0"
    const val T_CMD = "riego/cmd"       // app -> Arduino: "REGAR" | "CFG;umbral;duracion;auto"

    const val CMD_REGAR = "REGAR"

    /** Arma el comando de configuracion. Devuelve null si los valores estan fuera de rango. */
    fun cmdConfig(umbral: Int, duracion: Int, auto: Boolean): String? =
        if (umbral in 10..90 && duracion in 3..30) "CFG;$umbral;$duracion;${if (auto) 1 else 0}" else null

    /** Devuelve null si el mensaje no cumple formato o rangos (integridad de datos). */
    fun parseEstado(payload: String): EstadoEstacion? {
        val partes = payload.trim().split(";")
        if (partes.size != 5) return null
        val n = partes.map { it.toIntOrNull() ?: return null }
        if (n[0] !in 0..100 || n[1] !in 0..1 || n[2] !in 10..90 || n[3] !in 3..30 || n[4] !in 0..1) return null
        return EstadoEstacion(n[0], n[1] == 1, n[2], n[3], n[4] == 1)
    }
}
