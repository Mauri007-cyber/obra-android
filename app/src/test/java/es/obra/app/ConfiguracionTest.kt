package es.obra.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Sin la dirección del Worker la APK sale «desconectada»: el botón de generar imagen se queda
 * apagado y el albañil no puede hacer nada. Esta prueba falla en `testDebugUnitTest` si alguien
 * compila con `-PimagenUrl=` (vacía) o con una dirección rota, antes de repartir el APK.
 */
class ConfiguracionTest {

    @Test
    fun `la app no se compila con la direccion del Worker vacia`() {
        val url = BuildConfig.IMAGEN_URL

        assertTrue(
            "BuildConfig.IMAGEN_URL está vacía: compila con la URL por defecto o pasa " +
                "-PimagenUrl=https://…",
            url.isNotBlank()
        )
        assertTrue(
            "BuildConfig.IMAGEN_URL no es una dirección: «$url»",
            url.startsWith("https://") || url.startsWith("http://")
        )
    }

    @Test
    fun `la direccion del Worker es una url absoluta con host`() {
        val url = BuildConfig.IMAGEN_URL
        val uri = runCatching { URI(url) }.getOrNull()

        assertNotNull("No es una URL válida: «$url»", uri)
        assertEquals("La dirección debe ser http o https: «$url»", "https", uri!!.scheme)
        assertTrue("La dirección no tiene host: «$url»", !uri.host.isNullOrBlank())
    }
}
