package es.obra.app.datos

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Entity(tableName = "obras")
@Serializable
data class ObraEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val direccion: String = "",
    val cliente: String = "",
    val telefono: String = "",
    val estado: String = "presupuesto",
    val creada: Long = System.currentTimeMillis(),
    val altura: Double = 2.5,
    val largo: Double = 0.0,
    val ancho: Double = 0.0,
    val metodo: String = "manual",
    val notas: String = "",
    /** Cuánto hay que corregir lo que mide la cámara. 1,0 = sin corrección. */
    val factorCalibracion: Double = 1.0,
    /** Superficie del contorno cerrado dibujado. 0 = usar largo × ancho. */
    val superficieCerrada: Double = 0.0,
    /** Precios propios de esta obra, separados por «;». Vacío = los de por defecto. */
    val tarifas: String = ""
) {
    val superficie: Double get() = largo * ancho

    /** La buena: la del contorno si existe, si no la del rectángulo. */
    val superficieEfectiva: Double
        get() = if (superficieCerrada > 0.0) superficieCerrada else largo * ancho
}

@Entity(
    tableName = "estancias",
    foreignKeys = [
        ForeignKey(
            entity = ObraEntity::class,
            parentColumns = ["id"],
            childColumns = ["obraId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("obraId")]
)
@Serializable
data class EstanciaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val obraId: Long,
    val nombre: String,
    val altura: Double = 2.5,
    /** Superficie propia de esta habitación. 0 = usar la de la obra. */
    val superficie: Double = 0.0,
    /** Foto real de la estancia, para enseñar el antes y el después. */
    val foto: String = ""
)

@Entity(
    tableName = "muros",
    foreignKeys = [
        ForeignKey(
            entity = EstanciaEntity::class,
            parentColumns = ["id"],
            childColumns = ["estanciaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("estanciaId")]
)
@Serializable
data class MuroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val estanciaId: Long,
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    val grosor: Double = 0.12,
    val altura: Double = 2.5,
    val estado: String = "original",
    val carga: Boolean = false
)

@Entity(
    tableName = "huecos",
    foreignKeys = [
        ForeignKey(
            entity = MuroEntity::class,
            parentColumns = ["id"],
            childColumns = ["muroId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("muroId")]
)
@Serializable
data class HuecoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val muroId: Long,
    val posicion: Double = 0.5,
    val ancho: Double = 0.8,
    val alto: Double = 2.05,
    val tipo: String = "puerta"
)

/**
 * Un cambio de reforma dentro de una estancia.
 *
 * El tipo se guarda por nombre para que la base siga leyéndose si mañana cambia el catálogo:
 * lo que ya no exista se descarta al cargar, no rompe la obra entera.
 */
@Entity(
    tableName = "cambios",
    foreignKeys = [
        ForeignKey(
            entity = EstanciaEntity::class,
            parentColumns = ["id"],
            childColumns = ["estanciaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("estanciaId")]
)
@Serializable
data class CambioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val estanciaId: Long,
    val tipo: String,
    val cantidad: Double = 0.0,
    val precioPropio: Double? = null,
    val acabado: String = "",
    val nota: String = "",
    val estado: String = "propuesto",
    val grupo: String = "A",
    val creado: Long = System.currentTimeMillis()
)

@Dao
interface ObraDao {

    @Query("SELECT * FROM obras ORDER BY creada DESC")
    fun observarObras(): Flow<List<ObraEntity>>

    @Query("SELECT * FROM obras WHERE id = :id")
    fun observarObra(id: Long): Flow<ObraEntity?>

    @Query("SELECT * FROM obras WHERE id = :id")
    suspend fun obtenerObra(id: Long): ObraEntity?

    @Insert
    suspend fun insertarObra(obra: ObraEntity): Long

    @Update
    suspend fun actualizarObra(obra: ObraEntity)

    @Query("UPDATE obras SET largo = :largo, ancho = :ancho, altura = :altura, superficieCerrada = CASE WHEN largo != :largo OR ancho != :ancho THEN 0 ELSE superficieCerrada END, metodo = CASE WHEN largo != :largo OR ancho != :ancho THEN 'manual' ELSE metodo END WHERE id = :id")
    suspend fun guardarMedidasManuales(id: Long, largo: Double, ancho: Double, altura: Double)

    @Query("UPDATE obras SET largo = :largo, ancho = :ancho, factorCalibracion = :factor, metodo = 'arcore', superficieCerrada = 0 WHERE id = :id")
    suspend fun guardarMedidasAr(id: Long, largo: Double, ancho: Double, factor: Double)

    @Query("UPDATE estancias SET superficie = :superficie, altura = :altura WHERE id = :id AND obraId = :obraId")
    suspend fun guardarMedidasEstancia(obraId: Long, id: Long, superficie: Double, altura: Double)

    @Query("UPDATE estancias SET superficie = :superficie WHERE id = :id AND obraId = :obraId")
    suspend fun guardarSuperficieEstancia(obraId: Long, id: Long, superficie: Double)

    @Transaction
    suspend fun instantaneaObra(id: Long): ObraExport {
        val obra = requireNotNull(obtenerObra(id)) { "La obra ya no existe" }
        val estancias = estancias(id)
        val muros = estancias.flatMap { muros(it.id) }
        return ObraExport(obra = obra, estancias = estancias, muros = muros,
            huecos = muros.flatMap { huecos(it.id) }, cambios = cambios(id))
    }

    @Transaction
    suspend fun importarObra(paquete: ObraExport): Long {
        val nuevaObra = insertarObra(paquete.obra.copy(id = 0))
        val idsEstancias = mutableMapOf<Long, Long>()
        for (estancia in paquete.estancias) {
            idsEstancias[estancia.id] = insertarEstancia(estancia.copy(id = 0, obraId = nuevaObra))
        }
        val idsMuros = mutableMapOf<Long, Long>()
        for (muro in paquete.muros) {
            idsMuros[muro.id] = insertarMuro(muro.copy(id = 0, estanciaId = idsEstancias.getValue(muro.estanciaId)))
        }
        for (hueco in paquete.huecos) insertarHueco(hueco.copy(id = 0, muroId = idsMuros.getValue(hueco.muroId)))
        for (cambio in paquete.cambios) insertarCambio(cambio.copy(id = 0, estanciaId = idsEstancias.getValue(cambio.estanciaId)))
        return nuevaObra
    }

    @Delete
    suspend fun borrarObra(obra: ObraEntity)

    @Query("SELECT * FROM estancias WHERE obraId = :obraId")
    suspend fun estancias(obraId: Long): List<EstanciaEntity>

    @Query("SELECT * FROM muros WHERE estanciaId = :estanciaId")
    suspend fun muros(estanciaId: Long): List<MuroEntity>

    @Query("SELECT * FROM huecos WHERE muroId = :muroId")
    suspend fun huecos(muroId: Long): List<HuecoEntity>

    @Insert
    suspend fun insertarEstancia(estancia: EstanciaEntity): Long

    @Query("SELECT * FROM cambios WHERE estanciaId = :estanciaId ORDER BY creado ASC")
    fun observarCambiosDeEstancia(estanciaId: Long): Flow<List<CambioEntity>>

    @Query(
        "SELECT c.* FROM cambios c " +
            "JOIN estancias e ON e.id = c.estanciaId " +
            "WHERE e.obraId = :obraId ORDER BY c.creado ASC"
    )
    fun observarCambios(obraId: Long): Flow<List<CambioEntity>>

    @Query(
        "SELECT c.* FROM cambios c " +
            "JOIN estancias e ON e.id = c.estanciaId " +
            "WHERE e.obraId = :obraId ORDER BY c.creado ASC"
    )
    suspend fun cambios(obraId: Long): List<CambioEntity>

    @Insert
    suspend fun insertarCambio(cambio: CambioEntity): Long

    @Insert
    suspend fun insertarCambios(cambios: List<CambioEntity>)

    @Update
    suspend fun actualizarCambio(cambio: CambioEntity)

    @Query("UPDATE cambios SET estado = :estado WHERE id = :id")
    suspend fun cambiarEstado(id: Long, estado: String)

    @Query("DELETE FROM cambios WHERE id = :id")
    suspend fun borrarCambio(id: Long)

    @Query("DELETE FROM estancias WHERE id = :id")
    suspend fun borrarEstancia(id: Long)

    @Update
    suspend fun actualizarEstancia(estancia: EstanciaEntity)

    @Insert
    suspend fun insertarMuro(muro: MuroEntity): Long

    @Insert
    suspend fun insertarHueco(hueco: HuecoEntity): Long

    @Update
    suspend fun actualizarMuro(muro: MuroEntity)

    @Query("SELECT * FROM huecos WHERE muroId IN (SELECT id FROM muros WHERE estanciaId = :estanciaId)")
    suspend fun huecosDeEstancia(estanciaId: Long): List<HuecoEntity>

    @Query("DELETE FROM huecos WHERE id = :id")
    suspend fun borrarHueco(id: Long)

    @Query("DELETE FROM muros WHERE id = :id")
    suspend fun borrarMuro(id: Long)

    @Query("DELETE FROM muros WHERE estanciaId = :estanciaId")
    suspend fun borrarMurosDeEstancia(estanciaId: Long)
}

private val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE obras ADD COLUMN factorCalibracion REAL NOT NULL DEFAULT 1.0"
        )
    }
}

private val MIGRACION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE obras ADD COLUMN superficieCerrada REAL NOT NULL DEFAULT 0.0"
        )
    }
}

private val MIGRACION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE obras ADD COLUMN tarifas TEXT NOT NULL DEFAULT ''"
        )
    }
}

