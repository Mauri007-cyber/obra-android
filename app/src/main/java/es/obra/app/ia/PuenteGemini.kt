package es.obra.app.ia

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

/**
 * El atajo a la mejor calidad que hay gratis: la app de Gemini (Nano Banana).
 *
 * No se integra ninguna API ni se paga nada: se comparte la foto con el prompt ya escrito y el
 * usuario pulsa enviar en Gemini. Después trae la imagen con el selector de fotos y la app la
 * guarda como resultado de la estancia. Dos toques, coste cero, y la foto solo sale si el
 * usuario lo decide.
 */
object PuenteGemini {

    /**
     * Guarda una copia y abre el menú de compartir con la foto y el texto ya puestos.
     *
     * Devuelve false si el dispositivo no tiene ninguna app capaz de recibir una imagen (pasa en
     * emuladores sin apps de Google). En ese caso deja el prompt en el portapapeles para no
     * perderlo. Un fallo aquí no puede quedarse en silencio: la pantalla lo cuenta.
     */
    fun abrir(contexto: Context, foto: Bitmap, prompt: String): Boolean = runCatching {
        val carpeta = File(contexto.filesDir, "retoques").apply { mkdirs() }
        val archivo = File(carpeta, "gemini-${System.currentTimeMillis()}.jpg")
        archivo.outputStream().use { salida ->
            foto.compress(Bitmap.CompressFormat.JPEG, 92, salida)
        }
        val uri = FileProvider.getUriForFile(
            contexto,
            "${contexto.packageName}.archivos",
            archivo
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, prompt)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val selector = Intent.createChooser(intent, "Abrir la foto en Gemini")
        // Desde un contexto de aplicación (pruebas o servicios) hace falta NEW_TASK.
        if (contexto !is Activity) selector.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(selector)
        true
    }.getOrElse {
        copiarPrompt(contexto, prompt)
        false
    }

    private fun copiarPrompt(contexto: Context, prompt: String) {
        runCatching {
            val portapapeles = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            portapapeles.setPrimaryClip(ClipData.newPlainText("Prompt de la reforma", prompt))
        }
    }
}
