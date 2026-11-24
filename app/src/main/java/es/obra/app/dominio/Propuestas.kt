package es.obra.app.dominio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Escenarios de reforma que propone la IA cuando el cliente no lo tiene claro.
 *
 * La IA nunca decide precios ni escribe en la obra: devuelve partidas del catálogo y la app las
 * valida, las cobra con sus precios y las deja como propuestas para que el albañil las apruebe.
 * Todo lo que no se entiende se descarta en silencio; nunca se inventa una partida.
 */

@Serializable
internal data class ActuacionDto(
    val tipo: String = "",
    val estancia: String = "",
    val cantidad: Double = 0.0,
    val motivo: String = ""
)

@Serializable
internal data class PropuestaDto(
    val titulo: String = "",
    val resumen: String = "",
    val actuaciones: List<ActuacionDto> = emptyList()
)

@Serializable
internal data class RespuestaPropuestasDto(
    val propuestas: List<PropuestaDto> = emptyList(),
    val restantes: Int? = null,
    val error: String = ""
)

data class Actuacion(
    val tipo: TipoCambio,
    val estancia: String,
    val cantidad: Double,
    val motivo: String
) {
    val importe: Double
        get() = if (!tipo.mide) tipo.precio
        else tipo.precio * cantidad.coerceAtLeast(0.0)
}

data class Escenario(
    val titulo: String,
    val resumen: String,
    val actuaciones: List<Actuacion>
) {
    val coste: Double get() = actuaciones.sumOf { it.importe }

    /** «Salón · Tirar un tabique · 8,5 m²», tal cual se lee en la tarjeta. */
    fun linea(actuacion: Actuacion): String = buildList {
        if (actuacion.estancia.isNotBlank()) add(actuacion.estancia)
        add(actuacion.tipo.nombre)
        if (actuacion.tipo.mide) {
            add("${Presupuesto.cantidad(actuacion.cantidad)} ${actuacion.tipo.unidad.etiqueta}")
        }
    }.joinToString(" · ")
}

/** Las partidas de un oficio, para no repetir la lista en cada permiso. */
private fun tiposDe(ambito: Ambito): Set<TipoCambio> =
    TipoCambio.entries.filter { it.ambito == ambito }.toSet()

/**
 * Bloques cortos en los que se reparten los chips. Catorce sueltos de golpe agobian a quien no
 * sabe de obra; con cuatro títulos se encuentra lo que busca sin leerlo todo.
 */
enum class GrupoChips(val etiqueta: String) {
    ESTRUCTURA("Estructura"),
    ACABADOS("Acabados"),
    INSTALACIONES("Instalaciones"),
    MOBILIARIO("Muebles")
}

/**
 * Lo que el cliente está dispuesto a tocar. Cada chip abre solo sus partidas: la app manda a la
 * IA únicamente ese trozo del catálogo y después tira lo que se salga. Así «no quiero tirar
 * paredes» se cumple de verdad, en vez de quedarse en una frase del prompt que el modelo puede
 * ignorar.
 *
 * El orden de los valores es el que se pinta: primero por bloque y dentro de cada bloque de lo
 * más gordo a lo más pequeño.
 */
