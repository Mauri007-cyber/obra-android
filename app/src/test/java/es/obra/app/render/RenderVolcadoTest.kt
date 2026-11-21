package es.obra.app.render

import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraEntity
import org.junit.Test
import java.io.File

class RenderVolcadoTest {

    @Test
    fun volcar() {
        val obra = ObraEntity(nombre = "Salón", largo = 4.0, ancho = 3.0, altura = 2.5)

        val muros = listOf(
            MuroEntity(id = 1, estanciaId = 1, x1 = 0.0, y1 = 0.0, x2 = 4.0, y2 = 0.0, grosor = 0.11),
            MuroEntity(id = 2, estanciaId = 1, x1 = 4.0, y1 = 0.0, x2 = 4.0, y2 = 3.0, grosor = 0.11),
            MuroEntity(id = 3, estanciaId = 1, x1 = 4.0, y1 = 3.0, x2 = 0.0, y2 = 3.0, grosor = 0.11),
            MuroEntity(id = 4, estanciaId = 1, x1 = 0.0, y1 = 3.0, x2 = 0.0, y2 = 0.0, grosor = 0.11),
            MuroEntity(id = 5, estanciaId = 1, x1 = 2.0, y1 = 0.0, x2 = 2.0, y2 = 3.0, grosor = 0.09, estado = "nuevo")
        )

        val huecos = listOf(
            HuecoEntity(id = 1, muroId = 1, posicion = 0.18, ancho = 0.8, alto = 2.05, tipo = "puerta"),
            HuecoEntity(id = 2, muroId = 2, posicion = 0.5, ancho = 1.2, alto = 1.2, tipo = "ventana"),
            HuecoEntity(id = 3, muroId = 5, posicion = 0.5, ancho = 0.8, alto = 2.05, tipo = "puerta")
        )

        File("build/render").mkdirs()

        listOf(false to "antes", true to "despues").forEach { (despues, nombre) ->
            val escena = Modelo3D.construir(obra, muros, huecos, despues)
            val vistas = listOf(
                0.85 to 0.42,
                0.85 to 1.15,
                -2.30 to 0.42,
                -2.30 to 0.20
            )
            vistas.forEachIndexed { indice, (giro, inclinacion) ->
                val planas = Render3D.proyectar(
                    escena,
                    Render3D.Vista(1200, 900, giro, inclinacion, 1.0)
                )
                val texto = planas.joinToString(",", "[", "]") { plana ->
                    "{\"color\":${plana.color},\"contorno\":${plana.contorno}," +
                        "\"pts\":[${plana.puntos.joinToString(",")}]}"
                }
                File("build/render/$nombre-$indice.json").writeText(texto)
            }
        }
    }
}
