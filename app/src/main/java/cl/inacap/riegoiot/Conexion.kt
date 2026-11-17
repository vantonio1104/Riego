package cl.inacap.riegoiot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code

/** true si hay una red con salida a internet (Wi-Fi o datos). */
fun Context.hayRed(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java)
    return cm.getNetworkCapabilities(cm.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

/** Mensaje para el usuario segun el tipo de error de Firebase. */
@StringRes
fun mensajeError(e: Exception): Int = when {
    e is FirebaseNetworkException -> R.string.login_no_network
    e is FirebaseFirestoreException && e.code == Code.UNAVAILABLE -> R.string.login_no_network
    e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED -> R.string.error_sin_permiso
    e is FirebaseFirestoreException && e.code == Code.UNAUTHENTICATED -> R.string.error_auth
    e is FirebaseAuthException -> R.string.error_auth
    else -> R.string.error_generico
}

/** Snackbar con boton "Reintentar" para cuando falla una carga. */
fun errorConReintentar(vista: View, e: Exception, reintentar: () -> Unit) {
    Snackbar.make(vista, mensajeError(e), Snackbar.LENGTH_INDEFINITE)
        .setAction(R.string.reintentar) { reintentar() }
        .show()
}
