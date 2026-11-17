package cl.inacap.riegoiot

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

/** Fila de las listas de Admin (historial de acciones y solicitudes). */
data class ActionLog(
    val texto: String,
    val fecha: String
) {
    companion object {
        fun fecha(ts: Timestamp?): String =
            ts?.toDate()?.let { SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(it) } ?: "—"
    }
}
