package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptReformaTest {

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
    fun `nombra la estancia cuando se sabe`() {
        val prompt = PromptReforma.para(
            cambios = listOf(cambio(TipoCambio.PINTURA_PLASTICA, "blanco")),
            estancia = "Salón"
        )

        assertTrue(prompt.contains("preview of this salón"))
    }
}
