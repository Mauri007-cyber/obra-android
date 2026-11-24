package es.obra.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import es.obra.app.datos.EstanciaEntity
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ObraEntity
import es.obra.app.datos.ProyectoRepositorio
import es.obra.app.dominio.Cambio
import es.obra.app.dominio.Escenario
import es.obra.app.dominio.EstadoCambio
import es.obra.app.dominio.Permiso
import es.obra.app.dominio.Presupuesto
import es.obra.app.dominio.Propuestas
import es.obra.app.ia.ClientePropuestas
import es.obra.app.ia.CuotaImagen
import es.obra.app.ia.EstanciaGuia
import es.obra.app.ia.PeticionPropuestas
import es.obra.app.ia.ResultadoPropuestas
import kotlinx.coroutines.launch

/**
 * Cuando el cliente no lo tiene claro: se marca qué se puede tocar, se cuenta la idea con sus
 * palabras y la IA devuelve tres escenarios con partidas reales del catálogo. El albañil elige uno
 * y las partidas entran como propuestas, con sus precios, para revisarlas y aprobarlas.
 *
 * No se manda ninguna foto: solo la guía y las medidas. Nada se escribe en la obra hasta que se
 * pulsa «Usar esta propuesta».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PantallaPropuestas(
    obraId: Long,
    dao: ObraDao,
    alVolver: () -> Unit
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val cliente = remember { ClientePropuestas() }

    var obra by remember { mutableStateOf<ObraEntity?>(null) }
    var estancias by remember { mutableStateOf<List<EstanciaEntity>>(emptyList()) }
    var permite by remember { mutableStateOf<Set<Permiso>>(emptySet()) }
    var deseo by remember { mutableStateOf("") }
    var presupuesto by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf("") }
    var escenarios by remember { mutableStateOf<List<Escenario>>(emptyList()) }
    var restantes by remember { mutableStateOf<Int?>(null) }
    var aAplicar by remember { mutableStateOf<Escenario?>(null) }

    LaunchedEffect(obraId) {
        obra = dao.obtenerObra(obraId)
        estancias = dao.estancias(obraId)
    }

    fun generar() {
        val actual = obra ?: return
        if (trabajando) return
        trabajando = true
        aviso = ""
        escenarios = emptyList()
        alcance.launch {
            val peticion = PeticionPropuestas(
                dispositivo = CuotaImagen.dispositivo(contexto),
                superficie = actual.superficieEfectiva,
                altura = actual.altura,
                presupuesto = presupuesto.replace(",", ".").toDoubleOrNull() ?: 0.0,
                estancias = estancias.map {
                    EstanciaGuia(
                        nombre = it.nombre,
                        superficie = if (it.superficie > 0.0) it.superficie
                        else if (estancias.size == 1) actual.superficieEfectiva else 0.0
                    )
                },
                permite = permite.map { it.etiqueta },
                deseo = deseo.trim(),
                catalogo = cliente.catalogo(permite.map { it.etiqueta })
            )
            when (val salida = cliente.generar(peticion)) {
                is ResultadoPropuestas.Ok -> {
                    escenarios = salida.escenarios
                    restantes = salida.restantes
                }
                is ResultadoPropuestas.Fallo -> aviso = salida.motivo
                is ResultadoPropuestas.SinCuota -> aviso = salida.motivo
            }
            trabajando = false
        }
    }

    fun usar(escenario: Escenario) {
        alcance.launch {
            val porNombre = estancias
                .associateBy { Propuestas.normalizarNombre(it.nombre) }
                .toMutableMap()
            for (actuacion in escenario.actuaciones) {
                val clave = Propuestas.normalizarNombre(actuacion.estancia)
                val estancia = porNombre[clave] ?: run {
                    val nombre = actuacion.estancia.trim().ifBlank { "Estancia" }
                    val nueva = EstanciaEntity(
                        obraId = obraId,
                        nombre = nombre,
                        superficie = if (estancias.size == 1) obra?.superficieEfectiva ?: 0.0 else 0.0
                    )
                    val id = dao.insertarEstancia(nueva)
                    nueva.copy(id = id).also { porNombre[Propuestas.normalizarNombre(nombre)] = it }
                }
                ProyectoRepositorio.guardar(
                    dao,
                    estancia.id,
                    Cambio(
                        tipo = actuacion.tipo,
                        cantidad = actuacion.cantidad,
                        acabado = actuacion.tipo.acabados.firstOrNull().orEmpty(),
                        nota = actuacion.motivo,
                        estado = EstadoCambio.PROPUESTO
                    )
                )
            }
            alVolver()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Propuestas") },
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
            Spacer(Modifier.height(6.dp))
            Text(
                "Cuando no está claro qué hacer, la app propone tres escenarios.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Marca lo que se puede tocar: la IA solo propondrá esas partidas. Si quieres, " +
                    "cuéntalo con tus palabras. Nada entra en la obra hasta que elijas un escenario.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))
            Text("¿Qué estás dispuesto a hacer?", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            // Cuatro bloques cortos en vez de catorce chips sueltos, que agobian a quien no sabe
            // de obra. Dentro de cada bloque, FlowRow: con columnas fijas las etiquetas largas se
            // cortaban en pantallas estrechas.
            Propuestas.porGrupo().forEachIndexed { indice, (grupo, opciones) ->
                if (indice > 0) Spacer(Modifier.height(12.dp))
                Text(
                    grupo.etiqueta,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    opciones.forEach { opcion ->
                        FilterChip(
                            selected = opcion in permite,
                            onClick = {
                                permite = if (opcion in permite) permite - opcion
                                else permite + opcion
                            },
                            label = { Text(opcion.etiqueta) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = deseo,
                onValueChange = { deseo = it.take(600) },
                label = { Text("¿Qué quiere conseguir? (opcional)") },
                placeholder = { Text("Por ejemplo: cocina abierta al salón y un baño nuevo") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = presupuesto,
                onValueChange = {
                    presupuesto = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8)
                },
                label = { Text("Presupuesto aproximado (€, opcional)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { generar() },
                enabled = !trabajando && (permite.isNotEmpty() || deseo.trim().length >= 8),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (escenarios.isEmpty()) "Generar 3 propuestas" else "Generar otras 3")
            }
            if (permite.isEmpty() && deseo.trim().length < 8) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Marca alguna casilla o escribe qué se quiere hacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trabajando) {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Pensando tres escenarios…",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (aviso.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    aviso,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            escenarios.forEachIndexed { indice, escenario ->
                Spacer(Modifier.height(18.dp))
                TarjetaEscenario(
                    escenario = escenario,
                    letra = ('A' + indice).toString(),
                    alElegir = { aAplicar = escenario }
                )
            }

            restantes?.let { quedan ->
                Spacer(Modifier.height(14.dp))
                Text(
                    "Quedan $quedan propuestas gratis hoy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    aAplicar?.let { escenario ->
        AlertDialog(
            onDismissRequest = { aAplicar = null },
            title = { Text("Usar «${escenario.titulo}»") },
            text = {
                Text(
                    "Se añadirán ${escenario.actuaciones.size} partidas como propuestas, " +
                        "con sus precios. Después puedes cambiar cantidades, quitar lo que no " +
                        "quieras y aprobarlas."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        aAplicar = null
                        usar(escenario)
                    }
                ) { Text("Añadir partidas") }
            },
            dismissButton = {
                TextButton(onClick = { aAplicar = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun TarjetaEscenario(
    escenario: Escenario,
    letra: String,
    alElegir: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "$letra · ${escenario.titulo}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (escenario.resumen.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    escenario.resumen,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            escenario.actuaciones.forEach { actuacion ->
                Text("• ${escenario.linea(actuacion)}", style = MaterialTheme.typography.bodyLarge)
                if (actuacion.motivo.isNotBlank()) {
                    Text(
                        "   ${actuacion.motivo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text(
                "Coste orientativo sin IVA: ${Presupuesto.euros(escenario.coste)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = alElegir,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Usar esta propuesta") }
        }
    }
}
