package es.obra.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedidasTest {

    @Test
    fun `mediana con numero impar de lecturas`() {
        assertEquals(3.42, Medidas.mediana(listOf(3.50, 3.42, 3.39)), 0.0001)
    }

    @Test
    fun `mediana con numero par promedia el centro`() {
        assertEquals(3.0, Medidas.mediana(listOf(2.0, 4.0)), 0.0001)
    }

    @Test
    fun `una lectura suelta no se considera medida`() {
        assertEquals(0.0, Medidas.dispersion(listOf(3.42)), 0.0001)
    }

    @Test
    fun `dispersion relativa entre extremos`() {
        // 3,42 y 3,49 -> 0,07 / 3,455 = 2,03 %
        assertEquals(0.02026, Medidas.dispersion(listOf(3.42, 3.49)), 0.0001)
    }

    @Test
    fun `calidad segun dispersion`() {
        assertEquals(Medidas.Calidad.BUENA, Medidas.calidad(0.01))
        assertEquals(Medidas.Calidad.MEDIA, Medidas.calidad(0.04))
        assertEquals(Medidas.Calidad.FLOJA, Medidas.calidad(0.09))
    }

    @Test
    fun `el factor de calibracion corrige lo medido`() {
        val factor = Medidas.factorCalibracion(medido = 3.30, real = 3.42)
        assertEquals(3.42, Medidas.aplicar(3.30, factor), 0.0001)
    }

    @Test
    fun `el factor no se dispara con datos absurdos`() {
        assertEquals(1.35, Medidas.factorCalibracion(1.0, 10.0), 0.0001)
        assertEquals(0.75, Medidas.factorCalibracion(10.0, 1.0), 0.0001)
        assertEquals(1.0, Medidas.factorCalibracion(0.0, 3.0), 0.0001)
    }

    @Test
    fun `detecta el error de teclear 342 en vez de 3 coma 42`() {
        assertFalse(Medidas.plausible(342.0, 2.88))
        assertTrue(Medidas.plausible(3.42, 2.88))
        assertTrue(Medidas.plausible(0.0, 0.0))
    }

    @Test
    fun `una habitacion de menos de medio metro no es creible`() {
        assertFalse(Medidas.plausible(0.3, 3.0))
    }

    @Test
    fun `incertidumbre por metodo`() {
        assertEquals(0.03, Medidas.incertidumbre("arcore"), 0.0001)
        assertEquals(0.05, Medidas.incertidumbre("foto"), 0.0001)
        assertEquals(0.01, Medidas.incertidumbre("manual"), 0.0001)
    }
}
