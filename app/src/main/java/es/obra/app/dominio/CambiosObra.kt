package es.obra.app.dominio

import java.util.Locale

/**
 * Un cambio concreto dentro de una estancia: qué se hace, cuánto, con qué acabado y si el
 * cliente lo ha dado por bueno.
 *
 * El estado es lo que convierte esto en una herramienta de evaluación: el albañil propone,
 * el cliente aprueba o descarta, y lo que se enseña al final es solo lo aprobado.
 */
data class Cambio(
    val id: Long = 0,
    val tipo: TipoCambio,
    val cantidad: Double = 0.0,
    /** Precio de esta obra para este cambio. null = el orientativo del catálogo. */
    val precioPropio: Double? = null,
    val acabado: String = "",
    val nota: String = "",
    val estado: EstadoCambio = EstadoCambio.PROPUESTO,
    /** A, B, C… para poder enseñar dos propuestas de la misma estancia. */
    val grupo: String = "A"
) {
    val precio: Double get() = precioPropio ?: tipo.precio

    /** Las partidas enteras se cobran una vez; el resto, por cantidad. */
    val importe: Double
        get() = if (!tipo.mide) precio else precio * cantidad.coerceAtLeast(0.0)

    val cerrado: Boolean get() = estado == EstadoCambio.APROBADO

    val detalle: String
        get() = buildList {
            if (tipo.mide && cantidad > 0.0) {
                add("${cantidad(cantidad)} ${tipo.unidad.etiqueta}")
            } else if (!tipo.mide) {
                add("partida entera")
            }
            if (acabado.isNotBlank()) add(acabado)
            if (nota.isNotBlank()) add(nota)
        }.joinToString(" · ")

    /** Una línea para el cliente: «Suelo porcelánico gris cemento — 18,40 m²». */
    val linea: String
        get() = if (detalle.isBlank()) tipo.nombre else "${tipo.nombre} — $detalle"

    /** Cantidad razonable al añadirlo a una estancia: 18 m² de suelo, 12 m de rodapié… */
    fun conCantidadSugerida(superficie: Double, altura: Double = 2.5): Cambio {
        if (!tipo.mide || cantidad > 0.0) return this
        val sugerida = when (tipo.unidad) {
            Unidad.M2 -> if (tipo.capa == Capa.PARED || tipo.ambito == Ambito.PINTURA) {
                // Una habitación de 4 × 3 tiene unas paredes de ~35 m², no 12.
                (2 * (Math.sqrt(superficie) + Math.sqrt(superficie)) * altura)
            } else {
                superficie
            }
            Unidad.ML -> Math.sqrt(superficie) * 2
            Unidad.UD -> 1.0
            Unidad.GLOBAL -> 1.0
        }
        return copy(cantidad = redondear(sugerida, 1))
    }

    private fun redondear(valor: Double, decimales: Int): Double {
        val factor = Math.pow(10.0, decimales.toDouble())
        return Math.round(valor * factor) / factor
    }

    private fun cantidad(valor: Double): String =
        String.format(Locale.forLanguageTag("es-ES"), "%,.2f", valor)
}

enum class EstadoCambio(val etiqueta: String) {
    PROPUESTO("Propuesto"),
    APROBADO("Aprobado"),
    RECHAZADO("Descartado");

    companion object {
        fun porNombre(texto: String): EstadoCambio =
            entries.firstOrNull { it.name.equals(texto, ignoreCase = true) } ?: PROPUESTO
    }
}

/**
 * Una habitación de la obra: su foto, sus medidas y todos sus cambios.
 *
 * Aquí está la diferencia con la primera versión: una obra no es una foto con un prompt, es
 * una lista de estancias, y cada estancia puede llevar un cambio o veinte.
 */
data class EstanciaProyecto(
    val id: Long,
    val nombre: String,
    val superficie: Double = 0.0,
    val altura: Double = 2.5,
    val foto: String = "",
    /** Última imagen terminada guardada, para no perderla al salir de la pantalla. */
    val fotoTerminada: String = "",
    val cambios: List<Cambio> = emptyList(),
    val grupo: String = "A"
) {
    fun deGrupo(grupo: String = this.grupo): EstanciaProyecto =
        copy(grupo = grupo, cambios = cambios.filter { it.grupo == grupo })

    val aprobados: List<Cambio> get() = cambios.filter { it.cerrado }
    val propuestos: List<Cambio> get() = cambios.filter { it.estado == EstadoCambio.PROPUESTO }
    val descartados: List<Cambio> get() = cambios.filter { it.estado == EstadoCambio.RECHAZADO }

    val costeAprobado: Double get() = aprobados.sumOf { it.importe }
    val costePropuesto: Double get() = propuestos.sumOf { it.importe }
    val costeTotal: Double get() = cambios.filter { it.estado != EstadoCambio.RECHAZADO }.sumOf { it.importe }

    val terminada: Boolean get() = cambios.isNotEmpty() && propuestos.isEmpty()
    val vacia: Boolean get() = cambios.isEmpty()

    /** Cambios aprobados que se ven en la foto: los que se pueden enseñar. */
    val visibles: List<Cambio> get() = aprobados.filter { it.tipo.seVe }

    val sePuedeEnsenar: Boolean get() = foto.isNotBlank() && visibles.isNotEmpty()

    fun porAmbito(): List<Pair<Ambito, List<Cambio>>> =
        cambios.groupBy { it.tipo.ambito }
            .toList()
            .sortedBy { it.first.orden }

    /** Las frases que entiende el cliente, sacadas de los cambios, no de la imaginación. */
    fun resumen(): List<String> = aprobados
        .sortedBy { it.tipo.ambito.orden }
        .map { it.linea }
}

