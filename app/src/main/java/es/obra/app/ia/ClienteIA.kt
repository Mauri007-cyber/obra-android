package es.obra.app.ia

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Habla con el Worker (worker/), que es quien tiene la clave.
 * Aquí no hay credenciales: solo una dirección y una foto.
 */
internal fun esRespuestaSimulada(valor: String?): Boolean = valor == "true"

class ClienteIA(private val direccion: String) {

    sealed interface Resultado {
        data class Bien(val imagen: ByteArray, val ms: Long, val quedan: Int?) : Resultado
        data class Mal(val mensaje: String, val cupo: Boolean = false) : Resultado
    }

    fun retoque(foto: ByteArray, prompt: String): Resultado {
        val base = direccion.trim().trimEnd('/')
        if (base.isEmpty()) return Resultado.Mal("Falta la dirección del servicio.")

        val cuerpo = buildJsonObject {
            put("foto", Base64.encodeToString(foto, Base64.NO_WRAP))
            put("prompt", prompt)
        }.toString()

        val conexion = try {
            URL("$base/retoque").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Resultado.Mal("La dirección del servicio no es válida.")
        }

        return try {
            conexion.requestMethod = "POST"
            conexion.doOutput = true
            conexion.connectTimeout = 15_000
            conexion.readTimeout = 120_000
            conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conexion.setRequestProperty("Accept", "application/json")

            val inicio = System.currentTimeMillis()
            conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }

            val codigo = conexion.responseCode
            val texto = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (codigo !in 200..299) {
                return Resultado.Mal(mensajeDe(codigo), cupo = codigo == 429)
            }

            val raiz = Json.parseToJsonElement(texto).jsonObject
            if (esRespuestaSimulada(raiz["simulado"]?.jsonPrimitive?.contentOrNull)) {
                return Resultado.Mal("El servicio está en modo de prueba: devuelve la foto original, no genera un retoque.")
            }
            val imagen = raiz["imagen"]?.jsonPrimitive?.contentOrNull
                ?: return Resultado.Mal("El servicio no ha devuelto ninguna imagen.")
            val quedan = raiz["quedan"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

            Resultado.Bien(
                imagen = Base64.decode(imagen, Base64.DEFAULT),
                ms = System.currentTimeMillis() - inicio,
                quedan = quedan
            )
        } catch (e: IOException) {
            Resultado.Mal("No se ha podido conectar. Mira la cobertura y prueba otra vez.")
        } catch (e: Exception) {
            Resultado.Mal("El servicio ha contestado algo que no se entiende.")
        } finally {
            conexion.disconnect()
        }
    }

    private fun mensajeDe(codigo: Int): String = when (codigo) {
        400 -> "Esa petición no vale. Cambia la frase y prueba."
        413 -> "La foto es demasiado grande. Hazla otra vez más pequeña."
        429 -> "Has llegado al límite de hoy. Mañana puedes seguir."
        415 -> "El servicio no acepta lo que le hemos mandado."
        503 -> "El servicio no está disponible o está desactivado por seguridad."
        502, 504 -> "El servicio no responde. Prueba más tarde."
        else -> "No se ha podido hacer el retoque (código $codigo)."
    }
}
