package es.obra.app.ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ClienteCloudflareTest {

    private fun base64(bytes: ByteArray = byteArrayOf(1, 2, 3)): String =
        Base64.getEncoder().encodeToString(bytes)

    @Test
    fun `lee la imagen y las imagenes restantes`() {
        val respuesta = ClienteCloudflare.interpretar(
            200 to """{"imagen":"${base64()}","restantes":7}"""
        )

        assertTrue(respuesta is ResultadoImagen.Ok)
        respuesta as ResultadoImagen.Ok
        assertEquals(3, respuesta.imagen.size)
        assertEquals(7, respuesta.restantes)
    }

    @Test
    fun `la cuota agotada no es un error tecnico`() {
        val respuesta = ClienteCloudflare.interpretar(429 to """{"error":"sin_cuota"}""")

        assertTrue(respuesta is ResultadoImagen.SinCuota)
        assertTrue((respuesta as ResultadoImagen.SinCuota).mensaje.contains("mañana"))
    }

    @Test
    fun `un servicio ocupado se explica y sugiere el retoque local`() {
        val respuesta = ClienteCloudflare.interpretar(503 to "")

        assertTrue(respuesta is ResultadoImagen.Fallo)
        assertTrue((respuesta as ResultadoImagen.Fallo).mensaje.contains("retoque local"))
    }

    @Test
    fun `una respuesta sin imagen es un fallo`() {
        val respuesta = ClienteCloudflare.interpretar(200 to """{"imagen":""}""")

        assertTrue(respuesta is ResultadoImagen.Fallo)
    }

    @Test
    fun `una respuesta que no es json es un fallo`() {
        val respuesta = ClienteCloudflare.interpretar(200 to "<html>error</html>")

        assertTrue(respuesta is ResultadoImagen.Fallo)
    }

    @Test
    fun `un base64 roto es un fallo`() {
        val respuesta = ClienteCloudflare.interpretar(200 to """{"imagen":"no-es-base64!!"}""")

        assertTrue(respuesta is ResultadoImagen.Fallo)
        assertTrue((respuesta as ResultadoImagen.Fallo).mensaje.contains("dañada"))
    }

    @Test
    fun `el tamano de salida respeta el encuadre`() {
        assertEquals(1024 to 768, ClienteCloudflare.salida(4000, 3000))
        assertEquals(768 to 1024, ClienteCloudflare.salida(3000, 4000))
        assertEquals(1024 to 1024, ClienteCloudflare.salida(1000, 1000))
        assertEquals(1024 to 768, ClienteCloudflare.salida(0, 0))
    }

    @Test
    fun `un 403 de cloudflare se explica y ofrece alternativa`() {
        val respuesta = ClienteCloudflare.interpretar(403 to "<html>error code: 1010</html>")

        assertTrue(respuesta is ResultadoImagen.Fallo)
        val mensaje = (respuesta as ResultadoImagen.Fallo).mensaje
        assertTrue(mensaje.contains("403"))
        assertTrue(mensaje.contains("Gemini"))
    }

    @Test
    fun `el 1010 se nombra tal cual`() {
        val respuesta = ClienteCloudflare.interpretar(403 to "error code: 1010")

        assertTrue((respuesta as ResultadoImagen.Fallo).mensaje.contains("1010"))
    }

    @Test
    fun `la peticion se presenta como navegador android`() {
        val agente = ClienteCloudflare.agente(android = "14", version = "0.9.4")

        assertTrue(agente.contains("Linux; Android 14"))
        assertTrue(agente.contains("Mobile Safari"))
        assertTrue(agente.contains("Obra/0.9.4"))
    }

    @Test
    fun `sin version de android se usa una de respaldo`() {
        val agente = ClienteCloudflare.agente(android = "", version = "0.9.4")

        assertTrue(agente.contains("Linux; Android 14"))
    }

    @Test
    fun `sin direccion configurada lo dice en vez de fallar`() {
        val cliente = ClienteCloudflare("")

        assertTrue(!cliente.configurado)
    }
}
