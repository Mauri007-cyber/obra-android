package es.obra.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import es.obra.app.datos.EstanciaEntity
import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraEntity
import es.obra.app.dominio.Geometria
import es.obra.app.dominio.Poligono
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Ficha de obra en A4: medidas, materiales y croquis.
 *
 * Se genera con el PdfDocument del propio Android: sin librerías, sin licencias y sin servidor.
 * No lleva precios porque la app no inventa precios de mercado: eso es el presupuesto, que va aparte.
 */
object PdfObra {

    private const val ANCHO = 595
    private const val ALTO = 842
    private const val MARGEN = 48f

    fun generar(
        contexto: Context,
        obra: ObraEntity,
        muros: List<MuroEntity> = emptyList(),
        huecos: List<HuecoEntity> = emptyList(),
        estancias: List<EstanciaEntity> = emptyList()
    ): File {
        val documento = PdfDocument()
        val pagina = documento.startPage(
            PdfDocument.PageInfo.Builder(ANCHO, ALTO, 1).create()
        )

        dibujar(pagina.canvas, obra, muros, huecos, estancias)
        documento.finishPage(pagina)

        val carpeta = File(contexto.filesDir, "export").apply { mkdirs() }
        val archivo = File(carpeta, "ficha-${obra.id}.pdf")
        archivo.outputStream().use { documento.writeTo(it) }
        documento.close()
        return archivo
    }

    private fun dibujar(
        lienzo: Canvas,
        obra: ObraEntity,
        muros: List<MuroEntity>,
        huecos: List<HuecoEntity>,
        estancias: List<EstanciaEntity>
    ) {
        val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14181C") }
        val gris = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7280") }
        val regla = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D6D1C7")
            strokeWidth = 0.8f
        }
        val acento = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2B01E") }
        val fondo = Paint().apply { color = Color.WHITE }
        lienzo.drawRect(0f, 0f, ANCHO.toFloat(), ALTO.toFloat(), fondo)

        var y = 74f

        // Cabecera
        lienzo.drawRect(MARGEN, y - 34f, MARGEN + 42f, y - 30f, acento)
        tinta.textSize = 22f
        tinta.isFakeBoldText = true
        lienzo.drawText("Ficha de obra", MARGEN, y, tinta)
        y += 26f

        tinta.textSize = 15f
        lienzo.drawText(obra.nombre, MARGEN, y, tinta)
        y += 18f

        tinta.isFakeBoldText = false
        gris.textSize = 10.5f
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
        y += 10f

        lienzo.drawLine(MARGEN, y, ANCHO - MARGEN, y, regla)
        y += 30f

        // Medidas
        tinta.textSize = 12.5f
        tinta.isFakeBoldText = true
        lienzo.drawText("Medidas", MARGEN, y, tinta)
        tinta.isFakeBoldText = false
        y += 22f

        // Con varias estancias, la superficie de la obra es la suma de las suyas; las medidas
        // generales solo valen cuando la obra es una sola habitación.
        val suma = estancias.sumOf { it.superficie }
        val superficie = if (suma > 0.0) suma else obra.superficieEfectiva
        val perimetro = Poligono.perimetro(muros) ?: (2 * (obra.largo + obra.ancho))
        val paredes = if (muros.isNotEmpty()) {
            (muros.filter { it.estado != "tirado" }
                .sumOf { Poligono.longitud(it) * obra.altura } -
                huecos.sumOf { it.ancho * it.alto }).coerceAtLeast(0.0)
        } else {
            perimetro * obra.altura
        }

        y = fila(lienzo, tinta, gris, y, "Largo", "${Geometria.formatear(obra.largo, 2)} m")
        y = fila(lienzo, tinta, gris, y, "Ancho", "${Geometria.formatear(obra.ancho, 2)} m")
        y = fila(lienzo, tinta, gris, y, "Altura libre", "${Geometria.formatear(obra.altura, 2)} m")
        y = fila(lienzo, tinta, gris, y, "Superficie", "${Geometria.formatear(superficie, 2)} m²")
        y = fila(lienzo, tinta, gris, y, "Perímetro", "${Geometria.formatear(perimetro, 2)} m")
        y = fila(lienzo, tinta, gris, y, "Superficie de paredes", "${Geometria.formatear(paredes, 2)} m²")
        if (estancias.size > 1) {
            val detalle = estancias.joinToString(" · ") {
                "${it.nombre} ${Geometria.formatear(it.superficie, 1)} m²"
            }
            y = fila(lienzo, tinta, gris, y, "Estancias", detalle.take(58))
        }

