package es.obra.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.obra.app.BuildConfig
import es.obra.app.actualizacion.Actualizador
import es.obra.app.actualizacion.EstadoActualizacion
import es.obra.app.ar.ArDisponible
import es.obra.app.ar.EstadoAr
import es.obra.app.datos.CopiaObra
import es.obra.app.datos.Exportador
import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ObraDb
import es.obra.app.datos.ObraEntity
import es.obra.app.dominio.Geometria
import es.obra.app.dominio.Medidas
import es.obra.app.dominio.Poligono
import es.obra.app.pdf.PdfObra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Pantalla {
    data object Inicio : Pantalla
    data object NuevaObra : Pantalla
    data class Obra(val id: Long) : Pantalla
    data class Medir(val id: Long) : Pantalla
    data class Escanear(val id: Long) : Pantalla
    data class MedirFoto(val id: Long) : Pantalla
    data class Plano(val id: Long, val estanciaId: Long? = null) : Pantalla
    data class Visual(val id: Long) : Pantalla
    data class Presupuesto(val id: Long) : Pantalla
    data class ObraFinal(val id: Long) : Pantalla
    data class Propuestas(val id: Long) : Pantalla
    data class Retoque(val id: Long, val estanciaId: Long) : Pantalla
    data object Actualizar : Pantalla
}

@Composable
fun AppObra() {
    val contexto = LocalContext.current
    val dao = remember { ObraDb.obtener(contexto).dao() }
    val alcance = rememberCoroutineScope()
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Inicio) }
    var hayVersionNueva by remember { mutableStateOf(false) }
    val actualizador = remember { Actualizador(contexto) }

    LaunchedEffect(Unit) {
        // Comprobación silenciosa: sin red o sin servidor, no se molesta a nadie.
        hayVersionNueva = actualizador.comprobar() is EstadoActualizacion.Disponible
    }

    // Dentro de una obra, las cuatro cosas que se hacen siempre, a un toque y siempre visibles.
    val obraEnCurso: Long? = when (val p = pantalla) {
        is Pantalla.Obra -> p.id
        is Pantalla.ObraFinal -> p.id
        is Pantalla.Presupuesto -> p.id
        is Pantalla.Visual -> p.id
        else -> null
    }

    Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
    when (val actual = pantalla) {
        Pantalla.Inicio -> PantallaInicio(
            dao = dao,
            hayVersion = hayVersionNueva,
            alAbrir = { pantalla = Pantalla.Obra(it) },
            alCrear = { pantalla = Pantalla.NuevaObra },
            alActualizar = { pantalla = Pantalla.Actualizar }
        )

        Pantalla.NuevaObra -> PantallaNuevaObra(
            alVolver = { pantalla = Pantalla.Inicio },
            alCrear = { nombre, direccion, cliente ->
                alcance.launch {
                    val id = dao.insertarObra(
                        ObraEntity(nombre = nombre, direccion = direccion, cliente = cliente)
                    )
                    pantalla = Pantalla.Obra(id)
                }
            }
        )

        is Pantalla.Obra -> PantallaObra(
            id = actual.id,
            dao = dao,
            alVolver = { pantalla = Pantalla.Inicio },
            alMedir = { pantalla = Pantalla.Medir(actual.id) },
            alPlano = { pantalla = Pantalla.Plano(actual.id) },
            alVisual = { pantalla = Pantalla.Visual(actual.id) },
            alPresupuesto = { pantalla = Pantalla.Presupuesto(actual.id) },
            alProyecto = { pantalla = Pantalla.ObraFinal(actual.id) },
            alActualizar = { pantalla = Pantalla.Actualizar }
        )

        is Pantalla.Plano -> PantallaPlano(
            obraId = actual.id,
            dao = dao,
            estanciaSeleccionadaId = actual.estanciaId,
            alVolver = { pantalla = if (actual.estanciaId == null) Pantalla.Obra(actual.id) else Pantalla.ObraFinal(actual.id) },
            alAplicar = { largo, ancho, superficie ->
                alcance.launch {
                    if (actual.estanciaId != null) {
                        dao.guardarSuperficieEstancia(actual.id, actual.estanciaId, superficie)
                    } else dao.obtenerObra(actual.id)?.let { obra ->
                        dao.actualizarObra(
                            obra.copy(
                                largo = largo,
                                ancho = if (ancho > 0.0) ancho else obra.ancho,
                                metodo = "plano",
                                superficieCerrada = superficie
                            )
                        )
                    }
                    pantalla = if (actual.estanciaId == null) Pantalla.Obra(actual.id) else Pantalla.ObraFinal(actual.id)
                }
            }
        )

        is Pantalla.Visual -> PantallaVisual(
            obraId = actual.id,
            dao = dao,
            alVolver = { pantalla = Pantalla.Obra(actual.id) }
        )

        is Pantalla.Presupuesto -> PantallaPresupuesto(
            obraId = actual.id,
            dao = dao,
            alVolver = { pantalla = Pantalla.Obra(actual.id) }
        )

        is Pantalla.ObraFinal -> PantallaObraFinal(
            obraId = actual.id,
            dao = dao,
            alVolver = { pantalla = Pantalla.Obra(actual.id) },
            alPropuestas = { pantalla = Pantalla.Propuestas(actual.id) },
            alPlano = { estanciaId -> pantalla = Pantalla.Plano(actual.id, estanciaId) },
            alRetoque = { estanciaId -> pantalla = Pantalla.Retoque(actual.id, estanciaId) }
        )

        is Pantalla.Propuestas -> PantallaPropuestas(
            obraId = actual.id,
            dao = dao,
            alVolver = { pantalla = Pantalla.ObraFinal(actual.id) }
        )

        is Pantalla.Retoque -> PantallaRetoque(
            obraId = actual.id,
            estanciaId = actual.estanciaId,
            dao = dao,
            alVolver = { pantalla = Pantalla.ObraFinal(actual.id) }
        )

        is Pantalla.Medir -> PantallaMedir(
            alVolver = { pantalla = Pantalla.Obra(actual.id) },
            alEscanear = { pantalla = Pantalla.Escanear(actual.id) },
            alFoto = { pantalla = Pantalla.MedirFoto(actual.id) }
        )

        is Pantalla.MedirFoto -> PantallaMedirFoto(
            alVolver = { pantalla = Pantalla.Medir(actual.id) },
            alAplicar = { superficie ->
                alcance.launch {
                    dao.obtenerObra(actual.id)?.let { obra ->
                        dao.actualizarObra(
                            obra.copy(metodo = "foto", superficieCerrada = superficie)
                        )
                    }
                    pantalla = Pantalla.Obra(actual.id)
                }
            }
        )

        is Pantalla.Escanear -> PantallaEscanear(
            obraId = actual.id,
            dao = dao,
            alVolver = { pantalla = Pantalla.Medir(actual.id) },
            alTerminar = { pantalla = Pantalla.Obra(actual.id) }
        )

        Pantalla.Actualizar -> PantallaActualizar(
            alVolver = { pantalla = Pantalla.Inicio }
        )
    }
    }
    if (obraEnCurso != null) {
        BarraPasos(obraId = obraEnCurso, pantalla = pantalla, alIr = { pantalla = it })
    }
    }
}

