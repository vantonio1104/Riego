package cl.inacap.riegoiot

/** Limites del formulario "Solicitud de riego" (los mismos que valida firestore.rules). */
object Solicitud {
    const val MAX_SECTOR = 40
    const val MAX_COMENTARIO = 200

    fun textoValido(texto: String, max: Int): Boolean = texto.isNotBlank() && texto.trim().length <= max
}
