package cl.inacap.riegoiot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocoloTest {

    @Test
    fun estadoValido() {
        assertEquals(EstadoEstacion(45, false, 30, 10, true), Protocolo.parseEstado("45;0;30;10;1"))
    }

    @Test
    fun estadoInvalidoSeDescarta() {
        assertNull(Protocolo.parseEstado(""))
        assertNull(Protocolo.parseEstado("45;0;30;10"))      // faltan campos
        assertNull(Protocolo.parseEstado("45;0;30;10;1;9"))  // sobran campos
        assertNull(Protocolo.parseEstado("abc;0;30;10;1"))   // no numerico
        assertNull(Protocolo.parseEstado("150;0;30;10;1"))   // humedad fuera de rango
        assertNull(Protocolo.parseEstado("45;2;30;10;1"))    // bomba no es 0/1
        assertNull(Protocolo.parseEstado("45;0;30;99;1"))    // duracion fuera de rango
    }

    @Test
    fun comandoConfig() {
        assertEquals("CFG;30;10;1", Protocolo.cmdConfig(30, 10, true))
        assertEquals("CFG;60;3;0", Protocolo.cmdConfig(60, 3, false))
        assertNull(Protocolo.cmdConfig(5, 10, true))
        assertNull(Protocolo.cmdConfig(30, 31, true))
    }
}
