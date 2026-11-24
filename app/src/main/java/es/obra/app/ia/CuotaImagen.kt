package es.obra.app.ia

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Lo poco que la app recuerda entre imágenes: quién es este móvil para el contador del Worker,
 * cuántas imágenes quedan hoy y si el usuario ya aceptó que la foto salga a internet.
 *
 * El identificador es un UUID al azar, no el aparato ni la cuenta de nadie: sirve para que un
 * móvil no se coma la cuota de los demás y no se puede usar para identificar a una persona.
 */
object CuotaImagen {

    private const val PREFS = "imagen_gratis"
    private const val CLAVE_ID = "dispositivo"
    private const val CLAVE_DIA = "dia"
    private const val CLAVE_RESTANTES = "restantes"
    private const val CLAVE_AVISO = "aviso_envio_aceptado"

    fun dispositivo(contexto: Context): String {
        val prefs = prefs(contexto)
        prefs.getString(CLAVE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val nuevo = UUID.randomUUID().toString()
        prefs.edit().putString(CLAVE_ID, nuevo).apply()
        return nuevo
    }

    /** null = todavía no se sabe (primera imagen del día o servicio recién conectado). */
    fun restantes(contexto: Context): Int? {
        val prefs = prefs(contexto)
        if (prefs.getString(CLAVE_DIA, null) != hoy()) return null
        if (!prefs.contains(CLAVE_RESTANTES)) return null
        return prefs.getInt(CLAVE_RESTANTES, 0)
    }

    fun fijar(contexto: Context, restantes: Int) {
        prefs(contexto).edit()
            .putString(CLAVE_DIA, hoy())
            .putInt(CLAVE_RESTANTES, restantes.coerceAtLeast(0))
            .apply()
    }

    fun haAceptadoEnvio(contexto: Context): Boolean =
        prefs(contexto).getBoolean(CLAVE_AVISO, false)

    fun aceptarEnvio(contexto: Context) {
        prefs(contexto).edit().putBoolean(CLAVE_AVISO, true).apply()
    }

    private fun prefs(contexto: Context) =
        contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** El día de la cuota es el mismo para todos: el Worker la reinicia a medianoche UTC. */
    internal fun hoy(ahora: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ahora))
}
