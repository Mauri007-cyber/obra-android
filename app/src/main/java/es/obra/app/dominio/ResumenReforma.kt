package es.obra.app.dominio

import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity

/**
 * Las tres frases que entiende un cliente que no sabe leer un plano.
 *
 * Se sacan de lo dibujado, no de la imaginación: si dice «se tira un tabique de 3,20 m»,
 * ese tabique está marcado en el plano.
 */
object ResumenReforma {

    fun lineas(
        muros: List<MuroEntity>,
        huecos: List<HuecoEntity>,
        superficie: Double,
        altura: Double
    ): List<String> {
        val salida = mutableListOf<String>()

        val tirados = muros.filter { it.estado == "tirado" }
        val nuevos = muros.filter { it.estado == "nuevo" }

        val metrosTirados = tirados.sumOf { Poligono.longitud(it) }
        if (metrosTirados > 0.05) {
            salida.add(
                if (tirados.size == 1) "Se tira un tabique de ${fmt(metrosTirados)} m"
                else "Se tiran ${tirados.size} tabiques (${fmt(metrosTirados)} m en total)"
            )
        }

        val metrosNuevos = nuevos.sumOf { Poligono.longitud(it) }
        if (metrosNuevos > 0.05) {
            salida.add("Se levanta un tabique nuevo de ${fmt(metrosNuevos)} m")
        }

        val puertas = huecos.count { it.tipo == "puerta" }
        val ventanas = huecos.count { it.tipo == "ventana" }
        if (puertas + ventanas > 0) {
            val partes = mutableListOf<String>()
            if (puertas > 0) partes.add(if (puertas == 1) "1 puerta" else "$puertas puertas")
            if (ventanas > 0) partes.add(if (ventanas == 1) "1 ventana" else "$ventanas ventanas")
            salida.add("Huecos: ${partes.joinToString(" y ")}")
        }

        if (superficie > 0.5) {
            salida.add("Estancia de ${fmt(superficie)} m² con ${fmt(altura)} m de altura")
        }

        return salida
    }

    /** Para la lámina: tres frases como mucho. */
    fun paraLamina(
        muros: List<MuroEntity>,
        huecos: List<HuecoEntity>,
        superficie: Double,
        altura: Double
    ): List<String> = lineas(muros, huecos, superficie, altura).take(3)

    private fun fmt(valor: Double): String {
        val redondeado = Math.round(valor * 100.0) / 100.0
        return String.format(java.util.Locale.forLanguageTag("es-ES"), "%.2f", redondeado)
            .replace('.', ',')
    }
}
