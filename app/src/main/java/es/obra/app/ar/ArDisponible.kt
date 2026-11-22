package es.obra.app.ar

import android.content.Context
import com.google.ar.core.ArCoreApk

enum class EstadoAr {
    DISPONIBLE,
    HAY_QUE_INSTALAR,
    NO_SOPORTADO
}

/**
 * ARCore no está en todos los móviles: exige haber salido de fábrica con Play Store y tener
 * instalado "Google Play Services for AR". Si no está, la app no debe parecer rota:
 * simplemente no ofrece el botón de medir en AR, o pide instalar el servicio.
 */
object ArDisponible {

    fun estado(context: Context): EstadoAr = runCatching {
        when (ArCoreApk.getInstance().checkAvailability(context)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> EstadoAr.DISPONIBLE
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> EstadoAr.HAY_QUE_INSTALAR

            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> EstadoAr.NO_SOPORTADO
            // Consulta remota en curso: se trata como no soportado hasta que responda.
            else -> EstadoAr.NO_SOPORTADO
        }
    }.getOrDefault(EstadoAr.NO_SOPORTADO)

    fun comprobar(context: Context): Boolean = estado(context) != EstadoAr.NO_SOPORTADO
}
