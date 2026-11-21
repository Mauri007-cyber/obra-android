package es.obra.app.dominio

import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity

/**
 * Dónde suele haber una puerta y una ventana.
 *
 * No es reconocimiento por imagen: es lo que se repite en un piso español, para que al tocar
 * un muro aparezcan ya colocadas y el usuario solo tenga que borrar lo que no toque. Si no
 * acierta, se quita con un toque y no pasa nada.
 */
object Sugerencias {

    private const val ANCHO_PUERTA = 0.80
    private const val ALTO_PUERTA = 2.05
    private const val ANCHO_VENTANA = 1.20
    private const val ALTO_VENTANA = 1.20

    fun tipicos(muro: MuroEntity): List<HuecoEntity> {
        val largo = Poligono.longitud(muro)
        if (largo < 1.6) return emptyList()

        val salida = mutableListOf<HuecoEntity>()

        // La puerta, cerca de un extremo y nunca pegada a la esquina.
        val centro = ANCHO_PUERTA / 2 + 0.25
        val posicion = (centro / largo).coerceIn(0.06, 0.94)
        salida.add(
            HuecoEntity(
                muroId = muro.id,
                posicion = posicion,
                ancho = ANCHO_PUERTA.coerceAtMost(largo * 0.9),
                alto = ALTO_PUERTA,
                tipo = "puerta"
            )
        )

        // La ventana, centrada, solo si el muro da para las dos cosas.
        if (largo >= 3.6) {
            salida.add(
                HuecoEntity(
                    muroId = muro.id,
                    posicion = 0.5,
                    ancho = ANCHO_VENTANA,
                    alto = ALTO_VENTANA,
                    tipo = "ventana"
                )
            )
        }

        return salida
    }
}