/**
 * Los cambios de reforma: el corazón de la obra completa.
 *
 * La tabla nace vacía en las obras que ya existían, así que actualizar la app no pierde nada.
 */
private val MIGRACION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE estancias ADD COLUMN superficie REAL NOT NULL DEFAULT 0.0"
        )
        db.execSQL(
            "ALTER TABLE estancias ADD COLUMN foto TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cambios` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`estanciaId` INTEGER NOT NULL, " +
                "`tipo` TEXT NOT NULL, " +
                "`cantidad` REAL NOT NULL, " +
                "`precioPropio` REAL, " +
                "`acabado` TEXT NOT NULL, " +
                "`nota` TEXT NOT NULL, " +
                "`estado` TEXT NOT NULL, " +
                "`grupo` TEXT NOT NULL, " +
                "`creado` INTEGER NOT NULL, " +
                "FOREIGN KEY(`estanciaId`) REFERENCES `estancias`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cambios_estanciaId` ON `cambios` (`estanciaId`)"
        )
    }
}

@Database(
    entities = [
        ObraEntity::class,
        EstanciaEntity::class,
        MuroEntity::class,
        HuecoEntity::class,
        CambioEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ObraDb : RoomDatabase() {

    abstract fun dao(): ObraDao

    companion object {
        @Volatile
        private var instancia: ObraDb? = null

        fun obtener(context: Context): ObraDb =
            instancia ?: synchronized(this) {
                instancia ?: crear(context).also { instancia = it }
            }

        /** Si la apertura o una migración falla, no sustituir ni mover la base existente. */
        private fun crear(context: Context): ObraDb = Room.databaseBuilder(
            context.applicationContext,
            ObraDb::class.java,
            "obra.db"
        ).addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5).build()
    }
}
