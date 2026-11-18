package cl.inacap.riegoiot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocoloTest {

    @Test
    fun comandoConfig() {
        assertEquals("CFG;30;10;1", Protocolo.cmdConfig(30, 10, true))
        assertEquals("CFG;60;3;0", Protocolo.cmdConfig(60, 3, false))
        assertNull(Protocolo.cmdConfig(5, 10, true))   // umbral fuera de rango
        assertNull(Protocolo.cmdConfig(30, 31, true))  // duracion fuera de rango
        assertNull(Protocolo.cmdConfig(30, 2, true))
    }
}