enum class Permiso(
    val etiqueta: String,
    val grupo: GrupoChips,
    val abre: Set<TipoCambio>
) {
    TIRAR_PAREDES(
        "tirar paredes",
        GrupoChips.ESTRUCTURA,
        setOf(
            TipoCambio.TABIQUE_TIRADO,
            TipoCambio.ABRIR_HUECO,
            TipoCambio.CERRAR_HUECO,
            TipoCambio.AMPLIAR_HUECO
        )
    ),
    MOVER_TABIQUES(
        "mover tabiques",
        GrupoChips.ESTRUCTURA,
        setOf(
            TipoCambio.TABIQUE_TIRADO,
            TipoCambio.ABRIR_HUECO,
            TipoCambio.CERRAR_HUECO,
            TipoCambio.AMPLIAR_HUECO,
            TipoCambio.TABIQUE_NUEVO,
            TipoCambio.TABIQUE_PLADUR
        )
    ),
    CAMBIAR_TECHO(
        "cambiar el techo",
        GrupoChips.ESTRUCTURA,
        tiposDe(Ambito.TECHO)
    ),
    // La fachada es obra de exterior, no «pintar» por dentro: tiene su propio chip.
    FACHADA(
        "fachada y exterior",
        GrupoChips.ESTRUCTURA,
        setOf(
            TipoCambio.PINTURA_FACHADA,
            TipoCambio.AISLAMIENTO,
            TipoCambio.IMPERMEABILIZAR
        )
    ),
    CAMBIAR_SUELOS(
        "cambiar suelos",
        GrupoChips.ACABADOS,
        tiposDe(Ambito.SUELO) + setOf(
            TipoCambio.LEVANTAR_SUELO,
            TipoCambio.NIVELAR_SUELO,
            TipoCambio.RECRECIDO
        )
    ),
    CAMBIAR_PUERTAS(
        "cambiar puertas y ventanas",
        GrupoChips.ACABADOS,
        tiposDe(Ambito.CARPINTERIA)
    ),
    ALICATAR(
        "alicatar o revestir paredes",
        GrupoChips.ACABADOS,
        tiposDe(Ambito.PARED) + TipoCambio.QUITAR_ALICATADO
    ),
    PINTAR(
        "pintar",
        GrupoChips.ACABADOS,
        tiposDe(Ambito.PINTURA)
    ),
    AISLAR(
        "aislar e impermeabilizar",
        GrupoChips.ACABADOS,
        setOf(TipoCambio.AISLAMIENTO, TipoCambio.IMPERMEABILIZAR)
    ),
    RENOVAR_COCINA(
        "renovar cocina",
        GrupoChips.INSTALACIONES,
        tiposDe(Ambito.COCINA)
    ),
    // Un baño nuevo necesita mover agua, desagüe y termo, no solo cambiar los sanitarios.
    RENOVAR_BANO(
        "renovar baño",
        GrupoChips.INSTALACIONES,
        tiposDe(Ambito.BANO) + setOf(
            TipoCambio.TOMA_AGUA,
            TipoCambio.DESAGUE,
            TipoCambio.TERMO
        )
    ),
    ELECTRICIDAD(
        "electricidad",
        GrupoChips.INSTALACIONES,
        tiposDe(Ambito.ELECTRICIDAD)
    ),
    CLIMATIZACION(
        "calefacción y aire",
        GrupoChips.INSTALACIONES,
        tiposDe(Ambito.CLIMATIZACION)
    ),
    MOBILIARIO(
        "muebles a medida",
        GrupoChips.MOBILIARIO,
        tiposDe(Ambito.MOBILIARIO)
    )
}

object Propuestas {

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** Los chips tal cual se pintan: cada bloque con sus opciones, en el orden del enum. */
    fun porGrupo(): List<Pair<GrupoChips, List<Permiso>>> =
        GrupoChips.entries.map { grupo -> grupo to Permiso.entries.filter { it.grupo == grupo } }

    /**
     * Trozo del catálogo que abren los chips marcados. Sin chips no se limita nada: decide la
     * frase del cliente.
     */
    fun catalogoPara(permisos: Collection<String>): Set<TipoCambio>? {
        val abiertas = permisos
            .mapNotNull { texto ->
                Permiso.entries.firstOrNull { it.etiqueta.equals(texto.trim(), ignoreCase = true) }
            }
            .flatMap { it.abre }
            .toSet()
        return abiertas.ifEmpty { null }
    }

    /** «Salón» y «salon» son la misma estancia: sin tildes, en minúsculas y sin espacios de más. */
    fun normalizarNombre(texto: String): String =
        java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()

    /**
     * Devuelve null si el cuerpo no es JSON o no trae ni un escenario usable. Si se pasan
     * [permitidos], se descartan las partidas que el cliente no autorizó.
     */
    fun desdeJson(cuerpo: String, permitidos: Set<TipoCambio>? = null): List<Escenario>? {
        val respuesta = runCatching {
            json.decodeFromString<RespuestaPropuestasDto>(cuerpo)
        }.getOrNull() ?: return null
        val escenarios = desdeDto(respuesta.propuestas, permitidos)
        return escenarios.ifEmpty { null }
    }

    internal fun desdeDto(
        lista: List<PropuestaDto>,
        permitidos: Set<TipoCambio>? = null
    ): List<Escenario> =
        lista.take(3).mapIndexedNotNull { indice, propuesta ->
            val actuaciones = propuesta.actuaciones
                .take(8)
                .mapNotNull { actuacion -> convertir(actuacion, permitidos) }
                .distinctBy { it.tipo to normalizarNombre(it.estancia) }

            if (actuaciones.size < 2) return@mapIndexedNotNull null

            Escenario(
                titulo = propuesta.titulo.trim().take(60)
                    .ifBlank { "Propuesta ${'A' + indice}" },
                resumen = propuesta.resumen.trim().take(240),
                actuaciones = actuaciones
            )
        }

    private fun convertir(dto: ActuacionDto, permitidos: Set<TipoCambio>?): Actuacion? {
        val tipo = runCatching { TipoCambio.valueOf(dto.tipo.trim()) }.getOrNull()
            ?: return null
        if (permitidos != null && tipo !in permitidos) return null
        val cantidad = if (!tipo.mide) {
            1.0
        } else {
            dto.cantidad.takeIf { it.isFinite() }?.coerceIn(0.1, 500.0) ?: return null
        }
        return Actuacion(
            tipo = tipo,
            estancia = dto.estancia.trim().take(40),
            cantidad = cantidad,
            motivo = dto.motivo.trim().take(140)
        )
    }
}
