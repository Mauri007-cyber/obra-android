package es.obra.app.datos

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import es.obra.app.dominio.Poligono
import es.obra.app.dominio.Presupuesto
import es.obra.app.dominio.Tarifas
import es.obra.app.pdf.PdfPresupuesto
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecorridoObraTest {
    @Test fun copiaConFotosPlanosYPresupuestoSinSobrescribir() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ObraDb::class.java).build()
        val dao = db.dao()
        val carpeta = File(context.filesDir, "fotos").apply { mkdirs() }
        val originalFoto = File.createTempFile("prueba-", ".jpg", carpeta)
        val zip = File.createTempFile("copia-", ".zip", context.cacheDir)
        val png = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        png.eraseColor(android.graphics.Color.RED)
        originalFoto.outputStream().use { assertTrue(png.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        png.recycle()
        var restauradas = emptyList<File>()
        try {
            val obraId = dao.insertarObra(ObraEntity(nombre = "Casa"))
            val salon = dao.insertarEstancia(EstanciaEntity(obraId = obraId, nombre = "Salón", foto = "fotos/${originalFoto.name}"))
            val cocina = dao.insertarEstancia(EstanciaEntity(obraId = obraId, nombre = "Cocina"))
            suspend fun contorno(id: Long, ancho: Double, alto: Double) {
                val puntos = listOf(0.0 to 0.0, ancho to 0.0, ancho to alto, 0.0 to alto)
                puntos.indices.forEach { i ->
                    val a = puntos[i]; val b = puntos[(i + 1) % puntos.size]
                    dao.insertarMuro(MuroEntity(estanciaId = id, x1 = a.first, y1 = a.second, x2 = b.first, y2 = b.second))
                }
                dao.guardarSuperficieEstancia(obraId, id, Poligono.superficie(dao.muros(id))!!)
            }
            contorno(salon, 4.0, 3.0)
            contorno(cocina, 2.0, 3.0)
            val muro = dao.muros(salon).first()
            dao.insertarHueco(HuecoEntity(muroId = muro.id))
            dao.insertarCambio(CambioEntity(estanciaId = salon, tipo = "SUELO_LAMINADO", cantidad = 12.0, precioPropio = 10.0, estado = "APROBADO"))
            dao.insertarCambio(CambioEntity(estanciaId = cocina, tipo = "SUELO_LAMINADO", cantidad = 6.0, precioPropio = 20.0, estado = "APROBADO"))
            dao.insertarCambio(CambioEntity(estanciaId = cocina, tipo = "SUELO_LAMINADO", cantidad = 100.0, estado = "PROPUESTO"))
            CopiaObra.exportar(context, dao, obraId, zip.outputStream())
            val nueva = CopiaObra.importar(context, dao, Uri.fromFile(zip))
            assertNotEquals(obraId, nueva)
            val habitaciones = dao.estancias(nueva).associateBy { it.nombre }
            assertEquals(2, habitaciones.size)
            assertEquals(12.0, habitaciones.getValue("Salón").superficie, 0.001)
            assertEquals(6.0, habitaciones.getValue("Cocina").superficie, 0.001)
            assertEquals(12.0, Poligono.superficie(dao.muros(habitaciones.getValue("Salón").id))!!, 0.001)
            assertEquals(6.0, Poligono.superficie(dao.muros(habitaciones.getValue("Cocina").id))!!, 0.001)
            assertEquals(1, dao.huecosDeEstancia(habitaciones.getValue("Salón").id).size)
            val foto = rutaFoto(context.filesDir, habitaciones.getValue("Salón").foto)!!
            restauradas = listOf(foto)
            assertNotEquals(originalFoto.path, foto.path)
            assertArrayEquals(originalFoto.readBytes(), foto.readBytes())
            assertEquals(2, dao.estancias(obraId).size)
            val partidas = Presupuesto.partidasAprobadas(ProyectoRepositorio.cargar(dao, dao.obtenerObra(nueva)!!))
            assertEquals(2, partidas.size)
            assertEquals(240.0, Presupuesto.base(partidas), 0.001)
            assertEquals(290.4, Presupuesto.total(240.0, Tarifas.porDefecto), 0.001)
            val pdf = PdfPresupuesto.generar(context, dao.obtenerObra(nueva)!!, partidas, Tarifas.porDefecto, 240.0, 50.4, 290.4)
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { assertEquals(1, it.pageCount) }
            }
            val danada = File.createTempFile("copia-danada-", ".zip", context.cacheDir)
            try {
                danada.writeBytes(zip.readBytes().dropLast(10).toByteArray())
                var fallo = false
                try { CopiaObra.importar(context, dao, Uri.fromFile(danada)) } catch (_: Exception) { fallo = true }
                assertTrue(fallo)
                assertEquals(2, dao.estancias(obraId).size)
                assertEquals(2, dao.estancias(nueva).size)
            } finally { danada.delete() }
        } finally {
            db.close()
            zip.delete()
            originalFoto.delete()
            restauradas.forEach { it.delete() }
        }
    }
}
