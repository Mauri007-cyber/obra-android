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

    // La misma regla de dirección que la imagen: https siempre, http solo en un Worker local.
    val configurado: Boolean get() = ClienteCloudflare.problemaDeDireccion(url) == null

    /**
     * Las partidas que la IA puede proponer: si el cliente ha marcado permisos, solo las que
     * abren esos chips. Así no se le puede colar un tabique a quien dijo que no quería obras.
     */
    fun catalogo(permisos: Collection<String> = emptyList()): List<CatalogoGuia> {
        val permitidos = Propuestas.catalogoPara(permisos)
        return TipoCambio.entries
            .filter { permitidos == null || it in permitidos }
            .map { tipo ->
                CatalogoGuia(
                    clave = tipo.name,
                    nombre = tipo.nombre,
                    unidad = tipo.unidad.etiqueta
                )
            }
    }

    suspend fun generar(peticion: PeticionPropuestas): ResultadoPropuestas {
        if (!configurado) {
            return ResultadoPropuestas.Fallo(
                "Esta copia no tiene conectado el servicio de propuestas. Actualiza la app."
            )
        }
        val cuerpo = Propuestas.json.encodeToString(peticion)
        val permitidos = Propuestas.catalogoPara(peticion.permite)
        return withContext(Dispatchers.IO) {
            runCatching { interpretar(enviar(cuerpo), permitidos) }
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

    private fun interpretar(
        respuesta: Pair<Int, String>,
        permitidos: Set<TipoCambio>?
    ): ResultadoPropuestas {
        val (codigo, cuerpo) = respuesta
        val sobre = runCatching {
            Propuestas.json.decodeFromString<RespuestaPropuestasDto>(cuerpo)
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

        val escenarios = Propuestas.desdeJson(cuerpo, permitidos)
            ?: return ResultadoPropuestas.Fallo(
                sobre?.error?.takeIf { it.isNotBlank() }
                    ?: "La IA no devolvió nada que se pueda usar. Prueba otra vez."
            )
        return ResultadoPropuestas.Ok(escenarios, sobre?.restantes)
    }

    companion object {

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
