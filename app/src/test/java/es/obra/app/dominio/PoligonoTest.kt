package es.obra.app.dominio

import es.obra.app.datos.MuroEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoligonoTest {

    private fun muro(x1: Double, y1: Double, x2: Double, y2: Double, id: Long = 0) =
        MuroEntity(id = id, estanciaId = 1, x1 = x1, y1 = y1, x2 = x2, y2 = y2)

    @Test
    fun `las estancias se colocan una al lado de otra`() {
        val salon = listOf(muro(0.0, 0.0, 3.0, 0.0), muro(0.0, 2.0, 0.0, 0.0))
        val bano = listOf(
            muro(0.0, 0.0, 2.0, 0.0).copy(estanciaId = 2),
            muro(0.0, 2.0, 0.0, 0.0).copy(estanciaId = 2)
        )

        val enFila = Poligono.enFila(salon + bano)
        val primera = enFila.filter { it.estanciaId == 1L }
        val segunda = enFila.filter { it.estanciaId == 2L }

        // Cada estancia empieza en su esquina, sin pisarse con la anterior.
        assertEquals(0.0, primera.minOf { minOf(it.x1, it.x2) }, 0.0001)
        assertEquals(0.0, segunda.minOf { minOf(it.y1, it.y2) }, 0.0001)
        assertEquals(4.0, segunda.minOf { minOf(it.x1, it.x2) }, 0.0001)
        assertTrue(segunda.minOf { minOf(it.x1, it.x2) } > primera.maxOf { maxOf(it.x1, it.x2) })
    }

    @Test
    fun `un cuadrado cerrado da su superficie`() {
        val muros = listOf(
            muro(0.0, 0.0, 2.0, 0.0),
            muro(2.0, 0.0, 2.0, 2.0),
            muro(2.0, 2.0, 0.0, 2.0),
            muro(0.0, 2.0, 0.0, 0.0)
        )
        assertEquals(4.0, Poligono.superficie(muros)!!, 0.0001)
    }

    @Test
    fun `la superficie no depende del orden en que se dibujaron`() {
        val muros = listOf(
            muro(2.0, 2.0, 0.0, 2.0),
            muro(0.0, 0.0, 2.0, 0.0),
            muro(0.0, 2.0, 0.0, 0.0),
            muro(2.0, 0.0, 2.0, 2.0)
        )
        assertEquals(4.0, Poligono.superficie(muros)!!, 0.0001)
    }

    @Test
    fun `una forma en L da la superficie de la L, no la del rectangulo`() {
        // 4 x 3 menos el recorte de 2 x 2 = 8 m², mientras el rectángulo que la envuelve son 12.
        val muros = listOf(
            muro(0.0, 0.0, 4.0, 0.0),
            muro(4.0, 0.0, 4.0, 3.0),
            muro(4.0, 3.0, 2.0, 3.0),
            muro(2.0, 3.0, 2.0, 1.0),
            muro(2.0, 1.0, 0.0, 1.0),
            muro(0.0, 1.0, 0.0, 0.0)
        )
        assertEquals(8.0, Poligono.superficie(muros)!!, 0.0001)
    }

    @Test
    fun `un contorno que se cruza no da superficie`() {
        val muros = listOf(
            muro(0.0, 0.0, 4.0, 3.0),
            muro(4.0, 3.0, 0.0, 3.0),
            muro(0.0, 3.0, 3.0, 0.0),
            muro(3.0, 0.0, 0.0, 0.0)
        )
        assertNull(Poligono.superficie(muros))
    }

    @Test
    fun `un contorno abierto no da superficie`() {
        val muros = listOf(
            muro(0.0, 0.0, 2.0, 0.0),
            muro(2.0, 0.0, 2.0, 2.0),
            muro(2.0, 2.0, 0.0, 2.0)
        )
        assertNull(Poligono.superficie(muros))
    }

    @Test
    fun `un muro suelto estropea el contorno`() {
        val muros = listOf(
            muro(0.0, 0.0, 2.0, 0.0),
            muro(2.0, 0.0, 2.0, 2.0),
            muro(2.0, 2.0, 0.0, 2.0),
            muro(0.0, 2.0, 0.0, 0.0),
            muro(5.0, 5.0, 6.0, 5.0)
        )
        assertNull(Poligono.superficie(muros))
    }

    @Test
    fun `los extremos se pegan con tolerancia de centimetros`() {
        val muros = listOf(
            muro(0.0, 0.0, 2.0, 0.0),
            muro(2.02, 0.01, 2.0, 2.0),
            muro(2.0, 2.0, 0.0, 2.0),
            muro(0.0, 2.0, 0.0, 0.0)
        )
        assertEquals(4.0, Poligono.superficie(muros)!!, 0.05)
    }

    @Test
    fun `perimetro del contorno`() {
        val muros = listOf(
            muro(0.0, 0.0, 3.0, 0.0),
            muro(3.0, 0.0, 3.0, 2.0),
            muro(3.0, 2.0, 0.0, 2.0),
            muro(0.0, 2.0, 0.0, 0.0)
        )
        assertEquals(10.0, Poligono.perimetro(muros)!!, 0.0001)
    }
}
