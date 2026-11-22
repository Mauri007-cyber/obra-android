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

        val puntos = orden.map { nodos[it] }
        // Un contorno que se cruza no representa una superficie de estancia fiable.
        fun orientacion(a: Punto, b: Punto, c: Punto): Double =
            (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        fun toca(a: Punto, b: Punto, p: Punto): Boolean =
            abs(orientacion(a, b, p)) < 1e-9 &&
                p.x >= minOf(a.x, b.x) - 1e-9 && p.x <= maxOf(a.x, b.x) + 1e-9 &&
                p.y >= minOf(a.y, b.y) - 1e-9 && p.y <= maxOf(a.y, b.y) + 1e-9
        fun cruzan(a: Punto, b: Punto, c: Punto, d: Punto): Boolean {
            val o1 = orientacion(a, b, c); val o2 = orientacion(a, b, d)
            val o3 = orientacion(c, d, a); val o4 = orientacion(c, d, b)
            return (o1 * o2 < 0 && o3 * o4 < 0) || toca(a, b, c) || toca(a, b, d) || toca(c, d, a) || toca(c, d, b)
        }
        for (i in puntos.indices) for (j in i + 1 until puntos.size) {
            if (j == i + 1 || (i == 0 && j == puntos.lastIndex)) continue
            if (cruzan(puntos[i], puntos[(i + 1) % puntos.size], puntos[j], puntos[(j + 1) % puntos.size])) return null
        }
        return puntos
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
