package es.obra.app.datos

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La base que ya existe en los móviles está en la versión 5. Al actualizar, la columna de la
 * imagen terminada tiene que aparecer sin perder ninguna estancia ni su foto.
 *
 * Se monta la base con la misma factoría SQLite que usa Room, en memoria, para no tocar la del
 * dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class MigracionEstanciaTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun abrirVersion5() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val configuracion = SupportSQLiteOpenHelper.Configuration.builder(contexto)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(base: SupportSQLiteDatabase) {
                    // Réplica del esquema 5 de `estancias`, como lo dejaba la versión anterior.
                    base.execSQL(
                        "CREATE TABLE estancias (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "obraId INTEGER NOT NULL, " +
                            "nombre TEXT NOT NULL, " +
                            "altura REAL NOT NULL, " +
                            "superficie REAL NOT NULL, " +
                            "foto TEXT NOT NULL)"
                    )
                    base.execSQL(
                        "INSERT INTO estancias (obraId, nombre, altura, superficie, foto) " +
                            "VALUES (7, 'Salón', 2.5, 18.0, 'fotos/salon.png')"
                    )
                }

                override fun onUpgrade(base: SupportSQLiteDatabase, anterior: Int, nueva: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuracion)
        db = helper.writableDatabase
    }

    @After
    fun cerrar() {
        helper.close()
    }

    @Test
    fun la_migracion_5_6_conserva_las_estancias_y_anade_la_imagen() {
        MIGRACION_5_6.migrate(db)

        db.query("SELECT nombre, foto, fotoTerminada FROM estancias").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Salón", cursor.getString(0))
            assertEquals("fotos/salon.png", cursor.getString(1))
            assertEquals("", cursor.getString(2))
        }

        db.execSQL("UPDATE estancias SET fotoTerminada = 'retoques/terminada-1.jpg' WHERE id = 1")
        db.query("SELECT fotoTerminada FROM estancias").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("retoques/terminada-1.jpg", cursor.getString(0))
        }
    }
}
