package es.obra.app.datos

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RutaFotoTest {
    private val files = File("/data/user/0/es.obra.app/files")

    @Test fun resuelveReferenciaRelativaYAbsolutaAntiguaTrasRestauracion() {
        val esperado = File(files, "fotos/estancia-123.jpg")
        assertEquals(esperado, rutaFoto(files, "fotos/estancia-123.jpg"))
        assertEquals(esperado, rutaFoto(files, "/data/user/10/es.obra.app/files/fotos/estancia-123.jpg"))
    }

    @Test fun rechazaRutasInsegurasYReferenciasDesconocidas() {
        listOf("", "fotos/../secreto", "fotos/..", "fotos/a/b", "/tmp/secreto", "content://fotos/a").forEach {
            assertNull(rutaFoto(files, it))
        }
    }
}
