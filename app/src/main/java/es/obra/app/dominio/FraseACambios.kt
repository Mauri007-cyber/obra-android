package es.obra.app.dominio

import java.text.Normalizer

/**
 * Entender lo que el albañil escribe o dicta, sin mandar nada a ningún sitio.
 *
 * «Suelo de tarima clara y paredes blancas, tres enchufes» se convierte en tres cambios del
 * catálogo, con su acabado y su cantidad. No hay modelo de lenguaje ni servicio: son reglas
 * sobre las palabras que ya usa el catálogo, así que funciona sin conexión, no cuesta nada y
 * nunca inventa una partida que no exista.
 *
 * La cantidad se toma de la frase si viene («18 m²», «3 enchufes»); si no, se propone la que
 * toca por la superficie de la estancia, igual que al añadir el cambio a mano.
 */
object FraseACambios {

    private data class Regla(val tipo: TipoCambio, val claves: List<String>)

    /** Lo que se entiende de una frase y lo que no. Nada se descarta en silencio. */
    data class Analisis(
        val cambios: List<Cambio>,
        val noEntendido: List<String>
    )

    /**
     * De lo más concreto a lo más general: «plato de ducha» antes que «ducha», «suelo radiante»
     * antes que «suelo», «pintar el techo» antes que «techo».
     */
    private val REGLAS = listOf(
        Regla(TipoCambio.SANITARIO_COMPLETO, listOf("bano completo", "reforma del bano", "cambiar el bano")),
        Regla(TipoCambio.PLATO_DUCHA, listOf("plato de ducha", "ducha")),
        Regla(TipoCambio.BANERA, listOf("banera")),
        Regla(TipoCambio.MAMPARA, listOf("mampara")),
        Regla(TipoCambio.INODORO, listOf("inodoro", "vater", "retrete")),
        Regla(TipoCambio.LAVABO, listOf("lavabo")),
        Regla(TipoCambio.GRIFERIA, listOf("griferia", "grifo")),
        Regla(TipoCambio.ESPEJO_BANO, listOf("espejo")),
        Regla(TipoCambio.ISLA_COCINA, listOf("isla de cocina", "isla")),
        Regla(TipoCambio.ENCIMERA, listOf("encimera")),
        Regla(TipoCambio.MUEBLE_COCINA, listOf("mueble de cocina", "muebles de cocina", "cocina")),
        Regla(TipoCambio.ELECTRODOMESTICOS, listOf("electrodomestico")),
        Regla(TipoCambio.CAMPANA, listOf("campana")),
        Regla(TipoCambio.FRONTAL, listOf("frontal")),
        Regla(TipoCambio.SUELO_RADIANTE, listOf("suelo radiante")),
        // «tarima vinílica» es vinílico, no tarima natural: la regla concreta va antes.
        Regla(TipoCambio.SUELO_VINILICO, listOf("tarima vinilica", "vinilico", "vinilica")),
        Regla(TipoCambio.SUELO_TARIMA, listOf("tarima")),
        Regla(TipoCambio.SUELO_LAMINADO, listOf("laminado")),
        Regla(TipoCambio.SUELO_PORCELANICO, listOf("porcelanico", "porcelanato")),
        Regla(TipoCambio.SUELO_MICROCEMENTO, listOf("microcemento en el suelo", "suelo de microcemento")),
        Regla(TipoCambio.MICROCEMENTO_PARED, listOf("microcemento en la pared", "microcemento en las paredes", "microcemento")),
        Regla(TipoCambio.RODAPIE, listOf("rodapie")),
        Regla(
            TipoCambio.TECHO_MADERA,
            listOf(
                "techo de madera", "techo imitacion madera", "techo madera", "techo de vigas",
                "vigas de madera", "viga", "vigueta", "tabiques transversales"
            )
        ),
        Regla(TipoCambio.PINTURA_TECHO, listOf("pintar el techo", "pintar techo", "techo")),
        Regla(TipoCambio.FALSO_TECHO, listOf("falso techo", "pladur en el techo")),
        Regla(TipoCambio.ALICATADO, listOf("alicatar", "alicatado", "azulejo")),
        Regla(TipoCambio.PAPEL_PINTADO, listOf("papel pintado")),
        Regla(TipoCambio.PANEL_DECORATIVO, listOf("panel decorativo", "panel")),
        Regla(TipoCambio.PINTURA_PLASTICA, listOf("pintar", "pintura", "pared", "paredes")),
        // Antes que «ventana» y «puerta»: cerrar o agrandar un hueco no es poner una ventana.
        Regla(
            TipoCambio.CERRAR_HUECO,
            listOf(
                "cerrar el hueco", "cerrar hueco", "cerrar la ventana", "cerrar ventana",
                "tapar el hueco", "tapar hueco", "tapar la ventana", "cegar",
                "anular la ventana", "anular ventana"
            )
        ),
        Regla(
            TipoCambio.AMPLIAR_HUECO,
            listOf(
                "agrandar la ventana", "agrandar ventana", "ampliar la ventana", "ampliar ventana",
                "agrandar la puerta", "agrandar puerta", "ampliar la puerta", "ampliar el hueco",
                "hacer mas grande la ventana", "hacer mas grande la puerta",
                "cambiar el tamano de la ventana", "cambiar el tamano del hueco"
            )
        ),
        Regla(TipoCambio.PUERTA_CORREDERA, listOf("corredera")),
        Regla(TipoCambio.PUERTA_INTERIOR, listOf("puerta")),
        Regla(
            TipoCambio.VENTANA_OSCILO,
            listOf("ventana abatible", "abatible", "oscilobatiente", "oscilo")
        ),
        Regla(TipoCambio.VENTANA_NUEVA, listOf("ventana")),
        Regla(TipoCambio.PERSIANA, listOf("persiana")),
        Regla(TipoCambio.ARMARIO_EMPOTRADO, listOf("armario")),
        Regla(TipoCambio.ESTANTERIA, listOf("estanteria")),
        Regla(TipoCambio.MUEBLE_TV, listOf("mueble de salon", "mueble del salon")),
        Regla(TipoCambio.ENCHUFE, listOf("enchufe")),
        Regla(TipoCambio.FOCO_EMPOTRADO, listOf("foco")),
        Regla(TipoCambio.PUNTO_LUZ, listOf("punto de luz", "luz")),
        Regla(TipoCambio.TIRA_LED, listOf("tira led")),
        Regla(TipoCambio.RADIADOR, listOf("radiador")),
        Regla(TipoCambio.SPLIT, listOf("aire acondicionado", "split")),
        Regla(TipoCambio.TERMO, listOf("termo", "caldera")),
        Regla(TipoCambio.CUADRO_ELECTRICO, listOf("cuadro electrico")),
        Regla(TipoCambio.NIVELAR_SUELO, listOf("nivelar")),
        Regla(TipoCambio.TABIQUE_TIRADO, listOf("tirar un tabique", "tirar tabique", "tirar el tabique")),
        Regla(TipoCambio.TABIQUE_NUEVO, listOf("tabique nuevo", "tabique de ladrillo")),
        Regla(TipoCambio.TABIQUE_PLADUR, listOf("tabique de pladur", "pladur")),
        Regla(TipoCambio.AISLAMIENTO, listOf("aislar", "aislamiento")),
        Regla(TipoCambio.IMPERMEABILIZAR, listOf("impermeabilizar")),
        Regla(TipoCambio.PINTURA_FACHADA, listOf("fachada")),
        Regla(TipoCambio.TOMA_AGUA, listOf("toma de agua")),
        Regla(TipoCambio.DESAGUE, listOf("desague")),
        Regla(TipoCambio.VENTILACION, listOf("ventilacion"))
    )

