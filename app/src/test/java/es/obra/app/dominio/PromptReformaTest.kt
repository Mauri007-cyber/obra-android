package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptReformaTest {

    @Test
    fun `una correccion vuelve a partir de la foto original con lo aprobado`() {
        val prompt = PromptReforma.paraCorreccion(
            aprobados = listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")),
            correccion = "suelo de tarima clara",
            estancia = "Salón"
        )

        // Se regenera desde la habitación real: no se edita la imagen inventada.
        assertTrue(prompt, prompt.contains("renovation preview of this salón"))
        assertTrue(prompt, prompt.contains("paint the walls white"))
        assertTrue(prompt, prompt.contains("solid wood flooring"))
        assertFalse(prompt, prompt.contains("Edit this exact photograph"))
    }

    @Test
    fun `si la correccion repite una partida aprobada no se duplica`() {
        val prompt = PromptReforma.paraCorreccion(
            aprobados = listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")),
            correccion = "pinta las paredes de blanco"
        )

        assertEquals(1, prompt.split("paint the walls").size - 1)
        assertFalse(prompt, prompt.contains("Client's note"))
    }

    @Test
    fun `una correccion sobre algo ya aprobado no se pierde`() {
        val prompt = PromptReforma.paraCorreccion(
            aprobados = listOf(cambio(TipoCambio.SUELO_PORCELANICO, "gris cemento")),
            correccion = "el suelo un poco mas oscuro"
        )

        // La partida no cambia, pero el matiz tiene que llegar al modelo.
        assertEquals(1, prompt.split("replace the floor").size - 1)
        assertTrue(prompt, prompt.contains("The client also asks: el suelo un poco mas oscuro."))
    }

    @Test
    fun `una correccion que no se entiende viaja como peticion`() {
        val prompt = PromptReforma.paraCorreccion(
            aprobados = listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")),
            correccion = "que quede muy luminoso"
        )

        assertTrue(prompt, prompt.contains("The client also asks: que quede muy luminoso."))
        assertTrue(prompt, prompt.contains("paint the walls white"))
    }

    @Test
    fun `la correccion no se pierde aunque el prompt vaya lleno`() {
        val prompt = PromptReforma.paraCorreccion(
            aprobados = listOf(
                cambio(TipoCambio.SUELO_VINILICO, "gris"),
                cambio(TipoCambio.PINTURA_PLASTICA, "blanco roto"),
                cambio(TipoCambio.TECHO_MADERA, "roble oscuro"),
                cambio(TipoCambio.VENTANA_OSCILO, "aluminio imitación madera"),
                cambio(TipoCambio.ISLA_COCINA, "mármol"),
                cambio(TipoCambio.FOCO_EMPOTRADO)
            ),
            correccion = "que quede muy luminoso"
        )

        assertTrue(prompt, prompt.contains("The client also asks: que quede muy luminoso."))
    }

    @Test
    fun `una orden de quitar no se convierte en una partida`() {
        val prompt = PromptReforma.paraCorreccion(
            aprobados = listOf(
                cambio(TipoCambio.ISLA_COCINA, "mármol"),
                cambio(TipoCambio.PINTURA_PLASTICA, "blanco")
            ),
            correccion = "quita la isla"
        )

        // La isla deja de pedirse y se pide quitarla; lo demás se mantiene.
        assertTrue(prompt, prompt.contains("paint the walls white"))
        assertFalse(prompt, prompt.contains("These added items"))
        assertTrue(prompt, prompt.contains("Remove these from the room:"))
        assertTrue(prompt, prompt.contains("kitchen island"))
        assertFalse(prompt, prompt.contains(".."))
    }

    @Test
    fun `una correccion vacia no manda nada`() {
        assertEquals("", PromptReforma.paraCorreccion(emptyList(), "   "))
    }

    private fun cambio(tipo: TipoCambio, acabado: String = ""): Cambio =
        Cambio(tipo = tipo, cantidad = 10.0, acabado = acabado, estado = EstadoCambio.APROBADO)

    @Test
    fun `traduce el suelo y su acabado`() {
        val prompt = PromptReforma.para(listOf(cambio(TipoCambio.SUELO_LAMINADO, "roble claro")))

        assertTrue(prompt.contains("replace the floor with light oak laminate flooring"))
    }

    @Test
    fun `conserva camara geometria y muebles`() {
        val prompt = PromptReforma.para(listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco roto")))

        assertTrue(prompt.contains("Keep the camera position"))
        assertTrue(prompt.contains("all existing furniture"))
        assertTrue(prompt.contains("paint the walls off-white"))
        assertTrue(prompt.contains("no people"))
        assertTrue(prompt.contains("no watermark"))
    }

    @Test
    fun `junta suelo pared y techo en una sola instruccion`() {
        val prompt = PromptReforma.para(
            listOf(
                cambio(TipoCambio.SUELO_TARIMA, "roble"),
                cambio(TipoCambio.PINTURA_PLASTICA, "verde salvia"),
                cambio(TipoCambio.PINTURA_TECHO, "blanco mate")
            )
        )

        assertTrue(prompt.contains("oak solid wood flooring"))
        assertTrue(prompt.contains("paint the walls sage green"))
        assertTrue(prompt.contains("paint the ceiling matte white"))
        assertEquals(1, prompt.split("Change only the following").size - 1)
    }

    @Test
    fun `sin cambios visibles no hay prompt`() {
        assertEquals("", PromptReforma.para(emptyList()))
        assertEquals("", PromptReforma.para(listOf(cambio(TipoCambio.TABIQUE_NUEVO))))
        assertEquals("", PromptReforma.para(listOf(cambio(TipoCambio.IMPERMEABILIZAR))))
    }

    @Test
    fun `las luces se piden sin cambiar la geometria`() {
        val prompt = PromptReforma.para(
            listOf(
                cambio(TipoCambio.FOCO_EMPOTRADO),
                cambio(TipoCambio.PINTURA_PLASTICA, "arena")
            )
        )

        assertTrue(prompt.contains("warm recessed ceiling lighting"))
        assertTrue(prompt.contains("paint the walls sand"))
    }

    @Test
    fun `la nota del cliente se limpia y se incluye`() {
        val prompt = PromptReforma.para(
            cambios = listOf(cambio(TipoCambio.SUELO_VINILICO, "roble")),
            frase = "  que no se vea\n el radiador  "
        )

        assertTrue(prompt.contains("Client's note: que no se vea el radiador."))
        assertFalse(prompt.contains("\n"))
    }

    @Test
    fun `entiende acabados con tilde y con otra palabra dentro`() {
        val marmol = PromptReforma.para(listOf(cambio(TipoCambio.ALICATADO, "imitación mármol")))
        assertTrue(marmol.contains("marble-effect tiles"))

        val claro = PromptReforma.para(listOf(cambio(TipoCambio.SUELO_LAMINADO, "claro")))
        assertTrue(claro.contains("light oak laminate flooring"))
    }

    @Test
    fun `no repite dos veces la misma partida`() {
        val prompt = PromptReforma.para(
            listOf(
                cambio(TipoCambio.SUELO_LAMINADO, "roble claro"),
                cambio(TipoCambio.SUELO_LAMINADO, "roble claro")
            )
        )

        assertEquals(1, prompt.split("replace the floor").size - 1)
    }

    @Test
    fun `la frase ya entendida no se repite como nota`() {
        val prompt = PromptReforma.paraLaPantalla(
            aprobados = emptyList(),
            entendidos = listOf(cambio(TipoCambio.SUELO_TARIMA, "roble claro")),
            frase = "suelo de tarima clara"
        )

        assertTrue(prompt.contains("replace the floor"))
        assertFalse(prompt.contains("Client's note"))
        assertFalse(prompt.contains("suelo de tarima clara"))
    }

    @Test
    fun `la frase sin entender si viaja como nota`() {
        val prompt = PromptReforma.paraLaPantalla(
            aprobados = listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")),
            entendidos = emptyList(),
            frase = "que quede muy luminoso"
        )

        assertTrue(prompt.contains("Client's note: que quede muy luminoso."))
    }

    @Test
    fun `por defecto propone quitar los trastos y dejar los muebles`() {
        val prompt = PromptReforma.para(listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")))

        assertTrue(prompt, prompt.contains("Remove all tools, rubble, construction debris"))
        assertTrue(prompt, prompt.contains("all existing furniture"))
        assertFalse(prompt, prompt.contains("The room must be empty"))
    }

    @Test
    fun `no deja que el modelo anada muebles ni decoracion por su cuenta`() {
        val prompt = PromptReforma.para(listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")))

        assertTrue(
            prompt,
            prompt.contains("Do not add furniture, plants, rugs, pictures, curtains")
        )
        assertTrue(prompt, prompt.contains("the room stays as it is"))
    }

    @Test
    fun `si se pide vaciar se quitan tambien los muebles`() {
        val prompt = PromptReforma.paraLaPantalla(
            aprobados = listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")),
            entendidos = emptyList(),
            frase = "quita todos los trastos y deja el salon vacio"
        )

        assertTrue(prompt, prompt.contains("Remove all tools, rubble, construction debris"))
        assertTrue(prompt, prompt.contains("The room must be empty apart from the changes"))
        assertFalse(prompt, prompt.contains("all existing furniture"))
    }

    @Test
    fun `junta aprobados y entendidos sin duplicar partidas`() {
        val prompt = PromptReforma.paraLaPantalla(
            aprobados = listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")),
            entendidos = listOf(
                cambio(TipoCambio.PINTURA_PLASTICA, "blanco"),
                cambio(TipoCambio.SUELO_LAMINADO, "roble claro")
            ),
            frase = "paredes blancas y suelo de tarima"
        )

        assertEquals(1, prompt.split("paint the walls").size - 1)
        assertEquals(1, prompt.split("replace the floor").size - 1)
        assertFalse(prompt.contains("Client's note"))
    }

    @Test
    fun `cuenta una reforma completa con techo de madera e isla`() {
        val prompt = PromptReforma.para(
            listOf(
                cambio(TipoCambio.SUELO_VINILICO, "gris"),
                cambio(TipoCambio.PINTURA_PLASTICA, "blanco roto"),
                cambio(TipoCambio.TECHO_MADERA, "roble oscuro"),
                cambio(TipoCambio.VENTANA_OSCILO, "aluminio imitación madera"),
                cambio(TipoCambio.ISLA_COCINA, "mármol")
            )
        )

        assertTrue(prompt.contains("replace the floor with grey vinyl plank flooring"))
        assertTrue(prompt.contains("paint the walls off-white"))
        assertTrue(prompt.contains("dark oak timber ceiling with exposed transverse beams"))
        assertTrue(prompt.contains("wood-grain brown aluminium tilt-and-turn window"))
        assertTrue(prompt.contains("marble kitchen island"))
    }

    @Test
    fun `si no se cambia el suelo ni el techo se piden intactos`() {
        val prompt = PromptReforma.para(listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")))
        val antesDeCambiar = prompt.substringBefore("Change only")

        assertTrue(antesDeCambiar, antesDeCambiar.contains("the floor"))
        assertTrue(antesDeCambiar, antesDeCambiar.contains("the ceiling"))
        assertTrue(prompt, prompt.contains("exactly as in the input photograph"))
        assertTrue(prompt, prompt.contains("unfinished floor"))
    }

    @Test
    fun `si se cambia el suelo no se pide conservarlo`() {
        val prompt = PromptReforma.para(listOf(cambio(TipoCambio.SUELO_VINILICO, "gris")))
        val antesDeCambiar = prompt.substringBefore("Change only")

        assertFalse(antesDeCambiar, antesDeCambiar.contains("the floor"))
        assertTrue(antesDeCambiar, antesDeCambiar.contains("the ceiling"))
    }

    @Test
    fun `sin tocar huecos se conservan puertas y ventanas`() {
        val prompt = PromptReforma.para(listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")))

        assertTrue(prompt.contains("the windows, the doors"))
    }

    @Test
    fun `si se cambia una ventana no se pide conservarla`() {
        val prompt = PromptReforma.para(
            listOf(cambio(TipoCambio.VENTANA_OSCILO, "aluminio imitación madera"))
        )

        assertFalse(prompt.contains("the windows"))
        assertTrue(prompt.contains("the doors"))
        assertTrue(prompt.contains("tilt-and-turn window"))
    }

    @Test
    fun `si se cambia una puerta solo se conservan las ventanas`() {
        val prompt = PromptReforma.para(listOf(cambio(TipoCambio.PUERTA_INTERIOR, "roble")))

        assertFalse(prompt.contains("the doors"))
        assertTrue(prompt.contains("the windows"))
    }

    @Test
    fun `la colocacion de la frase llega al prompt`() {
        val prompt = PromptReforma.paraLaPantalla(
            aprobados = listOf(cambio(TipoCambio.ISLA_COCINA, "mármol")),
            entendidos = emptyList(),
            frase = "una isla a un metro de la pared",
            estancia = "Cocina"
        )

        assertTrue(prompt.contains("Respect these positions:"))
        assertTrue(prompt.contains("Place the kitchen island about 1 m from the wall."))
    }

    @Test
    fun `nombra la estancia cuando se sabe`() {
        val prompt = PromptReforma.para(
            cambios = listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")),
            estancia = "Salón"
        )

        assertTrue(prompt.contains("preview of this salón"))
    }
}
