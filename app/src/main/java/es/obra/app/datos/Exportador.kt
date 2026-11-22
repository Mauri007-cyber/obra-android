package es.obra.app.datos

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
 * Formato .obra: JSON legible, sin dependencias y sin nube.
 * Incluye cambios, pero no los archivos de fotos. No hay importación ni copia completa todavía.
 */
object Exportador {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportar(context: Context, obra: ObraEntity, dao: ObraDao): File =
        withContext(Dispatchers.IO) {
            val estancias = dao.estancias(obra.id)
            val muros = estancias.flatMap { dao.muros(it.id) }
            val huecos = muros.flatMap { dao.huecos(it.id) }

            val paquete = ObraExport(
                obra = obra,
                estancias = estancias,
                muros = muros,
                huecos = huecos,
                cambios = dao.cambios(obra.id)
            )

            val carpeta = File(context.filesDir, "export").apply { mkdirs() }
            val destino = File(carpeta, "obra-${obra.id}.obra")
            destino.writeText(json.encodeToString(paquete))
            destino
        }

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
