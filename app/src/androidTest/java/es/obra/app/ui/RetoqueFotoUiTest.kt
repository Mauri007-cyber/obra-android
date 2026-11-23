package es.obra.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.obra.app.datos.EstanciaEntity
import es.obra.app.datos.ObraDb
import es.obra.app.datos.ObraEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * El retoque tiene que funcionar sin cuenta, sin clave y sin conexión.
 *
 * Se comprueba con una foto de verdad escrita en el almacenamiento de la app: la frase se
 * entiende, se pinta la foto y aparece el botón de compartir. Es la prueba de que el camino
 * entero es local.
 */
@RunWith(AndroidJUnit4::class)
class RetoqueFotoUiTest {

    @get:Rule
    val regla = createComposeRule()

    @Test
    fun laFraseSeEntiendeYLaFotoSeRetocaSinConexion(): Unit = runBlocking {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(contexto, ObraDb::class.java).build()
        val dao = db.dao()
        val archivo = File(File(contexto.filesDir, "fotos").apply { mkdirs() }, "prueba-retoque.png")

        try {
            Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(180, 180, 180))
                archivo.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            }

            val obraId = dao.insertarObra(ObraEntity(nombre = "Piso"))
            val estanciaId = dao.insertarEstancia(
                EstanciaEntity(
                    obraId = obraId,
                    nombre = "Salón",
                    superficie = 18.0,
                    foto = "fotos/prueba-retoque.png"
                )
            )

            regla.setContent {
                TemaObra {
                    PantallaRetoque(
                        obraId = obraId,
                        estanciaId = estanciaId,
                        dao = dao,
                        alVolver = {}
                    )
                }
            }

            esperarTexto("Dilo con tus palabras")
            regla.onNodeWithText("Retoque local").assertIsNotEnabled()

            regla.onNode(hasSetTextAction())
                .performTextInput("suelo de tarima clara y paredes blancas")
            regla.onNodeWithText("Entender").performClick()

            esperarTexto("He entendido 2 trabajo(s)")
            regla.onNodeWithText("Retoque local").performClick()

            esperarTexto("Compartir la foto")
            regla.onNodeWithText("Compartir la foto").assertExists()
        } finally {
            db.close()
            archivo.delete()
        }
    }

    private fun esperarTexto(texto: String) {
        regla.waitUntil(timeoutMillis = 30_000) {
            regla.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
