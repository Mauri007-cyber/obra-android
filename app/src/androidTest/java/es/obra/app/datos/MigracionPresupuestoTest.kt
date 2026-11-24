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
 * Las obras que ya existen están en la versión 6. Al actualizar, el presupuesto gana estado,
 * validez y notas sin perder nada de lo guardado.
 */
@RunWith(AndroidJUnit4::class)
class MigracionPresupuestoTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun abrirVersion6() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val configuracion = SupportSQLiteOpenHelper.Configuration.builder(contexto)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(base: SupportSQLiteDatabase) {
                    base.execSQL(
                        "CREATE TABLE obras (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "nombre TEXT NOT NULL, direccion TEXT NOT NULL, cliente TEXT NOT NULL, " +
                            "telefono TEXT NOT NULL, estado TEXT NOT NULL, creada INTEGER NOT NULL, " +
                            "altura REAL NOT NULL, largo REAL NOT NULL, ancho REAL NOT NULL, " +
                            "metodo TEXT NOT NULL, notas TEXT NOT NULL, " +
                            "factorCalibracion REAL NOT NULL, superficieCerrada REAL NOT NULL, " +
                            "tarifas TEXT NOT NULL)"
                    )
                    base.execSQL(
                        "INSERT INTO obras (nombre, direccion, cliente, telefono, estado, creada, " +
                            "altura, largo, ancho, metodo, notas, factorCalibracion, " +
                            "superficieCerrada, tarifas) VALUES " +
                            "('Piso Mayor', 'Calle Mayor 3', 'Ana', '600000000', 'presupuesto', " +
                            "1700000000000, 2.5, 4.0, 3.0, 'manual', 'nota', 1.0, 0.0, '')"
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
    fun la_migracion_6_7_conserva_la_obra_y_estrena_presupuesto() {
        MIGRACION_6_7.migrate(db)

        db.query(
            "SELECT nombre, direccion, validezDias, notasPresupuesto, estadoPresupuesto FROM obras"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Piso Mayor", cursor.getString(0))
            assertEquals("Calle Mayor 3", cursor.getString(1))
            assertEquals(30, cursor.getInt(2))
            assertEquals("", cursor.getString(3))
            assertEquals("borrador", cursor.getString(4))
        }

        db.execSQL("UPDATE obras SET validezDias = 15, estadoPresupuesto = 'enviado' WHERE id = 1")
        db.query("SELECT validezDias, estadoPresupuesto FROM obras").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(15, cursor.getInt(0))
            assertEquals("enviado", cursor.getString(1))
        }
    }
}
