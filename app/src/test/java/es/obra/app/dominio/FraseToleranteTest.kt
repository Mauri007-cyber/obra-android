package es.obra.app.dominio

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cliente de la app no sabe hablarle a una máquina: escribe con faltas, sin comas y con
 * muletillas. Estas pruebas son frases reales de ese tipo.
 */
class FraseToleranteTest {

    private fun tipos(frase: String): List<TipoCambio> =
        FraseACambios.analizar(frase).cambios.map { it.tipo }

    @Test
    fun `pintame el piso y ponme una isla`() {
        val analisis = FraseACambios.analizar("pintame el piso de blanco y ponme una isla")

        assertTrue(analisis.cambios.toString(), tipos("pintame el piso de blanco y ponme una isla")
            .containsAll(listOf(TipoCambio.PINTURA_PLASTICA, TipoCambio.ISLA_COCINA)))
        assertTrue(analisis.noEntendido.isEmpty())
    }

    @Test
    fun `con faltas de ortografia y sin comas`() {
        val tipos = tipos("suelo de tarimo vinilico y pares de blanco roto")

        assertTrue(tipos.toString(), tipos.contains(TipoCambio.SUELO_VINILICO))
        assertTrue(tipos.toString(), tipos.contains(TipoCambio.PINTURA_PLASTICA))
    }

    @Test
    fun `entiende la raiz de la palabra`() {
        val tipos = tipos("ventanales nuevos y alicatado del baño")

        assertTrue(tipos.toString(), tipos.contains(TipoCambio.VENTANA_NUEVA))
        assertTrue(tipos.toString(), tipos.contains(TipoCambio.ALICATADO))
    }

    @Test
    fun `cerrar un hueco no es poner una ventana`() {
        val tipos = tipos("cerrar la ventana del salon y alisar")

        assertTrue(tipos.toString(), tipos.contains(TipoCambio.CERRAR_HUECO))
        assertTrue(tipos.toString(), !tipos.contains(TipoCambio.VENTANA_NUEVA))
    }

    @Test
    fun `agrandar un hueco es cambiar su tamano`() {
        val tipos = tipos("agrandar la ventana del dormitorio")

        assertTrue(tipos.toString(), tipos.contains(TipoCambio.AMPLIAR_HUECO))
    }

    @Test
    fun `quitar los trastos no se marca como no entendido`() {
        val analisis = FraseACambios.analizar("quita los cachivaches que hay en medio")

        assertTrue(analisis.cambios.isEmpty())
        assertTrue(analisis.noEntendido.toString(), analisis.noEntendido.isEmpty())
        assertTrue(FraseACambios.pideDespeje("quita los cachivaches que hay en medio"))
    }

    @Test
    fun `dice lo que no ha entendido en vez de callarse`() {
        val analisis = FraseACambios.analizar("no se, algo bonito que quede moderno")

        assertTrue(analisis.cambios.isEmpty())
        assertTrue(analisis.noEntendido.isNotEmpty())
    }

    @Test
    fun `no inventa trabajos con una frase vacia`() {
        val analisis = FraseACambios.analizar("   ")

        assertTrue(analisis.cambios.isEmpty())
        assertTrue(analisis.noEntendido.isEmpty())
    }
}
