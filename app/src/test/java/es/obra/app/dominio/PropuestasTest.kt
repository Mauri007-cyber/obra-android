package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La IA propone; la app solo acepta partidas que existen y cantidades con sentido. */
class PropuestasTest {

    @Test
    fun `lee tres escenarios con partidas del catalogo`() {
        val cuerpo = """
            {"propuestas":[
              {"titulo":"Económica","resumen":"Cocina abierta",
               "actuaciones":[
                 {"tipo":"TABIQUE_TIRADO","estancia":"Salón","cantidad":10,"motivo":"Abrir cocina"},
                 {"tipo":"PINTURA_PLASTICA","estancia":"Salón","cantidad":24,"motivo":"Paredes"}
               ]},
              {"titulo":"Media","resumen":"Con suelo",
               "actuaciones":[
                 {"tipo":"TABIQUE_TIRADO","estancia":"Salón","cantidad":10,"motivo":""},
                 {"tipo":"SUELO_VINILICO","estancia":"Salón","cantidad":24,"motivo":""}
               ]}
            ]}
        """.trimIndent()

        val escenarios = Propuestas.desdeJson(cuerpo)

        assertEquals(2, escenarios?.size)
        assertEquals("Económica", escenarios!!.first().titulo)
        assertEquals(TipoCambio.TABIQUE_TIRADO, escenarios.first().actuaciones.first().tipo)
        // 10 m² × 12 € + 24 m² × 8 €
        assertEquals(312.0, escenarios.first().coste, 1e-9)
    }

    @Test
    fun `descarta tipos que no existen y escenarios con una sola partida`() {
        val cuerpo = """
            {"propuestas":[
              {"titulo":"Inventada","actuaciones":[
                 {"tipo":"PONER_JACUZZI","estancia":"Baño","cantidad":1},
                 {"tipo":"PLATO_DUCHA","estancia":"Baño","cantidad":1},
                 {"tipo":"INODORO","estancia":"Baño","cantidad":1}
               ]},
              {"titulo":"Corta","actuaciones":[
                 {"tipo":"PLATO_DUCHA","estancia":"Baño","cantidad":1}
               ]}
            ]}
        """.trimIndent()

        val escenarios = Propuestas.desdeJson(cuerpo)

        assertEquals(1, escenarios?.size)
        assertEquals(2, escenarios!!.single().actuaciones.size)
        assertTrue(escenarios.single().actuaciones.none { it.tipo.name == "PONER_JACUZZI" })
    }

    @Test
    fun `una puerta es partida entera aunque su unidad sea ud`() {
        val cuerpo = """
            {"propuestas":[{"titulo":"Puertas","actuaciones":[
              {"tipo":"PUERTA_INTERIOR","estancia":"Salón","cantidad":3},
              {"tipo":"VENTANA_NUEVA","estancia":"Salón","cantidad":2}
            ]}]}
        """.trimIndent()

        val escenario = Propuestas.desdeJson(cuerpo)!!.single()

        assertEquals(1.0, escenario.actuaciones.first().cantidad, 1e-9)
        assertEquals(220.0 + 350.0, escenario.coste, 1e-9)
    }

    @Test
    fun `una partida global se cobra una vez aunque la IA diga otra cantidad`() {
        val cuerpo = """
            {"propuestas":[{"titulo":"Baño","actuaciones":[
              {"tipo":"SANITARIO_COMPLETO","estancia":"Baño","cantidad":7},
              {"tipo":"PLATO_DUCHA","estancia":"Baño","cantidad":2}
            ]}]}
        """.trimIndent()

        val escenario = Propuestas.desdeJson(cuerpo)!!.single()

        assertEquals(1.0, escenario.actuaciones.first().cantidad, 1e-9)
        // La ducha también es partida entera: dos unidades pedidas se cobran como una.
        assertEquals(2400.0 + 380.0, escenario.coste, 1e-9)
    }

    @Test
    fun `cantidades absurdas se recortan y las rotas se descartan`() {
        val cuerpo = """
            {"propuestas":[{"titulo":"X","actuaciones":[
              {"tipo":"PINTURA_PLASTICA","estancia":"Salón","cantidad":9999},
              {"tipo":"SOLO_LOS_ZOCALOS","estancia":"Salón","cantidad":3},
              {"tipo":"SUELO_LAMINADO","estancia":"Salón","cantidad":12}
            ]}]}
        """.trimIndent()

        val escenario = Propuestas.desdeJson(cuerpo)!!.single()

        assertEquals(500.0, escenario.actuaciones.first().cantidad, 1e-9)
        assertEquals(2, escenario.actuaciones.size)
    }

