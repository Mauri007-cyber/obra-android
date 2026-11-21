package es.obra.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import es.obra.app.ar.ArDisponible
import es.obra.app.ar.EstadoAr
import es.obra.app.datos.ObraDao
import es.obra.app.dominio.Geometria
import es.obra.app.dominio.Medidas
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * Medición en AR con retícula central.
 *
 * Cómo se hace que el número sea creíble:
 *  - no se usa un fotograma suelto: se guardan las últimas 12 lecturas y se toma la mediana,
 *    que es lo que quita el temblor del hit-test;
 *  - si las lecturas bailan, se avisa y se puede repetir;
 *  - al terminar la primera medida se pregunta "¿son 3,42 m?"; si no, el usuario escribe la
 *    medida real y todo se corrige con un factor guardado en la obra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEscanear(
    obraId: Long,
    dao: ObraDao,
    alVolver: () -> Unit,
    alTerminar: () -> Unit
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    var permiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> permiso = concedido }

    var estadoAr by remember { mutableStateOf<EstadoAr?>(null) }
    var factor by remember { mutableStateOf(1.0) }

    LaunchedEffect(Unit) {
        if (!permiso) pedirPermiso.launch(Manifest.permission.CAMERA)
        estadoAr = withContext(Dispatchers.IO) { ArDisponible.estado(contexto) }
        factor = withContext(Dispatchers.IO) { dao.obtenerObra(obraId)?.factorCalibracion ?: 1.0 }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Medir con la cámara") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Tinta.papel,
                    titleContentColor = Tinta.tinta,
                    navigationIconContentColor = Tinta.tinta
                )
            )
        }
    ) { relleno ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            when {
                estadoAr == null -> Aviso("Preparando la cámara…")

                estadoAr == EstadoAr.NO_SOPORTADO -> Aviso(
                    "Este móvil no puede medir con la cámara. Escribe las medidas a mano: el resultado es igual de bueno."
                )

                estadoAr == EstadoAr.HAY_QUE_INSTALAR -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Falta «Google Play Services for AR»",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Es gratis y lo instala el Play Store en un minuto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = {
                        runCatching {
                            contexto.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.core")
                                )
                            )
                        }
                    }) { Text("Abrir el Play Store") }
                }

                !permiso -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Necesito la cámara para medir",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { pedirPermiso.launch(Manifest.permission.CAMERA) }) {
                        Text("Dar permiso")
                    }
                }

                else -> VistaAr(
                    factorInicial = factor,
                    alAplicar = { largo, ancho, nuevoFactor ->
                        alcance.launch {
                            dao.obtenerObra(obraId)?.let { obra ->
                                dao.actualizarObra(
                                    obra.copy(
                                        largo = largo,
                                        ancho = if (ancho > 0.0) ancho else obra.ancho,
                                        metodo = "arcore",
                                        factorCalibracion = nuevoFactor
                                    )
                                )
                            }
                            alTerminar()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

@Composable
private fun VistaAr(factorInicial: Double, alAplicar: (Double, Double, Double) -> Unit) {
    var tamano by remember { mutableStateOf(IntSize.Zero) }
    var haySuperficie by remember { mutableStateOf(false) }
    var inestable by remember { mutableStateOf(false) }

    val muestras = remember { ArrayDeque<FloatArray>() }
    var primerPunto by remember { mutableStateOf<FloatArray?>(null) }
    var medidas by remember { mutableStateOf(listOf<Double>()) }

    var factor by remember { mutableStateOf(factorInicial) }
    var preguntando by remember { mutableStateOf(false) }
    var corrigiendo by remember { mutableStateOf(false) }
    var textoReal by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { tamano = it },
            planeRenderer = true,
            onSessionUpdated = { _, frame ->
                val punto = puntoBajoReticula(frame, tamano)
                if (punto == null) {
                    if (haySuperficie) haySuperficie = false
                    if (muestras.isNotEmpty()) muestras.clear()
                    if (inestable) inestable = false
                } else {
                    if (!haySuperficie) haySuperficie = true
                    muestras.addLast(punto)
                    while (muestras.size > MUESTRAS) muestras.removeFirst()
                    val temblor = inestabilidad(muestras.toList()) > 0.06
                    if (temblor != inestable) inestable = temblor
                }
            }
        ) {
            // La retícula y los textos van en Compose, encima del vídeo de la cámara.
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .border(
                    width = 2.dp,
                    color = when {
                        !haySuperficie -> Color.White.copy(alpha = 0.55f)
                        inestable -> Tinta.rojo
                        else -> Tinta.obra
                    },
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.66f))
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                when {
                    !haySuperficie -> "Apunta al suelo y espera a que se ponga amarillo"
                    inestable -> "Muévete más despacio y vuelve a intentarlo"
                    primerPunto == null && medidas.isEmpty() -> "Apunta a una punta de la pared"
                    primerPunto == null -> "Apunta al otro extremo"
                    else -> "Apunta al segundo extremo y marca"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )

            if (medidas.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                medidas.forEachIndexed { indice, bruta ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${if (indice == 0) "Largo" else "Ancho"}: " +
                                "${Geometria.formatear(bruta * factor, 2)} m",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = {
                            medidas = medidas.filterIndexed { i, _ -> i != indice }
                            preguntando = false
                            corrigiendo = false
                        }) {
                            Text("Quitar", color = Tinta.obra)
                        }
                    }
                }
            }

            if (factor != 1.0) {
                Text(
                    "Corrección aplicada ×${Geometria.formatear(factor, 3)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tinta.obra
                )
            }

            if (preguntando && medidas.size == 1 && !corrigiendo) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color.White,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "¿Son ${Geometria.formatear(medidas[0] * factor, 2)} m de verdad?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Tinta.tinta
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { preguntando = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Sí") }
                            OutlinedButton(
                                onClick = {
                                    corrigiendo = true
                                    textoReal = Geometria.formatear(medidas[0] * factor, 2)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Corregir") }
                        }
                    }
                }
            }

            if (corrigiendo) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color.White,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "¿Cuánto mide de verdad?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Tinta.tinta
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = textoReal,
                            onValueChange = { nuevo ->
                                if (nuevo.all { it.isDigit() || it == ',' || it == '.' }) textoReal = nuevo
                            },
                            label = { Text("Metros") },
                            suffix = { Text("m") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val real = Geometria.parsear(textoReal)
                                    if (real > 0.0 && medidas.isNotEmpty()) {
                                        factor = Medidas.factorCalibracion(medidas[0], real)
                                    }
                                    corrigiendo = false
                                    preguntando = false
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Corregir todo") }
                            OutlinedButton(
                                onClick = { corrigiendo = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancelar") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Con este dato se corrigen todas las medidas de la cámara en esta obra.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tinta.gris
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val punto = medianaMuestras(muestras.toList()) ?: return@Button
                    val primero = primerPunto
                    if (primero == null) {
                        primerPunto = punto
                    } else {
                        val bruta = distancia(primero, punto)
                        if (bruta in 0.05..60.0) {
                            medidas = medidas + bruta
                            if (medidas.size == 1) preguntando = true
                        }
                        primerPunto = null
                    }
                },
                enabled = haySuperficie && !inestable && !corrigiendo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Text(
                    if (primerPunto == null) "Marcar punto" else "Cerrar la medida",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (medidas.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        alAplicar(
                            medidas.getOrElse(0) { 0.0 } * factor,
                            medidas.getOrElse(1) { 0.0 } * factor,
                            factor
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Usar estas medidas", color = Color.White)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Precisión aproximada ±3 %. No es un medidor certificado.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

/** Hit-test en el centro de la pantalla, prefiriendo plano real sobre punto de profundidad. */
private fun puntoBajoReticula(frame: Frame, tamano: IntSize): FloatArray? {
    if (tamano.width == 0 || tamano.height == 0) return null
    if (frame.camera.trackingState != TrackingState.TRACKING) return null

    val golpe = frame.hitTest(tamano.width / 2f, tamano.height / 2f).firstOrNull { resultado ->
        when (val rastreable = resultado.trackable) {
            is Plane -> rastreable.isPoseInPolygon(resultado.hitPose)
            is DepthPoint -> true
            else -> false
        }
    } ?: return null

    val traslacion = golpe.hitPose.translation
    return floatArrayOf(traslacion[0], traslacion[1], traslacion[2])
}

/** Mediana componente a componente: quita el temblor sin inventar precisión. */
private fun medianaMuestras(muestras: List<FloatArray>): FloatArray? {
    if (muestras.isEmpty()) return null
    fun componente(indice: Int): Float =
        Medidas.mediana(muestras.map { it[indice].toDouble() }).toFloat()
    return floatArrayOf(componente(0), componente(1), componente(2))
}

/** Cuánto se mueve la lectura entre fotogramas, en metros. */
private fun inestabilidad(muestras: List<FloatArray>): Double {
    if (muestras.size < 2) return 0.0
    val primera = muestras.first()
    return muestras.maxOf { distancia(primera, it) }
}

private fun distancia(a: FloatArray, b: FloatArray): Double {
    val dx = (a[0] - b[0]).toDouble()
    val dy = (a[1] - b[1]).toDouble()
    val dz = (a[2] - b[2]).toDouble()
    return hypot(hypot(dx, dy), dz)
}

private const val MUESTRAS = 12
