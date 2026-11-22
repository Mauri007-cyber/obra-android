package es.obra.app.render

import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraEntity
import es.obra.app.dominio.Poligono
import es.obra.app.dominio.Punto
import es.obra.app.dominio.Triangulos
import kotlin.math.hypot
import kotlin.math.max

/**
 * Un trozo plano de la escena: una cara de muro, un cristal o un triángulo de suelo.
 *
 * `mitad` es el centro del muro al que pertenece la cara: sirve para quitar los muros que
 * quedan entre la cámara y la estancia y poder mirar dentro (corte de casa de muñecas).
 */
class Cara(
    val puntos: List<Vec3>,
    val color: Int,
    val solido: Boolean = true,
    val mitad: Vec3? = null
)

class Escena(
    val caras: List<Cara>,
    val centro: Vec3,
    val radio: Double,
    val alto: Double
)

/**
 * Levanta la habitación en 3D a partir de lo que está medido y dibujado.
 *
 * Nada de esto es inventado: cada muro es el muro del plano, con su grosor y su largo, y cada
 * puerta o ventana se abre **en su sitio** y **a su altura** (la puerta desde el suelo, la
 * ventana con su alféizar). El «antes» enseña lo que hay hoy y el «después» aplica lo marcado:
 * lo que se tira desaparece y lo nuevo aparece.
 */
object Modelo3D {

    private const val ALFEIZAR = 0.95
    private const val GROSOR_MIN = 0.06
    private const val GROSOR_MAX = 0.40

    private val MURO_ANTES = 0xFFBFB8AB.toInt()
    private val MURO_DESPUES = 0xFFF8F4EC.toInt()
    private val MURO_NUEVO = 0xFFEFE4CC.toInt()
    private val SUELO_ANTES = 0xFF8C8579.toInt()
    private val SUELO_DESPUES = 0xFFC08A5E.toInt()
    private val CRISTAL = 0x66BBD5E2

    fun construir(
        obra: ObraEntity,
        muros: List<MuroEntity>,
        huecos: List<HuecoEntity>,
        despues: Boolean
    ): Escena {
        val alto = if (obra.altura > 0.5) obra.altura else 2.5
        val caras = mutableListOf<Cara>()

        val visibles = muros.filter { muro ->
            if (despues) muro.estado != "tirado" else muro.estado != "nuevo"
        }

        visibles.forEach { muro ->
            val largo = Poligono.longitud(muro)
            if (largo < 0.05) return@forEach

            val dir = Vec3((muro.x2 - muro.x1) / largo, (muro.y2 - muro.y1) / largo, 0.0)
            val inicio = Vec3(muro.x1, muro.y1, 0.0)
            val mitad = Vec3((muro.x1 + muro.x2) / 2, (muro.y1 + muro.y2) / 2, 0.0)
            val grosor = muro.grosor.coerceIn(GROSOR_MIN, GROSOR_MAX)
            val color = when {
                !despues -> MURO_ANTES
                muro.estado == "nuevo" -> MURO_NUEVO
                else -> MURO_DESPUES
            }

            var cursor = 0.0
            huecos.filter { it.muroId == muro.id }.sortedBy { it.posicion }.forEach { hueco ->
                // Ojo: coerceIn peta si el mínimo es mayor que el máximo (muro más corto que
                // una puerta). Se acota primero el ancho al largo del muro.
                val ancho = hueco.ancho.coerceIn(minOf(0.35, largo), largo)
                val desde = (hueco.posicion * largo - ancho / 2).coerceIn(0.0, largo)
                val hasta = (desde + ancho).coerceAtMost(largo)
                if (hasta - desde < 0.1) return@forEach

                val alfeizar = if (hueco.tipo == "puerta") 0.0
                else ALFEIZAR.coerceAtMost((alto - 0.7).coerceAtLeast(0.0))
                val dintel = (alfeizar + hueco.alto).coerceIn(alfeizar + 0.3, alto)

                // Muro lleno antes del hueco.
                if (desde > cursor + 0.02) {
                    caja(caras, inicio + dir * cursor, inicio + dir * desde, grosor, 0.0, alto, color, mitad)
                }
                // Bajo la ventana y encima del hueco.
                if (alfeizar > 0.02) {
                    caja(caras, inicio + dir * desde, inicio + dir * hasta, grosor, 0.0, alfeizar, color, mitad)
                }
                if (dintel < alto - 0.02) {
                    caja(caras, inicio + dir * desde, inicio + dir * hasta, grosor, dintel, alto, color, mitad)
                }
                if (hueco.tipo == "ventana") {
                    cristal(caras, inicio + dir * desde, inicio + dir * hasta, grosor, alfeizar, dintel, mitad)
                }

                cursor = hasta
            }

            if (cursor < largo - 0.02) {
                caja(caras, inicio + dir * cursor, inicio + dir * largo, grosor, 0.0, alto, color, mitad)
            }
        }

        suelo(caras, visibles, if (despues) SUELO_DESPUES else SUELO_ANTES)

        val xs = muros.flatMap { listOf(it.x1, it.x2) }
        val ys = muros.flatMap { listOf(it.y1, it.y2) }
        val centroX = if (xs.isEmpty()) 0.0 else (xs.min() + xs.max()) / 2.0
        val centroY = if (ys.isEmpty()) 0.0 else (ys.min() + ys.max()) / 2.0
        val anchoEscena = if (xs.isEmpty()) 4.0 else xs.max() - xs.min()
        val fondoEscena = if (ys.isEmpty()) 4.0 else ys.max() - ys.min()
        val radio = max(1.5, hypot(anchoEscena, fondoEscena) / 2.0)

        return Escena(
            caras = caras,
            centro = Vec3(centroX, centroY, alto * 0.5),
            radio = radio,
            alto = alto
        )
    }