/** Las cuatro pantallas de una obra. Siempre abajo, siempre en el mismo orden. */
@Composable
private fun BarraPasos(obraId: Long, pantalla: Pantalla, alIr: (Pantalla) -> Unit) {
    val pasos = listOf(
        Triple("Medidas", Icons.Filled.Straighten, Pantalla.Obra(obraId)),
        Triple("Cambios", Icons.Filled.EditNote, Pantalla.ObraFinal(obraId)),
        Triple("Presupuesto", Icons.Filled.Euro, Pantalla.Presupuesto(obraId)),
        Triple("Ver", Icons.Filled.Image, Pantalla.Visual(obraId))
    )
    Surface(tonalElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            pasos.forEach { (etiqueta, icono, destino) ->
                val activo = pantalla::class == destino::class
                val color = if (activo) Tinta.obra else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { alIr(destino) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(icono, contentDescription = etiqueta, tint = color)
                    Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Inicio                                                              */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicio(
    dao: ObraDao,
    hayVersion: Boolean,
    alAbrir: (Long) -> Unit,
    alCrear: () -> Unit,
    alActualizar: () -> Unit
) {
    val obras by dao.observarObras().collectAsStateWithLifecycle(initialValue = emptyList())
    var busqueda by remember { mutableStateOf("") }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var avisoCopia by remember { mutableStateOf<String?>(null) }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) alcance.launch {
            try {
                val id = CopiaObra.importar(contexto, dao, uri)
                alAbrir(id)
            } catch (error: Exception) {
                avisoCopia = "No se pudo restaurar: ${error.message ?: "copia inválida"}. No se han sobrescrito obras."
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Obra", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = alActualizar) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Buscar actualización")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = alCrear,
                containerColor = Tinta.obra,
                contentColor = Tinta.tinta,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Empezar obra nueva") }
            )
        }
    ) { relleno ->
        Column(modifier = Modifier.fillMaxSize().padding(relleno)) {
        if (hayVersion) {
            BannerVersion(alPulsar = alActualizar)
        }
        OutlinedButton(onClick = { importar.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { Text("Restaurar copia de una obra") }
        avisoCopia?.let { Text(it, modifier = Modifier.padding(20.dp), color = Tinta.rojo) }
        if (obras.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Todavía no hay obras",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Pulsa «Empezar obra nueva». Solo hace falta un nombre; las medidas se ponen después, a mano o con la cámara. Todo se queda en el móvil.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                label = { Text("Buscar obra o cliente") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                "Toca una obra para seguir donde lo dejaste.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val consulta = busqueda.trim().lowercase()
                val filtradas = obras.filter { obra ->
                    consulta.isEmpty() ||
                        obra.nombre.lowercase().contains(consulta) ||
                        obra.direccion.lowercase().contains(consulta) ||
                        obra.cliente.lowercase().contains(consulta)
                }
                if (filtradas.isEmpty()) {
                    item {
                        Text(
                            "No hay ninguna obra que coincida con «$busqueda».",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(filtradas, key = { it.id }) { obra ->
                    FichaObra(obra = obra, alPulsar = { alAbrir(obra.id) })
                }
            }
        }
        }
    }
}

@Composable
private fun BannerVersion(alPulsar: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable(onClick = alPulsar),
        color = Tinta.obra,
        contentColor = Tinta.tinta,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Hay una versión nueva",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text("Actualizar", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FichaObra(obra: ObraEntity, alPulsar: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(obra.nombre, style = MaterialTheme.typography.titleMedium)
            if (obra.direccion.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    obra.direccion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Geometria.formatear(obra.superficieEfectiva),
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "m²",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    obra.estado.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Nueva obra                                                          */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaNuevaObra(alVolver: () -> Unit, alCrear: (String, String, String) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var cliente by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Nueva obra") },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "¿Dónde es el trabajo?",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre de la obra") },
                placeholder = { Text("Piso calle Mayor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = direccion,
                onValueChange = { direccion = it },
                label = { Text("Dirección") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = cliente,
                onValueChange = { cliente = it },
                label = { Text("Cliente (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { alCrear(nombre.trim(), direccion.trim(), cliente.trim()) },
                enabled = nombre.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Text("Crear obra", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Después podrás medir la estancia a mano, con foto o dando una vuelta con la cámara.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Obra                                                                */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaObra(
    id: Long,
    dao: ObraDao,
    alVolver: () -> Unit,
    alMedir: () -> Unit,
    alPlano: () -> Unit,
    alVisual: () -> Unit,
    alPresupuesto: () -> Unit,
    alProyecto: () -> Unit,
    alActualizar: () -> Unit
) {
    val obra by dao.observarObra(id).collectAsStateWithLifecycle(initialValue = null)
    val actual = obra

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(actual?.nombre ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = alActualizar) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Buscar actualización")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { relleno ->
        if (actual == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            ContenidoObra(
                actual,
                dao,
                alMedir,
                alPlano,
                alVisual,
                alPresupuesto,
                alProyecto,
                Modifier.padding(relleno)
            )
        }
    }
}

@Composable
private fun ContenidoObra(
    obra: ObraEntity,
    dao: ObraDao,
    alMedir: () -> Unit,
    alPlano: () -> Unit,
    alVisual: () -> Unit,
    alPresupuesto: () -> Unit,
    alProyecto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var avisoCopia by remember { mutableStateOf<String?>(null) }
    val guardarCopia = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) alcance.launch {
            try {
                val salida = contexto.contentResolver.openOutputStream(uri) ?: error("No se pudo abrir el destino")
                salida.use { CopiaObra.exportar(contexto, dao, obra.id, it) }
                avisoCopia = "Copia guardada con las fotos de las estancias."
            } catch (error: Exception) {
                avisoCopia = "No se pudo guardar la copia: ${error.message ?: "error de escritura"}. Descarta el archivo incompleto."
            }
        }
    }

    var largo by remember(obra.id) { mutableStateOf(cifra(obra.largo)) }
    var ancho by remember(obra.id) { mutableStateOf(cifra(obra.ancho)) }
    var altura by remember(obra.id) { mutableStateOf(cifra(obra.altura)) }
    var medidasEditadas by remember(obra.id) { mutableStateOf(false) }

    var muros by remember { mutableStateOf<List<MuroEntity>>(emptyList()) }
    var huecos by remember { mutableStateOf<List<HuecoEntity>>(emptyList()) }

    LaunchedEffect(obra.id) {
        val estancias = dao.estancias(obra.id)
        muros = estancias.flatMap { dao.muros(it.id) }
        huecos = estancias.flatMap { dao.huecosDeEstancia(it.id) }
    }

    val l = Geometria.parsear(largo)
    val a = Geometria.parsear(ancho)
    val h = Geometria.parsear(altura)

    val superficie = if (obra.metodo == "plano" && l == obra.largo && a == obra.ancho) {
        Poligono.superficie(muros) ?: (l * a)
    } else l * a
    val perimetro = Poligono.perimetro(muros) ?: (2 * (l + a))
    val paredesBrutas = if (muros.isNotEmpty()) {
        muros.filter { it.estado != "tirado" }.sumOf { Poligono.longitud(it) * h }
    } else {
        perimetro * h
    }
    val paredes = (paredesBrutas - huecos.sumOf { it.ancho * it.alto }).coerceAtLeast(0.0)
    val litros = Geometria.litrosPintura(paredes, manos = 2, rendimiento = 10.0)
    val botes15 = Geometria.botes(litros, 15.0)
    val cajas = Geometria.cajasAzulejo(superficie, merma = 0.10, m2Caja = 1.44)

    // Guardado con retardo: escribir no debe escribir en disco en cada tecla.
    LaunchedEffect(obra.id, l, a, h, medidasEditadas) {
        if (!medidasEditadas) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        val alturaFinal = if (h > 0.0) h else 2.5
        if (l != obra.largo || a != obra.ancho || alturaFinal != obra.altura) {
            dao.guardarMedidasManuales(obra.id, l, a, alturaFinal)
        }
        medidasEditadas = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Seccion("Los 4 pasos de la obra")
        TarjetaPaso(1, "Medir la habitación", "Largo, ancho y alto. También con la cámara.", alMedir)
        Spacer(Modifier.height(8.dp))
        TarjetaPaso(2, "Decir qué cambia", "Elige los trabajos y apruébalos.", alProyecto)
        Spacer(Modifier.height(8.dp))
        TarjetaPaso(3, "Ver el presupuesto", "Lo que se cobra, con IVA y PDF.", alPresupuesto)
        Spacer(Modifier.height(8.dp))
        TarjetaPaso(4, "Enseñar cómo queda", "La foto terminada de cada estancia.", alVisual)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = alPlano,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Abrir el plano")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(20.dp))

        Seccion("Medidas de la estancia")
        CampoNumero(
            etiqueta = "Largo",
            valor = largo,
            modifier = Modifier.fillMaxWidth(),
            alCambiar = { largo = it; medidasEditadas = true }
        )
        Spacer(Modifier.height(10.dp))
        CampoNumero(
            etiqueta = "Ancho",
            valor = ancho,
            modifier = Modifier.fillMaxWidth(),
            alCambiar = { ancho = it; medidasEditadas = true }
        )
        Spacer(Modifier.height(10.dp))
        CampoNumero(
            etiqueta = "Alto de la pared",
            valor = altura,
            modifier = Modifier.fillMaxWidth(),
            alCambiar = { altura = it; medidasEditadas = true }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Escribe 3,42 o 3.42, da igual. Si el número no tiene sentido, te aviso.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        CifraGrande(
            valor = superficie,
            unidad = "m²",
            etiqueta = if (muros.isNotEmpty()) "Superficie del contorno" else "Superficie del suelo"
        )

        if (!Medidas.plausible(l, a)) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = Tinta.obra,
                contentColor = Tinta.tinta,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "Esas medidas no parecen de una habitación. ¿Te has comido la coma? 3,42 no es 342.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CifraMedia(
                valor = Geometria.formatear(perimetro),
                unidad = "m",
                etiqueta = "Perímetro",
                modifier = Modifier.weight(1f)
            )
            CifraMedia(
                valor = Geometria.formatear(paredes),
                unidad = "m²",
                etiqueta = "Paredes",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(20.dp))

        Seccion("Lo que necesitas comprar")
        FilaMaterial(
            "Pintura",
            "Necesitas ${Geometria.formatear(litros)} L. Compra $botes15 bote(s) de 15 L."
        )
        FilaMaterial(
            "Suelo o azulejo",
            "$cajas cajas de 1,44 m², con la merma ya contada."
        )
        if (obra.factorCalibracion != 1.0) {
            FilaMaterial(
                "Corrección de la cámara",
                "Se aplica ×${Geometria.formatear(obra.factorCalibracion, 3)} a lo medido con el móvil."
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Son cuentas orientativas con rendimientos de manual. Ajústalas con el material que compres.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = {
                alcance.launch {
                    // El PDF se escribe en disco: fuera del hilo de la pantalla.
                    val archivo = withContext(Dispatchers.IO) {
                        PdfObra.generar(contexto, obra, muros, huecos)
                    }
                    Exportador.compartir(contexto, archivo, tipo = "application/pdf")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Ficha en PDF")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                alcance.launch {
                    val archivo = Exportador.exportar(contexto, obra, dao)
                    Exportador.compartir(contexto, archivo)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Exportar datos (.obra, sin fotos)")
        }
        OutlinedButton(onClick = { guardarCopia.launch("obra-${obra.id}.zip") }, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar copia con fotos")
        }
        avisoCopia?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text(
            "La exportación .obra no incluye fotos y no se puede restaurar. Usa la copia ZIP con fotos para guardar la obra; conserva también los originales hasta comprobarla en tu dispositivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(40.dp))
        Text(
            "Versión ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
    }
}

/* ------------------------------------------------------------------ */
/* Medir                                                               */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMedir(alVolver: () -> Unit, alEscanear: () -> Unit, alFoto: () -> Unit) {
    val contexto = LocalContext.current
    var arDisponible by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        arDisponible = withContext(Dispatchers.IO) {
            ArDisponible.estado(contexto) == EstadoAr.DISPONIBLE
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Medir") },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            when (arDisponible) {
                null -> Text(
                    "Comprobando si el móvil puede escanear…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                true -> Text(
                    "Este móvil puede escanear la habitación con la cámara.",
                    style = MaterialTheme.typography.bodyLarge
                )

                false -> Text(
                    "Este móvil no tiene ARCore, así que no puede escanear. Se mide escribiendo o con una foto y una medida conocida.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))

            OpcionMedida(
                titulo = "Medir con la cámara (AR)",
                detalle = "Apunta al suelo, marca un extremo y luego el otro: la app da la distancia real.",
                disponible = arDisponible == true,
                nota = when (arDisponible) {
                    true -> "Funciona sin conexión"
                    false -> "Este móvil no tiene ARCore"
                    null -> "Comprobando…"
                },
                alPulsar = if (arDisponible == true) alEscanear else null
            )
            Spacer(Modifier.height(12.dp))
            OpcionMedida(
                titulo = "Foto y una medida conocida",
                detalle = "Foto del suelo, marcar las esquinas y decir cuánto mide una puerta o una baldosa.",
                disponible = true,
                nota = "Funciona sin conexión y la foto no sale del móvil",
                alPulsar = alFoto
            )
            Spacer(Modifier.height(12.dp))
            OpcionMedida(
                titulo = "Escribir las medidas",
                detalle = "Largo, ancho y alto. Ya funciona: vuelve atrás y rellena las casillas.",
                disponible = true,
                nota = null
            )
        }
    }
}

@Composable
private fun OpcionMedida(
    titulo: String,
    detalle: String,
    disponible: Boolean,
    nota: String?,
    alPulsar: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (disponible) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
        shape = MaterialTheme.shapes.small,
        border = if (disponible) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                color = if (disponible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                detalle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (nota != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    nota,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (disponible) Tinta.verde else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            if (alPulsar != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = alPulsar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) { Text("Empezar a medir") }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Actualizar                                                          */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaActualizar(alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val actualizador = remember { Actualizador(contexto) }
    val alcance = rememberCoroutineScope()
    var estado by remember { mutableStateOf<EstadoActualizacion>(EstadoActualizacion.SinComprobar) }
    var puedeInstalar by remember { mutableStateOf(actualizador.puedeInstalar()) }
    var aviso by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Actualizar") },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Versión instalada ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(20.dp))

            when (val e = estado) {
                EstadoActualizacion.SinComprobar -> Text(
                    "Comprueba si hay una versión nueva. Se instala encima y no se pierde ninguna obra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                EstadoActualizacion.Comprobando -> {
                    LinkTexto("Buscando…")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                EstadoActualizacion.AlDia -> Text(
                    "Estás al día.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Tinta.verde
                )

                is EstadoActualizacion.Disponible -> {
                    Text(
                        "Hay una versión nueva: ${e.manifiesto.versionName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (e.manifiesto.notas.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            e.manifiesto.notas,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            aviso = null
                            alcance.launch {
                                estado = EstadoActualizacion.Descargando(0)
                                estado = actualizador.descargar(e.manifiesto) { progreso ->
                                    estado = EstadoActualizacion.Descargando(progreso)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    ) { Text("Descargar la actualización") }
                }

                is EstadoActualizacion.Descargando -> {
                    Text(
                        "Descargando… ${e.progreso} %",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { e.progreso / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is EstadoActualizacion.Lista -> {
                    Text(
                        "Descargada la versión ${e.manifiesto.versionName}.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Al instalar se conservan todas las obras y las fotos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (!actualizador.puedeInstalar()) {
                                actualizador.abrirAjusteInstalacion()
                                aviso = "Permite «Instalar apps desconocidas» para Obra y vuelve a pulsar."
                            } else {
                                when (val resultado = actualizador.instalar(e.archivo)) {
                                    is Actualizador.Resultado.Fallo -> aviso = resultado.motivo
                                    Actualizador.Resultado.Lanzado -> aviso = "Sigue el aviso de Android para instalar."
                                }
                            }
                            puedeInstalar = actualizador.puedeInstalar()
                        },
                        enabled = puedeInstalar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    ) { Text("Instalar y conservar los datos") }

                    if (!puedeInstalar) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = {
                            actualizador.abrirAjusteInstalacion()
                            aviso = "Activa «Instalar apps desconocidas» para Obra."
                        }) {
                            Text("Permitir instalar desde esta app")
                        }
                    }
                }

                is EstadoActualizacion.Error -> Text(
                    "No se pudo actualizar: ${e.mensaje}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            aviso?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = {
                    alcance.launch {
                        estado = EstadoActualizacion.Comprobando
                        estado = actualizador.comprobar()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) { Text("Buscar actualización") }

            Spacer(Modifier.height(24.dp))
            Text(
                "Android siempre pide confirmación para instalar. Es un toque, y solo la primera vez hay que dar permiso. Lo que no hace falta es desinstalar nada: los datos se quedan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Piezas comunes                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun Seccion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun CampoNumero(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    alCambiar: (String) -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo ->
            // Solo dígitos, coma y punto: nada de teclados que rompan la cuenta.
            if (nuevo.all { it.isDigit() || it == ',' || it == '.' }) alCambiar(nuevo)
        },
        label = { Text(etiqueta) },
        suffix = { Text("m") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun CifraGrande(valor: Double, unidad: String, etiqueta: String) {
    Column {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Geometria.formatear(valor), style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                unidad,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun CifraMedia(
    valor: String,
    unidad: String,
    etiqueta: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                unidad,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
    }
}

@Composable
private fun FilaMaterial(nombre: String, detalle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(nombre, style = MaterialTheme.typography.bodyLarge)
        Text(
            detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinkTexto(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Start
    )
}

private fun cifra(valor: Double): String =
    if (valor <= 0.0) "" else Geometria.formatear(valor, decimales = 2).trimEnd('0').trimEnd(',')

/** Un paso con su número grande: se toca la tarjeta entera, no hay que apuntar a nada pequeño. */
@Composable
private fun TarjetaPaso(numero: Int, titulo: String, detalle: String, alPulsar: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Tinta.obra, contentColor = Tinta.tinta, shape = CircleShape) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "$numero",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    detalle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
