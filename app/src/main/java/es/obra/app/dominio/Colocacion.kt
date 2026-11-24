package es.obra.app.dominio

import java.text.Normalizer

/**
 * Las posiciones que se dicen en la frase, traducidas a instrucciones para el modelo.
 *
 * «La isla en el fondo, a un metro de la pared» no es una partida del presupuesto, es colocación:
 * aquí solo se convierte en frases en inglés que se añaden al prompt para que la imagen no
 * coloque los muebles donde quiera. No cuesta nada y no toca el presupuesto.
 *
 * También se entiende la **orientación**, que es lo que más se le escapa al modelo: «en horizontal
 * respecto a la imagen», «paralelo a la pared del fondo», «pegado a la pared de la derecha». Sin
 * eso, un cuadro o una encimera acaban girados o en la pared contraria.
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

    /** El lado de la pared: sin él, «junto a la pared» acaba en la pared equivocada. */
    private val LADOS = mapOf(
        "de la derecha" to "the right-hand wall",
        "a la derecha" to "the right-hand wall",
        "de la izquierda" to "the left-hand wall",
        "a la izquierda" to "the left-hand wall",
        "del fondo" to "the back wall",
        "al fondo" to "the back wall",
        "de enfrente" to "the wall facing the camera",
        "de frente" to "the wall facing the camera"
    )

    private val LADO = "(?:de la derecha|a la derecha|de la izquierda|a la izquierda|" +
        "del fondo|al fondo|de enfrente|de frente)"

    /** «la pared», «el muro del fondo», «la pared de la derecha». */
    private val PARED = "(?:la |el )?(?:pared|muro|tabique)(?:\\s+($LADO))?"

    private val DISTANCIA = Regex(
        "(?:a|a unos|a unas)\\s+([a-z0-9.,]+)\\s*(?:m|metros?|metro)\\s+" +
            "(?:de|del|de la|de las|de los)\\s+(pared|muro|tabique|ventana|puerta)" +
            "(?:\\s+($LADO))?"
    )

    /** «2x0,5 metros» es el tamaño del objeto, no su posición. */
    private val MEDIDAS = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(?:x|por)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:m|metros?)\\b"
    )

    /** «90 cm de alto»: sin altura el modelo dibuja una barra alta o una caja. */
    private val ALTURAS = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:cm|centimetros?)\\b")

    /**
     * «en horizontal a la imagen»: el eje se mide contra la foto, no contra la habitación.
     * Si lo que sigue es «respecto a la pared», no es un eje de la foto: lo trata [PARALELO].
     */
    private val EJE = Regex(
        "(?:en\\s+)?(horizontal|vertical)" +
            "(?:\\s+(?:a|con|respecto\\s+a|en)\\s+(?:la\\s+)?(imagen|foto|camara|pantalla))?" +
            "(?!\\s+respecto\\s+a\\s+(?:la\\s+)?(?:pared|muro|tabique))"
    )

    /** «paralelo a la pared del fondo», «pegado a la pared», «a lo largo del muro». */
    private val PARALELO = Regex(
        "((?:paralel[oa]s?\\s+a|a lo largo de|pegad[oa]s?\\s+a|arrimad[oa]s?\\s+a|" +
            "(?:en\\s+)?horizontal\\s+respecto\\s+a|en paralelo\\s+a|contra))\\s+$PARED"
    )

    private val PERPENDICULAR = Regex("perpendicular(?:es)?\\s+a\\s+$PARED")

    /** «en la pared de la izquierda»: el objeto va a esa pared y no a otra. */
    private val EN_PARED = Regex("\\ben\\s+$PARED\\b")

    /** «de pared a pared»: ocupa el ancho completo de la pared. */
    private val DE_PARED_A_PARED = Regex("de\\s+$PARED\\s+a\\s+$PARED")

    /** «respecto a la pared del fondo» sin decir el eje: al menos se ata a esa pared. */
    private val RESPECTO = Regex("respecto\\s+a\\s+$PARED")

    /** «mirando a la cámara», «de cara a la ventana». */
    private val MIRANDO = Regex(
        "(?:mirando|de cara|orientad[oa])\\s+(?:hacia\\s+|a\\s+)?(?:la\\s+)?" +
            "(camara|pared|ventana|puerta|sofa|mesa)"
    )

    private val MIRADAS = mapOf(
        "camara" to "camera",
        "pared" to "wall",
        "ventana" to "window",
        "puerta" to "door",
        "sofa" to "sofa",
        "mesa" to "table"
    )

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
        val usados = mutableListOf<IntRange>()

        fun usado(rango: IntRange): Boolean =
            usados.any { rango.first <= it.last && it.first <= rango.last }

        fun colocar(objeto: String, rango: IntRange, texto: String) {
            usados.add(rango)
            objetosColocados.add(objeto)
            salida.add(texto)
        }

        DISTANCIA.findAll(limpia).forEach { encontrado ->
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            val valor = encontrado.groupValues[1].replace(',', '.')
            val metros = NUMEROS[valor] ?: valor
            val base = DESTINOS[encontrado.groupValues[2]] ?: "wall"
            val lado = LADOS[encontrado.groupValues[3]]
            val destino = if (base == "wall" && lado != null) lado else base
            // El lado ya trae «the»: «from the the back wall» sonaría a error del traductor.
            val lugar = destino.removePrefix("the ")
            colocar(
                objeto,
                encontrado.range,
                "Place the $objeto about ${formato(metros)} m from the $lugar."
            )
        }

        MEDIDAS.findAll(limpia).forEach { encontrado ->
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            val largo = formato(encontrado.groupValues[1].replace(',', '.'))
            val ancho = formato(encontrado.groupValues[2].replace(',', '.'))
            colocar(
                objeto,
                encontrado.range,
                "The $objeto measures about $largo m by $ancho m."
            )
        }

        PARALELO.findAll(limpia).forEach { encontrado ->
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            val modo = encontrado.groupValues[1]
            val pared = pared(encontrado.groupValues[2])
            val texto = when {
                modo.startsWith("pegad") || modo.startsWith("arrimad") || modo == "contra" ->
                    "Place the $objeto flat against $pared, parallel to it."

                modo.contains("a lo largo") ->
                    "Place the $objeto lengthways along $pared, parallel to it."

                else -> "Place the $objeto parallel to $pared."
            }
            colocar(objeto, encontrado.range, texto)
        }

        PERPENDICULAR.findAll(limpia).forEach { encontrado ->
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            colocar(
                objeto,
                encontrado.range,
                "Place the $objeto at a right angle to ${pared(encontrado.groupValues[1])}, " +
                    "sticking out from it."
            )
        }

        DE_PARED_A_PARED.findAll(limpia).forEach { encontrado ->
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            val desde = pared(encontrado.groupValues[1])
            val hasta = pared(encontrado.groupValues[2])
            val texto = if (encontrado.groupValues[1] == encontrado.groupValues[2]) {
                "The $objeto runs the whole length of $desde, from corner to corner."
            } else {
                "The $objeto spans from $desde to $hasta."
            }
            colocar(objeto, encontrado.range, texto)
        }

        EN_PARED.findAll(limpia).forEach { encontrado ->
            if (usado(encontrado.range)) return@forEach
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            colocar(
                objeto,
                encontrado.range,
                "The $objeto goes on ${pared(encontrado.groupValues[1])}, not on another wall."
            )
        }

        RESPECTO.findAll(limpia).forEach { encontrado ->
            if (usado(encontrado.range)) return@forEach
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            colocar(
                objeto,
                encontrado.range,
                "Place the $objeto in relation to ${pared(encontrado.groupValues[1])}: " +
                    "that wall is the reference."
            )
        }

        EJE.findAll(limpia).forEach { encontrado ->
            if (usado(encontrado.range)) return@forEach
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            val eje = encontrado.groupValues[1]
            val enFoto = encontrado.groupValues[2].isNotBlank()
            val texto = when {
                eje == "horizontal" && enFoto ->
                    "Place the $objeto horizontal in the image frame: its long side runs left " +
                        "to right across the photo."

                eje == "horizontal" ->
                    "Place the $objeto horizontally, with its long side parallel to the floor."

                enFoto ->
                    "Place the $objeto vertical in the image frame: its long side runs from " +
                        "the floor to the ceiling."

                else ->
                    "Place the $objeto standing upright and vertical, not lying down."
            }
            colocar(objeto, encontrado.range, texto)
        }

        MIRANDO.findAll(limpia).forEach { encontrado ->
            if (usado(encontrado.range)) return@forEach
            val objeto = objetoAntes(limpia, encontrado.range.first) ?: return@forEach
            val hacia = MIRADAS[encontrado.groupValues[1]] ?: return@forEach
            colocar(objeto, encontrado.range, "The $objeto faces the $hacia.")
        }

        DIRECCIONES.forEach { (clave, ingles) ->
            val posicion = limpia.indexOf(clave)
            if (posicion < 0) return@forEach
            if (usados.any { posicion in it }) return@forEach
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

    /** La pared con su lado si se ha dicho; sin lado, «the wall» a secas. */
    private fun pared(lado: String): String = LADOS[lado] ?: "the wall"

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
