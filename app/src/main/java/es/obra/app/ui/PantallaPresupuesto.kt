package es.obra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import es.obra.app.datos.Exportador
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ObraEntity
import es.obra.app.datos.ProyectoRepositorio
import es.obra.app.dominio.Proyecto
import es.obra.app.dominio.Partida
import es.obra.app.dominio.Presupuesto
import es.obra.app.dominio.Tarifas
import es.obra.app.pdf.PdfPresupuesto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * El número. Sale de lo medido y de los precios del albañil, no de una plantilla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPresupuesto(obraId: Long, dao: ObraDao, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    var obra by remember { mutableStateOf<ObraEntity?>(null) }
    var tarifas by remember { mutableStateOf(Tarifas.porDefecto) }
    var ajustando by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf(false) }
    var errorPdf by remember { mutableStateOf<String?>(null) }
    var numeroEstancias by remember { mutableStateOf<Int?>(null) }
    var proyecto by remember { mutableStateOf<Proyecto?>(null) }
    var totalCambios by remember { mutableStateOf(0) }
    var refresco by remember { mutableIntStateOf(0) }
    var editando by remember { mutableStateOf<Partida?>(null) }
    var estadoPresupuesto by remember { mutableStateOf("borrador") }
    var validez by remember { mutableStateOf("30") }
    var notasCliente by remember { mutableStateOf("") }

    LaunchedEffect(obraId, refresco) {
        val guardada = dao.obtenerObra(obraId)
        obra = guardada
        tarifas = Tarifas.desdeTexto(guardada?.tarifas ?: "")
        estadoPresupuesto = guardada?.estadoPresupuesto ?: "borrador"
        validez = (guardada?.validezDias ?: 30).toString()
        notasCliente = guardada?.notasPresupuesto.orEmpty()
        val estancias = dao.estancias(obraId)
        totalCambios = dao.cambios(obraId).size
        proyecto = guardada?.let { ProyectoRepositorio.cargar(dao, it) }
        numeroEstancias = estancias.size
    }

    LaunchedEffect(obraId, tarifas) {
        delay(700)
        val guardada = obra ?: return@LaunchedEffect
        if (Tarifas.desdeTexto(guardada.tarifas) != tarifas) {
            dao.guardarTarifas(guardada.id, tarifas.aTexto())
        }
    }

    // Se guarda solo: escribir las notas no debe obligar a pulsar nada.
    LaunchedEffect(obraId, estadoPresupuesto, validez, notasCliente) {
        delay(700)
        val guardada = obra ?: return@LaunchedEffect
        val dias = validez.toIntOrNull()?.coerceIn(1, 365) ?: 30
        if (guardada.estadoPresupuesto != estadoPresupuesto ||
            guardada.validezDias != dias ||
            guardada.notasPresupuesto != notasCliente
        ) {
            dao.guardarDatosPresupuesto(guardada.id, dias, notasCliente, estadoPresupuesto)
            obra = guardada.copy(
                estadoPresupuesto = estadoPresupuesto,
                validezDias = dias,
                notasPresupuesto = notasCliente
            )
        }
    }

    val aprobado = proyecto?.let { Presupuesto.partidasAprobadas(it) }.orEmpty()
    val cambiosDesconocidos = proyecto?.estancias?.sumOf { it.cambios.size }?.let { it < totalCambios } == true
    val valoresInvalidos = proyecto?.let { !Presupuesto.aprobadosValidos(it) } == true
    val permitido = numeroEstancias != null && !cambiosDesconocidos && !valoresInvalidos
    val partidas = if (permitido) aprobado else emptyList()
    val base = Presupuesto.base(partidas)
    val iva = Presupuesto.iva(base, tarifas)
    val total = Presupuesto.total(base, tarifas)

    fun compartir() {
        val guardada = obra ?: return
        if (trabajando || !permitido || partidas.isEmpty()) return
        trabajando = true
        alcance.launch {
            try {
                val archivo = withContext(Dispatchers.IO) {
                    PdfPresupuesto.generar(
                        contexto, guardada, partidas, tarifas, base, iva, total,
                        estado = estadoPresupuesto,
                        validezDias = validez.toIntOrNull() ?: 30,
                        notas = notasCliente
                    )
                }
                if (!Exportador.compartir(contexto, archivo, tipo = "application/pdf")) {
                    errorPdf = "No se pudo abrir el menú para compartir."
                } else if (estadoPresupuesto == "borrador") {
                    // Como en la app de referencia: enviar el PDF lo pasa a «enviado».
                    estadoPresupuesto = "enviado"
                }
            } catch (error: Exception) {
                errorPdf = error.message ?: "No se pudo crear el PDF."
            } finally {
                trabajando = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Presupuesto") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { compartir() }, enabled = !trabajando && permitido && partidas.isNotEmpty()) {
                        Icon(Icons.Filled.Share, contentDescription = "Compartir presupuesto")
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
            Spacer(Modifier.height(8.dp))
            errorPdf?.let { Text(it, color = Tinta.rojo) }

            if (partidas.isEmpty()) {
                Text(
                    if (numeroEstancias == null) "Cargando medidas…" else if (cambiosDesconocidos) "Esta obra tiene trabajos que ya no están en el catálogo. Entra en «Cambios» y revisa esa estancia." else if (valoresInvalidos) "Falta un precio o una cantidad en algún trabajo aprobado. Entra en «Cambios» y revisa esa estancia." else "Todavía no hay trabajos aprobados. Entra en «Cambios» (abajo), elige los trabajos y márcalos como aprobados.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
            } else {
                Text(
                    "Lo que cuesta la obra",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${partidas.size} partida(s) en ${partidas.map { it.estancia }.distinct().size} " +
                        "estancia(s). Toca una partida para cambiar la cantidad o el precio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                // Agrupado por estancia, como se enseña en obra: cada habitación con su subtotal.
                partidas.groupBy { it.estancia.ifBlank { "Obra" } }.forEach { (estancia, filas) ->
                    Surface(
                        color = Tinta.obra,
                        contentColor = Tinta.tinta,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                estancia,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                Presupuesto.euros(filas.sumOf { it.importe }),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    filas.forEach { partida ->
                        FilaPartida(partida, alPulsar = { editando = partida })
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))

                FilaTotal("Base", Presupuesto.euros(base))
                FilaTotal("IVA (${Presupuesto.cantidad(tarifas.iva)} %)", Presupuesto.euros(iva))

                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Tinta.tinta,
                    contentColor = Tinta.papel,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            Presupuesto.euros(total),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { compartir() },
                enabled = partidas.isNotEmpty() && !trabajando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Text(
                    if (trabajando) "Preparando el PDF…" else "Enviar presupuesto",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Text(
                "Datos del presupuesto",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "borrador" to "Borrador",
                    "enviado" to "Enviado",
                    "aceptado" to "Aceptado"
                ).forEach { (valor, etiqueta) ->
                    FilterChip(
                        selected = estadoPresupuesto == valor,
                        onClick = { estadoPresupuesto = valor },
                        label = { Text(etiqueta) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = validez,
                onValueChange = { validez = it.filter(Char::isDigit).take(3) },
                label = { Text("Validez (días)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = notasCliente,
                onValueChange = { notasCliente = it },
                label = { Text("Notas para el cliente (opcional)") },
                placeholder = { Text("Materiales incluidos, plazos, forma de pago…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { ajustando = !ajustando }) {
                Text(if (ajustando) "Ocultar precios" else "Ajustar precios")
            }

            if (ajustando) {
                Text(
                    "Para los trabajos aprobados, ajusta cada precio en su estancia. Aquí puedes modificar el IVA.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                CampoPrecio("IVA (%)", tarifas.iva) {
                    tarifas = tarifas.copy(iva = it.coerceIn(0.0, 30.0))
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Cuentas orientativas sobre las medidas tomadas en obra. No incluyen imprevistos " +
                    "ni materiales especiales, y no son un contrato.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    // Cambiar cantidad o precio sin salir del presupuesto.
    editando?.let { partida ->
        var cantidad by remember(partida) {
            mutableStateOf(Presupuesto.cantidad(partida.cantidad).replace(".", ","))
        }
        var precio by remember(partida) {
            mutableStateOf(Presupuesto.cantidad(partida.precio).replace(".", ","))
        }
        AlertDialog(
            onDismissRequest = { editando = null },
            title = { Text("Cambiar cantidad o precio") },
            text = {
                Column {
                    Text(
                        partida.concepto.removePrefix("${partida.estancia}: "),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cantidad,
                        onValueChange = { cantidad = it },
                        label = { Text("Cantidad (${partida.unidad})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = precio,
                        onValueChange = { precio = it },
                        label = { Text("Precio por ${partida.unidad} (€)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nuevaCantidad = cantidad.replace(',', '.').toDoubleOrNull()
                            ?: partida.cantidad
                        val nuevoPrecio = precio.replace(',', '.').toDoubleOrNull()
                            ?: partida.precio
                        val id = partida.cambioId
                        if (id > 0) {
                            alcance.launch {
                                dao.actualizarImporte(id, nuevaCantidad, nuevoPrecio)
                                editando = null
                                refresco++
                            }
                        } else {
                            editando = null
                        }
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editando = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun FilaPartida(partida: Partida, alPulsar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                partida.concepto.removePrefix("${partida.estancia}: "),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "${Presupuesto.cantidad(partida.cantidad)} ${partida.unidad} × " +
                    "${Presupuesto.cantidad(partida.precio)} €",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(Presupuesto.euros(partida.importe), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FilaTotal(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CampoPrecio(etiqueta: String, valor: Double, alCambiar: (Double) -> Unit) {
    var texto by remember {
        mutableStateOf(
            if (valor == 0.0) "" else String.format(Locale.forLanguageTag("es-ES"), "%.2f", valor)
                .replace('.', ',')
        )
    }

    OutlinedTextField(
        value = texto,
        onValueChange = { nuevo ->
            val limpio = nuevo.filter { it.isDigit() || it == ',' || it == '.' }
            texto = limpio
            alCambiar(limpio.replace(',', '.').toDoubleOrNull() ?: 0.0)
        },
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
        modifier = Modifier.fillMaxWidth()
    )
}
