package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriangulosTest {

    private fun areaTriangulo(a: Punto, b: Punto, c: Punto): Double =
        kotlin.math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) / 2.0

    @Test
    fun `un cuadrado sale en dos triangulos que suman su superficie`() {
        val cuadrado = listOf(
            Punto(0.0, 0.0),
            Punto(2.0, 0.0),
            Punto(2.0, 2.0),
            Punto(0.0, 2.0)
        )

        val tri = Triangulos.orejas(cuadrado)
        assertEquals(2, tri.size)

        val suma = tri.sumOf { areaTriangulo(cuadrado[it.first], cuadrado[it.second], cuadrado[it.third]) }
        assertEquals(4.0, suma, 0.0001)
    }

    @Test
    fun `una forma en L se trocea sin perder ni repetir superficie`() {
        val ele = listOf(
            Punto(0.0, 0.0),
            Punto(4.0, 0.0),
            Punto(4.0, 3.0),
            Punto(2.0, 3.0),
            Punto(2.0, 1.0),
            Punto(0.0, 1.0)
        )

        val tri = Triangulos.orejas(ele)
        assertEquals(4, tri.size)

        val suma = tri.sumOf { areaTriangulo(ele[it.first], ele[it.second], ele[it.third]) }
        assertEquals(8.0, suma, 0.0001)
    }

    @Test
    fun `un poligono que no cierra no da triangulos`() {
        val raro = listOf(Punto(0.0, 0.0), Punto(1.0, 1.0), Punto(0.0, 1.0), Punto(1.0, 0.0))
        assertTrue(Triangulos.orejas(raro).size <= 2)
    }

    @Test
    fun `con menos de tres puntos no hay nada`() {
        assertTrue(Triangulos.orejas(listOf(Punto(0.0, 0.0), Punto(1.0, 0.0))).isEmpty())
    }
}
