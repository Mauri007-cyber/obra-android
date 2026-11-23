package es.obra.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.obra.app.datos.Exportador
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ProyectoRepositorio
import es.obra.app.datos.rutaFoto
import es.obra.app.dominio.Cambio
import es.obra.app.dominio.EstanciaProyecto
import es.obra.app.dominio.FraseACambios
import es.obra.app.render.RetoqueFoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Enseñar la foto de la estancia con los materiales aprobados, sin gastar nada.
 *
 * Dos formas de decir lo que se quiere, las dos en el propio móvil: escribir la frase normal
 * («suelo de tarima clara y paredes blancas») o elegir los cambios en el proyecto. La frase se
 * entiende con reglas del catálogo, no con un servicio; el retoque se pinta en el teléfono.
 *
 * Nada sale del móvil: ni la foto, ni la frase, ni una clave. Y no se inventan muebles: se
 * tiñe lo que ya está en la foto con el material que se ha aprobado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRetoque(
    obraId: Long,
    estanciaId: Long,
    dao: ObraDao,
    alVolver: () -> Unit
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    var estancia by remember { mutableStateOf<EstanciaProyecto?>(null) }
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    var resultado by remember { mutableStateOf<Bitmap?>(null) }
    var verAntes by remember { mutableStateOf(false) }

    var frase by remember { mutableStateOf("") }
    var entendidos by remember { mutableStateOf<List<Cambio>>(emptyList()) }
    var aviso by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    var refresco by remember { mutableIntStateOf(0) }

    LaunchedEffect(obraId, estanciaId, refresco) {
        val obra = dao.obtenerObra(obraId)
        val proyecto = obra?.let { withContext(Dispatchers.IO) { ProyectoRepositorio.cargar(dao, it) } }
        val cargada = proyecto?.estancia(estanciaId)
        estancia = cargada
        resultado = null
        foto = withContext(Dispatchers.IO) {
            cargada?.foto
                ?.takeIf { it.isNotBlank() }
                ?.let { rutaFoto(contexto.filesDir, it) }
                ?.let { cargarFoto(it) }
        }
    }

    val aprobados = estancia?.visibles ?: emptyList()
    val aplicables = aprobados + entendidos.filter { nuevo -> aprobados.none { it.tipo == nuevo.tipo } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Retoque de la foto") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Gratis, en tu móvil y sin conexión. Se tiñe la foto con los materiales " +
                        "aprobados; no se inventan muebles ni se sube ninguna foto a internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }

            val visible = if (verAntes) foto else (resultado ?: foto)
            if (visible == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Esta estancia todavía no tiene foto. Ponla desde el proyecto.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                Image(
                    bitmap = visible.asImageBitmap(),
                    contentDescription = if (verAntes) "Foto original" else "Foto con los materiales",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(visible.width.toFloat() / visible.height.toFloat())
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )

                if (resultado != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { verAntes = true },
                            enabled = !verAntes,
                            modifier = Modifier.weight(1f)
                        ) { Text("Antes") }
                        OutlinedButton(
                            onClick = { verAntes = false },
                            enabled = verAntes,
                            modifier = Modifier.weight(1f)
                        ) { Text("Después") }
                    }
                }
            }

            Text(
                "Dilo con tus palabras",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = frase,
                onValueChange = {
                    frase = it
                    entendidos = emptyList()
                    aviso = ""
                },
                label = { Text("Qué quieres cambiar") },
                placeholder = { Text("Suelo de tarima clara y paredes blancas") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val actual = estancia
                        if (actual == null) {
                            aviso = "La estancia todavía no está cargada."
                            return@OutlinedButton
                        }
                        val cambios = FraseACambios.interpretar(
                            frase = frase,
                            superficie = actual.superficie,
                            altura = actual.altura
                        )
                        entendidos = cambios
                        aviso = if (cambios.isEmpty()) {
                            "No he reconocido ningún trabajo en esa frase. Prueba con «suelo de " +
                                "tarima», «pintar paredes» o «tres enchufes»."
                        } else {
                            ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Entender")
                }

                Button(
                    onClick = {
                        val original = foto
                        if (original == null) {
                            aviso = "Primero pon una foto en la estancia."
                            return@Button
                        }
                        if (aplicables.isEmpty()) {
                            aviso = "No hay nada aprobado que enseñar todavía."
                            return@Button
                        }
                        alcance.launch {
                            trabajando = true
                            aviso = ""
                            val retocada = withContext(Dispatchers.Default) {
                                RetoqueFoto.aplicar(original, aplicables)
                            }
                            resultado = retocada
                            verAntes = false
                            trabajando = false
                            aviso = "Listo. Es lo aprobado pintado sobre tu foto, no una imagen inventada."
                        }
                    },
                    enabled = !trabajando && foto != null && aplicables.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tinta.tinta,
                        contentColor = Tinta.papel
                    )
                ) {
                    if (trabajando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Tinta.papel
                        )
                    } else {
                        Text("Aplicar en la foto")
                    }
                }
            }

            if (entendidos.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "He entendido ${entendidos.size} trabajo(s)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        entendidos.forEach { cambio ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Color(RetoqueFoto.color(cambio))
                                        )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(cambio.linea, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        TextButton(
                            onClick = {
                                alcance.launch {
                                    entendidos.forEach { cambio ->
                                        ProyectoRepositorio.guardar(dao, estanciaId, cambio)
                                    }
                                    entendidos = emptyList()
                                    frase = ""
                                    aviso = "Añadido a la estancia, como propuesta. Apruébalo en el proyecto."
                                    refresco++
                                }
                            }
                        ) { Text("Añadir estos cambios a la estancia") }
                    }
                }
            }

            if (aprobados.isNotEmpty()) {
                Text(
                    "Lo aprobado que se ve en la foto",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                aprobados.forEach { cambio ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(androidx.compose.ui.graphics.Color(RetoqueFoto.color(cambio)))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(cambio.linea, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (aviso.isNotBlank()) {
                Text(
                    aviso,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (resultado != null) {
                HorizontalDivider()
                Button(
                    onClick = {
                        val retocada = resultado ?: return@Button
                        alcance.launch {
                            val archivo = withContext(Dispatchers.IO) {
                                RetoqueFoto.guardar(contexto, retocada)
                            }
                            val compartido = Exportador.compartir(contexto, archivo, "image/png")
                            if (!compartido) aviso = "No se ha podido abrir el menú de compartir."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Compartir la foto")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Carga la foto ya reducida: una foto de 12 MP entera no cabe en memoria con holgura. */
private fun cargarFoto(archivo: File, ladoMax: Int = 2048): Bitmap? {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(archivo.path, limites)
    if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

    var muestra = 1
    while (limites.outWidth / (muestra * 2) >= ladoMax || limites.outHeight / (muestra * 2) >= ladoMax) {
        muestra *= 2
    }

    val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
    return BitmapFactory.decodeFile(archivo.path, opciones)
}
