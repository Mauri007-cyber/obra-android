package es.obra.app.render

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Codificador GIF89a animado, en Kotlin puro.
 *
 * Escribe **fotograma a fotograma** según se generan: el vídeo de 8 s son 96 imágenes de
 * 640×480, y tenerlas todas en memoria serían más de 100 MB, que en un móvil corriente es
 * cerrar la app. Aquí solo hay una en memoria a la vez.
 */
object Gif {

    /**
     * Paleta sacada de unos pocos fotogramas.
     *
     * El color de cada cara no depende de dónde esté la cámara (sale de la orientación del muro
     * y de la luz), así que con tres muestras se cubren todos los colores de la escena.
     */
    fun paletaDe(muestras: List<IntArray>): List<Int> {
        val distintos = LinkedHashSet<Int>()
        muestras.forEach { frame ->
            for (color in frame) {
                distintos.add(color and 0xFFFFFF)
                if (distintos.size >= 256) return distintos.toList()
            }
        }
        if (distintos.isEmpty()) distintos.add(0xFFFFFF)
        return distintos.toList()
    }

    /** Codifica una lista de fotogramas ya en memoria. Para pruebas y escenas pequeñas. */
    fun codificar(fotogramas: List<IntArray>, ancho: Int, alto: Int, fps: Int): ByteArray {
        require(fotogramas.isNotEmpty()) { "sin fotogramas" }
        val salida = ByteArrayOutputStream()
        val escritor = Escritor(salida, ancho, alto, fps, paletaDe(fotogramas))
        escritor.iniciar()
        fotogramas.forEach { escritor.fotograma(it) }
        escritor.cerrar()
        return salida.toByteArray()
    }

    /**
     * Escritor incremental: se le van dando fotogramas y él los va comprimiendo y volcando.
     */
    class Escritor(
        private val salida: OutputStream,
        private val ancho: Int,
        private val alto: Int,
        fps: Int,
        paleta: List<Int>
    ) {
        private val retardo = (100.0 / fps.coerceAtLeast(1)).toInt().coerceAtLeast(2)
        private val tabla: List<Int> = if (paleta.isEmpty()) listOf(0xFFFFFF) else paleta
        private val indices = HashMap<Int, Int>(tabla.size * 2).apply {
            tabla.forEachIndexed { i, color -> put(color and 0xFFFFFF, i) }
        }

        private var iniciado = false

        fun iniciar() {
            if (iniciado) return
            iniciado = true

            salida.escribir("GIF89a")

            escribirCorto(salida, ancho)
            escribirCorto(salida, alto)
            salida.write(0xF7)
            salida.write(0)
            salida.write(0)

            for (i in 0 until 256) {
                val color = tabla.getOrElse(i) { 0 }
                salida.write((color shr 16) and 0xFF)
                salida.write((color shr 8) and 0xFF)
                salida.write(color and 0xFF)
            }

            salida.write(0x21)
            salida.write(0xFF)
            salida.write(0x0B)
            salida.escribir("NETSCAPE2.0")
            salida.write(0x03)
            salida.write(0x01)
            escribirCorto(salida, 0)
            salida.write(0)
        }

        fun fotograma(pixeles: IntArray) {
            if (!iniciado) iniciar()

            val mapeado = ByteArray(pixeles.size)
            for (i in pixeles.indices) {
                mapeado[i] = indiceDe(pixeles[i]).toByte()
            }

            salida.write(0x21)
            salida.write(0xF9)
            salida.write(0x04)
            salida.write(0x08)
            escribirCorto(salida, retardo)
            salida.write(0)
            salida.write(0)

            salida.write(0x2C)
            escribirCorto(salida, 0)
            escribirCorto(salida, 0)
            escribirCorto(salida, ancho)
            escribirCorto(salida, alto)
            salida.write(0)

            salida.write(8)
            escribirBloques(salida, comprimir(mapeado))
            salida.write(0)
        }

        fun cerrar() {
            if (!iniciado) return
            salida.write(0x3B)
            salida.flush()
        }

        /**
         * Índice de un color en la paleta.
         *
         * El cristal se mezcla con lo que hay detrás, así que puede salir un color que no
         * estaba en las muestras: se busca el más parecido y se guarda en la caché.
         */
        private fun indiceDe(color: Int): Int {
            val limpio = color and 0xFFFFFF
            val directo = indices[limpio]
            if (directo != null) return directo

            var mejor = 0
            var distancia = Int.MAX_VALUE
            val rojo = (limpio shr 16) and 0xFF
            val verde = (limpio shr 8) and 0xFF
            val azul = limpio and 0xFF

            tabla.forEachIndexed { i, candidato ->
                val dr = rojo - ((candidato shr 16) and 0xFF)
                val dg = verde - ((candidato shr 8) and 0xFF)
                val db = azul - (candidato and 0xFF)
                val d = dr * dr + dg * dg + db * db
                if (d < distancia) {
                    distancia = d
                    mejor = i
                }
            }

            indices[limpio] = mejor
            return mejor
        }
    }

