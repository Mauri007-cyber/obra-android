package es.obra.app

import es.obra.app.ia.ClienteCloudflare
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sin la dirección del Worker la APK sale «desconectada»: el botón de generar imagen se queda
 * apagado y el albañil no puede hacer nada. Estas pruebas comparten la misma regla que usa la app
 * ([ClienteCloudflare.problemaDeDireccion]), así que ya no hay una que acepte http y otra que lo
 * prohíba: un Worker local por http es válido, uno de internet tiene que ir por https.
 */
class ConfiguracionTest {

    @Test
    fun `la app se compila con una direccion utilizable`() {
        val url = BuildConfig.IMAGEN_URL

        assertNull(
            "BuildConfig.IMAGEN_URL = «$url» no sirve: " +
                "${ClienteCloudflare.problemaDeDireccion(url)}. Compila con la URL por defecto " +
                "o pasa -PimagenUrl=https://…",
            ClienteCloudflare.problemaDeDireccion(url)
        )
    }

    @Test
    fun `un Worker local puede ir por http`() {
        listOf(
            "http://localhost:8787",
            "http://127.0.0.1:8787",
            "http://192.168.1.40:8787",
            "http://10.0.0.5:8787",
            "http://172.16.0.9:8787"
        ).forEach { url ->
            assertNull(
                "«$url» es un Worker local y debería valer, pero se rechaza con: " +
                    ClienteCloudflare.problemaDeDireccion(url),
                ClienteCloudflare.problemaDeDireccion(url)
            )
        }
    }

    @Test
    fun `un Worker de internet tiene que ir por https`() {
        listOf(
            "",
            "   ",
            "obra.workers.dev",
            "ftp://obra.workers.dev",
            "http://obra.workers.dev",
            "http://mi-worker.example.com/imagen",
            "http://10.ejemplo.com"
        ).forEach { url ->
            assertNotNull(
                "«$url» no debería valer como dirección del Worker",
                ClienteCloudflare.problemaDeDireccion(url)
            )
        }
    }
}
