package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CambiosObraTest {

    private fun cambio(
        tipo: TipoCambio,
        cantidad: Double = 0.0,
        estado: EstadoCambio = EstadoCambio.PROPUESTO,
        acabado: String = ""
    ) = Cambio(tipo = tipo, cantidad = cantidad, estado = estado, acabado = acabado)

    private fun estancia(
        nombre: String = "Salón",
        superficie: Double = 18.0,
        foto: String = "foto.jpg",
        cambios: List<Cambio> = listOf(
            cambio(TipoCambio.SUELO_PORCELANICO, 18.0, EstadoCambio.APROBADO, "gris cemento")
        )
    ) = EstanciaProyecto(
        id = 1,
        nombre = nombre,
        superficie = superficie,
        foto = foto,
        cambios = cambios
    )

    @Test
    fun `el catalogo cubre todos los oficios de una reforma`() {
        assertTrue(TipoCambio.entries.size >= 40)

        val ambitos = TipoCambio.entries.map { it.ambito }.toSet()
        assertTrue(ambitos.size >= 10)

        TipoCambio.entries.forEach { tipo ->
            assertTrue("${tipo.name} sin nombre", tipo.nombre.isNotBlank())
            assertTrue("${tipo.name} con precio negativo", tipo.precio >= 0.0)
        }
    }

    @Test
    fun `las partidas enteras no piden cantidad y se cobran una vez`() {
        val cocina = cambio(TipoCambio.ELECTRODOMESTICOS, cantidad = 0.0, estado = EstadoCambio.APROBADO)

        assertFalse(cocina.tipo.mide)
        assertEquals(1800.0, cocina.importe, 0.0001)
    }

    @Test
    fun `la cantidad sugerida usa la superficie de la estancia`() {
        val suelo = cambio(TipoCambio.SUELO_LAMINADO).conCantidadSugerida(18.0)
        assertEquals(18.0, suelo.cantidad, 0.0001)

        val rodapie = cambio(TipoCambio.RODAPIE).conCantidadSugerida(16.0)
        assertEquals(8.0, rodapie.cantidad, 0.0001)

        val puerta = cambio(TipoCambio.PUERTA_INTERIOR).conCantidadSugerida(18.0)
        assertEquals(0.0, puerta.cantidad, 0.0001) // no mide
    }

    @Test
    fun `las paredes sugeridas salen del perimetro por la altura`() {
        // 16 m² son 4 x 4: perimetro 16 m por 2,5 m de alto = 40 m² de pared.
        val pintura = cambio(TipoCambio.PINTURA_PLASTICA).conCantidadSugerida(16.0, 2.5)
        assertEquals(40.0, pintura.cantidad, 0.001)
    }

    @Test
    fun `el total respeta lo aprobado y lo descartado`() {
        val est = estancia(
            cambios = listOf(
                cambio(TipoCambio.SUELO_PORCELANICO, 18.0, EstadoCambio.APROBADO),
                cambio(TipoCambio.PINTURA_PLASTICA, 40.0, EstadoCambio.PROPUESTO),
                cambio(TipoCambio.BANERA, 1.0, EstadoCambio.RECHAZADO)
            )
        )

        assertEquals(48.0 * 18.0, est.costeAprobado, 0.01)
        assertEquals(8.0 * 40.0, est.costePropuesto, 0.01)
        assertEquals(48.0 * 18.0 + 8.0 * 40.0, est.costeTotal, 0.01) // la bañera descartada no cuenta
        assertFalse(est.terminada)
    }

    @Test
    fun `una estancia esta terminada cuando no le queda nada propuesto`() {
        val est = estancia(
            cambios = listOf(
                cambio(TipoCambio.SUELO_PORCELANICO, 18.0, EstadoCambio.APROBADO),
                cambio(TipoCambio.BANERA, 1.0, EstadoCambio.RECHAZADO)
            )
        )

        assertTrue(est.terminada)
        assertFalse(est.vacia)
        assertEquals(1, est.aprobados.size)
        assertEquals(1, est.descartados.size)
    }

    @Test
    fun `el progreso cuenta lo aprobado sobre lo que no se ha descartado`() {
        val vacio = Proyecto("Vacía", emptyList())
        assertEquals(0f, vacio.progreso, 0.0001f)

        val proyecto = Proyecto(
            "Piso",
            listOf(
                estancia(
                    cambios = listOf(
                        cambio(TipoCambio.SUELO_PORCELANICO, 18.0, EstadoCambio.APROBADO),
                        cambio(TipoCambio.PINTURA_PLASTICA, 40.0, EstadoCambio.PROPUESTO),
                        cambio(TipoCambio.BANERA, 1.0, EstadoCambio.RECHAZADO)
                    )
                )
            )
        )
        assertEquals(0.5f, proyecto.progreso, 0.0001f)
        assertEquals(1, proyecto.pendientes.size)
    }

    @Test
    fun `la obra avisa de lo que falta antes de ensenarla`() {
        val proyecto = Proyecto(
            "Piso",
            listOf(
                EstanciaProyecto(id = 1, nombre = "Salón", foto = ""),
                EstanciaProyecto(
                    id = 2,
                    nombre = "Baño",
                    cambios = listOf(
                        cambio(TipoCambio.ALICATADO, cantidad = 0.0, estado = EstadoCambio.APROBADO)
                    )
                )
            )
        )

        val avisos = proyecto.avisos().joinToString(" | ")
        assertTrue(avisos.contains("Salón"))
        assertTrue(avisos.contains("sin precio") || avisos.contains("cantidad"))
        assertTrue(avisos.contains("foto"))
    }

    @Test
    fun `el guion recorre las estancias con sus cambios aprobados`() {
        val proyecto = Proyecto(
            "Piso",
            listOf(
                estancia(nombre = "Salón"),
                estancia(nombre = "Baño", cambios = emptyList())
            )
        )

        val guion = proyecto.guion()
        assertEquals(2, guion.size)
        assertEquals("Salón", guion[0].estancia)
        assertTrue(guion[0].frases.first().contains("Suelo porcelánico"))
        assertTrue(guion[0].frases.first().contains("gris cemento"))
        assertEquals("Sin cambios aprobados.", guion[1].frases.first())
    }

    @Test
    fun `el recorrido pone delante lo que se puede ensenar`() {
        val proyecto = Proyecto(
            "Piso",
            listOf(
                EstanciaProyecto(id = 1, nombre = "Pasillo", foto = ""),
                estancia(nombre = "Salón")
            )
        )

        assertEquals("Salón", proyecto.recorrido().first().nombre)
    }

    @Test
    fun `los grupos de version se pueden comparar`() {
        val a = Cambio(tipo = TipoCambio.SUELO_LAMINADO, cantidad = 18.0, grupo = "A")
        val b = Cambio(tipo = TipoCambio.SUELO_PORCELANICO, cantidad = 18.0, grupo = "B")
        val est = EstanciaProyecto(id = 1, nombre = "Salón", cambios = listOf(a, b))

        assertEquals(1, est.deGrupo("A").cambios.size)
        assertEquals(1, est.deGrupo("B").cambios.size)
        assertEquals("A", Proyecto.siguienteGrupo(listOf("B", "C")))
        assertEquals("A", Proyecto.siguienteGrupo(emptyList()))
        assertEquals("B", Proyecto.siguienteGrupo(listOf("A")))
    }

    @Test
    fun `sin cambios aprobados no hay prompt`() {
        val est = estancia(cambios = listOf(cambio(TipoCambio.SUELO_LAMINADO, 18.0)))

        assertEquals("", PromptEstancia.componer(est))
        assertTrue(PromptEstancia.componer(est, incluirPropuestos = true).isNotEmpty())
    }

    @Test
    fun `el prompt sale de los cambios aprobados y cierra con la regla`() {
        val est = estancia(
            cambios = listOf(
                cambio(TipoCambio.PINTURA_PLASTICA, 40.0, EstadoCambio.APROBADO, "verde salvia"),
                cambio(TipoCambio.SUELO_PORCELANICO, 18.0, EstadoCambio.APROBADO, "gris cemento"),
                cambio(TipoCambio.BANERA, 1.0, EstadoCambio.PROPUESTO)
            )
        )

        val prompt = PromptEstancia.componer(est)

        assertTrue(prompt.startsWith("Reforma la estancia"))
        // El suelo va antes que la pintura: es el orden en que se trabaja.
        assertTrue(prompt.indexOf("Suelo porcelánico") < prompt.indexOf("Pintar paredes"))
        assertTrue(prompt.contains("verde salvia"))
        assertTrue(prompt.contains("1. Suelo porcelánico"))
        assertTrue(prompt.endsWith("No cambies el tamaño de la habitación ni el sitio de las puertas y ventanas."))
        assertFalse(prompt.contains("Bañera")) // no aprobado
        assertTrue(prompt.length <= PromptEstancia.LIMITE)
    }

    @Test
    fun `contexto editable contiene medidas y solo cambios visibles aprobados`() {
        val est = EstanciaProyecto(
            id = 1, nombre = "Salón", superficie = 18.5, altura = 2.6,
            cambios = listOf(
                cambio(TipoCambio.SUELO_LAMINADO, 18.0, EstadoCambio.APROBADO),
                cambio(TipoCambio.BANERA, 1.0, EstadoCambio.PROPUESTO),
                cambio(TipoCambio.TABIQUE_TIRADO, 6.0, EstadoCambio.APROBADO)
            )
        )
        val texto = PromptEstancia.contextoEditable(est)
        assertTrue(texto.contains("Salón"))
        assertTrue(texto.contains("18,50 m²"))
        assertTrue(texto.contains("2,60 m"))
        assertTrue(texto.contains("Suelo laminado"))
        assertFalse(texto.contains("Bañera"))
        assertFalse(texto.contains("Tirar un tabique"))
    }

    @Test
    fun `los cambios que no se ven no se le piden al modelo`() {
        // Tirar un tabique cambia la distribución: va al plano y al presupuesto, pero no se le
        // pide al modelo de imagen, que tiene prohibido mover la geometría.
        val est = estancia(
            cambios = listOf(
                cambio(TipoCambio.SUELO_LAMINADO, 18.0, EstadoCambio.APROBADO),
                cambio(TipoCambio.TABIQUE_TIRADO, 6.0, EstadoCambio.APROBADO)
            )
        )

        val prompt = PromptEstancia.componer(est)
        assertTrue(prompt.contains("Suelo laminado"))
        assertFalse(prompt.contains("Tirar un tabique"))
        assertEquals(1, est.visibles.size)
    }

    @Test
    fun `con cuarenta cambios el prompt cabe y la regla sigue al final`() {
        val muchos = TipoCambio.entries.take(40).mapIndexed { indice, tipo ->
            Cambio(
                id = indice.toLong(),
                tipo = tipo,
                cantidad = if (tipo.mide) 12.5 else 1.0,
                estado = EstadoCambio.APROBADO,
                acabado = tipo.acabados.firstOrNull().orEmpty(),
                nota = "tal como se acordó con el cliente en la visita"
            )
        }
        val est = EstanciaProyecto(id = 1, nombre = "Casa entera", foto = "f.jpg", cambios = muchos)

        val prompt = PromptEstancia.componer(est)

        assertTrue(prompt.length <= PromptEstancia.LIMITE)
        assertTrue(prompt.endsWith("No cambies el tamaño de la habitación ni el sitio de las puertas y ventanas."))
        assertTrue(prompt.lines().size >= 3)
    }

    @Test
    fun `la cobertura dice cuantos cambios entran en el prompt`() {
        val uno = estancia()
        assertEquals(1 to 0, PromptEstancia.cobertura(uno))

        val muchos = EstanciaProyecto(
            id = 1,
            nombre = "Todo",
            foto = "f.jpg",
            cambios = TipoCambio.entries.take(40).map {
                Cambio(tipo = it, cantidad = 5.0, estado = EstadoCambio.APROBADO)
            }
        )
        val (incluidos, fuera) = PromptEstancia.cobertura(muchos)
        assertTrue(incluidos >= 1)
        assertEquals(muchos.visibles.size, incluidos + fuera)
    }

    @Test
    fun `la linea del cambio se lee como se lo contaria un albanil`() {
        val linea = cambio(TipoCambio.SUELO_PORCELANICO, 18.4, EstadoCambio.APROBADO, "gris cemento").linea

        assertTrue(linea.startsWith("Suelo porcelánico"))
        assertTrue(linea.contains("18,40 m²"))
        assertTrue(linea.contains("gris cemento"))
    }

    @Test
    fun `los precios propios mandan sobre el catalogo`() {
        val conPrecio = Cambio(
            tipo = TipoCambio.SUELO_LAMINADO,
            cantidad = 10.0,
            precioPropio = 20.0,
            estado = EstadoCambio.APROBADO
        )

        assertEquals(20.0, conPrecio.precio, 0.0001)
        assertEquals(200.0, conPrecio.importe, 0.0001)
    }
}
