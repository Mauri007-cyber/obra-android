package es.obra.app.datos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

/**
 * Una miniatura para las listas, decodificada con muestra. Abrir la foto entera de 12 MP ocupa
 * unos 48 MB en memoria, y con cuatro estancias en una lista el móvil modesto se queda sin ella.
 */
/** Borra el archivo de una referencia guardada (foto de estancia o imagen terminada). */
internal fun borrarFoto(filesDir: File, referencia: String) {
    if (referencia.isBlank() || referencia.contains("..")) return
    val archivo = rutaFoto(filesDir, referencia) ?: File(filesDir, referencia)
    runCatching { archivo.delete() }
}

internal fun miniatura(filesDir: File, referencia: String, ladoMax: Int = 640): Bitmap? {
    if (referencia.isBlank() || referencia.contains("..")) return null
    val archivo = (rutaFoto(filesDir, referencia) ?: File(filesDir, referencia))
        .takeIf { it.isFile } ?: return null

    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(archivo.path, limites)
    if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

    var muestra = 1
    while (limites.outWidth / (muestra * 2) >= ladoMax ||
        limites.outHeight / (muestra * 2) >= ladoMax
    ) {
        muestra *= 2
    }
    return BitmapFactory.decodeFile(
        archivo.path,
        BitmapFactory.Options().apply { inSampleSize = muestra }
    )
}
