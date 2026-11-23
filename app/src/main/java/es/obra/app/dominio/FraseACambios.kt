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
        Regla(TipoCambio.ENCIMERA, listOf("encimera")),
        Regla(TipoCambio.MUEBLE_COCINA, listOf("mueble de cocina", "muebles de cocina", "cocina")),
        Regla(TipoCambio.ELECTRODOMESTICOS, listOf("electrodomestico")),
        Regla(TipoCambio.CAMPANA, listOf("campana")),
        Regla(TipoCambio.FRONTAL, listOf("frontal")),
        Regla(TipoCambio.SUELO_RADIANTE, listOf("suelo radiante")),
        Regla(TipoCambio.SUELO_TARIMA, listOf("tarima")),
        Regla(TipoCambio.SUELO_LAMINADO, listOf("laminado")),
        Regla(TipoCambio.SUELO_PORCELANICO, listOf("porcelanico", "porcelanato")),
        Regla(TipoCambio.SUELO_VINILICO, listOf("vinilico")),
        Regla(TipoCambio.SUELO_MICROCEMENTO, listOf("microcemento en el suelo", "suelo de microcemento")),
        Regla(TipoCambio.MICROCEMENTO_PARED, listOf("microcemento en la pared", "microcemento en las paredes", "microcemento")),
        Regla(TipoCambio.RODAPIE, listOf("rodapie")),
        Regla(TipoCambio.PINTURA_TECHO, listOf("pintar el techo", "pintar techo", "techo")),
        Regla(TipoCambio.FALSO_TECHO, listOf("falso techo", "pladur en el techo")),
        Regla(TipoCambio.ALICATADO, listOf("alicatar", "alicatado", "azulejo")),
        Regla(TipoCambio.PAPEL_PINTADO, listOf("papel pintado")),
        Regla(TipoCambio.PANEL_DECORATIVO, listOf("panel decorativo", "panel")),
        Regla(TipoCambio.PINTURA_PLASTICA, listOf("pintar", "pintura", "pared", "paredes")),
        Regla(TipoCambio.PUERTA_CORREDERA, listOf("corredera")),
        Regla(TipoCambio.PUERTA_INTERIOR, listOf("puerta")),
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
        "beiges" to "beige", "azules" to "azul"
    )

    /**
     * Devuelve los cambios que entiende la frase, en el orden en que aparecen.
     * Los trozos que no reconoce se descartan: mejor no proponer nada que proponer de más.
     */
    fun interpretar(frase: String, superficie: Double = 0.0, altura: Double = 2.5): List<Cambio> {
        // «18,5 m²» no son dos frases: la coma decimal se protege antes de partir por comas.
        val limpia = normalizar(frase).replace(Regex("(\\d),(\\d)"), "\$1.\$2")
        if (limpia.isBlank()) return emptyList()

        val trozos = limpia
            .split(",", ";", "\n", " y ", " mas ", " con ", " + ")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val vistos = mutableSetOf<TipoCambio>()
        val salida = mutableListOf<Cambio>()

        trozos.forEach { trozo ->
            val regla = REGLAS.firstOrNull { regla -> regla.claves.any { trozo.contains(it) } }
                ?: return@forEach
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

        return salida
    }

    /** Lo que el catálogo sabe de ese tipo y se ha dicho en la frase: «tarima clara» → roble. */
    private fun acabadoDe(tipo: TipoCambio, trozo: String): String {
        if (tipo.acabados.isEmpty()) return ""
        val palabras = trozo.split(" ", "-", "·")
            .filter { it.isNotBlank() }
            .map { SINGULARES[it] ?: it }

        tipo.acabados.firstOrNull { acabado ->
            normalizar(acabado).split(" ").any { it in palabras }
        }?.let { return it }

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
