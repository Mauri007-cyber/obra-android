package es.obra.app.dominio

import java.text.Normalizer

/**
 * De la lista aprobada al texto que entiende un modelo de imagen.
 *
 * No es una traducción libre: cada partida del catálogo tiene su nombre en inglés y su zona
 * (suelo, pared, techo, hueco, objeto), y el prompt repite siempre lo que **no** se debe tocar.
 * Eso es lo que mantiene la habitación reconocible: misma cámara, mismas ventanas, mismos muebles.
 *
 * Es texto puro, sin red: se puede probar y cambiar sin gastar una sola imagen.
 */
object PromptReforma {

    /**
     * Devuelve el prompt, o vacío si no hay nada visible que enseñar (solo albañilería, por
     * ejemplo). El llamante decide qué hacer entonces; aquí no se inventa una reforma.
     */
    fun para(
        cambios: List<Cambio>,
        frase: String = "",
        estancia: String = "",
        colocaciones: List<String> = emptyList(),
        despejar: Boolean = false
    ): String {
        val visibles = cambios.filter { it.tipo.seVe }
        if (visibles.isEmpty()) return ""

        val frases = buildList {
            visibles.filter { it.tipo.capa == Capa.SUELO }.forEach { add(fraseSuelo(it)) }
            visibles.filter { it.tipo.capa == Capa.PARED }.forEach { add(frasePared(it)) }
            visibles.filter { it.tipo.capa == Capa.TECHO }.forEach { add(fraseTecho(it)) }
            visibles.filter { it.tipo.capa == Capa.HUECO }.forEach { add(fraseHueco(it)) }
            visibles.filter { it.tipo.capa == Capa.OBJETO }.forEach { add(fraseObjeto(it)) }
        }.distinct()

        if (frases.isEmpty()) return ""

        val sitio = if (estancia.isBlank()) "room" else estancia.trim().lowercase()
        val objetos = visibles.filter { it.tipo.capa == Capa.OBJETO }.map { material(it) }
        val vaciar = despejar || pideDespeje(frase)
        val luz = if (visibles.any { it.tipo.enciendeLuz }) {
            " Add warm recessed ceiling lighting and a soft warm glow. "
        } else {
            " "
        }
        val nota = if (frase.isBlank()) {
            ""
        } else {
            " Client's note: ${frase.trim().replace(Regex("\\s+"), " ")}. "
        }

        // Solo se pide conservar lo que no se ha pedido cambiar: si se cambia una ventana, las
        // puertas siguen intactas, y al contrario. Antes el modelo recibía dos órdenes contrarias.
        val tocaVentanas = visibles.any { it.tipo in VENTANAS }
        val tocaPuertas = visibles.any { it.tipo in PUERTAS }
        // Suelo y techo también: si no se cambian y no se nombran, el modelo los reinventa.
        val tocaSuelo = visibles.any { it.tipo.capa == Capa.SUELO }
        val tocaTecho = visibles.any { it.tipo.capa == Capa.TECHO }
        val conservar = buildList {
            add("the camera position")
            add("the perspective")
            add("the room geometry")
            add("the ceiling height")
            if (!tocaVentanas) add("the windows")
            if (!tocaPuertas) add("the doors")
            if (!tocaSuelo) add("the floor")
            if (!tocaTecho) add("the ceiling")
            // Si se pide vaciar, pedir a la vez «conserva los muebles» sería contradictorio.
            if (!vaciar) add("all existing furniture")
        }

        val cuerpo = "Photorealistic interior renovation preview of this $sitio. " +
            "Keep ${conservar.joinToString(", ")} exactly as they are. " +
            "Change only the following: ${frases.joinToString(", ")}."
        // Lo añadido se repite: con muchas partidas el modelo tiende a olvidar el mueble nuevo,
        // que es justo lo que el cliente mira.
        val anadidos = if (objetos.isEmpty()) "" else
            " These added items must be clearly visible: ${objetos.joinToString(", ")}. " +
                "Do not add furniture, doors or windows that are not listed."
        val vaciado = if (!vaciar) "" else
            " Remove all tools, rubble, construction debris and temporary objects; " +
                "the room must be empty apart from the changes listed."
        val posiciones = if (colocaciones.isEmpty()) "" else
            " Respect these positions: ${colocaciones.joinToString(" ")}"
        // El modelo tiende a «terminar» la habitación entera. Si el suelo o el techo no se tocan,
        // hay que decirlo con todas las letras o los inventa (terrazo, techo de pladur…).
        val intocable = if (tocaSuelo && tocaTecho) "" else
            " Every other surface must stay exactly as in the input photograph, including any " +
                "unfinished floor or ceiling: do not finish, replace or cover them."
        val cola = " Interior photograph, natural daylight, realistic materials and shadows, " +
            "sharp focus, no text, no people, no watermark."

        // Orden de importancia: si no cabe todo, se van cayendo la nota, la luz y el remate, pero
        // nunca las partidas ni la colocación que ha dictado el cliente.
        return limitar(listOf(cuerpo, intocable, posiciones, vaciado, anadidos, luz, nota, cola))
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** El Worker rechaza prompts largos: aquí se recorta sin cortar palabras ni frases a medias. */
    const val MAXIMO = 1350

    private fun limitar(partes: List<String>): String {
        val salida = StringBuilder(partes.first())
        partes.drop(1).forEach { parte ->
            if (parte.isNotBlank() && salida.length + parte.length <= MAXIMO) salida.append(parte)
        }
        val texto = salida.toString()
        if (texto.length <= MAXIMO) return texto
        val corte = texto.take(MAXIMO).substringBeforeLast(' ').trimEnd()
        return if (corte.endsWith('.')) corte else "$corte."
    }

    /**
     * Prompt de la pantalla: lo aprobado más lo que se entendió de una frase.
     *
     * Si la frase ya se tradujo a cambios, no se vuelve a añadir como nota: el modelo la leería
     * dos veces (una como partida y otra como texto libre) y el resultado se desvía.
     */
    fun paraLaPantalla(
        aprobados: List<Cambio>,
        entendidos: List<Cambio>,
        frase: String,
        estancia: String = ""
    ): String {
        // La frase se entiende aquí, siempre: si el usuario escribe y pulsa generar sin pasar por
        // «Entender», antes viajaba como nota en español y el modelo la ignoraba casi entera.
        val deLaFrase = entendidos.ifEmpty {
            if (frase.isBlank()) emptyList()
            else FraseACambios.analizar(frase).cambios
        }
        val extra = deLaFrase.filter { nuevo -> aprobados.none { it.tipo == nuevo.tipo } }
        return para(
            cambios = aprobados + extra,
            frase = if (deLaFrase.isEmpty()) frase else "",
            estancia = estancia,
            colocaciones = Colocacion.enIngles(frase),
            despejar = pideDespeje(frase)
        )
    }

    /** «Elimina todo lo que aparece entre medias» no es una partida: es vaciar la habitación. */
    fun pideDespeje(frase: String): Boolean = FraseACambios.pideDespeje(frase)

    private val PUERTAS = setOf(
        TipoCambio.PUERTA_INTERIOR,
        TipoCambio.PUERTA_CORREDERA,
        TipoCambio.PUERTA_BLINDADA
    )

    private val VENTANAS = setOf(
        TipoCambio.CERRAR_HUECO,
        TipoCambio.AMPLIAR_HUECO,
        TipoCambio.VENTANA_NUEVA,
        TipoCambio.VENTANA_OSCILO,
        TipoCambio.VENTANA_A_BALCON,
        TipoCambio.PERSIANA,
        TipoCambio.LUCERNARIO
    )

    private val TipoCambio.enciendeLuz: Boolean
        get() = this == TipoCambio.PUNTO_LUZ ||
            this == TipoCambio.FOCO_EMPOTRADO ||
            this == TipoCambio.TIRA_LED

    private fun fraseSuelo(cambio: Cambio): String = when (cambio.tipo) {
        TipoCambio.RODAPIE -> "fit ${tono(cambio.acabado)} skirting boards"
        else -> "replace the floor with ${material(cambio)}"
    }

    private fun frasePared(cambio: Cambio): String = when (cambio.tipo) {
        TipoCambio.PINTURA_PLASTICA -> "paint the walls ${tono(cambio.acabado)}"
        TipoCambio.PINTURA_ANTIHUMEDAD -> "paint the walls ${tono(cambio.acabado)} with anti-damp paint"
        TipoCambio.ALICATADO -> "tile the walls with ${tono(cambio.acabado)} tiles"
        TipoCambio.PAPEL_PINTADO -> "cover the walls with ${tono(cambio.acabado)} wallpaper"
        TipoCambio.PANEL_DECORATIVO -> "add ${tono(cambio.acabado)} decorative wall panelling"
        TipoCambio.MICROCEMENTO_PARED -> "finish the walls in ${tono(cambio.acabado)} microcement"
        else -> "change the walls to ${material(cambio)}"
    }

    private fun fraseTecho(cambio: Cambio): String = when (cambio.tipo) {
        TipoCambio.PINTURA_TECHO -> "paint the ceiling ${tono(cambio.acabado)}"
        else -> "install a ${material(cambio)}"
    }

    private fun fraseHueco(cambio: Cambio): String = when (cambio.tipo) {
        TipoCambio.CERRAR_HUECO -> "close the existing opening with masonry and plaster"
        TipoCambio.AMPLIAR_HUECO -> "make the existing opening wider"
        else -> {
            val material = material(cambio)
            "fit ${articulo(material)} $material"
        }
    }

    private fun fraseObjeto(cambio: Cambio): String {
        val material = material(cambio)
        // «tres enchufes» o «2 focos» tienen que llegar como cantidad, no como un objeto suelto.
        val cuantos = if (cambio.tipo.unidad == Unidad.UD && cambio.cantidad >= 2) {
            "${cambio.cantidad.toInt()} new"
        } else {
            "a new"
        }
        return "add $cuantos $material"
    }

    /** «a interior door» chirría y despista al modelo: se elige a/an por la primera letra. */
    private fun articulo(material: String): String =
        if (material.firstOrNull()?.lowercaseChar() in VOCALES) "an" else "a"

    private val VOCALES = setOf('a', 'e', 'i', 'o', 'u')

    /** Nombre inglés de la partida, con el acabado delante: «light oak laminate flooring». */
    private fun material(cambio: Cambio): String {
        val base = BASE[cambio.tipo] ?: cambio.tipo.nombre.lowercase()
        val matiz = acabadoEnIngles(cambio.acabado)
        return if (matiz.isBlank()) base else "$matiz $base"
    }

    /** Un acabado desconocido no se inventa: se deja el nombre de la partida a secas. */
    private fun tono(acabado: String): String = acabadoEnIngles(acabado).ifBlank { "neutral" }

    private val BASE: Map<TipoCambio, String> = mapOf(
        TipoCambio.SUELO_LAMINADO to "laminate flooring",
        TipoCambio.SUELO_PORCELANICO to "porcelain floor tiles",
        TipoCambio.SUELO_VINILICO to "vinyl plank flooring with visible wood planks",
        TipoCambio.SUELO_TARIMA to "solid wood flooring",
        TipoCambio.SUELO_MICROCEMENTO to "microcement floor",
        TipoCambio.RODAPIE to "skirting boards",
        TipoCambio.ALICATADO to "wall tiles",
        TipoCambio.PAPEL_PINTADO to "wallpaper",
        TipoCambio.PANEL_DECORATIVO to "decorative wall panelling",
        TipoCambio.MICROCEMENTO_PARED to "microcement walls",
        TipoCambio.PINTURA_PLASTICA to "painted walls",
        TipoCambio.PINTURA_ANTIHUMEDAD to "anti-damp painted walls",
        TipoCambio.PINTURA_TECHO to "painted ceiling",
        TipoCambio.PINTURA_FACHADA to "painted facade",
        TipoCambio.FALSO_TECHO to "plasterboard false ceiling",
        TipoCambio.TECHO_REGISTRABLE to "modular ceiling",
        TipoCambio.TECHO_MADERA to "timber ceiling with exposed transverse beams",
        TipoCambio.CERRAR_HUECO to "bricked-up opening",
        TipoCambio.AMPLIAR_HUECO to "enlarged opening",
        TipoCambio.MOLDURA to "ceiling cornice",
        TipoCambio.PUERTA_INTERIOR to "interior door",
        TipoCambio.PUERTA_CORREDERA to "sliding door",
        TipoCambio.PUERTA_BLINDADA to "security door",
        TipoCambio.VENTANA_NUEVA to "window",
        TipoCambio.VENTANA_OSCILO to "tilt-and-turn window",
        TipoCambio.VENTANA_A_BALCON to "balcony door",
        TipoCambio.PERSIANA to "roller shutter",
        TipoCambio.LUCERNARIO to "roof window",
        TipoCambio.SANITARIO_COMPLETO to "complete new bathroom",
        TipoCambio.PLATO_DUCHA to "walk-in shower tray",
        TipoCambio.BANERA to "bathtub",
        TipoCambio.MAMPARA to "shower screen",
        TipoCambio.INODORO to "toilet",
        TipoCambio.LAVABO to "vanity unit with basin",
        TipoCambio.GRIFERIA to "tapware",
        TipoCambio.ESPEJO_BANO to "backlit mirror",
        TipoCambio.MUEBLE_COCINA to "kitchen units",
        TipoCambio.ENCIMERA to "worktop",
        TipoCambio.ISLA_COCINA to "kitchen island",
        TipoCambio.ELECTRODOMESTICOS to "kitchen appliances",
        TipoCambio.CAMPANA to "extractor hood",
        TipoCambio.FRONTAL to "kitchen splashback",
        TipoCambio.ARMARIO_EMPOTRADO to "fitted wardrobe",
        TipoCambio.ESTANTERIA to "built-in shelving",
        TipoCambio.MUEBLE_TV to "media unit",
        TipoCambio.ENCHUFE to "power sockets",
        TipoCambio.RADIADOR to "radiator",
        TipoCambio.SPLIT to "air-conditioning unit",
        TipoCambio.SUELO_RADIANTE to "underfloor heating",
        TipoCambio.VENTILACION to "mechanical ventilation",
        TipoCambio.TERMO to "water heater",
        TipoCambio.CUADRO_ELECTRICO to "consumer unit",
        TipoCambio.TOMA_AGUA to "water supply point",
        TipoCambio.DESAGUE to "drain"
    )

    /** Los acabados del catálogo, tal y como se dicen en una foto de interior. */
    private val ACABADOS: Map<String, String> = mapOf(
        "blanco roto" to "off-white",
        "blanco mate" to "matte white",
        "blanco brillo" to "glossy white",
        "blanco" to "white",
        "negro" to "black",
        "negra mate" to "matte black",
        "gris" to "grey",
        "gris perla" to "pearl grey",
        "gris cemento" to "cement grey",
        "arena" to "sand",
        "beige" to "beige",
        "verde" to "green",
        "verde salvia" to "sage green",
        "azul humo" to "smoke blue",
        "roble" to "oak",
        "roble claro" to "light oak",
        "roble oscuro" to "dark oak",
        "nogal" to "walnut",
        "haya" to "beech",
        "teka" to "teak",
        "madera" to "wood",
        "imitacion madera" to "wood-effect",
        "imitacion marmol" to "marble-effect",
        "microcemento" to "microcement",
        "lacado" to "lacquered",
        "lacado gris" to "grey lacquered",
        "aluminio" to "aluminium",
        "aluminio blanco" to "white aluminium",
        "aluminio negro" to "black aluminium",
        "aluminio imitacion madera" to "wood-grain brown aluminium",
        "pvc" to "PVC",
        "cromada" to "chrome",
        "dorada" to "brass",
        "transparente" to "clear",
        "ahumada" to "smoked",
        "pizarra" to "slate",
        "granito" to "granite",
        "marmol" to "marble",
        "marmol blanco" to "white marble",
        "compacto blanco" to "white composite",
        "liso" to "plain",
        "rayas" to "striped",
        "floral" to "floral",
        "textura" to "textured"
    )

    private fun acabadoEnIngles(acabado: String): String {
        val limpio = normalizar(acabado)
        if (limpio.isBlank()) return ""
        ACABADOS[limpio]?.let { return it }
        // «roble claro» dicho al revés, o un acabado con una palabra conocida dentro.
        ACABADOS.entries
            .filter { (clave, _) -> limpio.contains(clave) || clave.contains(limpio) }
            .maxByOrNull { it.key.length }
            ?.let { return it.value }
        return ""
    }

    private fun normalizar(texto: String): String {
        val descompuesto = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        return descompuesto.replace(Regex("\\p{Mn}+"), "").trim()
    }
}
