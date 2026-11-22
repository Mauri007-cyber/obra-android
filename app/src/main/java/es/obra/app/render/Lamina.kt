package es.obra.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as LienzoAndroid
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import es.obra.app.datos.ObraEntity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * La lámina que se manda al cliente: dos imágenes iguales de encuadre —antes y después— y tres
 * frases en lenguaje llano. Es lo único que hace falta para que alguien que no lee planos
 * entienda qué se va a hacer en su casa.
 */
object Lamina {

    private val PAPEL = Color.rgb(247, 245, 240)
    private val TINTA = Color.rgb(20, 24, 28)
    private val GRIS = Color.rgb(107, 114, 128)
    private val OBRA = Color.rgb(242, 176, 30)
    private val HUECO = Color.rgb(237, 233, 225)

    fun cuadrada(
        obra: ObraEntity,
        antes: Bitmap,
        despues: Bitmap,
        lineas: List<String>
    ): Bitmap {
        val ancho = 1080
        val alto = 1080
        val mapa = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val lienzo = LienzoAndroid(mapa)
        lienzo.drawColor(PAPEL)

        lienzo.drawRect(0f, 0f, ancho.toFloat(), 10f, pincel(OBRA))

        lienzo.drawText(obra.nombre, 60f, 108f, pincel(TINTA, 50f, negrita = true))

        val subtitulo = buildString {
            if (obra.cliente.isNotBlank()) append(obra.cliente).append(" · ")
            append("${metro(obra.superficieEfectiva)} m²")
        }
        lienzo.drawText(subtitulo, 60f, 154f, pincel(GRIS, 26f))

        val altoPanel = 360
        val anchoPanel = 460
        val yPaneles = 196
        pintarPanel(lienzo, antes, Rect(60, yPaneles, 60 + anchoPanel, yPaneles + altoPanel), "ANTES")
        pintarPanel(
            lienzo,
            despues,
            Rect(560, yPaneles, 560 + anchoPanel, yPaneles + altoPanel),
            "DESPUÉS"
        )

        var y = 640f
        lineas.forEach { linea ->
            envolver(linea, pincel(TINTA, 27f), 940f).forEachIndexed { i, trozo ->
                lienzo.drawText(if (i == 0) "·  $trozo" else "   $trozo", 60f, y, pincel(TINTA, 27f))
                y += 34f
            }
            y += 10f
        }

        lienzo.drawText(
            "Medidas tomadas en obra. Es una idea, no un proyecto firmado.",
            60f,
            1030f,
            pincel(GRIS, 19f)
        )

        return mapa
    }

    fun a4(
        obra: ObraEntity,
        antes: Bitmap,
        despues: Bitmap,
        lineas: List<String>
    ): Bitmap {
        val ancho = 1754
        val alto = 1240
        val mapa = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val lienzo = LienzoAndroid(mapa)
        lienzo.drawColor(PAPEL)

        lienzo.drawRect(0f, 0f, ancho.toFloat(), 14f, pincel(OBRA))

        lienzo.drawText(obra.nombre, 90f, 150f, pincel(TINTA, 78f, negrita = true))

        val subtitulo = buildString {
            if (obra.cliente.isNotBlank()) append("Cliente: ").append(obra.cliente).append("   ·   ")
            append("Estancia de ${metro(obra.superficieEfectiva)} m²")
            if (obra.direccion.isNotBlank()) append("   ·   ").append(obra.direccion)
        }
        lienzo.drawText(subtitulo, 90f, 208f, pincel(GRIS, 30f))

        val yPaneles = 262
        val altoPanel = 560
        val anchoPanel = 740
        pintarPanel(lienzo, antes, Rect(90, yPaneles, 90 + anchoPanel, yPaneles + altoPanel), "ANTES")
        pintarPanel(
            lienzo,
            despues,
            Rect(924, yPaneles, 924 + anchoPanel, yPaneles + altoPanel),
            "DESPUÉS"
        )

        var y = 900f
        lineas.forEach { linea ->
            envolver(linea, pincel(TINTA, 34f), 1500f).forEachIndexed { i, trozo ->
                lienzo.drawText(if (i == 0) "·  $trozo" else "   $trozo", 90f, y, pincel(TINTA, 34f))
                y += 44f
            }
            y += 8f
        }

        lienzo.drawText(
            "Medidas tomadas en obra. Es una idea, no un proyecto firmado ni un presupuesto.",
            90f,
            1170f,
            pincel(GRIS, 22f)
        )

        return mapa
    }

    fun guardar(contexto: Context, mapa: Bitmap, nombre: String): File {
        val carpeta = File(contexto.filesDir, "export").apply { mkdirs() }
        val destino = File(carpeta, nombre)
        FileOutputStream(destino).use { salida ->
            mapa.compress(Bitmap.CompressFormat.PNG, 92, salida)
        }
        return destino
    }

    private fun pintarPanel(lienzo: LienzoAndroid, foto: Bitmap, destino: Rect, etiqueta: String) {
        lienzo.drawRect(destino, pincel(HUECO))
        pintarRecortado(lienzo, foto, destino)

        val pincelEtiqueta = pincel(TINTA, destino.width() / 22f, negrita = true)
        val anchoTexto = pincelEtiqueta.measureText(etiqueta)
        val caja = Rect(
            destino.left + 18,
            destino.top + 18,
            destino.left + 18 + (anchoTexto + 40).toInt(),
            destino.top + 18 + (destino.height() / 12)
        )
        lienzo.drawRect(caja, pincel(Color.argb(235, 247, 245, 240)))
        lienzo.drawText(
            etiqueta,
            caja.left + 20f,
            caja.bottom - caja.height() * 0.28f,
            pincelEtiqueta
        )
    }

    private fun pintarRecortado(lienzo: LienzoAndroid, foto: Bitmap, destino: Rect) {
        val proporcionDestino = destino.width().toFloat() / destino.height()
        val proporcionOrigen = foto.width.toFloat() / foto.height

        val origen = if (proporcionOrigen > proporcionDestino) {
            val ancho = (foto.height * proporcionDestino).toInt()
            val x = (foto.width - ancho) / 2
            Rect(x, 0, x + ancho, foto.height)
        } else {
            val alto = (foto.width / proporcionDestino).toInt()
            val y = (foto.height - alto) / 2
            Rect(0, y, foto.width, y + alto)
        }
        lienzo.drawBitmap(foto, origen, destino, pincel(Color.WHITE))
    }

    private fun pincel(color: Int, tamano: Float = 20f, negrita: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = tamano
            typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private fun envolver(texto: String, pincel: Paint, ancho: Float): List<String> {
        val palabras = texto.split(" ").filter { it.isNotBlank() }
        val salida = mutableListOf<String>()
        var actual = ""
        palabras.forEach { palabra ->
            val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
            if (pincel.measureText(prueba) <= ancho) {
                actual = prueba
            } else {
                if (actual.isNotEmpty()) salida.add(actual)
                actual = palabra
            }
        }
        if (actual.isNotEmpty()) salida.add(actual)
        return if (salida.isEmpty()) listOf(texto) else salida
    }

    private fun metro(valor: Double): String =
        String.format(Locale.forLanguageTag("es-ES"), "%.2f", valor).replace('.', ',')
}
