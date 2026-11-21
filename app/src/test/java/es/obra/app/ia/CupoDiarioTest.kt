package es.obra.app.ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CupoDiarioTest {

    @Test
    fun `empieza con todo el cupo`() {
        val cupo = CupoDiario(dia = "2026-11-20", usadas = 0, limite = 3)
        assertEquals(3, cupo.restantes)
        assertTrue(cupo.puede())
    }

    @Test
    fun `se agota y deja de poder usarse`() {
        var cupo = CupoDiario(dia = "2026-11-20", usadas = 0, limite = 2)
        cupo = cupo.consumir("2026-11-20")!!
        cupo = cupo.consumir("2026-11-20")!!
        assertEquals(0, cupo.restantes)
        assertFalse(cupo.puede())
        assertNull(cupo.consumir("2026-11-20"))
    }

    @Test
    fun `al cambiar el dia vuelve a cero`() {
        val gastado = CupoDiario(dia = "2026-11-20", usadas = 5, limite = 5)
        val manana = gastado.deHoy("2026-11-21")
        assertEquals(0, manana.usadas)
        assertEquals(5, manana.limite)
        assertEquals(5, manana.restantes)

        val primera = gastado.consumir("2026-11-21")
        assertNotNull(primera)
        assertEquals(1, primera!!.usadas)
    }

    @Test
    fun `nunca da restantes negativos`() {
        val cupo = CupoDiario(dia = "2026-11-20", usadas = 9, limite = 4)
        assertEquals(0, cupo.restantes)
    }
}
