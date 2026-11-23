package es.obra.app.ia

import es.obra.app.dominio.Cambio
import es.obra.app.dominio.EstadoCambio
import es.obra.app.dominio.PromptReforma
import es.obra.app.dominio.TipoCambio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Una frase muy larga no puede acabar en un 400 del Worker. */
class RecortePromptTest {

    @Test
    fun `un prompt corto no se toca`() {
        val prompt = "Photorealistic interior. Change only the following: paint the walls white."

        assertEquals(prompt, ClienteCloudflare.recortar(prompt))
    }

    @Test
    fun `un prompt larguisimo se corta por frase y por debajo del limite`() {
        val largo = buildString {
            repeat(80) { append("Fit a new interior door in dark oak and respect the wall. ") }
        }

        val recortado = ClienteCloudflare.recortar(largo)

        assertTrue(recortado.length <= ClienteCloudflare.MAXIMO_PROMPT)
        assertTrue(recortado.endsWith("."))
        assertTrue(recortado.startsWith("Fit a new interior door"))
    }

    @Test
    fun `la pantalla nunca manda mas de lo que acepta el Worker`() {
        val todos = TipoCambio.entries.filter { it.seVe }.map {
            Cambio(tipo = it, cantidad = 1.0, acabado = "", estado = EstadoCambio.APROBADO)
        }
        val frase = "paredes blancas ".repeat(400)

        val prompt = PromptReforma.paraLaPantalla(
            aprobados = todos,
            entendidos = emptyList(),
            frase = frase
        )

        assertTrue(prompt.length <= PromptReforma.MAXIMO)
        assertTrue(prompt.contains("Change only the following"))
        assertTrue(prompt.endsWith("."))
        assertEquals(1, prompt.split("Change only the following").size - 1)
    }
}
