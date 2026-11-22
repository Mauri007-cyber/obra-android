package es.obra.app.render

import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoOrbitaTest {

    private val obra = ObraEntity(nombre = "Salón", largo = 4.0, ancho = 3.0, altura = 2.5)

    private val muros = listOf(
        MuroEntity(id = 1, estanciaId = 1, x1 = 0.0, y1 = 0.0, x2 = 4.0, y2 = 0.0, grosor = 0.11),
        MuroEntity(id = 2, estanciaId = 1, x1 = 4.0, y1 = 0.0, x2 = 4.0, y2 = 3.0, grosor = 0.11),
        MuroEntity(id = 3, estanciaId = 1, x1 = 4.0, y1 = 3.0, x2 = 0.0, y2 = 3.0, grosor = 0.11),
        MuroEntity(id = 4, estanciaId = 1, x1 = 0.0, y1 = 3.0, x2 = 0.0, y2 = 0.0, grosor = 0.11),
        MuroEntity(id = 5, estanciaId = 1, x1 = 2.0, y1 = 0.0, x2 = 2.0, y2 = 3.0, grosor = 0.09, estado = "nuevo")
    )

    private val huecos = listOf(
        HuecoEntity(id = 1, muroId = 1, posicion = 0.18, ancho = 0.8, alto = 2.05, tipo = "puerta"),
        HuecoEntity(id = 2, muroId = 2, posicion = 0.5, ancho = 1.2, alto = 1.2, tipo = "ventana"),
        HuecoEntity(id = 3, muroId = 5, posicion = 0.5, ancho = 0.8, alto = 2.05, tipo = "puerta")
    )

    @Test
    fun `genera un gif de orbita con todos los fotogramas`() {
        val escena = Modelo3D.construir(obra, muros, huecos, despues = true)
        val destino = File("build/render/orbita.gif")

        VideoOrbita.generar(
            escena = escena,
            destino = destino,
            ancho = 320,
            alto = 240,
            fotogramas = 12,
            fps = 6
        )

        assertTrue("el GIF no se ha escrito", destino.exists())
        assertTrue("el GIF pesa demasiado poco: ${destino.length()}", destino.length() > 2000)

        val cabecera = destino.readBytes().copyOfRange(0, 6).toString(Charsets.US_ASCII)
        assertEquals("GIF89a", cabecera)

        val bytes = destino.readBytes()
        assertEquals(0x3B.toByte(), bytes[bytes.size - 1])
    }

    @Test
    fun `un fotograma no sale en blanco`() {
        val escena = Modelo3D.construir(obra, muros, huecos, despues = true)
        val pixeles = VideoOrbita.fotograma(escena, giro = 0.85, ancho = 200, alto = 150)

        // El render es de colores planos, así que lo que se comprueba es que la estancia ocupe
        // una parte razonable del fotograma y no sea un fondo vacío.
        val fondo = 0xF7F5F0
        val dibujados = pixeles.count { it != fondo }
        val proporcion = dibujados.toDouble() / pixeles.size

        assertTrue("la estancia ocupa solo el ${(proporcion * 100).toInt()} %", proporcion > 0.15)
        assertTrue("solo hay ${pixeles.toHashSet().size} colores", pixeles.toHashSet().size >= 3)
    }

    @Test
    fun `el video completo pesa lo que debe`() {
        val escena = Modelo3D.construir(obra, muros, huecos, despues = true)
        val destino = File("build/render/orbita-completo.gif")

        VideoOrbita.generar(escena = escena, destino = destino)

        assertTrue("no existe", destino.exists())
        assertTrue("demasiado pequeño: ${destino.length()}", destino.length() > 100_000)
        assertTrue("demasiado grande para WhatsApp: ${destino.length()}", destino.length() < 16_000_000)
    }
}
