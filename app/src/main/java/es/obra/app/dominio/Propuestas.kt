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

object Propuestas {

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Devuelve null si el cuerpo no es JSON o no trae ni un escenario usable. */
    fun desdeJson(cuerpo: String): List<Escenario>? {
        val respuesta = runCatching {
            json.decodeFromString<RespuestaPropuestasDto>(cuerpo)
        }.getOrNull() ?: return null
        val escenarios = desdeDto(respuesta.propuestas)
        return escenarios.ifEmpty { null }
    }

    internal fun desdeDto(lista: List<PropuestaDto>): List<Escenario> =
        lista.take(3).mapIndexedNotNull { indice, propuesta ->
            val actuaciones = propuesta.actuaciones
                .take(8)
                .mapNotNull { actuacion -> convertir(actuacion) }
                .distinctBy { it.tipo to it.estancia.lowercase() }

            if (actuaciones.size < 2) return@mapIndexedNotNull null

            Escenario(
                titulo = propuesta.titulo.trim().take(60)
                    .ifBlank { "Propuesta ${'A' + indice}" },
                resumen = propuesta.resumen.trim().take(240),
                actuaciones = actuaciones
            )
        }

    private fun convertir(dto: ActuacionDto): Actuacion? {
        val tipo = runCatching { TipoCambio.valueOf(dto.tipo.trim()) }.getOrNull()
            ?: return null
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
