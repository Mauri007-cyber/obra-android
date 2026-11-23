package es.obra.app.ia

import android.os.Build
import es.obra.app.BuildConfig
import es.obra.app.dominio.Escenario
import es.obra.app.dominio.Propuestas
import es.obra.app.dominio.RespuestaPropuestasDto
import es.obra.app.dominio.TipoCambio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class EstanciaGuia(
    val nombre: String,
    val superficie: Double = 0.0
)

@Serializable
data class CatalogoGuia(
    val clave: String,
    val nombre: String,
    val unidad: String
)

@Serializable
data class PeticionPropuestas(
    val dispositivo: String,
    val tipo: String = "piso",
    val superficie: Double = 0.0,
    val altura: Double = 2.5,
    val presupuesto: Double = 0.0,
    val estancias: List<EstanciaGuia> = emptyList(),
    val permite: List<String> = emptyList(),
    val deseo: String = "",
    val catalogo: List<CatalogoGuia> = emptyList()
)

sealed interface ResultadoPropuestas {
    data class Ok(val escenarios: List<Escenario>, val restantes: Int?) : ResultadoPropuestas
    data class SinCuota(val motivo: String) : ResultadoPropuestas
    data class Fallo(val motivo: String) : ResultadoPropuestas
}

/**
 * Pide escenarios al Worker propio. La cuenta de Cloudflare está allí; aquí solo hay una
 * dirección HTTPS. Igual que con la imagen, si no hay red o se agotó la cuota se dice claro y la
 * app sigue funcionando a mano.
 */
class ClientePropuestas(
    private val url: String = BuildConfig.IMAGEN_URL
) {

    private val punto: String = puntoPropuestas(url)

    val configurado: Boolean get() = url.startsWith("https://")

    /** El catálogo entero, para que la IA no invente partidas que la app no sabe cobrar. */
    fun catalogo(): List<CatalogoGuia> = TipoCambio.entries.map { tipo ->
        CatalogoGuia(
            clave = tipo.name,
            nombre = tipo.nombre,
            unidad = tipo.unidad.etiqueta
        )
    }

    suspend fun generar(peticion: PeticionPropuestas): ResultadoPropuestas {
        if (!configurado) {
            return ResultadoPropuestas.Fallo(
                "Esta copia no tiene conectado el servicio de propuestas. Actualiza la app."
            )
        }
        val cuerpo = jsonPropuestas.encodeToString(peticion)
        return withContext(Dispatchers.IO) {
            runCatching { interpretar(enviar(cuerpo)) }
                .getOrElse {
                    ResultadoPropuestas.Fallo(
                        "No hay conexión con el servicio. Comprueba los datos y prueba otra vez."
                    )
                }
        }
    }

    private fun enviar(cuerpo: String): Pair<Int, String> {
        val conexion = (URL(punto).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                ClienteCloudflare.agente(
                    android = Build.VERSION.RELEASE.orEmpty(),
                    version = BuildConfig.VERSION_NAME
                )
            )
        }
        return try {
            conexion.outputStream.use { salida -> salida.write(cuerpo.toByteArray()) }
            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader()?.use { it.readText() }.orEmpty()
            codigo to texto
        } finally {
            conexion.disconnect()
        }
    }

    private fun interpretar(respuesta: Pair<Int, String>): ResultadoPropuestas {
        val (codigo, cuerpo) = respuesta
        val sobre = runCatching {
            jsonPropuestas.decodeFromString<RespuestaPropuestasDto>(cuerpo)
        }.getOrNull()

        if (codigo == 429) {
            return ResultadoPropuestas.SinCuota(
                "Hoy ya no quedan propuestas gratis. Vuelve mañana o plantéalas a mano."
            )
        }
        if (codigo !in 200..299) {
            return ResultadoPropuestas.Fallo(
                sobre?.error?.takeIf { it.isNotBlank() }
                    ?: "El servicio respondió $codigo. Prueba en un rato."
            )
        }

        val escenarios = Propuestas.desdeJson(cuerpo)
            ?: return ResultadoPropuestas.Fallo(
                sobre?.error?.takeIf { it.isNotBlank() }
                    ?: "La IA no devolvió nada que se pueda usar. Prueba otra vez."
            )
        return ResultadoPropuestas.Ok(escenarios, sobre?.restantes)
    }

    companion object {

        internal val jsonPropuestas = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * La misma base que la imagen: si acaba en `/imagen` se cambia por `/propuestas`, y si
         * ya es `/propuestas` se deja tal cual.
         */
        internal fun puntoPropuestas(url: String): String {
            val limpia = url.trim().trimEnd('/')
            return when {
                limpia.endsWith("/propuestas") -> limpia
                limpia.endsWith("/imagen") -> limpia.removeSuffix("/imagen") + "/propuestas"
                else -> "$limpia/propuestas"
            }
        }
    }
}
