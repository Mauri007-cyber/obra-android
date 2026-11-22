package es.obra.app.datos

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CopiaObraTest {
    private val base = ObraExport(
        formato = 2,
        obra = ObraEntity(id = 1, nombre = "Obra"),
        estancias = listOf(EstanciaEntity(id = 2, obraId = 1, nombre = "Salón", foto = "fotos/2")),
        muros = listOf(MuroEntity(id = 3, estanciaId = 2, x1 = 0.0, y1 = 0.0, x2 = 1.0, y2 = 0.0)),
        huecos = listOf(HuecoEntity(id = 4, muroId = 3)),
        cambios = listOf(CambioEntity(id = 5, estanciaId = 2, tipo = "PINTURA_PLASTICA"))
    )

    @Test fun validaRelacionesYRechazaArchivosMaliciosos() {
        validarRelaciones(base)
        listOf(
            base.copy(estancias = base.estancias + base.estancias),
            base.copy(muros = base.muros.map { it.copy(estanciaId = 9) }),
            base.copy(huecos = base.huecos.map { it.copy(muroId = 9) }),
            base.copy(cambios = base.cambios.map { it.copy(estanciaId = 9) }),
            base.copy(estancias = base.estancias.map { it.copy(foto = "../secreto") })
        ).forEach { invalida -> assertThrows(IllegalArgumentException::class.java) { validarRelaciones(invalida) } }
    }

    @Test fun limitaContenidoAlLeer() {
        val salida = ByteArrayOutputStream()
        copiarLimitado(ByteArrayInputStream(byteArrayOf(1, 2)), salida, 2)
        assertArrayEquals(byteArrayOf(1, 2), salida.toByteArray())
        assertThrows(IllegalArgumentException::class.java) { copiarLimitado(ByteArrayInputStream(byteArrayOf(1, 2, 3)), ByteArrayOutputStream(), 2) }
    }
}
