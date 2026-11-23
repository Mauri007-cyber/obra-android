package es.obra.app.dominio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La frase real que gastó una imagen y salió mal: no aparecía la isla de mármol, la puerta no era
 * de roble oscuro, la ventana salió gris y los caballetes de obra seguían en medio.
 */
class PromptFraseRealTest {

    private val frase = "suelo de tarima vinilica imitacion de tablas de madera madera gris, " +
        "pintar las paredes de blanco roto liso, " +
        "techo imitiacion madera oscura con tabiques transversales de madera de roble oscura, " +
        "ventanas abatibles de aluminio imitacion madera oscuro, " +
        "añadir una isla en el fondo de la habitacion a un metro de la pared de marmol de " +
        "aproximadamente 2x0,5metros y la puerta debe ser de roble oscuro, " +
        "elimina Todo lo que ahora mismo aparece entre medias de la habitacion y realiza los " +
        "cambios, recuerda incluir la isla de marmol y esta debe ser blanca"

    private fun prompt(): String {
        val entendidos = FraseACambios.interpretar(frase)
        return PromptReforma.paraLaPantalla(
            aprobados = emptyList(),
            entendidos = entendidos,
            frase = frase,
            estancia = "Salón"
        )
    }

    @Test
    fun `entiende las seis partidas de la frase`() {
        val tipos = FraseACambios.interpretar(frase).map { it.tipo }

        assertTrue(tipos.contains(TipoCambio.SUELO_VINILICO))
        assertTrue(tipos.contains(TipoCambio.PINTURA_PLASTICA))
        assertTrue(tipos.contains(TipoCambio.TECHO_MADERA))
        assertTrue(tipos.contains(TipoCambio.VENTANA_OSCILO))
        assertTrue(tipos.contains(TipoCambio.ISLA_COCINA))
        assertTrue(tipos.contains(TipoCambio.PUERTA_INTERIOR))
    }

    @Test
    fun `la isla es blanca de marmol y lleva sus medidas`() {
        val prompt = prompt()

        assertTrue(prompt, prompt.contains("white marble kitchen island"))
        assertTrue(prompt, prompt.contains("2 m by 0.5 m"))
        assertTrue(prompt, prompt.contains("at the far end of the room"))
        assertTrue(prompt, prompt.contains("about 1 m from the wall"))
        assertTrue(prompt, prompt.contains("freestanding"))
    }

    @Test
    fun `la puerta es de roble oscuro`() {
        val prompt = prompt()

        assertTrue(prompt, prompt.contains("dark oak interior door"))
        assertFalse(prompt, prompt.contains("a oak "))
    }

    @Test
    fun `la ventana es imitacion madera marron, no gris`() {
        assertTrue(prompt(), prompt().contains("wood-grain brown aluminium"))
    }

    @Test
    fun `el suelo se ve como tablas`() {
        assertTrue(prompt(), prompt().contains("grey vinyl plank flooring"))
    }

    @Test
    fun `pide vaciar la habitacion de trastos`() {
        assertTrue(prompt(), prompt().contains("Remove all tools"))
    }
}
