package es.obra.app.ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptRetoqueTest {

    private val base = PromptRetoque.Eleccion(peticion = "Pon el suelo de tarima clara")

    @Test
    fun `sin peticion no hay prompt`() {
        assertEquals("", PromptRetoque.componer(PromptRetoque.Eleccion(peticion = "   ")))
        assertEquals("", PromptRetoque.componer(base.copy(peticion = "")))
    }

    @Test
    fun `siempre pide respetar la obra`() {
        val texto = PromptRetoque.componer(base)
        assertTrue(texto.contains("Cambios pedidos: Pon el suelo de tarima clara"))
        assertTrue(texto.contains("No cambies el tamaño de la habitación"))
        assertTrue(texto.contains("ventana"))
        assertTrue(texto.contains("puerta"))
    }

    @Test
    fun `no aparecen lineas de lo que no se ha elegido`() {
        val texto = PromptRetoque.componer(base)
        assertFalse(texto.contains("Acabados:"))
        assertFalse(texto.contains("Iluminación:"))
        assertFalse(texto.contains("Corrige esto"))
        assertFalse(texto.contains("Además:"))
    }

    @Test
    fun `acabados, luces y correcciones entran en orden`() {
        val texto = PromptRetoque.componer(
            base.copy(
                pared = "blanco roto",
                suelo = "tarima de roble",
                luces = listOf("foco en el techo", "tira LED"),
                fallos = listOf("La puerta no está donde era", "El suelo no es ese"),
                nota = "La ventana tiene que seguir igual"
            )
        )
        assertTrue(texto.contains("Acabados: paredes blanco roto, suelo tarima de roble."))
        assertTrue(texto.contains("Iluminación: foco en el techo, tira LED."))
        assertTrue(texto.contains("La puerta no está donde era; El suelo no es ese"))
        assertTrue(texto.contains("Además: La ventana tiene que seguir igual"))

        val posicionAcabados = texto.indexOf("Acabados:")
        val posicionLuces = texto.indexOf("Iluminación:")
        val posicionCorreccion = texto.indexOf("Corrige esto")
        assertTrue(posicionAcabados < posicionLuces)
        assertTrue(posicionLuces < posicionCorreccion)
    }

    @Test
    fun `una peticion larguisima se recorta pero conserva la regla final`() {
        val texto = PromptRetoque.componer(base.copy(peticion = "a".repeat(2000)))
        assertTrue("El prompt no puede pasar del límite", texto.length <= PromptRetoque.LIMITE)
        assertTrue(texto.endsWith("No cambies el tamaño de la habitación ni el sitio de las puertas y ventanas."))
        assertTrue(texto.contains("…"))
    }

    @Test
    fun `no quedan lineas en blanco ni espacios de mas`() {
        val texto = PromptRetoque.componer(base)
        assertFalse(texto.contains("\n\n"))
        assertFalse(texto.contains("  "))
        assertFalse(texto.startsWith("\n"))
        assertFalse(texto.endsWith("\n"))
    }
}