/** La obra entera: todas las estancias y sus cambios. */
data class Proyecto(
    val nombre: String,
    val estancias: List<EstanciaProyecto> = emptyList(),
    val grupo: String = "A"
) {
    companion object {
        private const val GRUPOS = "ABCDEFGH"

        /** Siguiente letra libre para una propuesta nueva: A, B, C… */
        fun siguienteGrupo(usados: Collection<String>): String =
            GRUPOS.firstOrNull { letra -> usados.none { it.equals(letra.toString(), true) } }
                ?.toString() ?: "Z"
    }

    val cambios: List<Cambio> get() = estancias.flatMap { it.cambios }
    val aprobados: List<Cambio> get() = cambios.filter { it.cerrado }
    val propuestos: List<Cambio> get() = cambios.filter { it.estado == EstadoCambio.PROPUESTO }
    val descartados: List<Cambio> get() = cambios.filter { it.estado == EstadoCambio.RECHAZADO }

    val costeAprobado: Double get() = aprobados.sumOf { it.importe }
    val costePropuesto: Double get() = propuestos.sumOf { it.importe }
    val costeTotal: Double get() = cambios.filter { it.estado != EstadoCambio.RECHAZADO }.sumOf { it.importe }

    /** De 0 a 1: cuánto de lo propuesto está ya aprobado. */
    val progreso: Float
        get() {
            val total = cambios.count { it.estado != EstadoCambio.RECHAZADO }
            if (total == 0) return 0f
            return aprobados.size.toFloat() / total
        }

    val listas: List<EstanciaProyecto> get() = estancias.filter { it.terminada }
    val pendientes: List<EstanciaProyecto> get() = estancias.filter { it.cambios.isNotEmpty() && !it.terminada }

    fun estancia(id: Long): EstanciaProyecto? = estancias.firstOrNull { it.id == id }

    fun deGrupo(grupo: String = this.grupo): Proyecto =
        copy(grupo = grupo, estancias = estancias.map { it.deGrupo(grupo) })

    /** Todo lo que falta para poder enseñar la obra sin que se caiga nada. */
    fun avisos(): List<String> = buildList {
        if (estancias.isEmpty()) add("La obra no tiene estancias todavía.")
        estancias.forEach { estancia ->
            if (estancia.cambios.isEmpty()) {
                add("«${estancia.nombre}» no tiene ningún cambio.")
            }
            if (estancia.foto.isBlank() && estancia.cambios.any { it.tipo.seVe }) {
                add("«${estancia.nombre}» tiene cambios que se ven y no tiene foto.")
            }
            estancia.aprobados.filter { it.tipo.mide && it.cantidad <= 0.0 }.forEach {
                add("«${estancia.nombre}»: ${it.tipo.nombre} no tiene cantidad.")
            }
        }
        val sinCoste = aprobados.filter { it.importe <= 0.0 }
        if (sinCoste.isNotEmpty()) {
            add("Hay ${sinCoste.size} cambio(s) aprobado(s) sin precio.")
        }
    }

    /** Resumen de la obra finalizada, estancia a estancia, para leerlo en voz alta. */
    fun guion(): List<ParrafoEstancia> = estancias.map { estancia ->
        ParrafoEstancia(
            estancia = estancia.nombre,
            frases = estancia.resumen().ifEmpty { listOf("Sin cambios aprobados.") },
            coste = estancia.costeAprobado
        )
    }

    /** Orden de recorrido: primero lo que se enseña, luego el resto. */
    fun recorrido(): List<EstanciaProyecto> =
        estancias.sortedWith(
            compareByDescending<EstanciaProyecto> { it.sePuedeEnsenar }
                .thenByDescending { it.aprobados.size }
                .thenBy { it.nombre }
        )
}

data class ParrafoEstancia(
    val estancia: String,
    val frases: List<String>,
    val coste: Double
)

/** Formato de dinero y de cifras en español, en un solo sitio. */
object Cifra {
    fun euros(valor: Double): String =
        String.format(Locale.forLanguageTag("es-ES"), "%,.2f", valor) + " €"

    fun numero(valor: Double, decimales: Int = 2): String =
        String.format(Locale.forLanguageTag("es-ES"), "%,.${decimales}f", valor)
}
