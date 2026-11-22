package es.obra.app.render

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Relleno de polígonos sobre un array de píxeles, sin Android y sin librerías.
 *
 * Así el vídeo se puede generar y comprobar fuera del móvil: lo que se ve en el GIF es
 * exactamente lo que pinta este código.
 */
object Rasterizador {

    /** Array de píxeles RGB (sin alfa), en formato 0xRRGGBB. */
    fun lienzo(ancho: Int, alto: Int, fondo: Int): IntArray =
        IntArray(ancho * alto) { fondo and 0xFFFFFF }

    fun rellenar(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        puntos: FloatArray,
        color: Int
    ) {
        val vertices = puntos.size / 2
        if (vertices < 3) return

        var yMin = Float.MAX_VALUE
        var yMax = -Float.MAX_VALUE
        for (i in 0 until vertices) {
            val y = puntos[i * 2 + 1]
            if (y < yMin) yMin = y
            if (y > yMax) yMax = y
        }

        val desde = max(0, floor(yMin).toInt())
        val hasta = min(alto - 1, ceil(yMax).toInt())
        if (hasta < desde) return

        val cruces = FloatArray(vertices)

        for (y in desde..hasta) {
            val yc = y + 0.5f
            var cuenta = 0

            for (i in 0 until vertices) {
                val j = (i + 1) % vertices
                val x1 = puntos[i * 2]
                val y1 = puntos[i * 2 + 1]
                val x2 = puntos[j * 2]
                val y2 = puntos[j * 2 + 1]

                if ((y1 <= yc && y2 > yc) || (y2 <= yc && y1 > yc)) {
                    cruces[cuenta] = x1 + (yc - y1) / (y2 - y1) * (x2 - x1)
                    cuenta++
                }
            }

            if (cuenta < 2) continue
            cruces.sort(0, cuenta)

            var i = 0
            while (i + 1 < cuenta) {
                val xa = max(0, ceil(cruces[i]).toInt())
                val xb = min(ancho - 1, floor(cruces[i + 1]).toInt())
                var x = xa
                while (x <= xb) {
                    mezclar(pixeles, y * ancho + x, color)
                    x++
                }
                i += 2
            }
        }
    }

    /** Arista de 1 px, para que se distingan dos muros del mismo color. */
    fun linea(
        pixeles: IntArray,
        ancho: Int,
        alto: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Int
    ) {
        var x = x0.toInt()
        var y = y0.toInt()
        val xFin = x1.toInt()
        val yFin = y1.toInt()

        val pasoX = if (x < xFin) 1 else -1
        val pasoY = if (y < yFin) 1 else -1
        val dx = kotlin.math.abs(xFin - x)
        val dy = -kotlin.math.abs(yFin - y)
        var error = dx + dy

        var vueltas = 0
        while (vueltas < 4000) {
            vueltas++
            if (x in 0 until ancho && y in 0 until alto) mezclar(pixeles, y * ancho + x, color)
            if (x == xFin && y == yFin) break
            val doble = 2 * error
            if (doble >= dy) {
                error += dy
                x += pasoX
            }
            if (doble <= dx) {
                error += dx
                y += pasoY
            }
        }
    }

    private fun mezclar(destino: IntArray, indice: Int, color: Int) {
        val alfa = (color ushr 24) and 0xFF
        if (alfa == 0) return

        if (alfa == 255) {
            destino[indice] = color and 0xFFFFFF
            return
        }

        val fondo = destino[indice]
        val encima = alfa / 255f
        val debajo = 1f - encima

        val rojo = (((color shr 16) and 0xFF) * encima + ((fondo shr 16) and 0xFF) * debajo).toInt()
        val verde = (((color shr 8) and 0xFF) * encima + ((fondo shr 8) and 0xFF) * debajo).toInt()
        val azul = ((color and 0xFF) * encima + (fondo and 0xFF) * debajo).toInt()

        destino[indice] = (rojo shl 16) or (verde shl 8) or azul
    }
}
