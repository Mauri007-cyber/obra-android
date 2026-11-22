package es.obra.app.render

import android.graphics.Bitmap
import android.graphics.Canvas as LienzoAndroid
import android.graphics.Paint
import android.graphics.Path as CaminoAndroid
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pintor de la escena 3D, sin librerías.
 *
 * Cámara en perspectiva orbitando la estancia, y las caras de detrás a delante (algoritmo del
 * pintor) con sombreado por la luz que entra. Con esto se ve la habitación como se vería desde
 * la puerta, y sale igual en pantalla que en el PNG de 2048 px que se manda al cliente.
 */
object Render3D {

    class Vista(
        val ancho: Int,
        val alto: Int,
        val giro: Double,
        val inclinacion: Double,
        val zoom: Double = 1.0
    )

    /** Cara ya proyectada a píxeles: puntos planos (x0, y0, x1, y1…). */
    class Plana(val puntos: FloatArray, val color: Int, val contorno: Int)

    private val LUZ = Vec3(-0.35, -0.45, 0.82).normalizado()

    fun proyectar(escena: Escena, vista: Vista): List<Plana> {
        val inclinacion = vista.inclinacion.coerceIn(0.10, 1.30)
        val zoom = vista.zoom.coerceIn(0.5, 3.0)
        val distancia = escena.radio * 2.4 / zoom

        val ojo = Vec3(
            escena.centro.x + distancia * cos(inclinacion) * cos(vista.giro),
            escena.centro.y + distancia * cos(inclinacion) * sin(vista.giro),
            1.45 + distancia * sin(inclinacion)
        )
        val objetivo = Vec3(escena.centro.x, escena.centro.y, escena.alto * 0.45)

        val adelante = (objetivo - ojo).normalizado()
        val derecha = adelante.cruz(Vec3(0.0, 0.0, 1.0)).normalizado()
        val arriba = derecha.cruz(adelante).normalizado()

        val focal = min(vista.ancho, vista.alto) * 0.92
        val centroX = vista.ancho / 2.0
        val centroY = vista.alto / 2.0

        // Hacia dónde está la cámara, para quitar los muros que tapan la estancia.
        val mirando = Vec3(ojo.x - escena.centro.x, ojo.y - escena.centro.y, 0.0).normalizado()

        val ordenadas = mutableListOf<Pair<Double, Plana>>()

        escena.caras.forEach { cara ->
            if (cara.puntos.size < 3) return@forEach

            val mitad = cara.mitad
            if (mitad != null) {
                val lado = Vec3(mitad.x - escena.centro.x, mitad.y - escena.centro.y, 0.0)
                // Ese muro está entre el ojo y la habitación: se recorta para poder mirar dentro.
                if ((lado punto mirando) > 0.05) return@forEach
            }

            var normal = normalDe(cara.puntos) ?: return@forEach
            val centroCara = cara.puntos.reduce { s, p -> s + p } * (1.0 / cara.puntos.size)

            val haciaOjo = ojo - centroCara
            if (normal punto haciaOjo <= 0.0) {
                // Cara de espaldas: si es sólida no se pinta; el cristal se ve por los dos lados.
                if (cara.solido) return@forEach
                normal = normal * -1.0
            }

            val planos = FloatArray(cara.puntos.size * 2)
            var profundidad = 0.0
            var visible = true

            for (i in cara.puntos.indices) {
                val v = cara.puntos[i] - ojo
                val z = v punto adelante
                if (z <= 0.08) {
                    visible = false
                    break
                }
                profundidad += z
                planos[i * 2] = (centroX + focal * (v punto derecha) / z).toFloat()
                planos[i * 2 + 1] = (centroY - focal * (v punto arriba) / z).toFloat()
            }
            if (!visible) return@forEach

            val brillo = 0.88 + 0.12 * max(0.0, normal punto LUZ)
            ordenadas.add(
                profundidad / cara.puntos.size to Plana(
                    puntos = planos,
                    color = sombrear(cara.color, brillo),
                    contorno = sombrear(cara.color, brillo * 0.70)
                )
            )
        }

        ordenadas.sortByDescending { it.first }
        return ordenadas.map { it.second }
    }

    /** Dibuja las caras ya proyectadas en un lienzo de Compose. */
    fun dibujar(lienzo: DrawScope, planas: List<Plana>, grosorLinea: Float = 1.1f) {
        planas.forEach { plana ->
            val camino = Path().apply {
                moveTo(plana.puntos[0], plana.puntos[1])
                var i = 2
                while (i < plana.puntos.size) {
                    lineTo(plana.puntos[i], plana.puntos[i + 1])
                    i += 2
                }
                close()
            }
            lienzo.drawPath(camino, Color(plana.color))
            lienzo.drawPath(camino, Color(plana.contorno), style = Stroke(width = grosorLinea))
        }
    }

    /** La misma vista, en un PNG del tamaño que se pida (2048 px para imprimir, 1080 para WhatsApp). */
    fun bitmap(
        escena: Escena,
        vista: Vista,
        fondo: Int = 0xFFF7F5F0.toInt()
    ): Bitmap {
        val mapa = Bitmap.createBitmap(vista.ancho, vista.alto, Bitmap.Config.ARGB_8888)
        val lienzo = LienzoAndroid(mapa)
        lienzo.drawColor(fondo)

        val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1.2f, vista.ancho / 900f)
        }

        proyectar(escena, vista).forEach { plana ->
            val camino = CaminoAndroid().apply {
                moveTo(plana.puntos[0], plana.puntos[1])
                var i = 2
                while (i < plana.puntos.size) {
                    lineTo(plana.puntos[i], plana.puntos[i + 1])
                    i += 2
                }
                close()
            }
            relleno.color = plana.color
            lienzo.drawPath(camino, relleno)
            trazo.color = plana.contorno
            lienzo.drawPath(camino, trazo)
        }

        return mapa
    }

    private fun normalDe(puntos: List<Vec3>): Vec3? {
        val a = puntos[0]
        val b = puntos[1]
        val c = puntos[2]
        val normal = (b - a) cruz (c - a)
        return if (normal.norma() < 1e-9) null else normal.normalizado()
    }

    private fun sombrear(color: Int, factor: Double): Int {
        val alfa = (color ushr 24) and 0xFF
        val rojo = (((color shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val verde = (((color shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val azul = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (alfa shl 24) or (rojo shl 16) or (verde shl 8) or azul
    }
}
