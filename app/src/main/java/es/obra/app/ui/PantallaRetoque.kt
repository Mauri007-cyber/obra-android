package es.obra.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import es.obra.app.datos.Galeria
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ProyectoRepositorio
import es.obra.app.datos.rutaFoto
import es.obra.app.dominio.Cambio
import es.obra.app.dominio.EstanciaProyecto
import es.obra.app.dominio.FraseACambios
import es.obra.app.dominio.PromptReforma
import es.obra.app.ia.ClienteCloudflare
import es.obra.app.ia.CuotaImagen
import es.obra.app.ia.PuenteGemini
import es.obra.app.ia.ResultadoImagen
import es.obra.app.render.RetoqueFoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Enseñar la estancia terminada, gratis y sin cuenta.
 *
 * Tres caminos, de más automático a más seguro:
 *  1. **Generar en la app**: los cambios aprobados se convierten en un prompt y el Worker propio
 *     devuelve una imagen fotorrealista (cuota diaria gratuita de Cloudflare).
 *  2. **Abrir en Gemini**: la foto y el prompt van a la app de Gemini, que da la mejor calidad
 *     gratis; después se trae la imagen con el selector.
 *  3. **Retoque local**: pinta el material aprobado sobre la foto, sin conexión y sin cuota.
 *
 * La foto solo sale del móvil en los dos primeros caminos, y se avisa antes la primera vez.
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
    val desplazamiento = rememberScrollState()

    var estancia by remember { mutableStateOf<EstanciaProyecto?>(null) }
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    var resultado by remember { mutableStateOf<Bitmap?>(null) }
    var verAntes by remember { mutableStateOf(false) }

    var frase by remember { mutableStateOf("") }
    var entendidos by remember { mutableStateOf<List<Cambio>>(emptyList()) }
    var aviso by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    var generando by remember { mutableStateOf(false) }
    var pedirPermiso by remember { mutableStateOf(false) }
    var restantes by remember { mutableStateOf(CuotaImagen.restantes(contexto)) }
    var refresco by remember { mutableIntStateOf(0) }

    val generador = remember { ClienteCloudflare() }

    LaunchedEffect(obraId, estanciaId, refresco) {
        val obra = dao.obtenerObra(obraId)
        val proyecto = obra?.let { withContext(Dispatchers.IO) { ProyectoRepositorio.cargar(dao, it) } }
        val cargada = proyecto?.estancia(estanciaId)
        estancia = cargada
        val (original, terminada) = withContext(Dispatchers.IO) {
            val original = cargada?.foto
                ?.takeIf { it.isNotBlank() }
                ?.let { rutaFoto(contexto.filesDir, it) }
                ?.let { cargarFoto(it) }
            // La última imagen terminada se recupera de la estancia: no se pierde al salir.
            val terminada = cargada?.fotoTerminada
                ?.takeIf { it.isNotBlank() }
                ?.let { File(contexto.filesDir, it) }
                ?.takeIf { it.exists() }
                ?.let { cargarFoto(it) }
            original to terminada
        }
        foto = original
        resultado = terminada
    }

    val aprobados = estancia?.visibles ?: emptyList()
    val aplicables = aprobados + entendidos.filter { nuevo -> aprobados.none { it.tipo == nuevo.tipo } }

    // Se queda a la vista: si la imagen se genera arriba, hay que llevarla a la pantalla.
    LaunchedEffect(resultado) {
        if (resultado != null) desplazamiento.animateScrollTo(0)
    }

    fun promptDeLaObra(): String = PromptReforma.paraLaPantalla(
        aprobados = aprobados,
        entendidos = entendidos,
        frase = frase,
        estancia = estancia?.nombre.orEmpty()
    )

    /** Deja la imagen a la vista y la guarda en la estancia para que no se pierda. */
    fun fijarResultado(mapa: Bitmap) {
        resultado = mapa
        verAntes = false
        alcance.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val carpeta = File(contexto.filesDir, "retoques").apply { mkdirs() }
                    val archivo = File(carpeta, "terminada-$estanciaId.jpg")
                    archivo.outputStream().use {
                        mapa.compress(Bitmap.CompressFormat.JPEG, 92, it)
                    }
                    dao.fijarFotoTerminada(estanciaId, "retoques/${archivo.name}")
                }
            }
        }
    }

    fun generarConNube() {
        val original = foto
        if (original == null) {
            aviso = "Primero pon una foto en la estancia."
            return
        }
        if (aplicables.isEmpty()) {
            aviso = "No hay ningún cambio que enseñar todavía: aprueba trabajos o escribe qué " +
                "quieres cambiar y pulsa «Entender»."
            return
        }
        val prompt = promptDeLaObra()
        if (prompt.isBlank()) {
            aviso = "Lo aprobado no se ve en una foto: es albañilería o fontanería, no acabados."
            return
        }
        alcance.launch {
            generando = true
            aviso = ""
            when (val salida = generador.generar(original, prompt, CuotaImagen.dispositivo(contexto))) {
                is ResultadoImagen.Ok -> {
                    val mapa = withContext(Dispatchers.IO) {
                        runCatching {
                            BitmapFactory.decodeByteArray(salida.imagen, 0, salida.imagen.size)
                        }.getOrNull()
                    }
                    if (mapa == null) {
                        aviso = "La imagen llegó, pero no se pudo abrir."
                    } else {
                        fijarResultado(mapa)
                        val enGaleria = withContext(Dispatchers.IO) {
                            Galeria.guardar(contexto, mapa, "obra-$estanciaId")
                        }
                        aviso = if (enGaleria) {
                            "Imagen generada y guardada en la galería. Es una vista previa " +
                                "orientativa de la reforma."
                        } else {
                            "Imagen generada y guardada en la estancia. No se pudo copiar a la " +
                                "galería."
                        }
                        CuotaImagen.fijar(contexto, salida.restantes ?: ((restantes ?: 1) - 1))
                        restantes = CuotaImagen.restantes(contexto)
                    }
                }

                is ResultadoImagen.SinCuota -> {
                    aviso = salida.mensaje
                    CuotaImagen.fijar(contexto, 0)
                    restantes = 0
                }

                is ResultadoImagen.Fallo -> aviso = salida.mensaje
            }
            generando = false
        }
    }

    fun abrirEnGemini() {
        val original = foto
        if (original == null) {
            aviso = "Primero pon una foto en la estancia."
            return
        }
        val prompt = promptDeLaObra().ifBlank {
            "Reform this room and keep the same layout, camera and furniture."
        }
        aviso = if (PuenteGemini.abrir(contexto, original, prompt)) {
            "Elige Gemini, envía la foto, guarda la imagen y tráela aquí."
        } else {
            "Este dispositivo no tiene ninguna app que pueda recibir la foto (en un emulador " +
                "suele faltar Gemini). He copiado el texto al portapapeles."
        }
    }

    val traerDeGemini = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val mapa = withContext(Dispatchers.IO) { cargarUri(contexto, uri) }
            if (mapa == null) {
                aviso = "No se pudo leer la imagen elegida."
            } else {
                fijarResultado(mapa)
                val enGaleria = withContext(Dispatchers.IO) {
                    Galeria.guardar(contexto, mapa, "obra-$estanciaId")
                }
                aviso = if (enGaleria) {
                    "Imagen traída de Gemini y guardada en la galería. Compárala con la original."
                } else {
                    "Imagen traída de Gemini y guardada en la estancia. Compárala con la original."
                }
            }
        }
    }

    // El botón solo se apaga por un motivo concreto: se dice debajo cuál es.
    val faltaParaGenerar = when {
        !generador.configurado ->
            "Falta conectar el servicio de imagen en esta copia de la app. Gemini y el " +
                "retoque local funcionan igual."
        foto == null -> "Falta la foto de la estancia: ponla desde el proyecto."
        aplicables.isEmpty() ->
            "Faltan cambios: aprueba algún trabajo o escribe qué quieres cambiar y pulsa «Entender»."
        else -> null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("La obra terminada") },
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
                .verticalScroll(desplazamiento)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    contentDescription = if (verAntes) "Foto original" else "Propuesta de la reforma",
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

            // --- Generar la imagen ------------------------------------------------
            HorizontalDivider()
            Text(
                "Generar la imagen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Convierte lo aprobado en una foto de la reforma terminada. Es una vista previa " +
                    "orientativa: no sustituye al presupuesto ni a las medidas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    if (CuotaImagen.haAceptadoEnvio(contexto)) {
                        generarConNube()
                    } else {
                        pedirPermiso = true
                    }
                },
                enabled = !trabajando && !generando && generador.configurado &&
                    foto != null && aplicables.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Tinta.tinta,
                    contentColor = Tinta.papel
                )
            ) {
                if (generando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Tinta.papel
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Generando… puede tardar medio minuto")
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generar imagen realista · gratis")
                }
            }

            if (faltaParaGenerar != null && !generando) {
                Text(
                    faltaParaGenerar,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            restantes?.let { quedan ->
                Text(
                    if (quedan > 0) "Te quedan $quedan imágenes gratis hoy." else "Hoy ya no quedan imágenes gratis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { abrirEnGemini() },
                enabled = !generando && foto != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Abrir la foto en Gemini · máxima calidad") }

            OutlinedButton(
                onClick = {
                    traerDeGemini.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !generando,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ya la tengo: traer la imagen") }

            // Los avisos van justo aquí, pegados a los botones que los provocan: al final de la
            // lista quedaban fuera de la pantalla y parecía que el botón no hacía nada.
            if (aviso.isNotBlank()) {
                Text(
                    aviso,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "Al generar, esta foto se envía al servicio de imagen (Cloudflare). Al abrir " +
                    "Gemini, la foto y el texto van a Google. No se envía nada más: ni el " +
                    "cliente, ni las medidas, ni el presupuesto.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // --- Decirlo con palabras y retoque local -----------------------------
            HorizontalDivider()
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
                            fijarResultado(retocada)
                            trabajando = false
                            aviso = "Listo. Es lo aprobado pintado sobre tu foto, no una imagen inventada."
                        }
                    },
                    enabled = !trabajando && foto != null && aplicables.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tinta.obra,
                        contentColor = Tinta.tinta
                    )
                ) {
                    if (trabajando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Tinta.tinta
                        )
                    } else {
                        Text("Retoque local")
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

            if (resultado != null) {
                HorizontalDivider()
                Button(
                    onClick = {
                        val terminada = resultado ?: return@Button
                        alcance.launch {
                            val archivo = withContext(Dispatchers.IO) {
                                RetoqueFoto.guardar(contexto, terminada)
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

    if (pedirPermiso) {
        AlertDialog(
            onDismissRequest = { pedirPermiso = false },
            title = { Text("La foto sale del móvil") },
            text = {
                Text(
                    "Para generar la imagen, esta foto se envía al servicio de imagen (Cloudflare) " +
                        "y vuelve. No se envía nada más: ni el nombre del cliente, ni las medidas, " +
                        "ni el presupuesto. Si prefieres no enviarla, usa el retoque local o abre " +
                        "la foto en Gemini tú mismo."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CuotaImagen.aceptarEnvio(contexto)
                        pedirPermiso = false
                        generarConNube()
                    }
                ) { Text("Enviar y generar") }
            },
            dismissButton = {
                TextButton(onClick = { pedirPermiso = false }) { Text("Ahora no") }
            }
        )
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

/** Lee una imagen elegida en el selector de fotos, sin cargarla entera en memoria. */
private fun cargarUri(contexto: Context, uri: Uri, ladoMax: Int = 2048): Bitmap? = runCatching {
    contexto.contentResolver.openInputStream(uri)?.use { entrada ->
        val mapa = BitmapFactory.decodeStream(entrada) ?: return@use null
        val lado = maxOf(mapa.width, mapa.height)
        if (lado <= ladoMax) {
            mapa
        } else {
            val factor = ladoMax.toFloat() / lado
            Bitmap.createScaledBitmap(
                mapa,
                (mapa.width * factor).toInt().coerceAtLeast(1),
                (mapa.height * factor).toInt().coerceAtLeast(1),
                true
            )
        }
    }
}.getOrNull()
