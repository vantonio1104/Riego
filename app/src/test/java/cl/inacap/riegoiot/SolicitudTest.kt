package cl.inacap.riegoiot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolicitudTest {
    @Test
    fun textoObligatorio() {
        assertFalse(Solicitud.textoValido("", Solicitud.MAX_SECTOR))
        assertFalse(Solicitud.textoValido("   ", Solicitud.MAX_SECTOR))
        assertTrue(Solicitud.textoValido("Invernadero 2", Solicitud.MAX_SECTOR))
    }

    @Test
    fun textoRespetaMaximo() {
        assertTrue(Solicitud.textoValido("a".repeat(200), Solicitud.MAX_COMENTARIO))
        assertFalse(Solicitud.textoValido("a".repeat(201), Solicitud.MAX_COMENTARIO))
        assertTrue(Solicitud.textoValido(" " + "a".repeat(40) + " ", Solicitud.MAX_SECTOR)) // se recortan espacios
        assertFalse(Solicitud.textoValido("a".repeat(41), Solicitud.MAX_SECTOR))
    }
}
