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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
    data class Plano(val id: Long, val estanciaId: Long? = null) : Pantalla
    data class Visual(val id: Long) : Pantalla
    data class Presupuesto(val id: Long) : Pantalla
    data class Retoque(val id: Long, val estanciaId: Long? = null) : Pantalla
    data class ObraFinal(val id: Long) : Pantalla
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
            alRetoque = { pantalla = Pantalla.Retoque(actual.id) },
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

        is Pantalla.Retoque -> PantallaRetoque(
            obraId = actual.id,
            estanciaId = actual.estanciaId,
            dao = dao,
            alVolver = { pantalla = if (actual.estanciaId != null) Pantalla.ObraFinal(actual.id) else Pantalla.Obra(actual.id) }
        )

        is Pantalla.ObraFinal -> PantallaObraFinal(
            obraId = actual.id,
            dao = dao,
            alVolver = { pantalla = Pantalla.Obra(actual.id) },
            alRetoque = { estanciaId -> pantalla = Pantalla.Retoque(actual.id, estanciaId) },
            alPlano = { estanciaId -> pantalla = Pantalla.Plano(actual.id, estanciaId) }
        )

        is Pantalla.Medir -> PantallaMedir(
            alVolver = { pantalla = Pantalla.Obra(actual.id) },
            alEscanear = { pantalla = Pantalla.Escanear(actual.id) }
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
                text = { Text("Nueva obra") }
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
                    "Pulsa «Nueva obra», escribe las medidas de la estancia y tendrás la superficie y los materiales calculados. Todo se queda en el móvil.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(obras, key = { it.id }) { obra ->
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
    alRetoque: () -> Unit,
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
                alRetoque,
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
    alRetoque: () -> Unit,
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
                CopiaObra.exportar(contexto, dao, obra.id, salida)
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
        Button(
            onClick = alProyecto,
            colors = ButtonDefaults.buttonColors(
                containerColor = Tinta.tinta,
                contentColor = Tinta.papel
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text("Proyecto: estancias y cambios", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = alVisual,
            colors = ButtonDefaults.buttonColors(
                containerColor = Tinta.verde,
                contentColor = Tinta.papel
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text("Enseñar al cliente", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = alPlano,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text("Abrir el plano", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = alMedir,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text("Medir de otra forma")
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = alPresupuesto,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text("Presupuesto", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = alRetoque,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text("Retoque con IA (foto)")
        }
        Spacer(Modifier.height(10.dp))
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
            "La exportación no incluye fotos y todavía no se puede restaurar en la app; no es una copia de seguridad completa.",
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
fun PantallaMedir(alVolver: () -> Unit, alEscanear: () -> Unit) {
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
                detalle = "Foto del suelo, marcar las cuatro esquinas y decir cuánto mide la puerta.",
                disponible = false,
                nota = "Llega en la siguiente entrega (H1)"
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
