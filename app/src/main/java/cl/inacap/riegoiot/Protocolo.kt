package cl.inacap.riegoiot

/** Comandos que el administrador guarda en la coleccion comandos, y sus rangos validos. */
object Protocolo {
    const val CMD_REGAR = "REGAR"

    /** Arma el comando de configuracion. Devuelve null si los valores estan fuera de rango. */
    fun cmdConfig(umbral: Int, duracion: Int, auto: Boolean): String? =
        if (umbral in 10..90 && duracion in 3..30) "CFG;$umbral;$duracion;${if (auto) 1 else 0}" else null
}
