package es.obra.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.obra.app.datos.Exportador
import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ObraEntity
import es.obra.app.dominio.Poligono
import es.obra.app.dominio.ResumenReforma
import es.obra.app.render.Lamina
import es.obra.app.render.Modelo3D
import es.obra.app.render.Render3D
import es.obra.app.render.VideoOrbita
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo que se le enseña al cliente.
 *
 * La misma cámara para el antes y para el después, para que se vea que es la misma habitación.
 * Arrastrando con el dedo se gira; desde aquí se saca la lámina de WhatsApp y el A4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaVisual(obraId: Long, dao: ObraDao, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    var obra by remember { mutableStateOf<ObraEntity?>(null) }
    var muros by remember { mutableStateOf<List<MuroEntity>>(emptyList()) }
    var huecos by remember { mutableStateOf<List<HuecoEntity>>(emptyList()) }
    var despues by remember { mutableStateOf(true) }
    var giro by remember { mutableStateOf(0.85) }
    var inclinacion by remember { mutableStateOf(0.42) }
    var zoom by remember { mutableStateOf(1.0f) }
    var trabajando by remember { mutableStateOf(false) }
    var presentando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(obraId) {
        obra = dao.obtenerObra(obraId)
        val estancias = dao.estancias(obraId)
        // Una al lado de otra: el plano de cada habitación empieza en su esquina y, sin esto,
        // dos estancias se dibujan superpuestas.
        muros = Poligono.enFila(estancias.flatMap { dao.muros(it.id) })
        huecos = estancias.flatMap { dao.huecosDeEstancia(it.id) }
    }

    val escena = remember(obra, muros, huecos, despues) {
        obra?.let { Modelo3D.construir(it, muros, huecos, despues) }
    }

    fun vista(ancho: Int, alto: Int) =
        Render3D.Vista(ancho, alto, giro, inclinacion, zoom.toDouble())

    fun frases(actual: ObraEntity): List<String> =
        ResumenReforma.paraLamina(muros, huecos, actual.superficieEfectiva, actual.altura)

    fun exportar(cuadrada: Boolean) {
        val actual = obra ?: return
        if (trabajando) return
        trabajando = true
        aviso = null
        alcance.launch {
            val archivo = runCatching {
                withContext(Dispatchers.Default) {
                val antesEscena = Modelo3D.construir(actual, muros, huecos, despues = false)
                val despuesEscena = Modelo3D.construir(actual, muros, huecos, despues = true)
                val ancho = if (cuadrada) 1000 else 1400
                val alto = if (cuadrada) 700 else 1050
                val antes = Render3D.bitmap(antesEscena, vista(ancho, alto))
                val despuesBmp = Render3D.bitmap(despuesEscena, vista(ancho, alto))
                val lamina = if (cuadrada) {
                    Lamina.cuadrada(actual, antes, despuesBmp, frases(actual))
                } else {
                    Lamina.a4(actual, antes, despuesBmp, frases(actual))
                }
                val nombre = "obra-${actual.id}-${if (cuadrada) "whatsapp" else "a4"}.png"
                Lamina.guardar(contexto, lamina, nombre)
                }
            }.getOrNull()
            trabajando = false
            if (archivo == null) {
                aviso = "No se pudo crear la imagen. Cierra otras apps y vuelve a intentarlo."
            } else {
                Exportador.compartir(contexto, archivo, tipo = "image/png")
            }
        }
    }

    fun generarVideo() {
        val actual = obra ?: return
        if (trabajando) return
        trabajando = true
        aviso = null
        alcance.launch {
            val archivo = runCatching {
                withContext(Dispatchers.Default) {
                val escenaVideo = Modelo3D.construir(actual, muros, huecos, despues = true)
                val carpeta = File(contexto.filesDir, "export").apply { mkdirs() }
                VideoOrbita.generar(
                    escena = escenaVideo,
                    destino = File(carpeta, "obra-${actual.id}-video.gif")
                )
                }
            }.getOrNull()
            trabajando = false
            if (archivo == null) {
                aviso = "No se pudo crear el vídeo. Cierra otras apps y vuelve a intentarlo."
            } else {
                Exportador.compartir(contexto, archivo, tipo = "image/gif")
            }
        }
    }

    fun guardarVista() {
        val actual = obra ?: return
        if (trabajando) return
        trabajando = true
        aviso = null
        alcance.launch {
            val archivo = runCatching {
                withContext(Dispatchers.Default) {
                val escenaActual = Modelo3D.construir(actual, muros, huecos, despues)
                val mapa = Render3D.bitmap(escenaActual, vista(2048, 1536))
                Lamina.guardar(
                    contexto,
                    mapa,
                    "obra-${actual.id}-${if (despues) "despues" else "antes"}.png"
                )
                }
            }.getOrNull()
            trabajando = false
            if (archivo == null) {
                aviso = "No se pudo crear la imagen. Cierra otras apps y vuelve a intentarlo."
            } else {
                Exportador.compartir(contexto, archivo, tipo = "image/png")
            }
        }
    }

    val escenaEnseñable = escena
    if (presentando && escenaEnseñable != null && muros.isNotEmpty()) {
        Presentacion(
            escena = escenaEnseñable,
            despues = despues,
            giroInicial = giro,
            inclinacion = inclinacion,
            alTocar = { despues = !despues },
            alSalir = { presentando = false }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Enseñar al cliente") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (despues) {
                    OutlinedButton(
                        onClick = { despues = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("Antes") }
                    Button(
                        onClick = { despues = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("Después") }
                } else {
                    Button(
                        onClick = { despues = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("Antes") }
                    OutlinedButton(
                        onClick = { despues = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("Después") }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(Tinta.papelHundido)
                    .pointerInput(Unit) {
                        detectDragGestures { _, arrastre ->
                            giro -= arrastre.x / 300.0
                            inclinacion = (inclinacion - arrastre.y / 520.0)
                                .coerceIn(0.12, 1.25)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val actual = escena ?: return@Canvas
                    Render3D.dibujar(
                        this,
                        Render3D.proyectar(
                            actual,
                            Render3D.Vista(
                                size.width.toInt(),
                                size.height.toInt(),
                                giro,
                                inclinacion,
                                zoom.toDouble()
                            )
                        )
                    )
                }

                Text(
                    if (despues) "DESPUÉS" else "ANTES",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Tinta.papel)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Arrastra para girar",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { giro = 0.85; inclinacion = 0.42 }) { Text("Esquina") }
                TextButton(onClick = { giro = 0.05; inclinacion = 0.18 }) { Text("De frente") }
                TextButton(onClick = { inclinacion = 1.15 }) { Text("Desde arriba") }
                TextButton(onClick = { zoom = (zoom * 1.2f).coerceAtMost(2.5f) }) { Text("+") }
                TextButton(onClick = { zoom = (zoom / 1.2f).coerceAtLeast(0.6f) }) { Text("−") }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Button(
                    onClick = { exportar(cuadrada = true) },
                    enabled = !trabajando && obra != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text("Enviar lámina al cliente", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportar(cuadrada = false) },
                        enabled = !trabajando,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) { Text("A4 para imprimir") }
                    OutlinedButton(
                        onClick = { guardarVista() },
                        enabled = !trabajando,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) { Text("Guardar vista") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { generarVideo() },
                    enabled = !trabajando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(if (trabajando) "Preparando el vídeo…" else "Vídeo de 8 s (gira la estancia)")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { presentando = true },
                    enabled = escena != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Presentar al cliente", style = MaterialTheme.typography.labelLarge)
                }
                if (muros.isEmpty()) {
                    Text(
                        "Todavía no hay muros. Dibuja el plano y vuelve: sin muros no hay nada que enseñar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }
                aviso?.let { texto ->
                    Text(
                        texto,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Las dos imágenes son la misma habitación desde el mismo sitio: " +
                        "así el cliente ve qué cambia. Medidas tomadas en obra, no certificadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
