package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Medir el suelo en una foto con una puerta como referencia. */
class MedidaFotoTest {

    @Test
    fun `una referencia de un metro que ocupa 100 pixeles da un centimetro por pixel`() {
        val escala = MedidaFoto.metrosPorPixel(
            MedidaFoto.Punto(0.0, 0.0),
            MedidaFoto.Punto(100.0, 0.0),
            1.0
        )

        assertNotNull(escala)
        assertEquals(0.01, escala!!, 1e-9)
    }

    @Test
    fun `un cuadrado de 300 por 200 pixeles con esa escala son 6 metros`() {
        val escala = 0.01
        val poligono = listOf(
            MedidaFoto.Punto(0.0, 0.0),
            MedidaFoto.Punto(300.0, 0.0),
            MedidaFoto.Punto(300.0, 200.0),
            MedidaFoto.Punto(0.0, 200.0)
        )

        assertEquals(6.0, MedidaFoto.area(poligono, escala), 1e-9)
    }

    @Test
    fun `una habitacion en L mide la suma, no el rectangulo que la envuelve`() {
        val escala = 0.02
        val ele = listOf(
            MedidaFoto.Punto(0.0, 0.0),
            MedidaFoto.Punto(100.0, 0.0),
            MedidaFoto.Punto(100.0, 50.0),
            MedidaFoto.Punto(50.0, 50.0),
            MedidaFoto.Punto(50.0, 100.0),
            MedidaFoto.Punto(0.0, 100.0)
        )

        // 5000 + 2500 px² = 7500 px² → 3 m² con 0.02 m/px.
        assertEquals(3.0, MedidaFoto.area(ele, escala), 1e-9)
    }

    @Test
    fun `sin referencia valida no hay superficie`() {
        val punto = MedidaFoto.Punto(10.0, 10.0)

        assertNull(MedidaFoto.metrosPorPixel(punto, punto, 1.0))
        assertNull(MedidaFoto.metrosPorPixel(punto, MedidaFoto.Punto(50.0, 10.0), 0.0))
        assertEquals(
            0.0,
            MedidaFoto.area(listOf(punto, MedidaFoto.Punto(20.0, 20.0)), 0.01),
            1e-9
        )
    }

    @Test
    fun `el atajo de la pantalla coincide con el calculo a mano`() {
        val area = MedidaFoto.areaConReferencia(
            poligono = listOf(
                MedidaFoto.Punto(0.0, 0.0),
                MedidaFoto.Punto(200.0, 0.0),
                MedidaFoto.Punto(200.0, 100.0),
                MedidaFoto.Punto(0.0, 100.0)
            ),
            referenciaA = MedidaFoto.Punto(0.0, 0.0),
            referenciaB = MedidaFoto.Punto(100.0, 0.0),
            metrosDeLaReferencia = 0.8
        )

        assertNotNull(area)
        assertEquals(2 * 1 * 0.8 * 0.8, area!!, 1e-9)
    }
}
