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

    @Test
    fun `entiende la distancia a una pared concreta`() {
        val frases = Colocacion.enIngles("un sofa a un metro de la pared del fondo")

        assertTrue(frases.toString(), frases.contains("Place the sofa about 1 m from the back wall."))
    }

    @Test
    fun `entiende el horizontal respecto a la imagen`() {
        val frases = Colocacion.enIngles("la isla en horizontal respecto a la imagen")

        assertTrue(
            frases.toString(),
            frases.contains(
                "Place the kitchen island horizontal in the image frame: its long side runs " +
                    "left to right across the photo."
            )
        )
    }

    @Test
    fun `entiende el vertical respecto a la imagen`() {
        val frases = Colocacion.enIngles("el armario en vertical a la imagen")

        assertTrue(
            frases.toString(),
            frases.contains(
                "Place the wardrobe vertical in the image frame: its long side runs from the " +
                    "floor to the ceiling."
            )
        )
    }

    @Test
    fun `entiende el paralelo a una pared`() {
        val frases = Colocacion.enIngles("la isla en paralelo a la pared del fondo")

        assertTrue(
            frases.toString(),
            frases.contains("Place the kitchen island parallel to the back wall.")
        )
    }

    @Test
    fun `entiende pegado a la pared de la derecha`() {
        val frases = Colocacion.enIngles("el sofa pegado a la pared de la derecha")

        assertTrue(
            frases.toString(),
            frases.contains("Place the sofa flat against the right-hand wall, parallel to it.")
        )
    }

    @Test
    fun `entiende que el objeto va en una pared concreta`() {
        val frases = Colocacion.enIngles("el armario en la pared de la izquierda")

        assertTrue(
            frases.toString(),
            frases.contains("The wardrobe goes on the left-hand wall, not on another wall.")
        )
    }

    @Test
    fun `entiende de pared a pared`() {
        val frases = Colocacion.enIngles("una encimera de pared a pared")

        assertTrue(
            frases.toString(),
            frases.contains("The worktop runs the whole length of the wall, from corner to corner.")
        )
    }
}
