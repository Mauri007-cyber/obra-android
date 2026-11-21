package es.obra.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.obra.app.datos.EstanciaEntity
import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraDao
import es.obra.app.dominio.Geometria
import es.obra.app.dominio.Poligono
import es.obra.app.dominio.Punto
import es.obra.app.dominio.Sugerencias
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Editor de plano 2D.
 *
 * Un solo gesto para todo, para que no haya modos que recordar:
 *  - tocar el suelo dibuja un muro (rejilla de 5 cm e imán a 45°);
 *  - tocar un muro lo selecciona y ofrece lo que se hace en obra: puerta, ventana, tirar,
 *    levantar o borrar.
 *
 * Cuando los muros cierran el contorno se calcula la superficie de verdad, no la del
 * rectángulo que los envuelve: una cocina con pasillo en L no mide lo mismo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPlano(
    obraId: Long,
    dao: ObraDao,
    alVolver: () -> Unit,
    alAplicar: (largo: Double, ancho: Double, superficie: Double) -> Unit
) {
    val alcance = rememberCoroutineScope()
    val medidorTexto = rememberTextMeasurer()

    var estanciaId by remember { mutableStateOf<Long?>(null) }
    var muros by remember { mutableStateOf<List<MuroEntity>>(emptyList()) }
    var huecos by remember { mutableStateOf<List<HuecoEntity>>(emptyList()) }
    var seleccionado by remember { mutableStateOf<MuroEntity?>(null) }
    var inicio by remember { mutableStateOf<Offset?>(null) }
    var escala by remember { mutableStateOf(90f) }
    var desplazamiento by remember { mutableStateOf(Offset.Zero) }
    var tamano by remember { mutableStateOf(IntSize.Zero) }
    var cargando by remember { mutableStateOf(true) }

    LaunchedEffect(obraId) {
        val estancias = dao.estancias(obraId)
        val id = estancias.firstOrNull()?.id
            ?: dao.insertarEstancia(EstanciaEntity(obraId = obraId, nombre = "Estancia"))
        estanciaId = id
        muros = dao.muros(id)
        huecos = dao.huecosDeEstancia(id)
        cargando = false
    }

    val contorno = remember(muros) { Poligono.cerrado(muros) }
    val superficie = remember(contorno) { contorno?.let { Geometria.area(it) } }
    val metrosLineales = muros.sumOf { Poligono.longitud(it) }

    fun aPantalla(p: Offset): Offset {
        val origenX = tamano.width / 2f + desplazamiento.x
        val origenY = tamano.height / 2f + desplazamiento.y
        return Offset(origenX + p.x * escala, origenY + p.y * escala)
    }

    fun aMundo(p: Offset): Offset {
        val origenX = tamano.width / 2f + desplazamiento.x
        val origenY = tamano.height / 2f + desplazamiento.y
        return Offset((p.x - origenX) / escala, (p.y - origenY) / escala)
    }

    fun anadirMuro(desde: Offset, hasta: Offset) {
        val id = estanciaId ?: return
        val nuevo = MuroEntity(
            estanciaId = id,
            x1 = desde.x.toDouble(),
            y1 = desde.y.toDouble(),
            x2 = hasta.x.toDouble(),
            y2 = hasta.y.toDouble()
        )
        alcance.launch {
            val nuevoId = dao.insertarMuro(nuevo)
            muros = muros + nuevo.copy(id = nuevoId)
        }
    }

    fun cambiarEstado(muro: MuroEntity, estado: String) {
        alcance.launch {
            val actualizado = muro.copy(estado = estado)
            dao.actualizarMuro(actualizado)
            muros = muros.map { if (it.id == muro.id) actualizado else it }
            seleccionado = actualizado
        }
    }

    fun anadirHueco(muro: MuroEntity, tipo: String) {
        alcance.launch {
            val nuevo = HuecoEntity(
                muroId = muro.id,
                posicion = 0.5,
                ancho = if (tipo == "puerta") 0.8 else 1.2,
                alto = if (tipo == "puerta") 2.05 else 1.2,
                tipo = tipo
            )
            val id = dao.insertarHueco(nuevo)
            huecos = huecos + nuevo.copy(id = id)
        }
    }

    fun anadirHuecosTipicos(muro: MuroEntity) {
        alcance.launch {
            val nuevos = Sugerencias.tipicos(muro).map { it.copy(id = dao.insertarHueco(it)) }
            huecos = huecos + nuevos
        }
    }

    fun quitarHuecos(muro: MuroEntity) {
        alcance.launch {
            huecos.filter { it.muroId == muro.id }.forEach { dao.borrarHueco(it.id) }
            huecos = huecos.filterNot { it.muroId == muro.id }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Plano") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    Column(
                        modifier = Modifier.padding(end = 16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (superficie != null) {
                            Text(
                                "${Geometria.formatear(superficie, 2)} m²",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            "${Geometria.formatear(metrosLineales, 2)} m de muro",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFFBF9F5))
                    .onSizeChanged { tamano = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            desplazamiento += pan
                            escala = (escala * zoom).coerceIn(30f, 700f)
                        }
                    }
                    .pointerInput(muros) {
                        detectTapGestures { punto ->
                            val mundo = imanRejilla(aMundo(punto))
                            val tocado = muroMasCercano(mundo, muros)
                            if (tocado != null) {
                                seleccionado = tocado
                                inicio = null
                                return@detectTapGestures
                            }
                            seleccionado = null
                            val ancla = inicio
                            if (ancla == null) {
                                inicio = mundo
                            } else {
                                val fin = imanAngulo(ancla, mundo)
                                if ((fin - ancla).getDistance() >= 0.15f) {
                                    anadirMuro(ancla, fin)
                                }
                                inicio = null
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val origenX = size.width / 2f + desplazamiento.x
                    val origenY = size.height / 2f + desplazamiento.y

                    // Rejilla: 10 cm fina, 1 m marcada.
                    val pasoFino = 0.1f * escala
                    if (pasoFino > 6f) {
                        var x = origenX % pasoFino
                        while (x < size.width) {
                            if (x >= 0f) {
                                drawLine(
                                    color = Color(0xFFE7E2D8),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f
                                )
                            }
                            x += pasoFino
                        }
                        var y = origenY % pasoFino
                        while (y < size.height) {
                            if (y >= 0f) {
                                drawLine(
                                    color = Color(0xFFE7E2D8),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }
                            y += pasoFino
                        }
                    }
                    var xm = origenX % escala
                    while (xm < size.width) {
                        drawLine(
                            color = Color(0xFFD6D1C7),
                            start = Offset(xm, 0f),
                            end = Offset(xm, size.height),
                            strokeWidth = if (abs(xm - origenX) < 1f) 2.5f else 1.2f
                        )
                        xm += escala
                    }
                    var ym = origenY % escala
                    while (ym < size.height) {
                        drawLine(
                            color = Color(0xFFD6D1C7),
                            start = Offset(0f, ym),
                            end = Offset(size.width, ym),
                            strokeWidth = if (abs(ym - origenY) < 1f) 2.5f else 1.2f
                        )
                        ym += escala
                    }

                    // Muros, con el hueco recortado donde hay puerta o ventana.
                    muros.forEach { muro ->
                        val a = aPantalla(Offset(muro.x1.toFloat(), muro.y1.toFloat()))
                        val b = aPantalla(Offset(muro.x2.toFloat(), muro.y2.toFloat()))
                        val esSeleccionado = seleccionado?.id == muro.id
                        val color = when {
                            esSeleccionado -> Tinta.obra
                            muro.estado == "nuevo" -> Tinta.verde
                            muro.estado == "tirado" -> Tinta.rojo
                            else -> Tinta.tinta
                        }
                        val grosor = (muro.grosor.toFloat() * escala).coerceIn(4f, 26f)

                        val longitud = Poligono.longitud(muro).toFloat()
                        val ux = if (longitud > 0f) (b.x - a.x) / (longitud * escala) else 0f
                        val uy = if (longitud > 0f) (b.y - a.y) / (longitud * escala) else 0f

                        val tramos = huecos.filter { it.muroId == muro.id }
                        if (tramos.isEmpty()) {
                            drawLine(
                                color = color,
                                start = a,
                                end = b,
                                strokeWidth = grosor,
                                pathEffect = if (muro.estado == "tirado") {
                                    PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                                } else null
                            )
                        } else {
                            var cursor = 0f
                            tramos.sortedBy { it.posicion }.forEach { hueco ->
                                val largo = longitud.coerceAtLeast(0.01f).toDouble()
                                val desde = (hueco.posicion - (hueco.ancho / 2.0) / largo)
                                    .toFloat().coerceIn(0f, 1f)
                                val hasta = (hueco.posicion + (hueco.ancho / 2.0) / largo)
                                    .toFloat().coerceIn(0f, 1f)
                                if (desde > cursor) {
                                    drawLine(
                                        color = color,
                                        start = a + Offset(ux * escala * cursor * longitud, uy * escala * cursor * longitud),
                                        end = a + Offset(ux * escala * desde * longitud, uy * escala * desde * longitud),
                                        strokeWidth = grosor
                                    )
                                }
                                cursor = hasta
                            }
                            if (cursor < 1f) {
                                drawLine(
                                    color = color,
                                    start = a + Offset(ux * escala * cursor * longitud, uy * escala * cursor * longitud),
                                    end = b,
                                    strokeWidth = grosor
                                )
                            }
                        }

                        drawCircle(color = Tinta.obra, radius = if (esSeleccionado) 6f else 4.5f, center = a)
                        drawCircle(color = Tinta.obra, radius = if (esSeleccionado) 6f else 4.5f, center = b)

                        val etiqueta = medidorTexto.measure(
                            text = "${Geometria.formatear(Poligono.longitud(muro).toDouble(), 2)} m",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = Tinta.gris,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        val medio = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                        val caja = Offset(
                            medio.x - etiqueta.size.width / 2f,
                            medio.y - etiqueta.size.height - 10f
                        )
                        drawRect(
                            color = Color(0xFFFBF9F5),
                            topLeft = caja - Offset(3f, 2f),
                            size = Size(etiqueta.size.width + 6f, etiqueta.size.height + 4f)
                        )
                        drawText(textLayoutResult = etiqueta, topLeft = caja)
                    }

                    inicio?.let { punto ->
                        val p = aPantalla(punto)
                        drawCircle(
                            color = Tinta.obra,
                            radius = 10f,
                            center = p,
                            style = Stroke(width = 3f)
                        )
                    }
                }

                if (!cargando && muros.isEmpty() && inicio == null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Toca un punto y luego otro", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cada toque dibuja un muro. Cuando el contorno cierra, sale la superficie.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { escala = (escala * 1.25f).coerceAtMost(700f) }) {
                        Text("+", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(onClick = { escala = (escala / 1.25f).coerceAtLeast(30f) }) {
                        Text("−", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(onClick = { desplazamiento = Offset.Zero }) {
                        Text("Centrar", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val muro = seleccionado
            if (muro != null) {
                PanelMuro(
                    muro = muro,
                    tieneHuecos = huecos.any { it.muroId == muro.id },
                    alTipicos = { anadirHuecosTipicos(muro) },
                    alPuerta = { anadirHueco(muro, "puerta") },
                    alVentana = { anadirHueco(muro, "ventana") },
                    alTirar = {
                        cambiarEstado(muro, if (muro.estado == "tirado") "original" else "tirado")
                    },
                    alLevantar = {
                        cambiarEstado(muro, if (muro.estado == "nuevo") "original" else "nuevo")
                    },
                    alQuitarHuecos = { quitarHuecos(muro) },
                    alBorrar = {
                        alcance.launch {
                            dao.borrarMuro(muro.id)
                            muros = muros.filterNot { it.id == muro.id }
                            huecos = huecos.filterNot { it.muroId == muro.id }
                            seleccionado = null
                        }
                    },
                    alCerrar = { seleccionado = null }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val ultimo = muros.lastOrNull() ?: return@OutlinedButton
                                alcance.launch {
                                    dao.borrarMuro(ultimo.id)
                                    muros = muros.dropLast(1)
                                }
                            },
                            enabled = muros.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) { Text("Deshacer") }

                        OutlinedButton(
                            onClick = { inicio = null },
                            enabled = inicio != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) { Text("Cancelar") }
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val puntos = muros.flatMap {
                                listOf(
                                    Offset(it.x1.toFloat(), it.y1.toFloat()),
                                    Offset(it.x2.toFloat(), it.y2.toFloat())
                                )
                            }
                            if (puntos.isEmpty()) return@Button
                            val rect = Rect(
                                left = puntos.minOf { it.x },
                                top = puntos.minOf { it.y },
                                right = puntos.maxOf { it.x },
                                bottom = puntos.maxOf { it.y }
                            )
                            val largo = (rect.right - rect.left).toDouble()
                            val ancho = (rect.bottom - rect.top).toDouble()
                            alAplicar(largo, ancho, superficie ?: (largo * ancho))
                        },
                        enabled = muros.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    ) {
                        Text("Usar estas medidas", style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (superficie != null) {
                            "El contorno cierra: la superficie es la real. Largo y ancho salen del rectángulo que lo envuelve."
                        } else {
                            "Falta cerrar el contorno: se usará el rectángulo que envuelve los muros."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelMuro(
    muro: MuroEntity,
    tieneHuecos: Boolean,
    alTipicos: () -> Unit,
    alPuerta: () -> Unit,
    alVentana: () -> Unit,
    alTirar: () -> Unit,
    alLevantar: () -> Unit,
    alQuitarHuecos: () -> Unit,
    alBorrar: () -> Unit,
    alCerrar: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Muro de ${Geometria.formatear(Poligono.longitud(muro), 2)} m",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = alCerrar) { Text("Cerrar") }
            }

            if (!tieneHuecos) {
                TextButton(onClick = alTipicos) {
                    Text("Poner puerta y ventana típicas")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = alPuerta, modifier = Modifier.weight(1f)) {
                    Text("Puerta")
                }
                OutlinedButton(onClick = alVentana, modifier = Modifier.weight(1f)) {
                    Text("Ventana")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = alTirar, modifier = Modifier.weight(1f)) {
                    Text(if (muro.estado == "tirado") "No tirar" else "Tirar")
                }
                OutlinedButton(onClick = alLevantar, modifier = Modifier.weight(1f)) {
                    Text(if (muro.estado == "nuevo") "Ya existía" else "Muro nuevo")
                }
            }

            if (tieneHuecos) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = alQuitarHuecos) {
                    Text("Quitar puertas y ventanas de este muro")
                }
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = alBorrar) {
                Text("Borrar el muro", color = Tinta.rojo)
            }
        }
    }
}

/** Rejilla de 5 cm. */
private fun imanRejilla(p: Offset): Offset {
    val paso = 0.05f
    return Offset(
        (p.x / paso).roundToInt() * paso,
        (p.y / paso).roundToInt() * paso
    )
}

/** Imán a múltiplos de 45° cuando el ángulo está a menos de 3°. */
private fun imanAngulo(ancla: Offset, p: Offset): Offset {
    val delta = p - ancla
    val largo = delta.getDistance()
    if (largo < 0.001f) return p

    val angulo = atan2(delta.y, delta.x)
    val paso = (PI / 4).toFloat()
    val ajustado = (angulo / paso).roundToInt() * paso
    val tolerancia = Math.toRadians(3.0).toFloat()

    return if (abs(angulo - ajustado) <= tolerancia) {
        ancla + Offset(cos(ajustado) * largo, sin(ajustado) * largo)
    } else {
        p
    }
}

/** Muro más cercano al punto tocado, con margen para el dedo. */
private fun muroMasCercano(mundo: Offset, muros: List<MuroEntity>): MuroEntity? {
    val punto = Punto(mundo.x.toDouble(), mundo.y.toDouble())
    var mejor: MuroEntity? = null
    var mejorDistancia = Double.MAX_VALUE

    muros.forEach { muro ->
        val d = Geometria.distanciaPuntoSegmento(
            punto,
            Punto(muro.x1, muro.y1),
            Punto(muro.x2, muro.y2)
        )
        val margen = muro.grosor / 2.0 + 0.12
        if (d <= margen && d < mejorDistancia) {
            mejor = muro
            mejorDistancia = d
        }
    }
    return mejor
}
