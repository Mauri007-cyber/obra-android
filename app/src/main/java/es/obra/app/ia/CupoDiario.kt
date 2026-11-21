package es.obra.app.ia

/**
 * Cuántas imágenes quedan hoy. Se lleva en el propio móvil, además del tope del Worker:
 * si el servidor dice que no, tampoco se intenta.
 */
data class CupoDiario(
    val dia: String = "",
    val usadas: Int = 0,
    val limite: Int = 12
) {
    init {
        require(limite > 0) { "El límite tiene que ser mayor que cero" }
    }

    val restantes: Int get() = (limite - usadas).coerceAtLeast(0)

    fun puede(): Boolean = restantes > 0

    /** Si cambió el día, el contador vuelve a cero sin tocar el límite. */
    fun deHoy(hoy: String): CupoDiario =
        if (dia == hoy) this else copy(dia = hoy, usadas = 0)

    /** Devuelve el cupo ya gastado, o null si no queda nada. */
    fun consumir(hoy: String): CupoDiario? {
        val actual = deHoy(hoy)
        return if (actual.puede()) actual.copy(usadas = actual.usadas + 1) else null
    }
}
