package es.obra.app.dominio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import java.util.Locale

data class Punto(val x: Double, val y: Double)

/**
 * Geometría en metros. Sin píxeles: la escala se resuelve antes de llegar aquí.
 * Todas las superficies se pueden corregir a mano en la interfaz; esto solo propone.
 */
object Geometria {

    /** Área por fórmula del polígono (Gauss). Siempre positiva. */
    fun area(puntos: List<Punto>): Double {
        if (puntos.size < 3) return 0.0
        var suma = 0.0
        for (i in puntos.indices) {
            val a = puntos[i]
            val b = puntos[(i + 1) % puntos.size]
            suma += a.x * b.y - b.x * a.y
        }
        return abs(suma) / 2.0
    }

    fun perimetro(puntos: List<Punto>): Double {
        if (puntos.size < 2) return 0.0
        return puntos.indices.sumOf { i ->
            val a = puntos[i]
            val b = puntos[(i + 1) % puntos.size]
            hypot(b.x - a.x, b.y - a.y)
        }
    }

    /** Área de paredes descontando huecos, dada por pares (ancho, alto) en metros. */
    fun areaParedes(perimetro: Double, altura: Double, huecos: List<Pair<Double, Double>>): Double {
        val bruto = perimetro * altura
        val descontar = huecos.sumOf { (ancho, alto) -> ancho * alto }
        return (bruto - descontar).coerceAtLeast(0.0)
    }

    fun litrosPintura(area: Double, manos: Int, rendimiento: Double): Double =
        if (rendimiento <= 0.0 || manos <= 0) 0.0 else area * manos / rendimiento

    /** Botes necesarios. Nunca 0 si hay algo que pintar. */
    fun botes(litros: Double, tamano: Double): Int {
        if (litros <= 0.0 || tamano <= 0.0) return 0
        return ceil(litros / tamano).toInt().coerceAtLeast(1)
    }

    fun cajasAzulejo(m2: Double, merma: Double, m2Caja: Double): Int {
        if (m2 <= 0.0 || m2Caja <= 0.0) return 0
        return ceil(m2 * (1.0 + merma) / m2Caja).toInt().coerceAtLeast(1)
    }

    /** Distancia de un punto a un segmento: sirve para saber qué muro se ha tocado. */
    fun distanciaPuntoSegmento(p: Punto, a: Punto, b: Punto): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val largo2 = dx * dx + dy * dy
        if (largo2 <= 0.0) return hypot(p.x - a.x, p.y - a.y)
        var t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / largo2
        t = t.coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Un decimal como máximo: nunca prometer precisión que no hay. */
    fun formatear(valor: Double, decimales: Int = 1): String =
        String.format(Locale.forLanguageTag("es-ES"), "%.${decimales}f", valor)

    /** Acepta "3,42", "3.42" y devuelve 0 si no es un número válido. */
    fun parsear(texto: String): Double =
        texto.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
}
