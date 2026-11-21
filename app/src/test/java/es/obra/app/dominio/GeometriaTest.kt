package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Test

class GeometriaTest {

    @Test
    fun `area de un rectangulo`() {
        val puntos = listOf(
            Punto(0.0, 0.0),
            Punto(3.42, 0.0),
            Punto(3.42, 2.88),
            Punto(0.0, 2.88)
        )
        assertEquals(9.8496, Geometria.area(puntos), 0.0001)
    }

    @Test
    fun `area no depende del sentido de recorrido`() {
        val horario = listOf(
            Punto(0.0, 0.0),
            Punto(0.0, 2.0),
            Punto(4.0, 2.0),
            Punto(4.0, 0.0)
        )
        assertEquals(8.0, Geometria.area(horario), 0.0001)
    }

    @Test
    fun `perimetro de un rectangulo`() {
        val puntos = listOf(
            Punto(0.0, 0.0),
            Punto(3.42, 0.0),
            Punto(3.42, 2.88),
            Punto(0.0, 2.88)
        )
        assertEquals(12.6, Geometria.perimetro(puntos), 0.0001)
    }

    @Test
    fun `paredes descuentan los huecos`() {
        // 12,6 m de perímetro por 2,5 m de alto = 31,5 m², menos una puerta de 0,8 x 2,05.
        val area = Geometria.areaParedes(12.6, 2.5, listOf(0.8 to 2.05))
        assertEquals(29.86, area, 0.0001)
    }

    @Test
    fun `las paredes nunca son negativas`() {
        val area = Geometria.areaParedes(1.0, 1.0, listOf(5.0 to 5.0))
        assertEquals(0.0, area, 0.0001)
    }

    @Test
    fun `pintura con dos manos y rendimiento de diez`() {
        val litros = Geometria.litrosPintura(30.0, manos = 2, rendimiento = 10.0)
        assertEquals(6.0, litros, 0.0001)
        assertEquals(1, Geometria.botes(litros, 15.0))
    }

    @Test
    fun `botes redondean hacia arriba`() {
        assertEquals(2, Geometria.botes(15.5, 15.0))
        assertEquals(0, Geometria.botes(0.0, 15.0))
    }

    @Test
    fun `cajas de azulejo con merma`() {
        // 20 m² con 10 % de merma y cajas de 1,44 m²: 15,27 -> 16 cajas.
        assertEquals(16, Geometria.cajasAzulejo(20.0, 0.10, 1.44))
    }

    @Test
    fun `acepta coma y punto decimal`() {
        assertEquals(3.42, Geometria.parsear("3,42"), 0.0001)
        assertEquals(3.42, Geometria.parsear("3.42"), 0.0001)
        assertEquals(0.0, Geometria.parsear(""), 0.0001)
        assertEquals(0.0, Geometria.parsear("dos metros"), 0.0001)
        assertEquals(0.0, Geometria.parsear("-3"), 0.0001)
    }

    @Test
    fun `distancia de un punto a un segmento`() {
        // Punto (0, 1) frente a un segmento horizontal de (0,0) a (2,0): 1 m.
        assertEquals(
            1.0,
            Geometria.distanciaPuntoSegmento(Punto(0.0, 1.0), Punto(0.0, 0.0), Punto(2.0, 0.0)),
            0.0001
        )
        // Más allá del extremo, la distancia es al extremo, no a la recta.
        assertEquals(
            2.0,
            Geometria.distanciaPuntoSegmento(Punto(4.0, 0.0), Punto(0.0, 0.0), Punto(2.0, 0.0)),
            0.0001
        )
    }

    @Test
    fun `formatea con coma decimal`() {
        assertEquals("3,4", Geometria.formatear(3.42))
    }
}