    private val CANTIDAD = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(m2|m²|metros cuadrados|metro cuadrado|metros|metro|m|ud|uds|unidades|unidad|puntos?|punto de luz)"
    )

    /** «tres enchufes» se dice tanto como «3 enchufes». */
    private val NUMEROS = mapOf(
        "un" to 1.0, "uno" to 1.0, "una" to 1.0,
        "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0, "cinco" to 5.0,
        "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0
    )

    /** Plurales de color que la gente escribe y el catálogo guarda en singular. */
    private val SINGULARES = mapOf(
        "blancas" to "blanco", "blancos" to "blanco",
        "negras" to "negro", "negros" to "negro",
        "grises" to "gris", "verdes" to "verde",
        "claras" to "clara", "claros" to "claro",
        "oscuras" to "oscuro", "oscuros" to "oscuro",
        "beiges" to "beige", "azules" to "azul",
        "blanca" to "blanco", "negro" to "negro", "negra" to "negro"
    )

    /**
     * Devuelve los cambios que entiende la frase, en el orden en que aparecen.
     * Los trozos que no reconoce se descartan: mejor no proponer nada que proponer de más.
     */
    fun interpretar(frase: String, superficie: Double = 0.0, altura: Double = 2.5): List<Cambio> =
        analizar(frase, superficie, altura).cambios

    /**
     * Como [interpretar], pero además devuelve los trozos que no ha entendido. Un albañil escribe
     * «pintame el piso de blanco y ponme una isla», y esa frase tiene que salir con dos partidas y
     * una lista de lo que sobra, para poder pedir que lo repita con otras palabras.
     */
    fun analizar(frase: String, superficie: Double = 0.0, altura: Double = 2.5): Analisis {
        // «18,5 m²» no son dos frases: la coma decimal se protege antes de partir por comas.
        val limpia = normalizar(frase).replace(Regex("(\\d),(\\d)"), "\$1.\$2")
        if (limpia.isBlank()) return Analisis(emptyList(), emptyList())

        val trozos = limpia
            .split(",", ";", "\n", " y ", " mas ", " con ", " + ")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val vistos = mutableSetOf<TipoCambio>()
        val salida = mutableListOf<Cambio>()
        val sobrantes = mutableListOf<String>()

        trozos.forEach { trozo ->
            val regla = reglaDe(trozo)
            if (regla == null) {
                // «quita los cachivaches» no es una partida, pero sí se usa en el prompt: no se
                // avisa como no entendido porque se está haciendo caso de ello.
                if (tieneContenido(trozo) && !pideDespeje(trozo)) sobrantes.add(trozo)
                return@forEach
            }
            if (!vistos.add(regla.tipo)) return@forEach

            val acabado = acabadoDe(regla.tipo, trozo)
            val cantidad = cantidadDe(trozo, regla.tipo)
            val cambio = Cambio(
                tipo = regla.tipo,
                cantidad = cantidad ?: 0.0,
                acabado = acabado,
                estado = EstadoCambio.PROPUESTO
            )
            salida.add(
                if (cantidad == null) cambio.conCantidadSugerida(superficie, altura) else cambio
            )
        }

        return Analisis(
            cambios = salida.map { afinarColor(it, colorPedido(limpia)) },
            noEntendido = sobrantes.distinct()
        )
    }

    /**
     * Vaciar de trastos no es una partida del catálogo (no se cobra), pero se le dice al modelo.
     * Vive aquí para que el intérprete no lo marque como «no entendido».
     */
    fun pideDespeje(frase: String): Boolean {
        val limpia = normalizar(frase.replace("\u00a0", " "))
        return DESPEJE.any { limpia.contains(it) }
    }

    private val DESPEJE = listOf(
        "elimina", "eliminar", "quita", "quitar", "retira", "retirar",
        "vaciar", "vacia", "despeja", "despejar", "sin muebles",
        "nada entre medias", "no debe haber nada", "vacio de obras", "escombros",
        "cachivaches", "trastos", "porqueria", "bultos"
    )

    /** Palabras que no cambian nada: se quitan antes de buscar parecidos. */
    private val RELLENO = setOf(
        "quiero", "quisiera", "me", "mi", "gustaria", "poner", "pon", "ponme", "ponerle",
        "hacer", "haz", "hacerle", "hay", "que", "tambien", "ademas", "favor", "seria",
        "algo", "nuevo", "nueva", "nuevos", "nuevas", "cambiar", "cambia", "cambiame",
        "dejar", "dejarlo", "dejarla", "falta", "necesito", "toda", "todo", "todos", "todas",
        "bastante", "muy", "un", "una", "unos", "unas", "del", "los", "las", "para", "por"
    )

    /** Cómo lo dice la gente y cómo está en el catálogo. */
    private val SINONIMOS = mapOf(
        "piso" to "suelo", "pisos" to "suelo", "suelito" to "suelo",
        "ventanal" to "ventana", "ventanas" to "ventana", "ventanitas" to "ventana",
        "puertas" to "puerta", "puertita" to "puerta",
        "azulejos" to "azulejo", "alicatar" to "alicatar",
        "enchufes" to "enchufe", "enchufes" to "enchufe", "enchufito" to "enchufe",
        "focos" to "foco", "focos" to "foco", "grifos" to "grifo", "radiadores" to "radiador",
        "armarios" to "armario", "tabiques" to "tabique", "techos" to "techo",
        "pared" to "paredes", "pareces" to "paredes", "pares" to "paredes",
        "baños" to "bano", "banos" to "bano", "cocinas" to "cocina"
    )

    /**
     * Reduce la palabra a la forma del catálogo: sinónimos y raíces («pintame» → «pintar»,
     * «ventanales» → «ventana», «vinílico» → «vinilico»).
     */
    private fun reducir(palabra: String): String {
        SINONIMOS[palabra]?.let { return it }
        val raices = listOf(
            "pint" to "pintar",
            "alicat" to "alicatar",
            "azulej" to "azulejo",
            "enchuf" to "enchufe",
            "ventan" to "ventana",
            "puert" to "puerta",
            "tabiq" to "tabique",
            "tarim" to "tarima",
            "vinil" to "vinilico",
            "lamin" to "laminado",
            "porcelan" to "porcelanico",
            "rodapi" to "rodapie",
            "grifer" to "griferia",
            "radiador" to "radiador",
            "armari" to "armario",
            "estanter" to "estanteria"
        )
        raices.firstOrNull { (raiz, _) -> palabra.startsWith(raiz) }?.let { return it.second }
        return palabra
    }

    /** Un trozo con alguna palabra de verdad, aunque no se entienda. */
    private fun tieneContenido(trozo: String): Boolean =
        trozo.split(" ", "-", "·").any { it.length >= 3 && it !in RELLENO && it.any(Char::isLetter) }

    /** Distancia de edición pequeña: «bano»/«baño», «tarima»/«tarimo», «pintar»/«pintra». */
    private fun parecido(a: String, b: String): Boolean {
        if (a == b) return true
        val largo = maxOf(a.length, b.length)
        if (largo < 5) return false
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var fallos = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            fallos++
            if (fallos > 1) return false
            if (a.length > b.length) i++ else if (b.length > a.length) j++ else { i++; j++ }
        }
        return fallos + (a.length - i) + (b.length - j) <= 1
    }

    /**
     * «… y esta debe ser blanca»: el color del mueble nuevo suele caer en otra frase del dictado,
     * después de un «que sea», «debe ser» o «tiene que ser». No se toma un color suelto de la
     * frase, que casi siempre habla de las paredes.
     */
    private val COLOR_PEDIDO = Regex(
        "(?:que|debe|deberia|tiene que|tienen que|esta|este|esto)\\s+(?:[a-z]+\\s+)?ser\\s+" +
            "(blanco|blanca|blancos|blancas|negro|negra|negros|negras|gris|beige|verde|azul)\\b"
    )

    private fun colorPedido(frase: String): String? {
        val forma = COLOR_PEDIDO.find(frase)?.groupValues?.get(1) ?: return null
        return SINGULARES[forma] ?: forma
    }

    /**
     * Afina el acabado del mueble añadido con ese color, pero solo si el catálogo tiene justo esa
     * variante: «mármol» → «mármol blanco». Si no existe, se deja como está.
     */
    private fun afinarColor(cambio: Cambio, color: String?): Cambio {
        if (color == null || cambio.acabado.isBlank()) return cambio
        if (cambio.tipo.capa != Capa.OBJETO) return cambio
        val base = normalizar(cambio.acabado)
        cambio.tipo.acabados
            .firstOrNull { normalizar(it) == "$base $color" }
            ?.let { return cambio.copy(acabado = it) }
        return cambio
    }

    /**
     * La regla que corresponde al trozo. Además de las claves literales, «techo» junto a «madera»
     * o «viga» es siempre un techo de madera: así vale también «techo imitiacion madera» con
     * faltas o con palabras en medio, en vez de pintarlo y forrarlo a la vez.
     */
    private fun reglaDe(trozo: String): Regla? {
        if (trozo.contains("techo") && (trozo.contains("madera") || trozo.contains("viga"))) {
            return REGLAS.first { it.tipo == TipoCambio.TECHO_MADERA }
        }
        // 1. La frase tal cual, que es lo que ya funcionaba.
        REGLAS.firstOrNull { regla -> regla.claves.any { trozo.contains(it) } }?.let { return it }

        // 2. Sin muletillas y con sinónimos: «pintame el piso de blanco» → «pintar suelo blanco».
        val palabras = trozo.split(" ", "-", "·", "\n")
            .filter { it.isNotBlank() && it.length > 2 && it !in RELLENO }
            .map { reducir(it) }
        if (palabras.isEmpty()) return null
        val texto = palabras.joinToString(" ")
        REGLAS.firstOrNull { regla -> regla.claves.any { texto.contains(it) } }?.let { return it }

        // 3. Última red: palabras parecidas («tarimo vinilico»). Solo con palabras largas para no
        // confundir «luz» con «luz» de «pladur» ni «foco» con «coco».
        return REGLAS.firstOrNull { regla ->
            regla.claves.any { clave ->
                clave.split(" ").any { palabraClave ->
                    palabraClave.length >= 5 && palabras.any { parecido(it, palabraClave) }
                }
            }
        }
    }

    /** Lo que el catálogo sabe de ese tipo y se ha dicho en la frase: «tarima clara» → roble. */
    private fun acabadoDe(tipo: TipoCambio, trozo: String): String {
        if (tipo.acabados.isEmpty()) return ""
        val palabras = trozo.split(" ", "-", "·")
            .filter { it.isNotBlank() }
            .map { SINGULARES[it] ?: it }

        // Gana el acabado que más palabras comparte: «aluminio imitación madera» (3) antes que
        // «aluminio blanco» (1), y «blanco roto» antes que un «blanco» suelto.
        tipo.acabados
            .map { acabado -> acabado to normalizar(acabado).split(" ").count { it in palabras } }
            .filter { (_, aciertos) -> aciertos > 0 }
            .maxByOrNull { (_, aciertos) -> aciertos }
            ?.let { (acabado, _) -> return acabado }

        palabras.forEach { palabra ->
            tipo.acabados.firstOrNull { normalizar(it).contains(palabra) }?.let { return it }
        }

        // «clara» y «oscura» no son acabados, pero todo el mundo las dice así.
        if (palabras.any { it == "clara" || it == "claro" }) {
            tipo.acabados.firstOrNull { normalizar(it).contains("claro") || normalizar(it) == "roble" }
                ?.let { return it }
        }
        if (palabras.any { it == "oscura" || it == "oscuro" }) {
            tipo.acabados.firstOrNull { normalizar(it).contains("oscuro") || normalizar(it) == "nogal" }
                ?.let { return it }
        }

        return ""
    }

    private fun cantidadDe(trozo: String, tipo: TipoCambio): Double? {
        if (!tipo.mide) return null

        CANTIDAD.find(trozo)?.let { encontrado ->
            val valor = encontrado.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (valor != null && valor > 0.0) return valor
        }

        val palabra = trozo.split(" ").firstOrNull { NUMEROS.containsKey(it) }
        return palabra?.let { NUMEROS[it] }
    }

    /** Minúsculas y sin tildes, para que «bañera» y «banera» sean lo mismo. */
    private fun normalizar(texto: String): String {
        val descompuesto = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        return descompuesto.replace(Regex("\\p{Mn}+"), "").trim()
    }
}
