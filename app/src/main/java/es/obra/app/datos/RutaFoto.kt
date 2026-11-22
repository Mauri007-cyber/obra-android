package es.obra.app.datos

import java.io.File

/** Resuelve referencias nuevas y rutas absolutas de instalaciones antiguas. */
internal fun rutaFoto(filesDir: File, referencia: String): File? {
    val normalizada = referencia.replace('\\', '/')
    val nombre = when {
        normalizada.startsWith("fotos/") -> normalizada.removePrefix("fotos/")
        normalizada.startsWith("/") && normalizada.contains("/files/fotos/") ->
            normalizada.substringAfterLast("/files/fotos/")
        else -> return null
    }
    if (nombre.isBlank() || nombre == "." || nombre == ".." || '/' in nombre) return null
    return File(File(filesDir, "fotos"), nombre)
}
