package es.obra.app.actualizacion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import es.obra.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Serializable
data class Manifiesto(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String = "",
    val notas: String = ""
)

sealed interface EstadoActualizacion {
    data object SinComprobar : EstadoActualizacion
    data object Comprobando : EstadoActualizacion
    data object AlDia : EstadoActualizacion
    data class Disponible(val manifiesto: Manifiesto) : EstadoActualizacion
    data class Descargando(val progreso: Int) : EstadoActualizacion
    data class Lista(val archivo: File, val manifiesto: Manifiesto) : EstadoActualizacion
    data class Error(val mensaje: String) : EstadoActualizacion
}

/**
 * Actualización dentro de la app.
 *
 * Reglas de seguridad:
 *  - solo HTTPS;
 *  - se rechaza cualquier versión con versionCode <= al instalado (nada de downgrades);
 *  - si el manifiesto trae sha256, el APK descargado debe coincidir o se borra;
 *  - nunca se instala sin que el usuario lo pida.
 *
 * Android siempre muestra su diálogo de confirmación: la instalación totalmente silenciosa
 * solo existe con Play Store, MDM o root.
 */
class Actualizador(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun conectar(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Solo se permite HTTPS" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, application/octet-stream")
            setRequestProperty("User-Agent", "Obra/${BuildConfig.VERSION_NAME}")
        }
    }

    suspend fun comprobar(): EstadoActualizacion = withContext(Dispatchers.IO) {
        runCatching {
            val conexion = conectar(BuildConfig.UPDATE_URL)
            try {
                if (conexion.responseCode != 200) error("Respuesta ${conexion.responseCode}")
                val texto = conexion.inputStream.bufferedReader().use { it.readText() }
                val manifiesto = json.decodeFromString<Manifiesto>(texto)
                if (manifiesto.versionCode <= BuildConfig.VERSION_CODE) {
                    EstadoActualizacion.AlDia
                } else {
                    EstadoActualizacion.Disponible(manifiesto)
                }
            } finally {
                conexion.disconnect()
            }
        }.getOrElse { EstadoActualizacion.Error(it.message ?: "No se pudo comprobar") }
    }

    suspend fun descargar(
        manifiesto: Manifiesto,
        alProgresar: (Int) -> Unit
    ): EstadoActualizacion = withContext(Dispatchers.IO) {
        runCatching {
            val carpeta = File(context.cacheDir, "updates").apply { mkdirs() }
            val archivo = File(carpeta, "obra-${manifiesto.versionName}.apk")
            if (archivo.exists()) archivo.delete()

            val conexion = conectar(manifiesto.apkUrl)
            try {
                if (conexion.responseCode != 200) error("Respuesta ${conexion.responseCode}")
                val total = conexion.contentLengthLong
                if (total > MAXIMO_APK) error("El APK es demasiado grande")

                conexion.inputStream.use { entrada ->
                    archivo.outputStream().use { salida ->
                        val buffer = ByteArray(64 * 1024)
                        var leido = 0L
                        var ultimo = -1
                        while (true) {
                            val n = entrada.read(buffer)
                            if (n < 0) break
                            salida.write(buffer, 0, n)
                            leido += n
                            if (total > 0) {
                                val pct = ((leido * 100) / total).toInt()
                                if (pct != ultimo) {
                                    ultimo = pct
                                    alProgresar(pct)
                                }
                            }
                        }
                    }
                }
            } finally {
                conexion.disconnect()
            }

            if (archivo.length() > MAXIMO_APK) {
                archivo.delete()
                error("El APK es demasiado grande")
            }
            if (manifiesto.sha256.isNotBlank()) {
                val real = sha256(archivo)
                if (!real.equals(manifiesto.sha256, ignoreCase = true)) {
                    archivo.delete()
                    error("El APK no coincide con la firma esperada")
                }
            }
            EstadoActualizacion.Lista(archivo, manifiesto)
        }.getOrElse { EstadoActualizacion.Error(it.message ?: "No se pudo descargar") }
    }

    fun instalar(archivo: File): Resultado {
        if (!archivo.exists() || archivo.length() == 0L) return Resultado.Fallo("El archivo no existe")
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.archivos",
                archivo
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Resultado.Lanzado
        }.getOrElse { Resultado.Fallo(it.message ?: "No se pudo abrir el instalador") }
    }

    fun puedeInstalar(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun abrirAjusteInstalacion() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun sha256(archivo: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        archivo.inputStream().use { entrada ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = entrada.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    sealed interface Resultado {
        data object Lanzado : Resultado
        data class Fallo(val motivo: String) : Resultado
    }

    private companion object {
        const val MAXIMO_APK = 120L * 1024 * 1024
    }
}
