package es.obra.app.dominio

import es.obra.app.datos.MuroEntity
import kotlin.math.abs

/**
 * Convertir muros sueltos en una estancia cerrada.
 *
 * El editor deja dibujar muros sin orden. Para saber la superficie real (y no el rectángulo
 * que los envuelve, que en una cocina con pasillo miente mucho) hay que recorrer el contorno:
 * todos los extremos deben tocarse y cada punto debe tener exactamente dos muros.
 */
object Poligono {

    /**
     * Devuelve los vértices en orden si los muros forman un contorno cerrado.
     * Si falta un tramo o hay un muro suelto, devuelve null: mejor no dar superficie que darla mal.
     */
    fun cerrado(muros: List<MuroEntity>, toleranciaMetros: Double = 0.03): List<Punto>? {
        if (muros.size < 3) return null

        val nodos = mutableListOf<Punto>()

        fun nodoDe(x: Double, y: Double): Int {
            val existente = nodos.indexOfFirst {
                abs(it.x - x) <= toleranciaMetros && abs(it.y - y) <= toleranciaMetros
            }
            if (existente >= 0) return existente
            nodos.add(Punto(x, y))
            return nodos.lastIndex
        }

        val aristas = muros.map { nodoDe(it.x1, it.y1) to nodoDe(it.x2, it.y2) }

        // Cada esquina debe tener exactamente dos muros.
        val grado = IntArray(nodos.size)
        aristas.forEach { (a, b) ->
            if (a == b) return null
            grado[a]++
            grado[b]++
        }
        if (grado.any { it != 2 }) return null

        // Recorrer el contorno desde la primera esquina.
        val usadas = BooleanArray(aristas.size)
        val orden = mutableListOf(0)
        var actual = 0

        while (true) {
            val indice = aristas.indices.firstOrNull { i ->
                !usadas[i] && (aristas[i].first == actual || aristas[i].second == actual)
            } ?: return null

            usadas[indice] = true
            val (a, b) = aristas[indice]
            actual = if (a == actual) b else a
            if (actual == 0) break
            orden.add(actual)
        }

        // Si sobra algún muro, no es un contorno simple: mejor no dar superficie que darla mal.
        if (usadas.any { !it }) return null

        return orden.map { nodos[it] }
    }

    fun superficie(muros: List<MuroEntity>): Double? =
        cerrado(muros)?.let { Geometria.area(it) }

    /** Perímetro del contorno cerrado; null si no cierra. */
    fun perimetro(muros: List<MuroEntity>): Double? =
        cerrado(muros)?.let { Geometria.perimetro(it) }

    fun longitud(muro: MuroEntity): Double = kotlin.math.hypot(
        muro.x2 - muro.x1,
        muro.y2 - muro.y1
    )
}
