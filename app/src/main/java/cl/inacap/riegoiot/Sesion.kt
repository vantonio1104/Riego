package cl.inacap.riegoiot

import android.app.Activity
import android.content.Intent
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

    /**
     * Devuelve "admin" o "usuario". Devuelve null si no hay sesion, no hay doc
     * o el rol no es de la app (ej. "estacion"). Lanza excepcion si la lectura falla.
     */
    suspend fun leerRol(fuente: Source = Source.DEFAULT): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val doc = FirebaseFirestore.getInstance().collection("usuarios").document(uid).get(fuente).await()
        return doc.getString("rol")?.takeIf { it == ADMIN || it == USUARIO }
    }

    fun cerrar(activity: Activity) {
        FirebaseAuth.getInstance().signOut()
        activity.startActivity(
            Intent(activity, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity.finish()
    }

    /** Para onCreate de pantallas protegidas: si el rol no es el esperado, vuelve al login. */
    fun exigirRol(activity: AppCompatActivity, esperado: String) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            cerrar(activity)
            return
        }
        activity.lifecycleScope.launch {
            val rol = try {
                leerRol()
            } catch (e: Exception) {
                null
            }
            if (rol != esperado) cerrar(activity)
        }
    }
}