    @Test
    fun `sin titulo se numera y el resumen se recorta`() {
        val cuerpo = """
            {"propuestas":[
              {"titulo":"","resumen":"${"muy largo ".repeat(50)}","actuaciones":[
                {"tipo":"PINTURA_PLASTICA","estancia":"Salón","cantidad":10},
                {"tipo":"PINTURA_TECHO","estancia":"Salón","cantidad":10}
              ]}
            ]}
        """.trimIndent()

        val escenario = Propuestas.desdeJson(cuerpo)!!.single()

        assertEquals("Propuesta A", escenario.titulo)
        assertTrue(escenario.resumen.length <= 240)
    }

    @Test
    fun `los chips abren solo sus partidas y sin chips no se limita nada`() {
        val bano = Propuestas.catalogoPara(setOf("renovar baño"))!!

        assertTrue(TipoCambio.PLATO_DUCHA in bano)
        assertTrue(TipoCambio.PINTURA_TECHO !in bano)
        assertTrue(TipoCambio.TABIQUE_TIRADO !in bano)
        assertNull(Propuestas.catalogoPara(emptySet()))
        assertNull(Propuestas.catalogoPara(setOf("poner un jacuzzi")))
    }

    @Test
    fun `renovar bano abre tambien fontaneria`() {
        val bano = Propuestas.catalogoPara(setOf("renovar baño"))!!

        assertTrue(
            TipoCambio.TOMA_AGUA in bano &&
                TipoCambio.DESAGUE in bano &&
                TipoCambio.TERMO in bano
        )
        assertTrue(TipoCambio.PLATO_DUCHA in bano)
    }

    @Test
    fun `pintar es de interior y la fachada va en su propio chip`() {
        assertTrue(TipoCambio.PINTURA_PLASTICA in Permiso.PINTAR.abre)
        assertTrue(TipoCambio.PINTURA_FACHADA !in Permiso.PINTAR.abre)
        assertTrue(TipoCambio.PINTURA_FACHADA in Permiso.FACHADA.abre)
    }

    @Test
    fun `cada chip abre al menos dos partidas`() {
        Permiso.entries.forEach { permiso ->
            assertTrue(
                "«${permiso.etiqueta}» abre ${permiso.abre.size}",
                permiso.abre.size >= 2
            )
        }
    }

    @Test
    fun `ninguna partida del catalogo se queda sin chip`() {
        val abiertas = Permiso.entries.flatMap { it.abre }.toSet()
        val huerfanas = TipoCambio.entries.filter { it !in abiertas }.map { it.name }

        assertTrue("Partidas sin chip: $huerfanas", huerfanas.isEmpty())
    }

    @Test
    fun `una partida que el cliente no autorizo no entra aunque el modelo la devuelva`() {
        val cuerpo = """
            {"propuestas":[{"titulo":"Pintura","actuaciones":[
              {"tipo":"PINTURA_PLASTICA","estancia":"Salón","cantidad":20},
              {"tipo":"TABIQUE_TIRADO","estancia":"Salón","cantidad":6},
              {"tipo":"PINTURA_TECHO","estancia":"Salón","cantidad":20}
            ]}]}
        """.trimIndent()

        val escenario = Propuestas.desdeJson(cuerpo, Propuestas.catalogoPara(setOf("pintar")))!!
            .single()

        assertEquals(2, escenario.actuaciones.size)
        assertTrue(escenario.actuaciones.none { it.tipo == TipoCambio.TABIQUE_TIRADO })
    }

    @Test
    fun `la estancia se reconoce con o sin tildes y no se duplica`() {
        assertEquals("salon", Propuestas.normalizarNombre("  Salón "))
        val cuerpo = """
            {"propuestas":[{"titulo":"Paredes","actuaciones":[
              {"tipo":"PINTURA_PLASTICA","estancia":"Salón","cantidad":10},
              {"tipo":"PINTURA_PLASTICA","estancia":"salon","cantidad":12},
              {"tipo":"PINTURA_TECHO","estancia":"Salón","cantidad":10}
            ]}]}
        """.trimIndent()

        val escenario = Propuestas.desdeJson(cuerpo)!!.single()

        assertEquals(2, escenario.actuaciones.size)
    }

    @Test
    fun `un cuerpo que no es json o viene vacio no rompe nada`() {
        assertNull(Propuestas.desdeJson("no soy json"))
        assertNull(Propuestas.desdeJson("""{"propuestas":[]}"""))
        assertNull(Propuestas.desdeJson("""{"error":"sin_cuota"}"""))
    }
}
