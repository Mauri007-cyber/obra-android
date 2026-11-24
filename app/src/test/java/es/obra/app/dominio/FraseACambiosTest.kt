package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FraseACambiosTest {

    private fun tipos(frase: String, superficie: Double = 18.0): List<TipoCambio> =
        FraseACambios.interpretar(frase, superficie).map { it.tipo }

    @Test
    fun `suelo y paredes de una frase normal`() {
        assertEquals(
            listOf(TipoCambio.SUELO_TARIMA, TipoCambio.PINTURA_PLASTICA),
            tipos("suelo de tarima clara y paredes blancas")
        )
    }

    @Test
    fun `entiende el acabado que se dice`() {
        val cambio = FraseACambios.interpretar("suelo porcelánico gris cemento", 20.0).single()
        assertEquals(TipoCambio.SUELO_PORCELANICO, cambio.tipo)
        assertEquals("gris cemento", cambio.acabado)
    }

    @Test
    fun `la cantidad de la frase manda`() {
        val cambio = FraseACambios.interpretar("18,5 m2 de tarima", 30.0).single()
        assertEquals(18.5, cambio.cantidad, 0.001)
    }

    @Test
    fun `sin cantidad se propone la de la estancia`() {
        val cambio = FraseACambios.interpretar("pintar las paredes", 20.0).single()
        assertTrue(cambio.cantidad > 0.0)
    }

    @Test
    fun `tres enchufes son tres unidades`() {
        val cambio = FraseACambios.interpretar("tres enchufes", 12.0).single()
        assertEquals(TipoCambio.ENCHUFE, cambio.tipo)
        assertEquals(3.0, cambio.cantidad, 0.001)
    }

    @Test
    fun `no repite el mismo trabajo dos veces`() {
        assertEquals(
            listOf(TipoCambio.ENCHUFE),
            tipos("un enchufe y otro enchufe")
        )
    }

    @Test
    fun `lo que no entiende no lo inventa`() {
        assertTrue(FraseACambios.interpretar("poner un jacuzzi en el jardín", 10.0).isEmpty())
        assertTrue(FraseACambios.interpretar("   ", 10.0).isEmpty())
    }

    @Test
    fun `reconoce el bano completo antes que la ducha`() {
        assertEquals(
            listOf(TipoCambio.SANITARIO_COMPLETO, TipoCambio.PLATO_DUCHA),
            tipos("baño completo con plato de ducha")
        )
    }

    @Test
    fun `entiende una reforma larga dictada de golpe`() {
        val cambios = FraseACambios.interpretar(
            "suelo de tarima vinilica imitacion madera gris, pintar las paredes de blanco roto " +
                "liso, techo imitiacion madera oscura con tabiques transversales de madera de " +
                "roble oscura, ventanas abatibles de aluminio imitacion madera oscuro, anadir una " +
                "isla en el fondo de la habitacion a un metro de la pared de marmol de " +
                "aproximadamente 2x0,5metros",
            25.0
        )

        assertEquals(
            listOf(
                TipoCambio.SUELO_VINILICO,
                TipoCambio.PINTURA_PLASTICA,
                TipoCambio.TECHO_MADERA,
                TipoCambio.VENTANA_OSCILO,
                TipoCambio.ISLA_COCINA
            ),
            cambios.map { it.tipo }
        )
        assertEquals("gris", cambios[0].acabado)
        assertEquals("blanco roto", cambios[1].acabado)
        assertEquals("roble oscuro", cambios[2].acabado)
        assertEquals("aluminio imitación madera", cambios[3].acabado)
        assertEquals("mármol", cambios[4].acabado)
    }

    @Test
    fun `hablar del suelo no propone suelo radiante`() {
        // La regla difusa partía «suelo radiante» y aceptaba la palabra «suelo» a secas.
        assertTrue(FraseACambios.interpretar("cambiar el suelo", 18.0).isEmpty())
        assertTrue(FraseACambios.interpretar("el suelo un poco mas oscuro", 18.0).isEmpty())
    }

    @Test
    fun `el falso techo no se confunde con pintar el techo`() {
        assertEquals(listOf(TipoCambio.FALSO_TECHO), tipos("falso techo de pladur"))
        assertEquals(listOf(TipoCambio.TECHO_REGISTRABLE), tipos("techo registrable"))
        assertEquals(listOf(TipoCambio.PINTURA_TECHO), tipos("pintar el techo de blanco"))
    }

    @Test
    fun `sin tildes tambien vale`() {
        assertEquals(
            listOf(TipoCambio.PINTURA_TECHO, TipoCambio.SUELO_LAMINADO),
            tipos("pintar techo y suelo laminado")
        )
    }
}
