package cl.inacap.riegoiot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesionTest {
    @Test
    fun rolesDeLaAppSeAceptan() {
        assertEquals(Sesion.ADMIN, Sesion.rolDeApp("admin"))
        assertEquals(Sesion.USUARIO, Sesion.rolDeApp("usuario"))
        assertEquals(Sesion.OBSERVADOR, Sesion.rolDeApp("observador"))
    }

    @Test
    fun rolesAjenosOFaltantesNoDanAcceso() {
        assertNull(Sesion.rolDeApp("estacion")) // la cuenta del Arduino no entra a la app
        assertNull(Sesion.rolDeApp("Admin"))    // sin coincidencias aproximadas
        assertNull(Sesion.rolDeApp(""))
        assertNull(Sesion.rolDeApp(null))
    }
}
