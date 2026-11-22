package es.obra.app.actualizacion

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SeguridadActualizacionTest {
    private val valido = Manifiesto(2, "2", "https://github.com/example/app.apk", "a".repeat(64))

    @Test fun exigeVersionNuevaChecksumYHttps() {
        validarActualizacion(valido, 1)
        listOf(
            valido.copy(versionCode = 1),
            valido.copy(sha256 = ""),
            valido.copy(sha256 = "g".repeat(64)),
            valido.copy(apkUrl = "http://example.com/app.apk"),
            valido.copy(apkUrl = "https://evil@example.com/app.apk")
        ).forEach { manifiesto ->
            assertThrows(IllegalArgumentException::class.java) { validarActualizacion(manifiesto, 1) }
        }
    }

    @Test fun limitaDuranteLaCopiaYRechazaArchivoVacio() {
        val salida = ByteArrayOutputStream()
        copiarConLimite(ByteArrayInputStream(byteArrayOf(1, 2, 3)), salida, 3) { }
        assertArrayEquals(byteArrayOf(1, 2, 3), salida.toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            copiarConLimite(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), ByteArrayOutputStream(), 3) { }
        }
        assertThrows(IllegalArgumentException::class.java) {
            copiarConLimite(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 3) { }
        }
    }
}
