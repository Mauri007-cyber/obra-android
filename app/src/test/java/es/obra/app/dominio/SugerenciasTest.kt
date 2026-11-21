package es.obra.app.dominio

import es.obra.app.datos.MuroEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SugerenciasTest {

    private fun muro(largo: Double, id: Long = 1) =
        MuroEntity(id = id, estanciaId = 1, x1 = 0.0, y1 = 0.0, x2 = largo, y2 = 0.0)

    @Test
    fun `un muro largo lleva puerta y ventana`() {
        val huecos = Sugerencias.tipicos(muro(4.2))

        assertEquals(2, huecos.size)
        assertEquals("puerta", huecos[0].tipo)
        assertEquals("ventana", huecos[1].tipo)
    }

    @Test
    fun `un muro corto solo lleva puerta`() {
        val huecos = Sugerencias.tipicos(muro(2.4))

        assertEquals(1, huecos.size)
        assertEquals("puerta", huecos[0].tipo)
    }

    @Test
    fun `un muro demasiado corto no lleva nada`() {
        assertTrue(Sugerencias.tipicos(muro(1.2)).isEmpty())
    }

    @Test
    fun `las sugerencias caben dentro del muro`() {
        val muro = muro(3.4)
        Sugerencias.tipicos(muro).forEach { hueco ->
            val desde = hueco.posicion * 3.4 - hueco.ancho / 2
            val hasta = hueco.posicion * 3.4 + hueco.ancho / 2
            assertTrue(desde >= -0.01)
            assertTrue(hasta <= 3.41)
        }
    }
}
