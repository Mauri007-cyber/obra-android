package es.obra.app.datos

import android.content.Context
import es.obra.app.dominio.TipoCambio

/**
 * El último precio que el albañil puso a cada partida.
 *
 * Se guarda en el móvil, no en la obra: si cobra la pintura a 9 €/m² en un piso, la siguiente vez
 * que añada pintura ya sale con 9. Nunca se inventa un precio: si no hay nada guardado, se usa el
 * orientativo del catálogo.
 */
object PreciosPropios {

    private const val ARCHIVO = "precios-propios"

    fun leer(contexto: Context, tipo: TipoCambio): Double? {
        val guardado = contexto
            .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            .getString(tipo.name, null) ?: return null
        return guardado.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    }

    fun recordar(contexto: Context, tipo: TipoCambio, precio: Double) {
        if (!precio.isFinite() || precio <= 0.0) return
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            .edit()
            .putString(tipo.name, precio.toString())
            .apply()
    }

    /** El precio que se propone al añadir la partida: el suyo si lo hay, si no el del catálogo. */
    fun propuesto(contexto: Context, tipo: TipoCambio): Double =
        leer(contexto, tipo) ?: tipo.precio
}
