package es.obra.app.dominio

/**
 * Todo lo que se puede cambiar en una obra, ordenado por oficio.
 *
 * La idea es que nadie tenga que escribir «pon un suelo bonito»: se elige una partida del
 * catálogo, se le dice cuánto y cómo, y con eso ya se sabe qué enseñar, cuánto cuesta y qué
 * zona de la foto se tiñe con ese material.
 *
 * Los precios son orientativos y con la mano de obra incluida, como en [Tarifas]: se cambian
 * por obra, no aquí.
 */
enum class Ambito(val etiqueta: String, val orden: Int) {
    DEMOLICION("Tirar y abrir", 10),
    ALBANILERIA("Albañilería", 20),
    SUELO("Suelo", 30),
    PARED("Paredes", 40),
    TECHO("Techo", 50),
    PINTURA("Pintura", 60),
    CARPINTERIA("Puertas y ventanas", 70),
    BANO("Baño", 80),
    COCINA("Cocina", 90),
    ELECTRICIDAD("Electricidad", 100),
    FONTANERIA("Fontanería", 110),
    CLIMATIZACION("Clima y aire", 120),
    MOBILIARIO("Muebles a medida", 130),
    EXTERIOR("Exterior", 140);

    companion object {
        fun porNombre(texto: String): Ambito =
            entries.firstOrNull { it.name.equals(texto, ignoreCase = true) } ?: ALBANILERIA
    }
}

enum class Unidad(val etiqueta: String) {
    M2("m²"),
    ML("m"),
    UD("ud"),
    GLOBAL("partida");

    companion object {
        fun porNombre(texto: String): Unidad =
            entries.firstOrNull { it.name.equals(texto, ignoreCase = true) } ?: M2
    }
}

/** Qué trozo de la foto hay que repintar para que el cambio se vea. */
enum class Capa {
    SUELO,
    PARED,
    TECHO,
    HUECO,
    OBJETO,
    NINGUNA
}