        y += 12f
        lienzo.drawLine(MARGEN, y, ANCHO - MARGEN, y, regla)
        y += 28f

        // Materiales
        tinta.textSize = 12.5f
        tinta.isFakeBoldText = true
        lienzo.drawText("Materiales orientativos", MARGEN, y, tinta)
        tinta.isFakeBoldText = false
        y += 22f

        val litros = Geometria.litrosPintura(paredes, manos = 2, rendimiento = 10.0)
        val botes = Geometria.botes(litros, 15.0)
        val cajas = Geometria.cajasAzulejo(superficie, merma = 0.10, m2Caja = 1.44)

        y = fila(lienzo, tinta, gris, y, "Pintura (2 manos, 10 m²/L)", "${Geometria.formatear(litros, 1)} L · $botes botes de 15 L")
        y = fila(lienzo, tinta, gris, y, "Solado o azulejo (+10 % merma)", "$cajas cajas de 1,44 m²")

        gris.textSize = 9f
        y += 16f
        lienzo.drawText(
            "Rendimientos de manual. Ajustar con el material real antes de comprar.",
            MARGEN,
            y,
            gris
        )
        y += 26f

        // Croquis
        if (muros.isNotEmpty()) {
            lienzo.drawLine(MARGEN, y, ANCHO - MARGEN, y, regla)
            y += 26f
            tinta.textSize = 12.5f
            tinta.isFakeBoldText = true
            lienzo.drawText("Croquis", MARGEN, y, tinta)
            tinta.isFakeBoldText = false
            y += 16f
            dibujarCroquis(
                lienzo,
                Poligono.enFila(muros),
                MARGEN,
                y,
                ANCHO - MARGEN * 2,
                250f,
                tinta,
                acento
            )
        }

        // Pie
        gris.textSize = 8.5f
        lienzo.drawText(
            "Medidas estimadas con la app Obra. No sustituyen a una medición certificada.",
            MARGEN,
            ALTO - 40f,
            gris
        )
    }

    private fun fila(
        lienzo: Canvas,
        tinta: Paint,
        gris: Paint,
        y: Float,
        etiqueta: String,
        valor: String
    ): Float {
        tinta.textSize = 11.5f
        lienzo.drawText(etiqueta, MARGEN, y, gris)
        val anchoValor = tinta.measureText(valor)
        lienzo.drawText(valor, ANCHO - MARGEN - anchoValor, y, tinta)
        return y + 18f
    }

    private fun dibujarCroquis(
        lienzo: Canvas,
        muros: List<MuroEntity>,
        x: Float,
        y: Float,
        ancho: Float,
        alto: Float,
        tinta: Paint,
        acento: Paint
    ) {
        val minX = muros.minOf { min(it.x1, it.x2) }
        val maxX = muros.maxOf { max(it.x1, it.x2) }
        val minY = muros.minOf { min(it.y1, it.y2) }
        val maxY = muros.maxOf { max(it.y1, it.y2) }

        val anchoMundo = max(maxX - minX, 0.5)
        val altoMundo = max(maxY - minY, 0.5)
        val escala = min(ancho / anchoMundo.toFloat(), alto / altoMundo.toFloat())

        val desfaseX = x + (ancho - anchoMundo.toFloat() * escala) / 2f
        val desfaseY = y + (alto - altoMundo.toFloat() * escala) / 2f

        val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tinta.color
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        val camino = Path()
        muros.forEach { muro ->
            val ax = desfaseX + ((muro.x1 - minX).toFloat()) * escala
            val ay = desfaseY + ((muro.y1 - minY).toFloat()) * escala
            val bx = desfaseX + ((muro.x2 - minX).toFloat()) * escala
            val by = desfaseY + ((muro.y2 - minY).toFloat()) * escala
            camino.moveTo(ax, ay)
            camino.lineTo(bx, by)
            lienzo.drawCircle(ax, ay, 2.2f, acento)
            lienzo.drawCircle(bx, by, 2.2f, acento)
        }
        lienzo.drawPath(camino, trazo)

        // Cota general
        val etiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tinta.color
            textSize = 9f
        }
        lienzo.drawText(
            "${Geometria.formatear(anchoMundo.toDouble(), 2)} × ${Geometria.formatear(altoMundo.toDouble(), 2)} m",
            desfaseX,
            desfaseY + altoMundo.toFloat() * escala + 16f,
            etiqueta
        )
    }
}
