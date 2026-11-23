package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColocacionTest {

    @Test
    fun `la isla a un metro de la pared`() {
        val frases = Colocacion.enIngles(
            "añadir una isla en el fondo de la habitación a un metro de la pared de mármol"
        )

        assertTrue(frases.contains("Place the kitchen island about 1 m from the wall."))
        assertTrue(frases.contains("Place the kitchen island at the far end of the room."))
    }

    @Test
    fun `el sofa a dos metros y medio de la ventana`() {
        val frases = Colocacion.enIngles("un sofá a 2,5 m de la ventana")

        assertEquals(listOf("Place the sofa about 2.5 m from the window."), frases)
    }

    @Test
    fun `sin posiciones no inventa nada`() {
        assertTrue(Colocacion.enIngles("suelo de tarima y paredes blancas").isEmpty())
        assertTrue(Colocacion.enIngles("   ").isEmpty())
    }

    @Test
    fun `una distancia sin objeto no se usa`() {
        assertTrue(Colocacion.enIngles("a un metro de la pared").isEmpty())
    }
}
