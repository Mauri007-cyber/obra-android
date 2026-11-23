package es.obra.app.ia

import android.graphics.Bitmap
import android.os.Build
import es.obra.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

@Serializable
internal data class PeticionImagen(
    val foto: String,
    val prompt: String,
    val dispositivo: String,
    val ancho: Int = 1024,
    val alto: Int = 768
)

@Serializable
internal data class RespuestaImagen(
    val imagen: String = "",
    val restantes: Int? = null,
    val error: String = ""
)

internal val jsonImagen = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Habla con el Worker propio (plan gratuito de Cloudflare), que es quien tiene la cuenta de
 * Workers AI. Aquí no hay ninguna clave: solo una dirección HTTPS, y como mucho la cuota diaria.
 *
 * La foto se reduce antes de salir: el modelo de edición trabaja con referencias pequeñas, y así
 * la petición pesa poco y la respuesta llega antes. Si no hay red, no hay Worker o se acabó la
 * cuota, se devuelve un motivo claro y la pantalla sigue con el retoque local.
 */
class ClienteCloudflare(
    private val url: String = BuildConfig.IMAGEN_URL
) : GeneradorImagen {

    override val configurado: Boolean get() = url.startsWith("https://")

    override suspend fun generar(
        foto: Bitmap,
        prompt: String,
        dispositivo: String
    ): ResultadoImagen {
        if (!configurado) {
            return ResultadoImagen.Fallo(
                "Esta copia de la app no tiene conectado el servicio de imagen. " +
                    "Puedes abrir la foto en Gemini o usar el retoque local."
            )
        }
        val jpeg = reducir(foto)
            ?: return ResultadoImagen.Fallo("No se pudo preparar la foto para enviarla.")
        val (ancho, alto) = salida(foto.width, foto.height)
        val cuerpo = jsonImagen.encodeToString(
            PeticionImagen(
                foto = Base64.getEncoder().encodeToString(jpeg),
                prompt = prompt,
                dispositivo = dispositivo,
                ancho = ancho,
                alto = alto
            )
        )

        return withContext(Dispatchers.IO) {
            runCatching { interpretar(enviar(cuerpo)) }
                .getOrElse {
                    ResultadoImagen.Fallo(
                        "No hay conexión con el servicio de imagen. " +
                            "Prueba el retoque local, que funciona sin red."
                    )
                }
        }
    }

    private fun enviar(cuerpo: String): Pair<Int, String> {
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                agente(android = Build.VERSION.RELEASE.orEmpty(), version = BuildConfig.VERSION_NAME)
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

    private fun reducir(foto: Bitmap, ladoMax: Int = 480): ByteArray? = runCatching {
        val lado = maxOf(foto.width, foto.height)
        val reducida = if (lado <= ladoMax) {
            foto
        } else {
            Bitmap.createScaledBitmap(
                foto,
                (foto.width * ladoMax / lado).coerceAtLeast(1),
                (foto.height * ladoMax / lado).coerceAtLeast(1),
                true
            )
        }
        ByteArrayOutputStream().use { salida ->
            reducida.compress(Bitmap.CompressFormat.JPEG, 85, salida)
            salida.toByteArray()
        }
    }.getOrNull()

    companion object {

        /**
         * Tamaño de salida: el lado mayor a 1024 y el otro en múltiplos de 32, que es lo que
         * acepta el modelo. Una foto vertical no se convierte en cuadrada.
         */
        internal fun salida(ancho: Int, alto: Int): Pair<Int, Int> {
            if (ancho <= 0 || alto <= 0) return 1024 to 768
            val mayor = maxOf(ancho, alto).toDouble()
            val escala = 1024.0 / mayor
            return multiplo32(ancho * escala) to multiplo32(alto * escala)
        }

        private fun multiplo32(valor: Double): Int =
            (Math.round(valor / 32.0) * 32).toInt().coerceIn(512, 1024)

        /** Traduce la respuesta del Worker a algo que la pantalla pueda enseñar. */
        internal fun interpretar(respuesta: Pair<Int, String>): ResultadoImagen {
            val (codigo, cuerpo) = respuesta
            if (codigo == 429) {
                return ResultadoImagen.SinCuota(
                    "Hoy ya no quedan imágenes gratis. Vuelve mañana o usa el retoque local."
                )
            }
            if (codigo !in 200..299) return ResultadoImagen.Fallo(motivo(codigo, cuerpo))

            val datos = runCatching { jsonImagen.decodeFromString<RespuestaImagen>(cuerpo) }.getOrNull()
                ?: return ResultadoImagen.Fallo("El servicio devolvió una respuesta que no se entiende.")
            if (datos.imagen.isBlank()) {
                return ResultadoImagen.Fallo(
                    datos.error.ifBlank { "El servicio no devolvió ninguna imagen." }
                )
            }
            val bytes = runCatching { Base64.getDecoder().decode(datos.imagen) }.getOrNull()
                ?: return ResultadoImagen.Fallo("La imagen llegó dañada.")
            return ResultadoImagen.Ok(bytes, datos.restantes)
        }

        /** Cloudflare corta con su filtro anti-bots (error 1010) antes de llegar al Worker. */
        private fun motivo(codigo: Int, cuerpo: String): String = when (codigo) {
            400 -> "La foto o el texto no son válidos para el servicio."
            403 -> if (cuerpo.contains("1010")) {
                "Cloudflare ha bloqueado la petición (403) por su filtro de seguridad (error 1010). " +
                    "Espera un minuto y vuelve a intentarlo; si sigue igual, usa " +
                    "«Abrir la foto en Gemini» o el retoque local."
            } else {
                "Cloudflare ha bloqueado la petición (403). Suele ser temporal: espera un " +
                    "minuto y vuelve a intentarlo; si sigue igual, usa «Abrir la foto en " +
                    "Gemini» o el retoque local."
            }

            413 -> "La foto es demasiado grande para enviarla."
            502, 503, 504 -> "El servicio está ocupado. Prueba en un rato o usa el retoque local."
            else -> "El servicio de imagen respondió $codigo."
        }

        /**
         * Firma con la que sale la petición. Cloudflare filtra las firmas de script, así que la
         * app se presenta como un navegador Android normal y deja su nombre al final.
         */
        internal fun agente(android: String, version: String): String {
            val sistema = android.ifBlank { "14" }
            return "Mozilla/5.0 (Linux; Android $sistema; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 Obra/$version"
        }
    }
}
