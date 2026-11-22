package es.obra.app.dominio

import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresupuestoTest {

    private fun muro(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        estado: String = "original",
        id: Long = 1
    ) = MuroEntity(id = id, estanciaId = 1, x1 = x1, y1 = y1, x2 = x2, y2 = y2, estado = estado)

    private val muros = listOf(
        muro(0.0, 0.0, 4.0, 0.0, estado = "tirado", id = 1),
        muro(0.0, 0.0, 3.0, 0.0, estado = "nuevo", id = 2)
    )

    private val huecos = listOf(
        HuecoEntity(muroId = 2, posicion = 0.5, ancho = 0.8, alto = 2.05, tipo = "puerta")
    )

    @Test
    fun `el presupuesto heredado no se usa con varias estancias`() {
        assertTrue(Presupuesto.permiteCalculoHeredado(0, "manual", false))
        assertTrue(Presupuesto.permiteCalculoHeredado(1, "plano", true))
        assertFalse(Presupuesto.permiteCalculoHeredado(2, "plano", true))
        assertFalse(Presupuesto.permiteCalculoHeredado(1, "manual", true))
        assertFalse(Presupuesto.permiteCalculoHeredado(1, "arcore", true))
    }

    @Test
    fun `las partidas salen de lo dibujado`() {
        val partidas = Presupuesto.partidas(muros, huecos, 12.0, 30.0, Tarifas.porDefecto)

        assertEquals(5, partidas.size)
        assertEquals(10.0, partidas[0].cantidad, 0.0001) // 4 m de tabique por 2,5 m de alto
        assertEquals(7.5, partidas[1].cantidad, 0.0001) // 3 m de tabique por 2,5 m de alto
        assertEquals(1.0, partidas[2].cantidad, 0.0001) // una puerta
        assertEquals(12.0, partidas[3].cantidad, 0.0001) // suelo
        assertEquals(30.0, partidas[4].cantidad, 0.0001) // pintura
    }

    @Test
    fun `base iva y total cuadran`() {
        val partidas = Presupuesto.partidas(muros, huecos, 12.0, 30.0, Tarifas.porDefecto)

        val base = Presupuesto.base(partidas)
        assertEquals(1337.5, base, 0.0001)
        assertEquals(280.875, Presupuesto.iva(base, Tarifas.porDefecto), 0.0001)
        assertEquals(1618.375, Presupuesto.total(base, Tarifas.porDefecto), 0.0001)
    }

    @Test
    fun `las paredes descuentan huecos y no cuentan lo que se tira`() {
        val paredes = Presupuesto.paredes(muros, huecos, 4.0, 3.0, 2.5)

        // 3 m de tabique nuevo por 2,5 m de alto, menos la puerta de 0,80 x 2,05.
        assertEquals(3.0 * 2.5 - 0.8 * 2.05, paredes, 0.0001)
    }

    @Test
    fun `los precios se guardan y se recuperan`() {
        val propios = Tarifas(tabiqueNuevo = 50.0, suelo = 40.0, iva = 10.0)

        assertEquals(propios, Tarifas.desdeTexto(propios.aTexto()))
    }

    @Test
    fun `sin texto se usan los precios de por defecto`() {
        assertEquals(Tarifas.porDefecto, Tarifas.desdeTexto(""))
        assertEquals(Tarifas.porDefecto, Tarifas.desdeTexto("basura"))
    }

    @Test
    fun `sin cambios no hay partidas de obra`() {
        val partidas = Presupuesto.partidas(
            listOf(muro(0.0, 0.0, 4.0, 0.0)),
            emptyList(),
            12.0,
            30.0,
            Tarifas.porDefecto
        )

        assertEquals(2, partidas.size) // suelo y pintura
        assertEquals("Suelo nuevo", partidas[0].concepto)
    }
}
