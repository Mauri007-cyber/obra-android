package es.obra.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import es.obra.app.dominio.Cambio
import es.obra.app.dominio.Capa
import es.obra.app.dominio.TipoCambio
import java.io.File
import java.text.Normalizer

/**
 * Enseñar en la foto lo que ya está aprobado, sin salir del móvil.
 *
 * Se tiñe la zona que corresponde a cada capa (suelo, paredes, techo) con el color del acabado
 * elegido, conservando las sombras y la textura de la foto: es pintura encima de la realidad,
 * no una imagen inventada. Las luces nuevas se encienden como un resplandor cálido.
 *
 * Todo es gratis, instantáneo y sin conexión: no hay servicio, ni clave, ni cuota que gastar.
 * Lo que no se puede hacer sin inventar (una cocina nueva con muebles que no están) no se hace.
 */
object RetoqueFoto {

    private const val LADO_MAX = 1600

    /** La foto retocada, lista para enseñar o compartir. */
    fun aplicar(foto: Bitmap, cambios: List<Cambio>): Bitmap {
        val base = escalar(foto)
        val salida = base.copy(Bitmap.Config.ARGB_8888, true)
        val lienzo = Canvas(salida)
        val ancho = salida.width.toFloat()
        val alto = salida.height.toFloat()

        val pintura = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        }

        cambios.filter { it.tipo.seVe }
            .groupBy { it.tipo.capa }
            .forEach { (capa, lista) ->
                val color = color(lista.first())
                val rojo = Color.red(color)
                val verde = Color.green(color)
                val azul = Color.blue(color)

                val sombreado = when (capa) {
                    Capa.TECHO -> LinearGradient(
                        0f, 0f, 0f, alto * 0.20f,
                        intArrayOf(argb(200, rojo, verde, azul), argb(0, rojo, verde, azul)),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )

                    Capa.PARED -> LinearGradient(
                        0f, alto * 0.03f, 0f, alto * 0.70f,
                        intArrayOf(
                            argb(0, rojo, verde, azul),
                            argb(130, rojo, verde, azul),
                            argb(130, rojo, verde, azul),
                            argb(0, rojo, verde, azul)
                        ),
                        floatArrayOf(0f, 0.22f, 0.72f, 1f),
                        Shader.TileMode.CLAMP
                    )

                    Capa.SUELO -> LinearGradient(
                        0f, alto * 0.56f, 0f, alto,
                        intArrayOf(
                            argb(0, rojo, verde, azul),
                            argb(80, rojo, verde, azul),
                            argb(180, rojo, verde, azul)
                        ),
                        floatArrayOf(0f, 0.18f, 1f),
                        Shader.TileMode.CLAMP
                    )

                    // Un hueco o un mueble no se pueden poner sin inventarlos: no se tocan.
                    Capa.HUECO, Capa.OBJETO, Capa.NINGUNA -> null
                }

                if (sombreado != null) {
                    pintura.shader = sombreado
                    lienzo.drawRect(0f, 0f, ancho, alto, pintura)
                }
            }

        val enciendeLuces = cambios.any {
            it.tipo == TipoCambio.PUNTO_LUZ ||
                it.tipo == TipoCambio.FOCO_EMPOTRADO ||
                it.tipo == TipoCambio.TIRA_LED
        }
        if (enciendeLuces) {
            val luz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                shader = RadialGradient(
                    ancho * 0.38f, alto * 0.26f, alto * 0.62f,
                    intArrayOf(Color.argb(95, 255, 228, 176), Color.argb(0, 255, 228, 176)),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            lienzo.drawRect(0f, 0f, ancho, alto, luz)
        }

        pintura.shader = null
        return salida
    }

