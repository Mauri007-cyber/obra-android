package es.obra.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import es.obra.app.datos.ObraEntity
import es.obra.app.dominio.Partida
import es.obra.app.dominio.Presupuesto
import es.obra.app.dominio.Tarifas
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Presupuesto en una hoja A4, para enseñarlo o imprimirlo.
 *
 * Lleva las partidas con sus metros y su precio, el total y el aviso de que los precios son
 * orientativos: es un documento para hablar con el cliente, no un contrato.
 */
object PdfPresupuesto {

    private const val ANCHO = 595
    private const val ALTO = 842
    private const val MARGEN = 42f

    fun generar(
        contexto: Context,
        obra: ObraEntity,
        partidas: List<Partida>,
        tarifas: Tarifas,
        base: Double,
        iva: Double,
        total: Double
    ): File {
        val documento = PdfDocument()
        val pagina = documento.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, 1).create())
        dibujar(pagina.canvas, obra, partidas, tarifas, base, iva, total)
        documento.finishPage(pagina)

        val carpeta = File(contexto.filesDir, "export").apply { mkdirs() }
        val destino = File(carpeta, "presupuesto-obra-${obra.id}.pdf")
        FileOutputStream(destino).use { documento.writeTo(it) }
        documento.close()
        return destino
    }

    private fun dibujar(
        lienzo: Canvas,
        obra: ObraEntity,
        partidas: List<Partida>,
        tarifas: Tarifas,
        base: Double,
        iva: Double,
        total: Double
    ) {
        lienzo.drawColor(Color.WHITE)

        lienzo.drawRect(0f, 0f, ANCHO.toFloat(), 10f, pincel(Color.rgb(242, 176, 30), 1f))

        var y = 78f
        lienzo.drawText("Presupuesto", MARGEN, y, pincel(Color.rgb(20, 24, 28), 26f, negrita = true))
        y += 26f
        lienzo.drawText(obra.nombre, MARGEN, y, pincel(Color.rgb(20, 24, 28), 14f))
        y += 18f

        val gris = pincel(Color.rgb(107, 114, 128), 10f)
        if (obra.direccion.isNotBlank()) {
            lienzo.drawText(obra.direccion, MARGEN, y, gris)
            y += 14f
        }
        if (obra.cliente.isNotBlank()) {
            lienzo.drawText("Cliente: ${obra.cliente}", MARGEN, y, gris)
            y += 14f
        }
        val fecha = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))
            .format(Date())
        lienzo.drawText(fecha, MARGEN, y, gris)
        y += 26f

        val linea = pincel(Color.rgb(214, 209, 199), 1f)
        lienzo.drawLine(MARGEN, y, ANCHO - MARGEN, y, linea)
        y += 22f

        val cabecera = pincel(Color.rgb(107, 114, 128), 9f, negrita = true)
        lienzo.drawText("CONCEPTO", MARGEN, y, cabecera)
        lienzo.drawText("CANTIDAD", 330f, y, cabecera)
        lienzo.drawText("PRECIO", 410f, y, cabecera)
        lienzo.drawText("IMPORTE", 490f, y, cabecera)
        y += 8f
        lienzo.drawLine(MARGEN, y, ANCHO - MARGEN, y, linea)
        y += 20f

        val tinta = pincel(Color.rgb(20, 24, 28), 11f)
        val tintaGris = pincel(Color.rgb(90, 96, 104), 10f)

        partidas.forEach { partida ->
            lienzo.drawText(partida.concepto, MARGEN, y, tinta)
            lienzo.drawText(
                "${Presupuesto.cantidad(partida.cantidad)} ${partida.unidad}",
                330f,
                y,
                tintaGris
            )
            lienzo.drawText("${Presupuesto.cantidad(partida.precio)} €", 410f, y, tintaGris)
            lienzo.drawText(Presupuesto.euros(partida.importe), 490f, y, tinta)
            y += 20f
            lienzo.drawLine(MARGEN, y - 6f, ANCHO - MARGEN, y - 6f, linea)
        }

        y += 14f
        lienzo.drawText("Base", 410f, y, tintaGris)
        lienzo.drawText(Presupuesto.euros(base), 490f, y, tinta)
        y += 18f
        lienzo.drawText("IVA (${Presupuesto.cantidad(tarifas.iva)} %)", 410f, y, tintaGris)
        lienzo.drawText(Presupuesto.euros(iva), 490f, y, tinta)
        y += 10f
        lienzo.drawLine(400f, y, ANCHO - MARGEN, y, linea)
        y += 22f
        lienzo.drawText("TOTAL", 410f, y, pincel(Color.rgb(20, 24, 28), 13f, negrita = true))
        lienzo.drawText(
            Presupuesto.euros(total),
            490f,
            y,
            pincel(Color.rgb(20, 24, 28), 13f, negrita = true)
        )

        y += 46f
        lienzo.drawText(
            "Precios orientativos con la mano de obra incluida, calculados sobre las medidas tomadas",
            MARGEN,
            y,
            gris
        )
        y += 13f
        lienzo.drawText(
            "en obra. No incluye material especial ni imprevistos. No es un contrato.",
            MARGEN,
            y,
            gris
        )
    }

    private fun pincel(color: Int, tamano: Float, negrita: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = tamano
            typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
}
