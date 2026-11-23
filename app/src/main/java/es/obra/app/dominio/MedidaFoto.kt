package es.obra.app.dominio

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Medir el suelo en una foto con una referencia conocida.
 *
 * Se marcan dos puntos de algo que se sabe cuánto mide (una puerta, una baldosa) y luego las
 * esquinas del suelo. Con esa escala se calcula la superficie real, sin ARCore, sin vídeo y sin
 * enviar la foto a ningún sitio.
 *
 * Los puntos van en píxeles de la foto: así la escala es la misma en horizontal y en vertical,
 * aunque la foto no sea cuadrada.
 */
object MedidaFoto {

    data class Punto(val x: Double, val y: Double)

    fun distancia(a: Punto, b: Punto): Double = hypot(b.x - a.x, b.y - a.y)

    /** Metros reales que mide cada píxel de la foto. */
    fun metrosPorPixel(a: Punto, b: Punto, metros: Double): Double? {
        if (metros <= 0.0 || !metros.isFinite()) return null
        val pixeles = distancia(a, b)
        if (pixeles < 1.0) return null
        val escala = metros / pixeles
        return escala.takeIf { it.isFinite() && it > 0.0 }
    }

    /** Superficie del polígono en m², con la fórmula del área (shoelace). */
    fun area(poligono: List<Punto>, metrosPorPixel: Double): Double {
        if (poligono.size < 3 || metrosPorPixel <= 0.0) return 0.0
        var doble = 0.0
        for (i in poligono.indices) {
            val a = poligono[i]
            val b = poligono[(i + 1) % poligono.size]
            doble += a.x * b.y - b.x * a.y
        }
        val pixeles = abs(doble) / 2.0
        val metros = pixeles * metrosPorPixel * metrosPorPixel
        return if (metros.isFinite() && metros > 0.0) metros else 0.0
    }

    /** Atajo para la pantalla: de los cuatro puntos a la superficie. */
    fun areaConReferencia(
        poligono: List<Punto>,
        referenciaA: Punto,
        referenciaB: Punto,
        metrosDeLaReferencia: Double
    ): Double? {
        val escala = metrosPorPixel(referenciaA, referenciaB, metrosDeLaReferencia) ?: return null
        val superficie = area(poligono, escala)
        return superficie.takeIf { it > 0.0 }
    }
}
