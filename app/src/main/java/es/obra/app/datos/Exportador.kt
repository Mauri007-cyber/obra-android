package es.obra.app.datos

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ObraExport(
    val formato: Int = 1,
    val exportada: Long = System.currentTimeMillis(),
    val obra: ObraEntity,
    val estancias: List<EstanciaEntity> = emptyList(),
    val muros: List<MuroEntity> = emptyList(),
    val huecos: List<HuecoEntity> = emptyList(),
    val cambios: List<CambioEntity> = emptyList()
)

/**
 * El paquete de una obra tal cual se guarda en la copia ZIP. El formato `.obra` suelto se
 * retiró: no llevaba fotos y no se podía restaurar, así que solo confundía.
 */
object Exportador {

    /** Devuelve false si no se pudo abrir el menú de compartir (app cerrada, sin gestor…). */
    fun compartir(context: Context, archivo: File, tipo: String = "application/json"): Boolean =
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.archivos",
                archivo
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = tipo
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Obra exportada")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir obra"))
            true
        }.getOrDefault(false)
}
