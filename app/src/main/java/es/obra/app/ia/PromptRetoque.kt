package es.obra.app.ia

/**
 * El texto que se manda al modelo. Nadie escribe un prompt: se elige con botones
 * y aquí se ordena, se acota y se cierra con lo que no se puede tocar.
 *
 * Todo es puro para poder probarlo sin móvil ni red.
 */
object PromptRetoque {

    /** Mismo tope que el Worker: si se pasa, el servidor lo rechaza. */
    const val LIMITE = 1200

    val SUGERENCIAS = listOf(
        "Suelo de tarima clara",
        "Paredes blancas",
        "Baño moderno",
        "Cocina integrada",
        "Luz cálida"
    )

    val FALLOS = listOf(
        "La puerta no está donde era",
        "La ventana ha cambiado",
        "El suelo no es ese",
        "El color de la pared no es",
        "Ha metido muebles de más",
        "Todo demasiado oscuro",
        "Parece de revista, no real",
        "Ha cambiado el tamaño"
    )

    data class Eleccion(
        val peticion: String,
        val pared: String? = null,
        val suelo: String? = null,
        val luces: List<String> = emptyList(),
        val fallos: List<String> = emptyList(),
        val nota: String = ""
    )

    fun componer(eleccion: Eleccion): String {
        val peticion = eleccion.peticion.trim()
        if (peticion.isEmpty()) return ""

        fun montar(texto: String): String = buildList {
            add("Reforma esta habitación usando la foto como base.")
            add(
                "Mantén la geometría, el encuadre, la ventana, la puerta y la luz " +
                    "que entra por la ventana."
            )
            add("Cambios pedidos: $texto")
            acabados(eleccion)?.let { add("Acabados: $it.") }
            if (eleccion.luces.isNotEmpty()) {
                add("Iluminación: ${eleccion.luces.joinToString(", ")}.")
            }
            if (eleccion.fallos.isNotEmpty()) {
                add("Corrige esto de la propuesta anterior: ${eleccion.fallos.joinToString("; ")}.")
            }
            if (eleccion.nota.isNotBlank()) {
                add("Además: ${recortar(eleccion.nota.trim(), 240)}")
            }
            add("No cambies el tamaño de la habitación ni el sitio de las puertas y ventanas.")
        }.joinToString("\n")

        var texto = montar(peticion)
        if (texto.length > LIMITE) {
            // Se recorta la petición, nunca la regla final: es la que protege la obra.
            val exceso = texto.length - LIMITE + 1
            texto = montar(recortar(peticion, (peticion.length - exceso).coerceAtLeast(40)))
        }
        return texto
    }

    private fun acabados(eleccion: Eleccion): String? {
        val partes = listOfNotNull(
            eleccion.pared?.takeIf { it.isNotBlank() }?.let { "paredes $it" },
            eleccion.suelo?.takeIf { it.isNotBlank() }?.let { "suelo $it" }
        )
        return partes.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun recortar(texto: String, maximo: Int): String =
        if (texto.length <= maximo) texto else texto.take(maximo - 1).trimEnd() + "…"
}
