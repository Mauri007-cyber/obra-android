package es.obra.app.dominio

/**
 * Trocear un polígono simple en triángulos para poder pintar el suelo.
 *
 * Algoritmo de las orejas: se busca una esquina que forme triángulo sin que quede ningún
 * otro vértice dentro, se corta y se repite. Es suficiente para plantas de piso y no
 * necesita ninguna librería.
 */
object Triangulos {

    fun area(puntos: List<Punto>): Double {
        if (puntos.size < 3) return 0.0
        var suma = 0.0
        for (i in puntos.indices) {
            val a = puntos[i]
            val b = puntos[(i + 1) % puntos.size]
            suma += a.x * b.y - b.x * a.y
        }
        return suma / 2.0
    }

    /** Índices de los vértices de cada triángulo, en orden antihorario. */
    fun orejas(puntos: List<Punto>): List<Triple<Int, Int, Int>> {
        if (puntos.size < 3) return emptyList()

        val indices = puntos.indices.toMutableList()
        if (area(puntos) < 0) indices.reverse()

        val salida = mutableListOf<Triple<Int, Int, Int>>()
        var vueltas = 0

        while (indices.size > 3) {
            vueltas++
            if (vueltas > 5000) return emptyList()

            var cortada = false
            for (i in indices.indices) {
                val a = indices[(i + indices.size - 1) % indices.size]
                val b = indices[i]
                val c = indices[(i + 1) % indices.size]

                if (!esConvexa(puntos[a], puntos[b], puntos[c])) continue
                if (alguienDentro(puntos, indices, a, b, c)) continue

                salida.add(Triple(a, b, c))
                indices.removeAt(i)
                cortada = true
                break
            }
            // Sin ninguna oreja el polígono se cruza: mejor no pintar nada que pintar mal.
            if (!cortada) return emptyList()
        }

        if (indices.size == 3) salida.add(Triple(indices[0], indices[1], indices[2]))
        return salida
    }

    private fun esConvexa(a: Punto, b: Punto, c: Punto): Boolean =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) > 1e-12

    private fun alguienDentro(
        puntos: List<Punto>,
        indices: List<Int>,
        a: Int,
        b: Int,
        c: Int
    ): Boolean = indices.any { i ->
        i != a && i != b && i != c &&
            dentro(puntos[i], puntos[a], puntos[b], puntos[c])
    }

    private fun dentro(p: Punto, a: Punto, b: Punto, c: Punto): Boolean {
        val d1 = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        val d2 = (c.x - b.x) * (p.y - b.y) - (c.y - b.y) * (p.x - b.x)
        val d3 = (a.x - c.x) * (p.y - c.y) - (a.y - c.y) * (p.x - c.x)
        return d1 >= -1e-12 && d2 >= -1e-12 && d3 >= -1e-12
    }
}
