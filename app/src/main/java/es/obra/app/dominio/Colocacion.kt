package es.obra.app.dominio

import java.text.Normalizer

/**
 * Las posiciones que se dicen en la frase, traducidas a instrucciones para el modelo.
 *
 * «La isla en el fondo, a un metro de la pared» no es una partida del presupuesto, es colocación:
 * aquí solo se convierte en frases en inglés que se añaden al prompt para que la imagen no
 * coloque los muebles donde quiera. No cuesta nada y no toca el presupuesto.
 */
object Colocacion {

    private val OBJETOS = mapOf(
        "isla" to "kitchen island",
        "encimera" to "worktop",
        "mesa" to "table",
        "sofa" to "sofa",
        "armario" to "wardrobe",
        "cama" to "bed",
        "nevera" to "fridge",
        "mueble" to "cabinet"
    )

    private val DESTINOS = mapOf(
        "pared" to "wall",
        "muro" to "wall",
        "tabique" to "partition wall",
        "ventana" to "window",
        "puerta" to "door"
    )

    /** «un metro» se dice tanto como «1 m». */
    private val NUMEROS = mapOf(
        "un" to "1", "uno" to "1", "una" to "1", "dos" to "2", "tres" to "3",
        "cuatro" to "4", "cinco" to "5", "seis" to "6", "siete" to "7",
        "ocho" to "8", "nueve" to "9", "diez" to "10", "medio" to "0.5"
    )

    private val DISTANCIA = Regex(
        "(?:a|a unos|a unas)\\s+([a-z0-9.,]+)\\s*(?:m|metros?|metro)\\s+" +
            "(?:de|del|de la|de las|de los)\\s+(pared|muro|tabique|ventana|puerta)"
    )

    /** «2x0,5 metros» es el tamaño del objeto, no su posición. */
    private val MEDIDAS = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(?:x|por)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:m|metros?)\\b"
    )

    /** «90 cm de alto»: sin altura el modelo dibuja una barra alta o una caja. */
    private val ALTURAS = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:cm|centimetros?)\\b")

    /** El objeto se nombra antes de la posición: «isla … a un metro de la pared». */
    private val DIRECCIONES = listOf(
        "en el fondo" to "at the far end of the room",
        "al fondo" to "at the far end of the room",
        "a la derecha" to "on the right side of the room",
        "a la izquierda" to "on the left side of the room",
        "en el centro" to "in the centre of the room",
        "junto a la ventana" to "next to the window",
        "frente a la ventana" to "facing the window"
    )

    /** Devuelve frases completas en inglés, sin repetir, listas para pegar en el prompt. */
    fun enIngles(frase: String): List<String> {
        val limpia = normalizar(frase)
        if (limpia.isBlank()) return emptyList()

        val salida = mutableListOf<String>()
        val objetosColocados = mutableSetOf<String>()

        DISTANCIA.findAll(limpia).forEach { encontrado ->
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            objetosColocados.add(objeto)
            val valor = encontrado.groupValues[1].replace(',', '.')
            val metros = NUMEROS[valor] ?: valor
            val destino = DESTINOS[encontrado.groupValues[2]] ?: "wall"
            salida.add("Place the $objeto about ${formato(metros)} m from the $destino.")
        }

        MEDIDAS.findAll(limpia).forEach { encontrado ->
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            objetosColocados.add(objeto)
            val largo = formato(encontrado.groupValues[1].replace(',', '.'))
            val ancho = formato(encontrado.groupValues[2].replace(',', '.'))
            salida.add("The $objeto measures about $largo m by $ancho m.")
        }

        DIRECCIONES.forEach { (clave, ingles) ->
            val posicion = limpia.indexOf(clave)
            if (posicion < 0) return@forEach
            val objeto = objetoAntes(limpia, posicion) ?: return@forEach
            objetosColocados.add(objeto)
            salida.add("Place the $objeto $ingles.")
        }

        if ("kitchen island" in objetosColocados) {
            val alto = ALTURAS.find(limpia)?.groupValues?.get(1)?.replace(',', '.')
            val medida = if (alto == null) {
                "about 90 cm high (standard counter height)"
            } else {
                "about ${formato(alto)} cm high"
            }
            salida.add(
                "The kitchen island is $medida, low and long like a real kitchen island, " +
                    "and those dimensions must be respected exactly."
            )
            // Sin esto el modelo arrima la isla a la primera pared que encuentra.
            salida.add("The kitchen island is freestanding, with clear floor space on all sides.")
        }

        return salida.distinct()
    }

    /** «1.0» se dice «1»; «2.5» se queda en «2.5»; «10» no se toca. */
    private fun formato(metros: String): String =
        if (metros.contains('.')) metros.trimEnd('0').trimEnd('.') else metros

    private fun objetoAntes(texto: String, posicion: Int): String? =
        OBJETOS.entries
            .mapNotNull { (clave, ingles) ->
                val indice = texto.lastIndexOf(clave, (posicion - 1).coerceAtLeast(0))
                if (indice >= 0 && posicion - indice <= 140) ingles to indice else null
            }
            .maxByOrNull { (_, indice) -> indice }
            ?.first

    /** Minúsculas y sin tildes: «mármol», «MÁRMOL» y «marmol» son lo mismo. */
    private fun normalizar(texto: String): String {
        val descompuesto = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        return descompuesto.replace(Regex("\\p{Mn}+"), "").trim()
    }
}
