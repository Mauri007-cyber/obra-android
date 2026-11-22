package es.obra.app.dominio

import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumenReformaTest {

    private fun muro(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        estado: String = "original",
        id: Long = 1
    ) = MuroEntity(id = id, estanciaId = 1, x1 = x1, y1 = y1, x2 = x2, y2 = y2, estado = estado)

    @Test
    fun `dice los metros del tabique que se tira`() {
        val muros = listOf(
            muro(0.0, 0.0, 3.2, 0.0),
            muro(0.0, 0.0, 0.0, 4.0, estado = "tirado", id = 2)
        )

        val lineas = ResumenReforma.lineas(muros, emptyList(), 12.8, 2.5)

        assertTrue(lineas.first().contains("Se tira un tabique de 4,00 m"))
    }

    @Test
    fun `cuenta puertas y ventanas`() {
        val huecos = listOf(
            HuecoEntity(muroId = 1, posicion = 0.3, ancho = 0.8, alto = 2.05, tipo = "puerta"),
            HuecoEntity(muroId = 1, posicion = 0.7, ancho = 1.2, alto = 1.2, tipo = "ventana")
        )

        val lineas = ResumenReforma.lineas(listOf(muro(0.0, 0.0, 4.0, 0.0)), huecos, 10.0, 2.5)

        assertTrue(lineas.any { it.contains("1 puerta y 1 ventana") })
    }

    @Test
    fun `sin cambios solo describe la estancia`() {
        val lineas = ResumenReforma.lineas(
            listOf(muro(0.0, 0.0, 4.0, 0.0)),
            emptyList(),
            18.4,
            2.5
        )

        assertEquals(1, lineas.size)
        assertTrue(lineas.first().contains("18,40 m²"))
    }
}
