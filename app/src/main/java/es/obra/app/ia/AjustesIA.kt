package es.obra.app.ia

import android.content.Context

/**
 * Lo poco que hay que recordar de la IA: dónde está el proxy, si el dueño del móvil
 * ha dado permiso para mandar fotos y cuántas lleva hoy.
 *
 * La clave de Cloudflare no está aquí: vive en el Worker.
 */
class AjustesIA(contexto: Context) {

    private val prefs = contexto.getSharedPreferences("ia", Context.MODE_PRIVATE)

    var direccion: String
        get() = prefs.getString("direccion", "").orEmpty()
        set(valor) = prefs.edit().putString("direccion", valor.trim()).apply()

    var consentido: Boolean
        get() = prefs.getBoolean("consentido", false)
        set(valor) = prefs.edit().putBoolean("consentido", valor).apply()

    /** Tope en el móvil, por debajo del del Worker, para no gastar el cupo a lo tonto. */
    var limite: Int
        get() = prefs.getInt("limite", 12)
        set(valor) = prefs.edit().putInt("limite", valor.coerceIn(1, 200)).apply()

    fun cupoHoy(): CupoDiario = CupoDiario(
        dia = prefs.getString("dia", "").orEmpty(),
        usadas = prefs.getInt("usadas", 0),
        limite = limite
    )

    fun guardarCupo(cupo: CupoDiario) {
        prefs.edit().putString("dia", cupo.dia).putInt("usadas", cupo.usadas).apply()
    }

    val configurada: Boolean get() = direccion.isNotBlank()
}