enum class TipoCambio(
    val nombre: String,
    val ambito: Ambito,
    val unidad: Unidad,
    val precio: Double,
    val acabados: List<String> = emptyList(),
    /** false = es una partida entera (una cocina, unos electrodomésticos): no se pide cantidad. */
    val mide: Boolean = true
) {
    // Tirar y abrir
    TABIQUE_TIRADO("Tirar un tabique", Ambito.DEMOLICION, Unidad.M2, 12.0),
    ABRIR_HUECO("Abrir un hueco en un muro", Ambito.DEMOLICION, Unidad.UD, 180.0),
    QUITAR_ALICATADO("Quitar alicatado viejo", Ambito.DEMOLICION, Unidad.M2, 14.0),
    LEVANTAR_SUELO("Levantar el suelo viejo", Ambito.DEMOLICION, Unidad.M2, 10.0),

    // Albañilería
    TABIQUE_NUEVO("Tabique nuevo de ladrillo", Ambito.ALBANILERIA, Unidad.M2, 45.0),
    TABIQUE_PLADUR("Tabique de pladur", Ambito.ALBANILERIA, Unidad.M2, 38.0),
    AISLAMIENTO("Aislar pared (térmico y acústico)", Ambito.ALBANILERIA, Unidad.M2, 16.0),
    NIVELAR_SUELO("Nivelar el suelo con solera", Ambito.ALBANILERIA, Unidad.M2, 22.0),
    RECRECIDO("Recrecido para pasar tuberías", Ambito.ALBANILERIA, Unidad.M2, 26.0),
    IMPERMEABILIZAR("Impermeabilizar una pared", Ambito.ALBANILERIA, Unidad.M2, 18.0),

    // Suelo
    SUELO_LAMINADO("Suelo laminado", Ambito.SUELO, Unidad.M2, 35.0, listOf("roble claro", "roble oscuro", "nogal")),
    SUELO_PORCELANICO("Suelo porcelánico", Ambito.SUELO, Unidad.M2, 48.0, listOf("gris cemento", "blanco mate", "imitación madera", "negro")),
    SUELO_VINILICO("Suelo vinílico", Ambito.SUELO, Unidad.M2, 30.0, listOf("roble", "gris", "beige")),
    SUELO_TARIMA("Tarima natural", Ambito.SUELO, Unidad.M2, 62.0, listOf("roble", "haya", "teka")),
    SUELO_MICROCEMENTO("Suelo de microcemento", Ambito.SUELO, Unidad.M2, 55.0, listOf("gris", "arena", "blanco")),
    RODAPIE("Rodapié nuevo", Ambito.SUELO, Unidad.ML, 6.0, listOf("blanco", "madera", "aluminio")),

    // Paredes
    ALICATADO("Alicatar pared", Ambito.PARED, Unidad.M2, 42.0, listOf("blanco brillo", "beige", "verde", "imitación mármol")),
    PAPEL_PINTADO("Papel pintado", Ambito.PARED, Unidad.M2, 18.0, listOf("liso", "rayas", "floral", "textura")),
    PANEL_DECORATIVO("Panel decorativo en pared", Ambito.PARED, Unidad.M2, 45.0, listOf("madera", "microcemento", "lacado")),
    MICROCEMENTO_PARED("Microcemento en pared", Ambito.PARED, Unidad.M2, 52.0, listOf("gris", "arena")),

    // Pintura
    PINTURA_PLASTICA("Pintar paredes (2 manos)", Ambito.PINTURA, Unidad.M2, 8.0, listOf("blanco roto", "arena", "verde salvia", "gris perla", "azul humo")),
    PINTURA_ANTIHUMEDAD("Pintura antihumedad", Ambito.PINTURA, Unidad.M2, 12.0, listOf("blanco", "beige")),
    PINTURA_TECHO("Pintar el techo", Ambito.PINTURA, Unidad.M2, 7.0, listOf("blanco mate")),
    PINTURA_FACHADA("Pintar fachada", Ambito.EXTERIOR, Unidad.M2, 14.0, listOf("blanco", "crema", "gris")),

    // Techo
    FALSO_TECHO("Falso techo de pladur", Ambito.TECHO, Unidad.M2, 28.0),
    TECHO_REGISTRABLE("Techo registrable", Ambito.TECHO, Unidad.M2, 32.0, listOf("blanco", "gris")),
    MOLDURA("Moldura o cornisa", Ambito.TECHO, Unidad.ML, 9.0),

    // Puertas y ventanas
    PUERTA_INTERIOR("Puerta interior nueva", Ambito.CARPINTERIA, Unidad.UD, 220.0, listOf("blanca lisa", "roble", "cristal"), mide = false),
    PUERTA_CORREDERA("Puerta corredera", Ambito.CARPINTERIA, Unidad.UD, 380.0, listOf("blanca", "roble", "cristal"), mide = false),
    PUERTA_BLINDADA("Puerta blindada", Ambito.CARPINTERIA, Unidad.UD, 850.0, listOf("blanca", "roble", "negra"), mide = false),
    VENTANA_NUEVA("Ventana nueva", Ambito.CARPINTERIA, Unidad.UD, 350.0, listOf("aluminio blanco", "aluminio negro", "PVC"), mide = false),
    VENTANA_OSCILO("Ventana oscilobatiente", Ambito.CARPINTERIA, Unidad.UD, 460.0, listOf("aluminio blanco", "aluminio negro"), mide = false),
    VENTANA_A_BALCON("Ventana converted en puerta balcón", Ambito.CARPINTERIA, Unidad.UD, 700.0),
    PERSIANA("Persiana nueva", Ambito.CARPINTERIA, Unidad.UD, 180.0, listOf("aluminio", "PVC"), mide = false),
    LUCERNARIO("Lucernario o ventana de tejado", Ambito.CARPINTERIA, Unidad.UD, 900.0),

    // Baño
    SANITARIO_COMPLETO("Cambiar el baño completo", Ambito.BANO, Unidad.GLOBAL, 2400.0, mide = false),
    PLATO_DUCHA("Plato de ducha", Ambito.BANO, Unidad.UD, 380.0, listOf("blanco", "gris", "pizarra"), mide = false),
    BANERA("Bañera nueva", Ambito.BANO, Unidad.UD, 520.0, listOf("blanca"), mide = false),
    MAMPARA("Mampara de ducha", Ambito.BANO, Unidad.UD, 320.0, listOf("transparente", "ahumada"), mide = false),
    INODORO("Inodoro", Ambito.BANO, Unidad.UD, 210.0, listOf("blanco", "blanco mate"), mide = false),
    LAVABO("Lavabo con mueble", Ambito.BANO, Unidad.UD, 340.0, listOf("blanco", "roble", "negro"), mide = false),
    GRIFERIA("Grifería", Ambito.BANO, Unidad.UD, 120.0, listOf("cromada", "negra mate", "dorada"), mide = false),
    ESPEJO_BANO("Espejo con luz", Ambito.BANO, Unidad.UD, 190.0),

    // Cocina
    MUEBLE_COCINA("Mueble de cocina", Ambito.COCINA, Unidad.ML, 280.0, listOf("blanco mate", "roble", "verde", "gris")),
    ENCIMERA("Encimera", Ambito.COCINA, Unidad.ML, 180.0, listOf("compacto blanco", "granito", "madera")),
    ELECTRODOMESTICOS("Electrodomésticos", Ambito.COCINA, Unidad.GLOBAL, 1800.0, mide = false),
    CAMPANA("Campana extractora", Ambito.COCINA, Unidad.UD, 220.0, mide = false),
    FRONTAL("Frontal de cocina", Ambito.COCINA, Unidad.M2, 65.0, listOf("blanco", "verde", "madera")),

    // Electricidad
    PUNTO_LUZ("Punto de luz", Ambito.ELECTRICIDAD, Unidad.UD, 45.0),
    FOCO_EMPOTRADO("Foco empotrado", Ambito.ELECTRICIDAD, Unidad.UD, 38.0),
    ENCHUFE("Punto de enchufe", Ambito.ELECTRICIDAD, Unidad.UD, 32.0),
    CUADRO_ELECTRICO("Cuadro eléctrico nuevo", Ambito.ELECTRICIDAD, Unidad.UD, 380.0, mide = false),
    TIRA_LED("Tira LED", Ambito.ELECTRICIDAD, Unidad.ML, 22.0),

    // Fontanería
    TOMA_AGUA("Toma de agua", Ambito.FONTANERIA, Unidad.UD, 90.0),
    DESAGUE("Desagüe nuevo", Ambito.FONTANERIA, Unidad.UD, 85.0),
    TERMO("Termo o caldera", Ambito.FONTANERIA, Unidad.UD, 650.0, mide = false),

    // Clima
    RADIADOR("Radiador", Ambito.CLIMATIZACION, Unidad.UD, 260.0, mide = false),
    SUELO_RADIANTE("Suelo radiante", Ambito.CLIMATIZACION, Unidad.M2, 55.0),
    SPLIT("Split de aire acondicionado", Ambito.CLIMATIZACION, Unidad.UD, 420.0, mide = false),
    VENTILACION("Ventilación mecánica", Ambito.CLIMATIZACION, Unidad.UD, 180.0, mide = false),

    // Muebles
    ARMARIO_EMPOTRADO("Armario empotrado", Ambito.MOBILIARIO, Unidad.ML, 320.0, listOf("blanco", "roble", "lacado gris")),
    ESTANTERIA("Estantería a medida", Ambito.MOBILIARIO, Unidad.ML, 190.0),
    MUEBLE_TV("Mueble de salón a medida", Ambito.MOBILIARIO, Unidad.ML, 240.0);

    val capa: Capa
        get() = when (this) {
            // Pintar el techo es techo, aunque el oficio sea pintura: si no, se tiñen las paredes.
            PINTURA_TECHO -> Capa.TECHO
            else -> when (ambito) {
                Ambito.SUELO -> Capa.SUELO
                Ambito.PARED, Ambito.PINTURA -> Capa.PARED
                Ambito.TECHO -> Capa.TECHO
                Ambito.CARPINTERIA -> Capa.HUECO
                Ambito.BANO, Ambito.COCINA, Ambito.MOBILIARIO,
                Ambito.ELECTRICIDAD, Ambito.FONTANERIA, Ambito.CLIMATIZACION -> Capa.OBJETO
                Ambito.DEMOLICION, Ambito.ALBANILERIA, Ambito.EXTERIOR -> Capa.NINGUNA
            }
        }

    /** Si el cambio se ve en la foto (y por tanto hay que teñir su zona). */
    val seVe: Boolean get() = capa != Capa.NINGUNA

    companion object {
        fun porNombre(texto: String): TipoCambio? =
            entries.firstOrNull { it.name.equals(texto, ignoreCase = true) }

        /** Agrupado por oficio, en el orden en que se enseña al cliente. */
        fun porAmbito(): List<Pair<Ambito, List<TipoCambio>>> =
            Ambito.entries
                .sortedBy { it.orden }
                .map { ambito -> ambito to entries.filter { it.ambito == ambito } }
                .filter { it.second.isNotEmpty() }

        fun buscar(texto: String): List<TipoCambio> {
            val t = texto.trim()
            if (t.isEmpty()) return emptyList()
            return entries.filter {
                it.nombre.contains(t, ignoreCase = true) ||
                    it.ambito.etiqueta.contains(t, ignoreCase = true) ||
                    it.acabados.any { acabado -> acabado.contains(t, ignoreCase = true) }
            }
        }
    }
}
