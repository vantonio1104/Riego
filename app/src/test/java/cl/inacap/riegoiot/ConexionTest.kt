package cl.inacap.riegoiot

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import org.junit.Assert.assertEquals
import org.junit.Test

class ConexionTest {
    private fun fs(code: Code) = FirebaseFirestoreException("x", code)

    @Test
    fun firestoreSeDiferenciaPorCodigo() {
        assertEquals(R.string.login_no_network, mensajeError(fs(Code.UNAVAILABLE)))
        assertEquals(R.string.error_sin_permiso, mensajeError(fs(Code.PERMISSION_DENIED)))
        assertEquals(R.string.error_auth, mensajeError(fs(Code.UNAUTHENTICATED)))
        assertEquals(R.string.error_generico, mensajeError(fs(Code.INTERNAL)))
    }

    @Test
    fun authYRed() {
        assertEquals(R.string.login_no_network, mensajeError(FirebaseNetworkException("x")))
        assertEquals(R.string.error_auth, mensajeError(FirebaseAuthException("ERROR_USER_TOKEN_EXPIRED", "x")))
        assertEquals(R.string.error_generico, mensajeError(IllegalStateException("x")))
    }
}
