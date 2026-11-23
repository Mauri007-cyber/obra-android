package es.obra.app.datos

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * Guarda la imagen terminada en la galería del móvil, para que se pueda enseñar o enviar sin
 * depender de esta pantalla.
 *
 * En Android 10 o posterior no hace falta ningún permiso: la imagen va a Pictures/Obra. En
 * versiones anteriores se usa el almacén clásico, que sí necesita WRITE_EXTERNAL_STORAGE.
 */
object Galeria {

    fun guardar(contexto: Context, foto: Bitmap, nombre: String = "obra-terminada"): Boolean =
        runCatching {
            val resolver = contexto.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val valores = ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        "$nombre-${System.currentTimeMillis()}.jpg"
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Obra"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
                    ?: return false
                resolver.openOutputStream(uri)?.use { salida ->
                    foto.compress(Bitmap.CompressFormat.JPEG, 92, salida)
                } ?: return false
                valores.clear()
                valores.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, valores, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val ruta = MediaStore.Images.Media.insertImage(
                    resolver,
                    foto,
                    nombre,
                    "Obra"
                )
                !ruta.isNullOrBlank()
            }
        }.getOrDefault(false)
}