    /** El color con el que se pinta un cambio, a partir de su acabado. */
    fun color(cambio: Cambio): Int {
        val acabado = normalizar(cambio.acabado)

        val paleta = when (cambio.tipo.capa) {
            Capa.SUELO -> PALETA_SUELO
            Capa.PARED -> PALETA_PARED
            Capa.TECHO -> PALETA_TECHO
            else -> return GRIS
        }

        paleta.entries.firstOrNull { (clave, _) -> acabado.contains(clave) }?.let { return it.value }

        return when (cambio.tipo.capa) {
            Capa.SUELO -> Color.rgb(176, 138, 98)
            Capa.PARED -> Color.rgb(233, 228, 216)
            Capa.TECHO -> Color.rgb(242, 240, 233)
            else -> GRIS
        }
    }

    /** Guarda el resultado en PNG dentro de la app y devuelve el archivo para compartirlo. */
    fun guardar(contexto: Context, foto: Bitmap, nombre: String = "retoque"): File {
        val carpeta = File(contexto.filesDir, "retoques").apply { mkdirs() }
        val archivo = File(carpeta, "$nombre-${System.currentTimeMillis()}.png")
        archivo.outputStream().use { salida ->
            foto.compress(Bitmap.CompressFormat.PNG, 100, salida)
        }
        return archivo
    }

    private fun escalar(foto: Bitmap): Bitmap {
        val lado = maxOf(foto.width, foto.height)
        if (lado <= LADO_MAX) return foto
        val factor = LADO_MAX.toFloat() / lado
        return Bitmap.createScaledBitmap(
            foto,
            (foto.width * factor).toInt().coerceAtLeast(1),
            (foto.height * factor).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun argb(alfa: Int, rojo: Int, verde: Int, azul: Int): Int =
        Color.argb(alfa, rojo, verde, azul)

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .trim()

    private val GRIS = Color.rgb(150, 150, 148)

    private val PALETA_SUELO = linkedMapOf(
        "roble claro" to Color.rgb(200, 162, 117),
        "roble oscuro" to Color.rgb(122, 75, 42),
        "nogal" to Color.rgb(92, 59, 34),
        "gris cemento" to Color.rgb(156, 156, 151),
        "blanco mate" to Color.rgb(237, 234, 226),
        "imitacion madera" to Color.rgb(181, 138, 95),
        "negro" to Color.rgb(53, 53, 53),
        "gris" to Color.rgb(140, 150, 155),
        "beige" to Color.rgb(217, 199, 172),
        "roble" to Color.rgb(192, 154, 107),
        "haya" to Color.rgb(211, 185, 142),
        "teka" to Color.rgb(139, 98, 57),
        "arena" to Color.rgb(216, 200, 166),
        "blanco" to Color.rgb(239, 236, 228)
    )

    private val PALETA_PARED = linkedMapOf(
        "blanco roto" to Color.rgb(241, 238, 229),
        "blanco brillo" to Color.rgb(244, 244, 240),
        "blanco mate" to Color.rgb(242, 240, 233),
        "verde salvia" to Color.rgb(163, 179, 146),
        "gris perla" to Color.rgb(200, 202, 206),
        "azul humo" to Color.rgb(154, 173, 188),
        "imitacion marmol" to Color.rgb(233, 231, 226),
        "microcemento" to Color.rgb(169, 167, 162),
        "lacado" to Color.rgb(227, 227, 225),
        "arena" to Color.rgb(220, 201, 171),
        "beige" to Color.rgb(228, 215, 191),
        "verde" to Color.rgb(126, 154, 115),
        "madera" to Color.rgb(185, 141, 95),
        "liso" to Color.rgb(232, 227, 217),
        "blanco" to Color.rgb(244, 242, 236),
        "negro" to Color.rgb(60, 60, 60),
        "gris" to Color.rgb(190, 190, 188)
    )

    private val PALETA_TECHO = linkedMapOf(
        "blanco mate" to Color.rgb(243, 241, 234),
        "blanco" to Color.rgb(245, 243, 237),
        "gris" to Color.rgb(206, 206, 202)
    )
}
