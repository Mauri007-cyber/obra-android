package es.obra.app.dominio

/**
 * Todo lo que hace que una medida sea creíble y se pueda corregir.
 *
 * Principios:
 *  - una sola lectura nunca es una medida: se toma la mediana de muchas;
 *  - si se sabe cuánto mide algo de verdad, se corrige todo con un factor;
 *  - si el número no puede ser de una habitación, se avisa antes de presupuestar.
 */
object Medidas {

    enum class Calidad { BUENA, MEDIA, FLOJA }

    fun mediana(valores: List<Double>): Double {
        if (valores.isEmpty()) return 0.0
        val orden = valores.sorted()
        val medio = orden.size / 2
        return if (orden.size % 2 == 0) {
            (orden[medio - 1] + orden[medio]) / 2.0
        } else {
            orden[medio]
        }
    }

    /** Desviación relativa entre la lectura mayor y la menor. 0,02 = 2 %. */
    fun dispersion(valores: List<Double>): Double {
        if (valores.size < 2) return 0.0
        val centro = mediana(valores)
        if (centro <= 0.0) return 0.0
        return (valores.max() - valores.min()) / centro
    }

    fun calidad(dispersion: Double): Calidad = when {
        dispersion <= 0.02 -> Calidad.BUENA
        dispersion <= 0.05 -> Calidad.MEDIA
        else -> Calidad.FLOJA
    }

    /** Factor para corregir lo medido con una medida real. Acotado: un factor salvaje es un error. */
    fun factorCalibracion(medido: Double, real: Double): Double {
        if (medido <= 0.0 || real <= 0.0) return 1.0
        return (real / medido).coerceIn(0.75, 1.35)
    }

    fun aplicar(valor: Double, factor: Double): Double = valor * factor

    /** Incertidumbre esperada según cómo se midió. Se muestra, nunca se esconde. */
    fun incertidumbre(metodo: String): Double = when (metodo) {
        "arcore" -> 0.03
        "foto" -> 0.05
        "plano" -> 0.02
        else -> 0.01
    }

    /**
     * Una estancia de menos de 60 cm o de más de 40 m no es una estancia.
     * Detecta el error clásico de teclear 342 en vez de 3,42.
     */
    fun plausible(largo: Double, ancho: Double): Boolean {
        fun vale(valor: Double): Boolean = valor <= 0.0 || (valor >= 0.6 && valor <= 40.0)
        return vale(largo) && vale(ancho)
    }

    fun calidadTexto(calidad: Calidad): String = when (calidad) {
        Calidad.BUENA -> "Medida buena"
        Calidad.MEDIA -> "Medida aceptable"
        Calidad.FLOJA -> "Repite la medida"
    }
}
