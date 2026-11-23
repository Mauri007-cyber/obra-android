package es.obra.app.dominio

import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import java.util.Locale

/**
 * Precios orientativos de reforma, en euros y con la mano de obra incluida.
 *
 * Se guardan por obra (cada albañil tiene los suyos) y se pueden cambiar en la pantalla de
 * presupuesto sin tocar el código.
 */
data class Tarifas(
    val tabiqueNuevo: Double = 45.0,
    val demolicion: Double = 12.0,
    val suelo: Double = 35.0,
    val pintura: Double = 8.0,
    val puerta: Double = 220.0,
    val ventana: Double = 350.0,
    val iva: Double = 21.0
) {

    fun aTexto(): String = listOf(
        tabiqueNuevo, demolicion, suelo, pintura, puerta, ventana, iva
    ).joinToString(";")

    companion object {
        val porDefecto = Tarifas()

        fun desdeTexto(texto: String): Tarifas {
            if (texto.isBlank()) return porDefecto

            // «NaN» también se parsea como número: se filtra o el presupuesto sale NaN.
            val partes = texto.split(";").mapNotNull { trozo ->
                trozo.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
            }
            if (partes.size < 7) return porDefecto

            return Tarifas(
                tabiqueNuevo = partes[0],
                demolicion = partes[1],
                suelo = partes[2],
                pintura = partes[3],
                puerta = partes[4],
                ventana = partes[5],
                iva = partes[6].coerceIn(0.0, 30.0)
            )
        }
    }
}

data class Partida(
    val concepto: String,
    val cantidad: Double,
    val unidad: String,
    val precio: Double,
    /** Estancia de la que sale la partida, para agrupar el presupuesto como en obra. */
    val estancia: String = "",
    /** Cambio del que sale, para poder cambiar cantidad y precio desde el presupuesto. */
    val cambioId: Long = 0
) {
    val importe: Double get() = cantidad * precio
}

/**
 * El presupuesto se saca de lo medido, no de una plantilla: si el plano dice que se tira un
 * tabique de 4 m, esa demolición aparece con sus metros cuadrados.
 */
object Presupuesto {

    /** El cálculo heredado trata todos los muros como una sola habitación. */
    fun permiteCalculoHeredado(numeroEstancias: Int, metodo: String, hayMuros: Boolean): Boolean =
        numeroEstancias <= 1 && (!hayMuros || metodo == "plano")

    /** Impide presentar importes incompletos como un presupuesto terminado. */
    fun aprobadosValidos(proyecto: Proyecto): Boolean = proyecto.aprobados.all { cambio ->
        cambio.precio.isFinite() && cambio.precio > 0.0 &&
            (cambio.tipo.unidad == Unidad.GLOBAL || (cambio.cantidad.isFinite() && cambio.cantidad > 0.0)) &&
            cambio.importe.isFinite()
    }

    /** Solo trabajos aprobados; cada línea conserva el nombre de su estancia. */
    fun partidasAprobadas(proyecto: Proyecto): List<Partida> = proyecto.estancias.flatMap { estancia ->
        estancia.aprobados.map { cambio ->
            Partida(
                concepto = "${estancia.nombre}: ${cambio.tipo.nombre}",
                cantidad = if (cambio.tipo.unidad == Unidad.GLOBAL) 1.0 else cambio.cantidad,
                unidad = cambio.tipo.unidad.etiqueta,
                precio = cambio.precio,
                estancia = estancia.nombre,
                cambioId = cambio.id
            )
        }
    }

    /** Superficie del suelo: la del contorno si cierra, si no largo × ancho. */
    fun suelo(muros: List<MuroEntity>, largo: Double, ancho: Double): Double =
        Poligono.superficie(muros) ?: (largo * ancho)

    /** Paredes a pintar: lo que hay menos lo que se tira, descontando puertas y ventanas. */
    fun paredes(
        muros: List<MuroEntity>,
        huecos: List<HuecoEntity>,
        largo: Double,
        ancho: Double,
        altura: Double
    ): Double {
        val bruto = if (muros.isNotEmpty()) {
            muros.filter { it.estado != "tirado" }.sumOf { Poligono.longitud(it) * altura }
        } else {
            (Poligono.perimetro(muros) ?: (2 * (largo + ancho))) * altura
        }
        return (bruto - huecos.sumOf { it.ancho * it.alto }).coerceAtLeast(0.0)
    }

    fun partidas(
        muros: List<MuroEntity>,
        huecos: List<HuecoEntity>,
        superficie: Double,
        paredes: Double,
        tarifas: Tarifas,
        altura: Double = 2.5
    ): List<Partida> {
        val salida = mutableListOf<Partida>()
        val alto = if (altura > 0.0) altura else 2.5

        // Va por m² de tabique, no por metros lineales: se cobra la pared entera.
        val tirados = muros.filter { it.estado == "tirado" }.sumOf { Poligono.longitud(it) * alto }
        if (tirados > 0.05) {
            salida.add(
                Partida(
                    concepto = "Demolición de tabique",
                    cantidad = tirados,
                    unidad = "m²",
                    precio = tarifas.demolicion
                )
            )
        }

        val nuevos = muros.filter { it.estado == "nuevo" }.sumOf { Poligono.longitud(it) * alto }
        if (nuevos > 0.05) {
            salida.add(
                Partida(
                    concepto = "Tabique nuevo de ladrillo",
                    cantidad = nuevos,
                    unidad = "m²",
                    precio = tarifas.tabiqueNuevo
                )
            )
        }

        val puertas = huecos.count { it.tipo == "puerta" }
        if (puertas > 0) {
            salida.add(
                Partida(
                    concepto = "Puerta con marco y tapajuntas",
                    cantidad = puertas.toDouble(),
                    unidad = "ud",
                    precio = tarifas.puerta
                )
            )
        }

        val ventanas = huecos.count { it.tipo == "ventana" }
        if (ventanas > 0) {
            salida.add(
                Partida(
                    concepto = "Ventana con marco",
                    cantidad = ventanas.toDouble(),
                    unidad = "ud",
                    precio = tarifas.ventana
                )
            )
        }

        if (superficie > 0.5) {
            salida.add(
                Partida(
                    concepto = "Suelo nuevo",
                    cantidad = superficie,
                    unidad = "m²",
                    precio = tarifas.suelo
                )
            )
        }

        if (paredes > 0.5) {
            salida.add(
                Partida(
                    concepto = "Pintura de paredes (2 manos)",
                    cantidad = paredes,
                    unidad = "m²",
                    precio = tarifas.pintura
                )
            )
        }

        return salida
    }

    fun base(partidas: List<Partida>): Double = partidas.sumOf { it.importe }

    fun iva(base: Double, tarifas: Tarifas): Double = base * tarifas.iva / 100.0

    fun total(base: Double, tarifas: Tarifas): Double = base + iva(base, tarifas)

    fun euros(valor: Double): String =
        String.format(Locale.forLanguageTag("es-ES"), "%,.2f", valor) + " €"

    fun cantidad(valor: Double): String =
        String.format(Locale.forLanguageTag("es-ES"), "%,.2f", valor)
}
