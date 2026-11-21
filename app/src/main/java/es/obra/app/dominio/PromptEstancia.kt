package es.obra.app.dominio

/**
 * El prompt ya no se escribe: se deduce de la lista de cambios aprobados de la estancia.
 *
 * Así el modelo recibe exactamente lo que el cliente ha aprobado, en el mismo orden en que lo
 * vería un albañil (suelo, paredes, techo, huecos, objetos), y siempre con la misma regla final
 * que protege la obra: no mover nada de sitio.
 *
 * Es puro: se puede probar sin red y sin móvil.
 */
object PromptEstancia {

    /** Mismo tope que el Worker. Si el texto se pasa, el servidor lo rechaza. */
    const val LIMITE = 1200

    /** Orden de trabajo real: primero lo que se pisa, al final lo que se cuelga. */
    private val ORDEN = listOf(Capa.SUELO, Capa.PARED, Capa.TECHO, Capa.HUECO, Capa.OBJETO)

    private const val REGLA =
        "No cambies el tamaño de la habitación ni el sitio de las puertas y ventanas."

    private const val MARCO =
        "Reforma la estancia de la foto aplicando exactamente esta lista de cambios, " +
            "en este orden:"

    /**
     * @param incluirPropuestos si es true se piden también los cambios sin aprobar (vista previa).
     * @param fallos lo que ha salido mal en la propuesta anterior, con las palabras del cliente.
     */
    fun componer(
        estancia: EstanciaProyecto,
        incluirPropuestos: Boolean = false,
        fallos: List<String> = emptyList(),
        nota: String = ""
    ): String {
        val pedidos = if (incluirPropuestos) {
            estancia.cambios.filter { it.estado != EstadoCambio.RECHAZADO }
        } else {
            estancia.aprobados
        }

        // Solo se piden al modelo los cambios que se ven: pintar un cuadro eléctrico no se nota.
        val visibles = pedidos
            .filter { it.tipo.seVe }
            .sortedBy { cambio -> ORDEN.indexOf(cambio.tipo.capa) }

        if (visibles.isEmpty()) return ""

        fun montar(lista: List<Cambio>): String = buildList {
            add(MARCO)
            lista.forEachIndexed { indice, cambio ->
                add("${indice + 1}. ${cambio.instruccion}.")
            }
            add(
                "Mantén el encuadre, la perspectiva y la luz que entra por la ventana. " +
                    "Conserva el resto de lo que se ve tal cual."
            )
            if (fallos.isNotEmpty()) {
                add("Corrige esto de la propuesta anterior: ${fallos.joinToString("; ")}.")
            }
            if (nota.isNotBlank()) add("Además: ${recortar(nota.trim(), 240)}")
            add(REGLA)
        }.joinToString("\n")

        var texto = montar(visibles)
        if (texto.length <= LIMITE) return texto

        // Se van quitando cambios por la cola (los objetos primero) hasta que quepa.
        // La regla final y el marco no se tocan nunca.
        var recortados = visibles
        while (recortados.size > 1) {
            recortados = recortados.dropLast(1)
            texto = montar(recortados)
            if (texto.length <= LIMITE) return texto
        }

        // Un solo cambio y aún así no cabe: se acorta su descripción, no la regla.
        val unico = recortados.first()
        val cuerpo = montar(listOf(unico.copy(nota = "")))
        val hueco = (LIMITE - REGLA.length - 1).coerceAtLeast(MARCO.length)
        return cuerpo.take(hueco).trimEnd() + "\n" + REGLA
    }

    /** Para la pantalla: cuántos cambios van al modelo y cuántos se quedan fuera. */
    fun cobertura(estancia: EstanciaProyecto): Pair<Int, Int> {
        val pedidos = estancia.aprobados.filter { it.tipo.seVe }
        val texto = componer(estancia)
        if (texto.isEmpty()) return 0 to pedidos.size
        val incluidos = pedidos.count { texto.contains(it.instruccion) }
        return incluidos to (pedidos.size - incluidos)
    }

    private fun recortar(texto: String, maximo: Int): String =
        if (texto.length <= maximo) texto else texto.take(maximo - 1).trimEnd() + "…"
}
