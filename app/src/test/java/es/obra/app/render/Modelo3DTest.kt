package es.obra.app.render

import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class Modelo3DTest {

    private val obra = ObraEntity(nombre = "Prueba", largo = 4.0, ancho = 3.0, altura = 2.5)

    private fun muro(largo: Double, id: Long = 1) =
        MuroEntity(id = id, estanciaId = 1, x1 = 0.0, y1 = 0.0, x2 = largo, y2 = 0.0)

    @Test
    fun `un muro mas corto que una puerta no rompe el modelo`() {
        val muros = listOf(muro(0.2))
        val huecos = listOf(
            HuecoEntity(muroId = 1, posicion = 0.5, ancho = 0.8, alto = 2.05, tipo = "puerta")
        )

        val escena = Modelo3D.construir(obra, muros, huecos, despues = true)

        assertTrue("la escena no tiene caras", escena.caras.isNotEmpty())
        assertTrue("el radio no es válido", escena.radio > 0.0)
    }

    @Test
    fun `una altura casi cero no rompe el modelo`() {
        val baja = obra.copy(altura = 0.5)
        val huecos = listOf(
            HuecoEntity(muroId = 1, posicion = 0.5, ancho = 1.2, alto = 1.2, tipo = "ventana")
        )

        val escena = Modelo3D.construir(baja, listOf(muro(4.0)), huecos, despues = false)

        assertTrue(escena.caras.isNotEmpty())
    }

    @Test
    fun `sin muros el modelo queda vacio pero no falla`() {
        val escena = Modelo3D.construir(obra, emptyList(), emptyList(), despues = true)

        assertTrue(escena.caras.isEmpty())
        assertTrue(escena.radio > 0.0)
    }

    @Test
    fun `un hueco con posicion absurda se queda dentro del muro`() {
        val huecos = listOf(
            HuecoEntity(muroId = 1, posicion = 5.0, ancho = 0.8, alto = 2.05, tipo = "puerta"),
            HuecoEntity(muroId = 1, posicion = -3.0, ancho = 0.8, alto = 2.05, tipo = "puerta")
        )

        val escena = Modelo3D.construir(obra, listOf(muro(4.0)), huecos, despues = true)

        assertTrue(escena.caras.isNotEmpty())
    }
}
