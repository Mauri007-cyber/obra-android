package es.obra.app.actualizacion

import java.io.InputStream
import java.io.OutputStream
import java.net.URI

internal fun validarActualizacion(manifiesto: Manifiesto, versionActual: Int) {
    require(manifiesto.versionCode > versionActual) { "La versión no es más reciente" }
    require(manifiesto.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Falta un SHA-256 válido" }
    val uri = URI(manifiesto.apkUrl)
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
        "La dirección del APK debe ser HTTPS"
    }
}

internal fun copiarConLimite(entrada: InputStream, salida: OutputStream, maximo: Long, progreso: (Long) -> Unit) {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = entrada.read(buffer)
        if (n < 0) break
        total += n
        require(total <= maximo) { "El APK es demasiado grande" }
        salida.write(buffer, 0, n)
        progreso(total)
    }
    require(total > 0) { "El APK está vacío" }
}
