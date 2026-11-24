package es.obra.app.datos

import es.obra.app.dominio.Cambio
import es.obra.app.dominio.EstadoCambio
import es.obra.app.dominio.EstanciaProyecto
import es.obra.app.dominio.Proyecto
import es.obra.app.dominio.TipoCambio

/**
 * Traduce entre la base de datos y el modelo de la obra completa.
 *
 * Todo lo que la pantalla necesita saber de una obra se carga aquí de una vez: estancias,
 * cambios y totales. Así la interfaz no hace consultas sueltas ni se inventa números.
 */
object ProyectoRepositorio {

    suspend fun cargar(dao: ObraDao, obra: ObraEntity): Proyecto {
        val estancias = dao.estancias(obra.id)
        val cambios = dao.cambios(obra.id)

        val porEstancia = cambios.groupBy { it.estanciaId }

        return Proyecto(
            nombre = obra.nombre,
            estancias = estancias.mapIndexed { indice, estancia ->
                val superficie = when {
                    estancia.superficie > 0.0 -> estancia.superficie
                    // Los metros generales de la obra son los de la primera estancia. Antes solo
                    // se heredaban con una sola estancia, así que al añadir un segundo cuarto
                    // las medidas ya tomadas desaparecían del proyecto.
                    indice == 0 -> obra.superficieEfectiva
                    else -> 0.0
                }
                EstanciaProyecto(
                    id = estancia.id,
                    nombre = estancia.nombre,
                    superficie = superficie,
                    altura = estancia.altura,
                    foto = estancia.foto,
                    fotoTerminada = estancia.fotoTerminada,
                    cambios = porEstancia[estancia.id].orEmpty().mapNotNull { it.aDominio() }
                )
            }
        )
    }

    /** Si la obra aún no tiene estancias, crea la primera para poder empezar a proponer. */
    suspend fun asegurarPrimeraEstancia(dao: ObraDao, obra: ObraEntity): EstanciaEntity {
        val existentes = dao.estancias(obra.id)
        if (existentes.isNotEmpty()) return existentes.first()

        val id = dao.insertarEstancia(
            EstanciaEntity(
                obraId = obra.id,
                nombre = "Estancia 1",
                altura = obra.altura,
                superficie = obra.superficieEfectiva
            )
        )
        return EstanciaEntity(
            id = id,
            obraId = obra.id,
            nombre = "Estancia 1",
            altura = obra.altura,
            superficie = obra.superficieEfectiva
        )
    }

    suspend fun guardar(dao: ObraDao, estanciaId: Long, cambio: Cambio): Long {
        val entidad = cambio.aEntidad(estanciaId)
        if (cambio.id > 0) {
            dao.actualizarCambio(entidad)
            return cambio.id
        }
        return dao.insertarCambio(entidad)
    }

    suspend fun cambiarEstado(dao: ObraDao, cambioId: Long, estado: EstadoCambio) {
        if (cambioId <= 0) return
        dao.cambiarEstado(cambioId, estado.name)
    }
}

/** Devuelve null si el tipo ya no existe en el catálogo: se ignora, no rompe la obra. */
fun CambioEntity.aDominio(): Cambio? {
    val delCatalogo = TipoCambio.porNombre(this.tipo) ?: return null
    return Cambio(
        id = id,
        tipo = delCatalogo,
        cantidad = cantidad,
        precioPropio = precioPropio,
        acabado = acabado,
        nota = nota,
        estado = EstadoCambio.porNombre(estado),
        grupo = grupo.ifBlank { "A" }
    )
}

fun Cambio.aEntidad(estanciaId: Long): CambioEntity = CambioEntity(
    id = id,
    estanciaId = estanciaId,
    tipo = tipo.name,
    cantidad = cantidad,
    precioPropio = precioPropio,
    acabado = acabado,
    nota = nota,
    estado = estado.name,
    grupo = grupo
)
