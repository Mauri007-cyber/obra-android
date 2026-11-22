package es.obra.app.render

import kotlin.math.sqrt

/** Punto o vector en metros. X e Y son el plano; Z es la altura. */
data class Vec3(val x: Double, val y: Double, val z: Double) {

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)

    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)

    operator fun times(e: Double) = Vec3(x * e, y * e, z * e)

    infix fun punto(o: Vec3): Double = x * o.x + y * o.y + z * o.z

    infix fun cruz(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun norma(): Double = sqrt(x * x + y * y + z * z)

    fun normalizado(): Vec3 {
        val n = norma()
        return if (n < 1e-9) Vec3(0.0, 0.0, 1.0) else times(1.0 / n)
    }
}
