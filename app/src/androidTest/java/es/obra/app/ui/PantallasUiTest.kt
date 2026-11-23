package es.obra.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.obra.app.datos.CambioEntity
import es.obra.app.datos.EstanciaEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ObraDb
import es.obra.app.datos.ObraEntity
import es.obra.app.datos.ProyectoRepositorio
import es.obra.app.dominio.Geometria
import es.obra.app.dominio.Presupuesto
import es.obra.app.dominio.Tarifas
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas de interfaz sobre las pantallas reales, con una base de datos en memoria.
 * No sustituyen a la comprobación en un móvil, pero sí comprueban que lo que se ve y lo que
 * se puede pulsar coincide con los datos guardados.
 */
@RunWith(AndroidJUnit4::class)
class PantallaPresupuestoUiTest {

    @get:Rule
    val regla = createComposeRule()

    private fun baseDeDatos(): ObraDb =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ObraDb::class.java
        ).build()

    @Test
    fun soloAparecenLosCambiosAprobadosYElTotalCalculado(): Unit = runBlocking {
        val db = baseDeDatos()
        val dao = db.dao()
        try {
            val obraId = dao.insertarObra(ObraEntity(nombre = "Piso", largo = 4.0, ancho = 3.0))
            val salon = dao.insertarEstancia(
                EstanciaEntity(obraId = obraId, nombre = "Salón", superficie = 12.0)
            )
            dao.insertarCambio(
                CambioEntity(
                    estanciaId = salon,
                    tipo = "SUELO_LAMINADO",
                    cantidad = 12.0,
                    precioPropio = 10.0,
                    estado = "APROBADO"
                )
            )
            dao.insertarCambio(
                CambioEntity(
                    estanciaId = salon,
                    tipo = "TABIQUE_PLADUR",
                    cantidad = 5.0,
                    precioPropio = 30.0,
                    estado = "PROPUESTO"
                )
            )

            val proyecto = ProyectoRepositorio.cargar(dao, dao.obtenerObra(obraId)!!)
            val partidas = Presupuesto.partidasAprobadas(proyecto)
            val base = Presupuesto.base(partidas)
            val total = Presupuesto.total(base, Tarifas.porDefecto)
            assertEquals(120.0, base, 0.001)
            assertEquals(145.2, total, 0.001)

            regla.setContent { TemaObra { PantallaPresupuesto(obraId, dao, alVolver = {}) } }
            esperarTexto("Cambios aprobados por estancia")

            regla.onNodeWithText("Salón: Suelo laminado").assertExists()
            // Un cambio pendiente no puede aparecer en el presupuesto.
            regla.onNodeWithText("Salón: Tabique de pladur").assertDoesNotExist()
            regla.onNodeWithText("TOTAL").assertExists()
            regla.onNodeWithText(Presupuesto.euros(base)).assertExists()
            regla.onNodeWithText(Presupuesto.euros(total)).assertExists()
            regla.onNodeWithText("12,00 m² × 10,00 €").assertExists()
        } finally {
            db.close()
        }
    }

    @Test
    fun sinAprobacionesNoInventaPartidas(): Unit = runBlocking {
        val db = baseDeDatos()
        val dao = db.dao()
        try {
            val obraId = dao.insertarObra(ObraEntity(nombre = "Piso"))
            dao.insertarEstancia(EstanciaEntity(obraId = obraId, nombre = "Salón", superficie = 12.0))

            regla.setContent { TemaObra { PantallaPresupuesto(obraId, dao, alVolver = {}) } }
            esperarTexto("Todavía no hay trabajos aprobados", substring = true)
            regla.onNodeWithText("TOTAL").assertDoesNotExist()
            regla.onNodeWithText("Enviar presupuesto").assertIsNotEnabled()
        } finally {
            db.close()
        }
    }

    @Test
    fun unPrecioInvalidoBloqueaElPresupuesto(): Unit = runBlocking {
        val db = baseDeDatos()
        val dao = db.dao()
        try {
            val obraId = dao.insertarObra(ObraEntity(nombre = "Piso"))
            val salon = dao.insertarEstancia(EstanciaEntity(obraId = obraId, nombre = "Salón", superficie = 8.0))
            dao.insertarCambio(
                CambioEntity(estanciaId = salon, tipo = "SUELO_LAMINADO", cantidad = 8.0, precioPropio = 0.0, estado = "APROBADO")
            )

            regla.setContent { TemaObra { PantallaPresupuesto(obraId, dao, alVolver = {}) } }
            esperarTexto("Revisa los trabajos aprobados", substring = true)
            regla.onNodeWithText("TOTAL").assertDoesNotExist()
        } finally {
            db.close()
        }
    }

    private fun esperarTexto(texto: String, substring: Boolean = false) {
        regla.waitUntil(timeoutMillis = 10_000) {
            val nodos = if (substring) {
                regla.onAllNodesWithText(texto, substring = true)
            } else {
                regla.onAllNodesWithText(texto)
            }
            nodos.fetchSemanticsNodes().isNotEmpty()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class PantallaPlanoUiTest {

    @get:Rule
    val regla = createComposeRule()

    private fun preparar(): Triple<ObraDb, ObraDao, Long> {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ObraDb::class.java
        ).build()
        val dao = db.dao()
        val obraId = runBlocking {
            val obra = dao.insertarObra(ObraEntity(nombre = "Piso", largo = 4.0, ancho = 3.0))
            val salon = dao.insertarEstancia(EstanciaEntity(obraId = obra, nombre = "Salón"))
            val esquinas = listOf(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0)
            esquinas.indices.forEach { i ->
                val a = esquinas[i]
                val b = esquinas[(i + 1) % esquinas.size]
                dao.insertarMuro(
                    MuroEntity(estanciaId = salon, x1 = a.first, y1 = a.second, x2 = b.first, y2 = b.second)
                )
            }
            obra
        }
        return Triple(db, dao, obraId)
    }

    @Test
    fun elContornoCerradoDaLaSuperficieYSePuedeAplicar() {
        val (db, dao, obraId) = preparar()
        var aplicado: Triple<Double, Double, Double>? = null
        try {
            regla.setContent {
                TemaObra {
                    PantallaPlano(
                        obraId = obraId,
                        dao = dao,
                        alVolver = {},
                        alAplicar = { largo, ancho, superficie ->
                            aplicado = Triple(largo, ancho, superficie)
                        }
                    )
                }
            }

            esperarTexto(Geometria.formatear(12.0, 2) + " m²")
            esperarBotonActivo("Usar estas medidas")
            regla.onNodeWithText("Usar estas medidas").performClick()
            regla.waitForIdle()

            val medidas = aplicado
            assertTrue("No se avisó de las medidas del plano", medidas != null)
            assertEquals(4.0, medidas!!.first, 0.001)
            assertEquals(3.0, medidas.second, 0.001)
            assertEquals(12.0, medidas.third, 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun unPlanoVacioNoDejaAplicarMedidas() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ObraDb::class.java
        ).build()
        val dao = db.dao()
        try {
            val obraId = runBlocking { dao.insertarObra(ObraEntity(nombre = "Piso")) }
            regla.setContent { TemaObra { PantallaPlano(obraId, dao, alVolver = {}, alAplicar = { _, _, _ -> }) } }

            esperarTexto("Toca un punto y luego otro")
            regla.onNodeWithText("Usar estas medidas").assertIsNotEnabled()
            regla.onNodeWithText("Cierra el contorno antes de usar la superficie", substring = true).assertExists()
        } finally {
            db.close()
        }
    }

    private fun esperarTexto(texto: String) {
        regla.waitUntil(timeoutMillis = 10_000) {
            regla.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun esperarBotonActivo(texto: String) {
        regla.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                regla.onNodeWithText(texto).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class CopiaObraUiTest {

    @get:Rule
    val regla = createComposeRule()

    private fun baseDeDatos(): ObraDb =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ObraDb::class.java
        ).build()

    @Test
    fun laObraOfreceGuardarLaCopiaConFotos(): Unit = runBlocking {
        val db = baseDeDatos()
        val dao = db.dao()
        try {
            val obraId = dao.insertarObra(ObraEntity(nombre = "Piso"))
            dao.insertarEstancia(EstanciaEntity(obraId = obraId, nombre = "Salón"))

            regla.setContent {
                TemaObra {
                    PantallaObra(
                        id = obraId,
                        dao = dao,
                        alVolver = {},
                        alMedir = {},
                        alPlano = {},
                        alVisual = {},
                        alPresupuesto = {},
                        alRetoque = {},
                        alProyecto = {},
                        alActualizar = {}
                    )
                }
            }

            esperarTexto("Guardar copia con fotos")
            regla.onNodeWithText("Guardar copia con fotos").assertExists()
            // La exportación antigua queda claramente separada de la copia con fotos.
            regla.onNodeWithText("Exportar datos (.obra, sin fotos)").assertExists()
        } finally {
            db.close()
        }
    }

    @Test
    fun elInicioPermiteRestaurarUnaCopia() {
        val db = baseDeDatos()
        val dao = db.dao()
        try {
            regla.setContent {
                TemaObra {
                    PantallaInicio(
                        dao = dao,
                        hayVersion = false,
                        alAbrir = {},
                        alCrear = {},
                        alActualizar = {}
                    )
                }
            }

            esperarTexto("Restaurar copia de una obra")
            regla.onNodeWithText("Restaurar copia de una obra").assertIsEnabled()
            regla.onNodeWithText("Todavía no hay obras").assertExists()
        } finally {
            db.close()
        }
    }

    private fun esperarTexto(texto: String) {
        regla.waitUntil(timeoutMillis = 10_000) {
            regla.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