    /** LZW de GIF: códigos de 8 bits, con reinicio cuando la tabla llega a 4096. */
    private fun comprimir(indices: ByteArray): ByteArray {
        val tamanoMinimo = 8
        val salida = ByteArrayOutputStream()
        val bits = Empaquetador(salida)

        val limpia = 1 shl tamanoMinimo
        val fin = limpia + 1
        var tamano = tamanoMinimo + 1
        var siguiente = fin + 1

        val diccionario = HashMap<Int, Int>(4096)

        bits.escribir(limpia, tamano)

        if (indices.isEmpty()) {
            bits.escribir(fin, tamano)
            bits.cerrar()
            return salida.toByteArray()
        }

        var anterior = indices[0].toInt() and 0xFF

        for (i in 1 until indices.size) {
            val actual = indices[i].toInt() and 0xFF
            val clave = (anterior shl 8) or actual
            val existente = diccionario[clave]

            if (existente != null) {
                anterior = existente
                continue
            }

            bits.escribir(anterior, tamano)

            if (siguiente < 4096) {
                diccionario[clave] = siguiente
                siguiente++
                // El decodificador va un código por detrás: el tamaño crece al superar
                // 2^tamano, no justo al llegar.
                if (siguiente > (1 shl tamano) && tamano < 12) tamano++
            } else {
                bits.escribir(limpia, tamano)
                diccionario.clear()
                siguiente = fin + 1
                tamano = tamanoMinimo + 1
            }

            anterior = actual
        }

        bits.escribir(anterior, tamano)
        bits.escribir(fin, tamano)
        bits.cerrar()
        return salida.toByteArray()
    }

    private class Empaquetador(private val salida: ByteArrayOutputStream) {
        private var acumulado = 0
        private var bits = 0

        fun escribir(codigo: Int, tamano: Int) {
            acumulado = acumulado or (codigo shl bits)
            bits += tamano
            while (bits >= 8) {
                salida.write(acumulado and 0xFF)
                acumulado = acumulado ushr 8
                bits -= 8
            }
        }

        fun cerrar() {
            if (bits > 0) {
                salida.write(acumulado and 0xFF)
                acumulado = 0
                bits = 0
            }
        }
    }

    private fun escribirBloques(salida: OutputStream, datos: ByteArray) {
        var posicion = 0
        while (posicion < datos.size) {
            val trozo = minOf(255, datos.size - posicion)
            salida.write(trozo)
            salida.write(datos, posicion, trozo)
            posicion += trozo
        }
    }

    private fun escribirCorto(salida: OutputStream, valor: Int) {
        salida.write(valor and 0xFF)
        salida.write((valor shr 8) and 0xFF)
    }

    private fun OutputStream.escribir(texto: String) {
        this.write(texto.toByteArray(Charsets.US_ASCII))
    }
}
