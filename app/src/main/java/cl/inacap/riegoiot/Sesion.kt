package cl.inacap.riegoiot

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Rol del usuario en usuarios/{uid}.rol (ISO 27002 8.2 / 8.3: acceso por rol). */
object Sesion {
    const val ADMIN = "admin"
    const val USUARIO = "usuario"
    const val OBSERVADOR = "observador" // solo mira el monitoreo

    /** Deja pasar solo los roles que usan la app; "estacion" y desconocidos dan null. */
    fun rolDeApp(rol: String?): String? = rol?.takeIf { it == ADMIN || it == USUARIO || it == OBSERVADOR }

    /**
     * Devuelve "admin", "usuario" u "observador". Devuelve null si no hay sesion, no hay doc
     * o el rol no es de la app (ej. "estacion"). Lanza excepcion si la lectura falla.
     */
    suspend fun leerRol(fuente: Source = Source.DEFAULT): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val doc = FirebaseFirestore.getInstance().collection("usuarios").document(uid).get(fuente).await()
        return rolDeApp(doc.getString("rol"))
    }

    /** Cierra la sesion y vuelve al login. [mensaje]: aviso breve para el usuario (opcional). */
    fun cerrar(activity: Activity, @StringRes mensaje: Int? = null) {
        FirebaseAuth.getInstance().signOut()
        // applicationContext: el Toast sigue visible aunque esta pantalla se cierre
        mensaje?.let { Toast.makeText(activity.applicationContext, it, Toast.LENGTH_SHORT).show() }
        activity.startActivity(
            Intent(activity, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity.finish()
    }

    /**
     * Para onCreate de pantallas protegidas: si el rol no esta en [permitidos], vuelve al login.
     * Si esta, llama a [alConfirmar] con el rol confirmado.
     */
    fun exigirRol(activity: AppCompatActivity, vararg permitidos: String, alConfirmar: (String) -> Unit = {}) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            cerrar(activity, R.string.acceso_denegado)
            return
        }
        activity.lifecycleScope.launch {
            val rol = try {
                leerRol()
            } catch (e: Exception) {
                return@launch cerrar(activity, mensajeError(e)) // sin rol confirmado no hay acceso
            }
            if (rol == null || rol !in permitidos) cerrar(activity, R.string.acceso_denegado) else alConfirmar(rol)
        }
    }
}
