package es.obra.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import es.obra.app.dominio.Cifra
import es.obra.app.dominio.MedidaFoto
import es.obra.app.dominio.Punto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Medir el suelo con una foto y algo que se sepa cuánto mide.
 *
 * Pensado para no pedir nada raro: una foto de la habitación, dos toques en una puerta y decir
 * «80». No usa la cámara en vivo, no necesita ARCore y la foto no sale del móvil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMedirFoto(alVolver: () -> Unit, alAplicar: (Double) -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    var foto by remember { mutableStateOf<Bitmap?>(null) }
    var tamano by remember { mutableStateOf(IntSize.Zero) }
    var referencia by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var poligono by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var metros by remember { mutableStateOf("") }
    var paso by remember { mutableStateOf(Paso.REFERENCIA) }
    var superficie by remember { mutableStateOf(0.0) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val selector = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val cargada = withContext(Dispatchers.IO) { cargar(contexto, uri) }
            if (cargada == null) {
                aviso = "No se pudo abrir esa foto. Prueba con otra."
            } else {
                foto = cargada
                referencia = emptyList()
                poligono = emptyList()
                superficie = 0.0
                paso = Paso.REFERENCIA
                aviso = null
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Medir con una foto") },
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val actual = foto
            if (actual == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Haz una foto del suelo desde arriba, con las cuatro esquinas dentro.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Después tocas una puerta o una baldosa, dices cuánto mide, y marcas el suelo. " +
                        "La foto no se envía a ningún sitio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        selector.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Elegir foto") }
            } else {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(actual.width.toFloat() / actual.height.toFloat())
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { tamano = it }
                            .pointerInput(actual, paso, tamano) {
                                detectTapGestures { toque ->
                                    val escala = if (tamano.width > 0) {
                                        tamano.width / actual.width.toFloat()
                                    } else 0f
                                    if (escala <= 0f) return@detectTapGestures
                                    val punto = Offset(
                                        (toque.x / escala).coerceIn(0f, actual.width.toFloat()),
                                        (toque.y / escala).coerceIn(0f, actual.height.toFloat())
                                    )
                                    when (paso) {
                                        Paso.REFERENCIA -> if (referencia.size < 2) {
                                            referencia = referencia + punto
                                        }
                                        Paso.SUELO -> if (poligono.size < 12) {
                                            poligono = poligono + punto
                                        }
                                        Paso.LISTO -> Unit
                                    }
                                }
                            }
                    ) {
                        drawImage(
                            actual.asImageBitmap(),
                            dstSize = androidx.compose.ui.unit.IntSize(
                                size.width.toInt(),
                                size.height.toInt()
                            )
                        )
                        val escala = size.width / actual.width.toFloat()

                        // Polígono del suelo.
                        if (poligono.size >= 2) {
                            val puntos = poligono.map { Offset(it.x * escala, it.y * escala) }
                            for (i in puntos.indices) {
                                val a = puntos[i]
                                val b = puntos[(i + 1) % puntos.size]
                                if (poligono.size >= 3 || i == 0) {
                                    drawLine(
                                        Tinta.verde.copy(alpha = 0.9f),
                                        a,
                                        b,
                                        strokeWidth = 4f
                                    )
                                }
                            }
                            if (poligono.size >= 3) {
                                val camino = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(puntos[0].x, puntos[0].y)
                                    puntos.drop(1).forEach { lineTo(it.x, it.y) }
                                    close()
                                }
                                drawPath(camino, Tinta.verde.copy(alpha = 0.22f))
                            }
                            puntos.forEach {
                                drawCircle(Color.White, radius = 8f, center = it)
                                drawCircle(Tinta.verde, radius = 5f, center = it)
                            }
                        }

                        // Referencia conocida.
                        referencia.forEachIndexed { indice, punto ->
                            val centro = Offset(punto.x * escala, punto.y * escala)
                            drawCircle(Color.White, radius = 9f, center = centro)
                            drawCircle(Color(0xFF1E88E5), radius = 6f, center = centro)
                            if (indice > 0) {
                                val anterior = referencia[0]
                                drawLine(
                                    Color(0xFF1E88E5),
                                    Offset(anterior.x * escala, anterior.y * escala),
                                    centro,
                                    strokeWidth = 5f
                                )
                            }
                        }
                        drawRect(
                            Color.Black.copy(alpha = 0.08f),
                            style = Stroke(width = 1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        selector.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cambiar foto") }

                Spacer(Modifier.height(16.dp))

                when (paso) {
                    Paso.REFERENCIA -> {
                        Text(
                            "1. Toca los dos extremos de algo que sepas cuánto mide.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Por ejemplo, el ancho de una puerta o el lado de una baldosa. " +
                                "Lleva ${referencia.size} de 2 toques.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (referencia.size == 2) {
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                value = metros,
                                onValueChange = { metros = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                                label = { Text("¿Cuánto mide? (metros)") },
                                placeholder = { Text("0,80") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("0,80" to "Puerta", "0,60" to "Baldosa", "1,60" to "Puerta doble")
                                    .forEach { (valor, etiqueta) ->
                                        FilterChip(
                                            selected = metros == valor,
                                            onClick = { metros = valor },
                                            label = { Text(etiqueta) }
                                        )
                                    }
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    val valor = metros.replace(",", ".").toDoubleOrNull() ?: 0.0
                                    if (valor <= 0.0) {
                                        aviso = "Escribe cuánto mide, en metros. Por ejemplo 0,80."
                                    } else {
                                        aviso = null
                                        paso = Paso.SUELO
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) { Text("Seguir: marcar el suelo") }
                        }
                    }

                    Paso.SUELO -> {
                        Text(
                            "2. Toca las esquinas del suelo, en orden.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Con tres esquinas ya se puede calcular; cuatro es lo normal. " +
                                "Llevas ${poligono.size}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (poligono.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    poligono = poligono.dropLast(1)
                                    aviso = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Quitar el último toque") }
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val valor = metros.replace(",", ".").toDoubleOrNull() ?: 0.0
                                val a = referencia.getOrNull(0)
                                val b = referencia.getOrNull(1)
                                if (a == null || b == null || poligono.size < 3) {
                                    aviso = "Marca al menos tres esquinas del suelo."
                                } else {
                                    val calculada = MedidaFoto.metrosPorPixel(
                                        Punto(a.x.toDouble(), a.y.toDouble()),
                                        Punto(b.x.toDouble(), b.y.toDouble()),
                                        valor
                                    )?.let { escala ->
                                        MedidaFoto.area(
                                            poligono.map {
                                                Punto(it.x.toDouble(), it.y.toDouble())
                                            },
                                            escala
                                        )
                                    } ?: 0.0
                                    if (calculada <= 0.0) {
                                        aviso = "No se pudo calcular. Repite la referencia y las esquinas."
                                    } else {
                                        // Redondeo a dos decimales: 12,34 m² se lee mejor que 12,337891.
                                        superficie = kotlin.math.round(calculada * 100.0) / 100.0
                                        aviso = null
                                        paso = Paso.LISTO
                                    }
                                }
                            },
                            enabled = poligono.size >= 3,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("Calcular superficie") }
                    }

                    Paso.LISTO -> {
                        Text(
                            "Superficie del suelo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${Cifra.numero(superficie, 2)} m²",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Si no cuadra con lo que sabes, vuelve a marcar las esquinas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { alAplicar(superficie) },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("Usar esta superficie") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                poligono = emptyList()
                                paso = Paso.SUELO
                                aviso = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Volver a marcar el suelo") }
                    }
                }
            }

            aviso?.let { texto ->
                Spacer(Modifier.height(12.dp))
                Text(
                    texto,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private enum class Paso { REFERENCIA, SUELO, LISTO }

/** Se reduce la foto: solo se necesitan los píxeles para tocar esquinas, no la calidad completa. */
private fun cargar(contexto: Context, uri: Uri): Bitmap? = runCatching {
    val soloTamano = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contexto.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, soloTamano)
    }
    var muestra = 1
    while (soloTamano.outWidth / muestra > 1600 || soloTamano.outHeight / muestra > 1600) {
        muestra *= 2
    }
    val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
    contexto.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opciones)
    }
}.getOrNull()