    /** Prisma de muro: dos caras largas, la tapa y los dos testigos. */
    private fun caja(
        destino: MutableList<Cara>,
        a: Vec3,
        b: Vec3,
        grosor: Double,
        z0: Double,
        z1: Double,
        color: Int,
        mitad: Vec3
    ) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val largo = hypot(dx, dy)
        if (largo < 0.01 || z1 - z0 < 0.01) return

        val dir = Vec3(dx / largo, dy / largo, 0.0)
        val perp = Vec3(-dir.y, dir.x, 0.0) * (grosor / 2)

        val p1 = a + perp
        val p2 = b + perp
        val p3 = b - perp
        val p4 = a - perp

        destino.add(Cara(listOf(alto(p1, z0), alto(p2, z0), alto(p2, z1), alto(p1, z1)), color, true, mitad))
        destino.add(Cara(listOf(alto(p3, z0), alto(p4, z0), alto(p4, z1), alto(p3, z1)), color, true, mitad))
        destino.add(Cara(listOf(alto(p1, z1), alto(p2, z1), alto(p3, z1), alto(p4, z1)), color, true, mitad))
        destino.add(Cara(listOf(alto(p1, z0), alto(p4, z0), alto(p4, z1), alto(p1, z1)), color, true, mitad))
        destino.add(Cara(listOf(alto(p2, z0), alto(p3, z0), alto(p3, z1), alto(p2, z1)), color, true, mitad))
    }

    private fun cristal(
        destino: MutableList<Cara>,
        a: Vec3,
        b: Vec3,
        grosor: Double,
        z0: Double,
        z1: Double,
        mitad: Vec3
    ) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val largo = hypot(dx, dy)
        if (largo < 0.01) return
        val dir = Vec3(dx / largo, dy / largo, 0.0)
        val perp = Vec3(-dir.y, dir.x, 0.0) * (grosor / 2 * 1.02)

        destino.add(
            Cara(
                listOf(alto(a + perp, z0), alto(b + perp, z0), alto(b + perp, z1), alto(a + perp, z1)),
                CRISTAL,
                solido = false,
                mitad = mitad
            )
        )
    }

    private fun suelo(destino: MutableList<Cara>, muros: List<MuroEntity>, color: Int) {
        // Un tabique interior rompe el contorno: se prueba primero con todo y luego sin lo nuevo.
        val contorno = Poligono.cerrado(muros)
            ?: Poligono.cerrado(muros.filter { it.estado != "nuevo" })
        val puntos = contorno ?: rectangulo(muros)
        if (puntos.size < 3) return

        Triangulos.orejas(puntos).forEach { (i, j, k) ->
            destino.add(
                Cara(
                    listOf(
                        Vec3(puntos[i].x, puntos[i].y, 0.0),
                        Vec3(puntos[j].x, puntos[j].y, 0.0),
                        Vec3(puntos[k].x, puntos[k].y, 0.0)
                    ),
                    color
                )
            )
        }
    }

    private fun rectangulo(muros: List<MuroEntity>): List<Punto> {
        if (muros.isEmpty()) return emptyList()
        val xs = muros.flatMap { listOf(it.x1, it.x2) }
        val ys = muros.flatMap { listOf(it.y1, it.y2) }
        val x0 = xs.min()
        val x1 = xs.max()
        val y0 = ys.min()
        val y1 = ys.max()
        if (x1 - x0 < 0.2 || y1 - y0 < 0.2) return emptyList()
        return listOf(Punto(x0, y0), Punto(x1, y0), Punto(x1, y1), Punto(x0, y1))
    }

    private fun alto(p: Vec3, z: Double) = Vec3(p.x, p.y, z)
}
