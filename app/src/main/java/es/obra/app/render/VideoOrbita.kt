package es.obra.app.render

import java.io.File
import kotlin.math.PI

/**
 * Ocho segundos dando la vuelta a la estancia, en GIF.
 *
 * Es el formato que se abre solo en WhatsApp sin sonido y sin instalar nada, y sirve para lo
 * mismo en un móvil viejo que en un portátil. La cámara va despacio y siempre mirando al centro:
 * el cliente ve la habitación entera, no un plano.
 *
 * Se escribe según se calcula: **una imagen en memoria a la vez**, para que no reviente un móvil
 * con poca memoria.
 */
object VideoOrbita {

    const val ANCHO = 640
    const val ALTO = 480
    const val FOTOGRAMAS = 96
    const val FPS = 12

    private const val FONDO = 0xFFF7F5F0.toInt()

    fun generar(
        escena: Escena,
        destino: File,
        ancho: Int = ANCHO,
        alto: Int = ALTO,
        fotogramas: Int = FOTOGRAMAS,
        fps: Int = FPS,
        giroInicial: Double = 0.85,
        inclinacion: Double = 0.40
    ): File {
        require(fotogramas > 0) { "sin fotogramas" }

        // Tres muestras bastan para tener todos los colores: no dependen de la cámara.
        val muestras = listOf(0, fotogramas / 3, (2 * fotogramas) / 3)
            .map { i ->
                fotograma(escena, giro(giroInicial, i, fotogramas), ancho, alto, inclinacion)
            }
        val paleta = Gif.paletaDe(muestras)

        destino.parentFile?.mkdirs()
        destino.outputStream().buffered().use { salida ->
            val escritor = Gif.Escritor(salida, ancho, alto, fps, paleta)
            escritor.iniciar()
            for (i in 0 until fotogramas) {
                escritor.fotograma(
                    fotograma(escena, giro(giroInicial, i, fotogramas), ancho, alto, inclinacion)
                )
            }
            escritor.cerrar()
        }

        return destino
    }

    /** Un fotograma suelto, en píxeles RGB. */
    fun fotograma(
        escena: Escena,
        giro: Double,
        ancho: Int,
        alto: Int,
        inclinacion: Double = 0.40
    ): IntArray {
        val lienzo = Rasterizador.lienzo(ancho, alto, FONDO)
        Render3D.proyectar(escena, Render3D.Vista(ancho, alto, giro, inclinacion, 1.0))
            .forEach { plana ->
                Rasterizador.rellenar(lienzo, ancho, alto, plana.puntos, plana.color)

                val vertices = plana.puntos.size / 2
                for (i in 0 until vertices) {
                    val j = (i + 1) % vertices
                    Rasterizador.linea(
                        lienzo,
                        ancho,
                        alto,
                        plana.puntos[i * 2],
                        plana.puntos[i * 2 + 1],
                        plana.puntos[j * 2],
                        plana.puntos[j * 2 + 1],
                        plana.contorno
                    )
                }
            }
        return lienzo
    }

    private fun giro(inicial: Double, indice: Int, total: Int): Double =
        inicial + indice * 2.0 * PI / total
}
