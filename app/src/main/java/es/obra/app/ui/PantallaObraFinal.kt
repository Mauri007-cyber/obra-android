package es.obra.app.ui

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.obra.app.datos.EstanciaEntity
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ObraEntity
import es.obra.app.datos.ProyectoRepositorio
import es.obra.app.dominio.Ambito
import es.obra.app.dominio.Cambio
import es.obra.app.dominio.Cifra
import es.obra.app.dominio.EstadoCambio
import es.obra.app.dominio.EstanciaProyecto
import es.obra.app.dominio.Proyecto
import es.obra.app.dominio.PromptEstancia
import es.obra.app.dominio.TipoCambio
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La obra completa: estancias, cambios y lo que se va a ver al final.
 *
 * Aquí el cliente no aprueba una foto: aprueba una lista de trabajos, estancia por estancia.
 * Lo aprobado es lo que cuenta para el dinero y para el recorrido final; lo descartado
 * desaparece del resultado pero se queda en el historial de la estancia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaObraFinal(obraId: Long, dao: ObraDao, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    var obra by remember { mutableStateOf<ObraEntity?>(null) }
    var proyecto by remember { mutableStateOf<Proyecto?>(null) }
    var refresco by remember { mutableIntStateOf(0) }

    var editando by remember { mutableStateOf<EstanciaProyecto?>(null) }
    var creandoEstancia by remember { mutableStateOf(false) }
    var ensenando by remember { mutableStateOf(false) }
    var estanciaDeFoto by remember { mutableStateOf<Long?>(null) }

    fun recargar() {
        refresco++
    }

    LaunchedEffect(obraId, refresco) {
        val cargada = dao.obtenerObra(obraId)
        obra = cargada
        proyecto = cargada?.let { ProyectoRepositorio.cargar(dao, it) }
    }

    val selectorFoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val destino = estanciaDeFoto
        if (uri == null || destino == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val ruta = withContext(Dispatchers.IO) { copiarFoto(contexto, uri) }
            if (ruta != null) {
                val entidad = dao.estancias(obraId).firstOrNull { it.id == destino }
                if (entidad != null) dao.actualizarEstancia(entidad.copy(foto = ruta))
            }
            recargar()
        }
    }

    val actual = proyecto

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Proyecto de la obra") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (actual != null && actual.estancias.isNotEmpty()) {
                        IconButton(onClick = { ensenando = true }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Enseñar la obra finalizada")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Button(
                onClick = { creandoEstancia = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Tinta.tinta,
                    contentColor = Tinta.papel
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Estancia")
            }
        }
    ) { relleno ->
        when {
            obra == null || actual == null -> Box(
                Modifier.fillMaxSize().padding(relleno),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { CabeceraProyecto(actual) }

                if (actual.estancias.isEmpty()) {
                    item {
                        Text(
                            "Añade la primera estancia y luego sus cambios: suelo, paredes, " +
                                "baño, puerta… Lo que el cliente vea en la lista es lo que se hace.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(actual.estancias, key = { it.id }) { estancia ->
                    TarjetaEstancia(
                        estancia = estancia,
                        alAnadir = { editando = estancia },
                        alCambiarEstado = { cambio, estado ->
                            alcance.launch {
                                if (cambio.id > 0) {
                                    ProyectoRepositorio.cambiarEstado(dao, cambio.id, estado)
                                } else {
                                    ProyectoRepositorio.guardar(
                                        dao,
                                        estancia.id,
                                        cambio.copy(estado = estado)
                                    )
                                }
                                recargar()
                            }
                        },
                        alBorrar = { cambio ->
                            alcance.launch {
                                if (cambio.id > 0) dao.borrarCambio(cambio.id)
                                recargar()
                            }
                        },
                        alPonerFoto = {
                            estanciaDeFoto = estancia.id
                            selectorFoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                item {
                    Text(
                        "Una obra puede tener las estancias que haga falta, y cada una tantos " +
                            "cambios como hagan falta. Nada de prompts: se elige del catálogo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    editando?.let { estancia ->
        DialogoCatalogo(
            estancia = estancia,
            alCerrar = { editando = null },
            alElegir = { tipo, cantidad, acabado ->
                alcance.launch {
                    val nuevo = Cambio(
                        tipo = tipo,
                        cantidad = cantidad,
                        acabado = acabado,
                        estado = EstadoCambio.PROPUESTO
                    )
                    ProyectoRepositorio.guardar(dao, estancia.id, nuevo)
                    editando = null
                    recargar()
                }
            }
        )
    }

    if (creandoEstancia) {
        DialogoNuevaEstancia(
            alCerrar = { creandoEstancia = false },
            alCrear = { nombre, alto ->
                alcance.launch {
                    val id = dao.insertarEstancia(
                        EstanciaEntity(
                            obraId = obraId,
                            nombre = nombre,
                            altura = alto
                        )
                    )
                    if (id > 0) {
                        // La primera estancia hereda la superficie ya medida de la obra.
                        val estancias = dao.estancias(obraId)
                        if (estancias.size == 1 && obra != null) {
                            dao.actualizarEstancia(
                                estancias.first().copy(superficie = obra!!.superficieEfectiva)
                            )
                        }
                    }
                    creandoEstancia = false
                    recargar()
                }
            }
        )
    }

    if (ensenando && actual != null) {
        DialogoRecorrido(proyecto = actual, alCerrar = { ensenando = false })
    }
}

@Composable
private fun CabeceraProyecto(proyecto: Proyecto) {
    Surface(
        color = Tinta.tinta,
        contentColor = Tinta.papel,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                proyecto.nombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CifraBloque("Aprobado", Cifra.euros(proyecto.costeAprobado), Modifier.weight(1f))
                CifraBloque("Pendiente", Cifra.euros(proyecto.costePropuesto), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { proyecto.progreso },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Tinta.obra,
                trackColor = Tinta.papel.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${proyecto.aprobados.size} de " +
                    "${proyecto.cambios.count { it.estado != EstadoCambio.RECHAZADO }} cambios aprobados " +
                    "· ${proyecto.estancias.size} estancia(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = Tinta.papel.copy(alpha = 0.75f)
            )
            val avisos = proyecto.avisos()
            if (avisos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                avisos.take(3).forEach { aviso ->
                    Text(
                        "· $aviso",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tinta.obra
                    )
                }
            }
        }
    }
}

@Composable
private fun CifraBloque(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = Tinta.papel.copy(alpha = 0.7f)
        )
        Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TarjetaEstancia(
    estancia: EstanciaProyecto,
    alAnadir: () -> Unit,
    alCambiarEstado: (Cambio, EstadoCambio) -> Unit,
    alBorrar: (Cambio) -> Unit,
    alPonerFoto: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        estancia.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append(
                                if (estancia.superficie > 0.0) {
                                    "${Cifra.numero(estancia.superficie, 1)} m²"
                                } else {
                                    "sin medidas"
                                }
                            )
                            append(" · ${estancia.aprobados.size} aprobados")
                            if (estancia.propuestos.isNotEmpty()) {
                                append(" · ${estancia.propuestos.size} por decidir")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    Cifra.euros(estancia.costeAprobado),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (estancia.foto.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                val mapa = remember(estancia.foto) { BitmapFactory.decodeFile(estancia.foto) }
                if (mapa != null) {
                    Image(
                        bitmap = mapa.asImageBitmap(),
                        contentDescription = "Foto de ${estancia.nombre}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (estancia.cambios.isEmpty()) {
                Text(
                    "Todavía no hay cambios en esta estancia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            estancia.cambios.forEach { cambio ->
                FilaCambio(
                    cambio = cambio,
                    alCambiarEstado = { alCambiarEstado(cambio, it) },
                    alBorrar = { alBorrar(cambio) }
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = alAnadir, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir cambio")
                }
                OutlinedButton(onClick = alPonerFoto) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (estancia.foto.isBlank()) "Foto" else "Cambiar")
                }
            }
        }
    }
}

@Composable
private fun FilaCambio(
    cambio: Cambio,
    alCambiarEstado: (EstadoCambio) -> Unit,
    alBorrar: () -> Unit
) {
    val color = when (cambio.estado) {
        EstadoCambio.APROBADO -> Tinta.verde
        EstadoCambio.PROPUESTO -> Tinta.obra
        EstadoCambio.RECHAZADO -> Tinta.rojo
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(color, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(cambio.linea, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${cambio.estado.etiqueta} · ${Cifra.euros(cambio.importe)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (cambio.id > 0) {
                IconButton(onClick = alBorrar, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Quitar el cambio",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.width(19.dp))
            EstadoBoton(
                texto = "Aprobado",
                activo = cambio.estado == EstadoCambio.APROBADO,
                color = Tinta.verde,
                alPulsar = { alCambiarEstado(EstadoCambio.APROBADO) }
            )
            EstadoBoton(
                texto = "Por decidir",
                activo = cambio.estado == EstadoCambio.PROPUESTO,
                color = Tinta.obra,
                alPulsar = { alCambiarEstado(EstadoCambio.PROPUESTO) }
            )
            EstadoBoton(
                texto = "Fuera",
                activo = cambio.estado == EstadoCambio.RECHAZADO,
                color = Tinta.rojo,
                alPulsar = { alCambiarEstado(EstadoCambio.RECHAZADO) }
            )
        }
    }
}

@Composable
private fun EstadoBoton(
    texto: String,
    activo: Boolean,
    color: androidx.compose.ui.graphics.Color,
    alPulsar: () -> Unit
) {
    TextButton(
        onClick = alPulsar,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (activo) color else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (activo) Tinta.papel else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/* ------------------------------------------------------------------ */
/* Catálogo de cambios                                                 */
/* ------------------------------------------------------------------ */

@Composable
private fun DialogoCatalogo(
    estancia: EstanciaProyecto,
    alCerrar: () -> Unit,
    alElegir: (TipoCambio, Double, String) -> Unit
) {
    var busqueda by remember { mutableStateOf("") }
    var elegido by remember { mutableStateOf<TipoCambio?>(null) }
    var cantidad by remember { mutableStateOf("") }
    var acabado by remember { mutableStateOf("") }

    val encontrados = if (busqueda.isBlank()) {
        TipoCambio.porAmbito()
    } else {
        TipoCambio.buscar(busqueda)
            .groupBy { it.ambito }
            .toList()
            .sortedBy { it.first.orden }
    }

    val tipo = elegido

    Dialog(
        onDismissRequest = alCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (tipo == null) "Añadir cambio a ${estancia.nombre}" else tipo.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { if (tipo == null) alCerrar() else elegido = null }) {
                        Icon(
                            if (tipo == null) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cerrar"
                        )
                    }
                }

                if (tipo == null) {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        label = { Text("Buscar: suelo, baño, puerta…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        encontrados.forEach { (ambito, tipos) ->
                            item(key = "h-${ambito.name}") {
                                Text(
                                    ambito.etiqueta.uppercase(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(tipos, key = { "t-${it.name}" }) { candidato ->
                                FilaCatalogo(candidato) {
                                    if (candidato.mide) {
                                        elegido = candidato
                                        cantidad = Cifra.numero(
                                            Cambio(tipo = candidato)
                                                .conCantidadSugerida(estancia.superficie, estancia.altura)
                                                .cantidad,
                                            1
                                        )
                                        acabado = candidato.acabados.firstOrNull().orEmpty()
                                    } else {
                                        alElegir(candidato, 0.0, candidato.acabados.firstOrNull().orEmpty())
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "¿Cuánto? Se propone lo que mide la estancia; cámbialo si hace falta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = cantidad,
                        onValueChange = { cantidad = it },
                        label = { Text("Cantidad (${tipo.unidad.etiqueta})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tipo.acabados.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Acabado", style = MaterialTheme.typography.bodyMedium)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tipo.acabados.forEach { opcion ->
                                FilterChip(
                                    selected = acabado == opcion,
                                    onClick = { acabado = opcion },
                                    label = { Text(opcion) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            val valor = cantidad.replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                            alElegir(tipo, valor.coerceAtLeast(0.0), acabado)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Añadir a ${estancia.nombre}")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaCatalogo(tipo: TipoCambio, alPulsar: () -> Unit) {
    Surface(
        onClick = alPulsar,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tipo.nombre, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (tipo.mide) "por ${tipo.unidad.etiqueta}" else "partida entera",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(Cifra.euros(tipo.precio), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Estancia nueva                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun DialogoNuevaEstancia(
    alCerrar: () -> Unit,
    alCrear: (String, Double) -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var alto by remember { mutableStateOf("2,5") }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Nueva estancia") },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre: salón, cocina, baño…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = alto,
                    onValueChange = { alto = it },
                    label = { Text("Alto de la pared (m)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val medida = alto.replace(",", ".").toDoubleOrNull() ?: 2.5
                    if (nombre.isNotBlank()) alCrear(nombre.trim(), medida)
                },
                enabled = nombre.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

/* ------------------------------------------------------------------ */
/* Recorrido final                                                     */
/* ------------------------------------------------------------------ */

@Composable
private fun DialogoRecorrido(proyecto: Proyecto, alCerrar: () -> Unit) {
    Dialog(
        onDismissRequest = alCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "La obra terminada",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = alCerrar) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                Text(
                    "Lo que se ve al final, estancia por estancia. Solo lo aprobado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(proyecto.recorrido(), key = { "r-${it.id}" }) { estancia ->
                        Column {
                            Text(
                                estancia.nombre,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (estancia.foto.isNotBlank()) {
                                val mapa = remember(estancia.foto) {
                                    BitmapFactory.decodeFile(estancia.foto)
                                }
                                if (mapa != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Image(
                                        bitmap = mapa.asImageBitmap(),
                                        contentDescription = estancia.nombre,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            val frases = estancia.resumen()
                            if (frases.isEmpty()) {
                                Text(
                                    "Sin cambios aprobados.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                frases.forEach { frase ->
                                    Row(Modifier.padding(vertical = 2.dp)) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Tinta.verde,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(frase, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                            Text(
                                Cifra.euros(estancia.costeAprobado),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text(
                                "Total aprobado",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                Cifra.euros(proyecto.costeAprobado),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = alCerrar,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tinta.verde,
                        contentColor = Tinta.papel
                    )
                ) { Text("Volver al proyecto") }
            }
        }
    }
}

/** Lo que se le pediría al modelo para esta estancia, si algún día se usa. */
@Composable
fun TextoDelPrompt(estancia: EstanciaProyecto, modifier: Modifier = Modifier) {
    val texto = remember(estancia) { PromptEstancia.componer(estancia) }
    if (texto.isNotBlank()) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
            textAlign = TextAlign.Start
        )
    }
}

private fun copiarFoto(contexto: Context, uri: Uri): String? = runCatching {
    val carpeta = File(contexto.filesDir, "fotos").apply { mkdirs() }
    val destino = File(carpeta, "estancia-${System.currentTimeMillis()}.jpg")
    contexto.contentResolver.openInputStream(uri)?.use { entrada ->
        destino.outputStream().use { salida -> entrada.copyTo(salida) }
    } ?: return null
    destino.absolutePath
}.getOrNull()
