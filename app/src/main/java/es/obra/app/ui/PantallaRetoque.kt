package es.obra.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import es.obra.app.datos.Exportador
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ProyectoRepositorio
import es.obra.app.datos.rutaFoto
import es.obra.app.dominio.PromptEstancia
import es.obra.app.ia.AjustesIA
import es.obra.app.ia.ClienteIA
import es.obra.app.ia.CupoDiario
import es.obra.app.ia.PromptRetoque
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PAREDES = listOf("blanco roto", "verde salvia", "arena", "gris perla")
private val SUELOS = listOf("tarima de roble", "gres gris", "microcemento", "terrazo")
private val LUCES = listOf("foco en el techo", "aplique en la pared", "tira LED")

/**
 * El retoque con IA, tal como se diseñó para él: elegir con botones, mirar el resultado,
 * decir qué está mal con más botones y volver a pedir. Nada de escribir prompts.
 *
 * La clave del servicio no está aquí: la app solo conoce la dirección del proxy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRetoque(obraId: Long, estanciaId: Long? = null, dao: ObraDao, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val ajustes = remember { AjustesIA(contexto) }

    var url by remember { mutableStateOf(ajustes.direccion) }
    var editandoUrl by remember { mutableStateOf(!ajustes.configurada) }
    var cupo by remember { mutableStateOf(ajustes.cupoHoy()) }

    var foto by remember { mutableStateOf<ByteArray?>(null) }
    var vista by remember { mutableStateOf<Bitmap?>(null) }
    var resultado by remember { mutableStateOf<Bitmap?>(null) }
    var duracion by remember { mutableStateOf(0L) }

    var peticion by remember { mutableStateOf("") }
    var contextoEstancia by remember { mutableStateOf("") }
    var nombreEstancia by remember { mutableStateOf<String?>(null) }
    var pared by remember { mutableStateOf<String?>(null) }
    var suelo by remember { mutableStateOf<String?>(null) }
    var luces by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fallos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var nota by remember { mutableStateOf("") }

    var cargando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var ronda by remember { mutableStateOf(0) }
    var pidiendoPermiso by remember { mutableStateOf(false) }
    var permisoParaEnvio by remember { mutableStateOf(false) }
    var uriCamara by remember { mutableStateOf<Uri?>(null) }

    fun cargar(uri: Uri) {
        alcance.launch {
            cargando = true
            aviso = null
            val datos = withContext(Dispatchers.IO) { prepararFoto(contexto, uri) }
            val mapa = datos?.let {
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            cargando = false
            if (datos == null || mapa == null) {
                aviso = "No se ha podido abrir esa foto. Prueba con otra."
                return@launch
            }
            foto = datos
            vista = mapa
            resultado = null
            ronda = 0
            fallos = emptySet()
            nota = ""
        }
    }

    LaunchedEffect(obraId, estanciaId) {
        if (estanciaId != null) {
            val obra = dao.obtenerObra(obraId)
            val estancia = obra?.let { ProyectoRepositorio.cargar(dao, it).estancia(estanciaId) }
            if (estancia == null) {
                aviso = "No se ha encontrado la estancia."
            } else {
                nombreEstancia = estancia.nombre
                contextoEstancia = PromptEstancia.contextoEditable(estancia)
                val archivo = rutaFoto(contexto.filesDir, estancia.foto)
                if (archivo?.isFile == true) cargar(Uri.fromFile(archivo))
                else if (estancia.foto.isNotBlank()) aviso = "No se encuentra la foto guardada; elige otra."
            }
        }
    }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cargar(uri)
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { hecha ->
        val uri = uriCamara
        if (hecha && uri != null) cargar(uri) else aviso = "No se ha hecho la foto."
    }

    fun procesar() {
        val bytes = foto
        if (bytes == null) {
            aviso = "Primero haz o elige una foto."
            return
        }
        val solicitud = listOf(contextoEstancia.trim(), peticion.trim()).filter { it.isNotBlank() }.joinToString("\n")
        val prompt = PromptRetoque.componer(
            PromptRetoque.Eleccion(
                peticion = solicitud,
                pared = pared,
                suelo = suelo,
                luces = luces.toList(),
                fallos = fallos.toList(),
                nota = nota
            )
        )
        if (prompt.isEmpty()) {
            aviso = "Escribe qué quieres cambiar."
            return
        }
        if (!prompt.contains("Cambios pedidos: $solicitud")) {
            aviso = "La petición es demasiado larga. Acorta el contexto o las instrucciones antes de enviarla."
            return
        }
        val hoy = LocalDate.now().toString()
        val actual = cupo.deHoy(hoy)
        if (!actual.puede()) {
            aviso = "No te quedan imágenes hoy. El límite es ${actual.limite}."
            return
        }

        if (!permisoParaEnvio) {
            pidiendoPermiso = true
            return
        }
        permisoParaEnvio = false
        cargando = true
        aviso = null
        alcance.launch {
            val respuesta = withContext(Dispatchers.IO) {
                ClienteIA(ajustes.direccion).retoque(bytes, prompt)
            }
            cargando = false
            when (respuesta) {
                is ClienteIA.Resultado.Bien -> {
                    val mapa = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(respuesta.imagen, 0, respuesta.imagen.size)
                    }
                    if (mapa == null) {
                        aviso = "La imagen que ha vuelto no se puede abrir."
                    } else {
                        resultado = mapa
                        duracion = respuesta.ms
                        ronda += 1
                        actual.consumir(hoy)?.let {
                            cupo = it
                            ajustes.guardarCupo(it)
                        }
                        fallos = emptySet()
                        nota = ""
                    }
                }

                is ClienteIA.Resultado.Mal -> {
                    aviso = respuesta.mensaje
                    if (respuesta.cupo) {
                        cupo = cupo.copy(limite = cupo.limite, usadas = cupo.limite)
                        ajustes.guardarCupo(cupo)
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Retoque con IA") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    TextButton(onClick = { editandoUrl = !editandoUrl }) {
                        Text(if (editandoUrl) "Cerrar" else "Servicio")
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
            Spacer(Modifier.height(4.dp))
            Text(
                "Prepara una petición con tus palabras. El Worker incluido tiene la generación real desactivada por seguridad.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            if (editandoUrl) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Dirección del servicio") },
                    placeholder = { Text("https://obra-ia.tu-cuenta.workers.dev") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        ajustes.direccion = url
                        url = ajustes.direccion
                        editandoUrl = false
                        aviso = if (ajustes.configurada) "Servicio guardado." else "Falta la dirección."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar dirección") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Es la dirección del Worker que montas tú. La clave de Cloudflare se queda allí, no en el móvil.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    if (ajustes.configurada) "Dirección guardada (conexión no verificada)" else "Falta conectar el servicio",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (ajustes.configurada) Tinta.verde else Tinta.rojo
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))

            Text("1 · La foto", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            val actual = vista
            if (actual == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val carpeta = File(contexto.cacheDir, "fotos").apply { mkdirs() }
                            val archivo = File(carpeta, "obra-$obraId-${System.currentTimeMillis()}.jpg")
                            val destino = FileProvider.getUriForFile(
                                contexto,
                                "${contexto.packageName}.archivos",
                                archivo
                            )
                            uriCamara = destino
                            camara.launch(destino)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("Hacer foto") }
                    OutlinedButton(
                        onClick = {
                            galeria.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("De la galería") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "La foto tiene que ser de la habitación vacía, con la ventana y la puerta en el encuadre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Image(
                    bitmap = actual.asImageBitmap(),
                    contentDescription = "Foto de la habitación",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val carpeta = File(contexto.cacheDir, "fotos").apply { mkdirs() }
                        val archivo = File(carpeta, "obra-$obraId-${System.currentTimeMillis()}.jpg")
                        val destino = FileProvider.getUriForFile(
                            contexto,
                            "${contexto.packageName}.archivos",
                            archivo
                        )
                        uriCamara = destino
                        camara.launch(destino)
                    }) { Text("Otra foto") }
                    TextButton(onClick = {
                        galeria.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("De la galería") }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("2 · Qué quieres cambiar", style = MaterialTheme.typography.titleMedium)
            if (nombreEstancia != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = contextoEstancia,
                    onValueChange = { contextoEstancia = it },
                    label = { Text("Datos y cambios aprobados de ${nombreEstancia}") },
                    supportingText = { Text("Revisa estos datos; el modelo puede alterar medidas y geometría.") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = peticion,
                onValueChange = { peticion = it },
                label = { Text("Con tus palabras") },
                placeholder = { Text("Pon el suelo de tarima clara y las paredes blancas") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PromptRetoque.SUGERENCIAS.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fila.forEach { texto ->
                            FilterChip(
                                selected = peticion.equals(texto, ignoreCase = true),
                                onClick = { peticion = texto },
                                label = { Text(texto) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Acabados (opcional)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            ChipsDeLista(PAREDES, pared) { pared = if (pared == it) null else it }
            Spacer(Modifier.height(6.dp))
            ChipsDeLista(SUELOS, suelo) { suelo = if (suelo == it) null else it }
            Spacer(Modifier.height(10.dp))
            Text(
                "Luces que irán",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LUCES.forEach { luz ->
                    FilterChip(
                        selected = luz in luces,
                        onClick = { luces = if (luz in luces) luces - luz else luces + luz },
                        label = { Text(luz.substringBefore(" en").substringBefore(" LED")) }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            if (cargando) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp))
                    Spacer(Modifier.padding(6.dp))
                    Text("Pensándolo… puede tardar medio minuto.")
                }
            } else {
                Button(
                    onClick = { procesar() },
                    enabled = foto != null && (peticion.isNotBlank() || contextoEstancia.isNotBlank()) && ajustes.configurada,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) { Text(if (ronda == 0) "Procesar" else "Volver a procesar") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Quedan ${cupo.deHoy(LocalDate.now().toString()).restantes} de ${cupo.limite} hoy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val imagen = resultado
            if (imagen != null) {
                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("3 · Cómo queda", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Image(
                    bitmap = imagen.asImageBitmap(),
                    contentDescription = "Propuesta del retoque",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ronda $ronda · ${duracion / 1000} s · si no te vale, dime qué está mal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            alcance.launch {
                                val archivo = withContext(Dispatchers.IO) {
                                    val carpeta = File(contexto.filesDir, "export").apply { mkdirs() }
                                    val destino = File(
                                        carpeta,
                                        "obra-$obraId-retoque-${System.currentTimeMillis()}.jpg"
                                    )
                                    destino.outputStream().use {
                                        imagen.compress(Bitmap.CompressFormat.JPEG, 92, it)
                                    }
                                    destino
                                }
                                Exportador.compartir(contexto, archivo, tipo = "image/jpeg")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("Guardar y enviar") }
                    OutlinedButton(
                        onClick = { procesar() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("Otra versión") }
                }

                Spacer(Modifier.height(22.dp))
                Text("4 · ¿Qué ha fallado?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Marca lo que esté mal. Se lo digo al modelo en la siguiente vuelta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PromptRetoque.FALLOS.chunked(1).forEach { fila ->
                        fila.forEach { fallo ->
                            FilterChip(
                                selected = fallo in fallos,
                                onClick = { fallos = if (fallo in fallos) fallos - fallo else fallos + fallo },
                                label = { Text(fallo) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text("Y con tus palabras (opcional)") },
                    placeholder = { Text("La puerta la ha puesto a la izquierda") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { procesar() },
                    enabled = !cargando && ajustes.configurada,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) { Text("Corregir y pedir otra vez") }
            }

            aviso?.let {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(it, modifier = Modifier.padding(14.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "La generación remota puede tener coste y enviar fotos a terceros. El límite del móvil no protege el gasto del servicio. Comprueba las condiciones del proveedor antes de usarlo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pidiendoPermiso) {
        AlertDialog(
            onDismissRequest = { pidiendoPermiso = false },
            title = { Text("La foto sale del móvil") },
            text = {
                Text(
                    "La foto y la petición se envían al servicio configurado y pueden llegar a terceros. " +
                        "Consulta su política de privacidad y obtén permiso del cliente antes de continuar. ¿Seguimos?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    permisoParaEnvio = true
                    pidiendoPermiso = false
                    procesar()
                }) { Text("Sí, adelante") }
            },
            dismissButton = {
                TextButton(onClick = { pidiendoPermiso = false }) { Text("No") }
            }
        )
    }
}

@Composable
private fun ChipsDeLista(opciones: List<String>, elegido: String?, alElegir: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fila.forEach { texto ->
                    FilterChip(
                        selected = elegido == texto,
                        onClick = { alElegir(texto) },
                        label = { Text(texto) }
                    )
                }
            }
        }
    }
}

/**
 * Deja la foto en algo que el modelo entienda: 512 px de lado mayor y JPEG.
 *
 * El límite no es un capricho: FLUX.2 [klein] solo admite imágenes de entrada menores de
 * 512×512, y el Worker rechaza las que se pasan para poder avisar en vez de fallar sin decir nada.
 */
private fun prepararFoto(contexto: Context, uri: Uri, maximo: Int = 512): ByteArray? = runCatching {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contexto.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, limites)
    } ?: return null
    if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

    var muestra = 1
    while (maxOf(limites.outWidth, limites.outHeight) / muestra > maximo * 2) muestra *= 2

    val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
    val original = contexto.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opciones)
    } ?: return null

    val escala = minOf(1f, maximo.toFloat() / maxOf(original.width, original.height))
    val final = if (escala < 1f) {
        Bitmap.createScaledBitmap(
            original,
            (original.width * escala).toInt().coerceAtLeast(1),
            (original.height * escala).toInt().coerceAtLeast(1),
            true
        )
    } else {
        original
    }

    ByteArrayOutputStream().use { salida ->
        final.compress(Bitmap.CompressFormat.JPEG, 88, salida)
        salida.toByteArray()
    }
}.getOrNull()
