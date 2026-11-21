package es.obra.app.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.obra.app.datos.Exportador
import es.obra.app.datos.HuecoEntity
import es.obra.app.datos.MuroEntity
import es.obra.app.datos.ObraDao
import es.obra.app.datos.ObraEntity
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
    var muros by remember { mutableStateOf<List<MuroEntity>>(emptyList()) }
    var huecos by remember { mutableStateOf<List<HuecoEntity>>(emptyList()) }
    var tarifas by remember { mutableStateOf(Tarifas.porDefecto) }
    var ajustando by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf(false) }

    LaunchedEffect(obraId) {
        val guardada = dao.obtenerObra(obraId)
        obra = guardada
        tarifas = Tarifas.desdeTexto(guardada?.tarifas ?: "")
        val estancias = dao.estancias(obraId)
        muros = estancias.flatMap { dao.muros(it.id) }
        huecos = estancias.flatMap { dao.huecosDeEstancia(it.id) }
    }

    LaunchedEffect(obraId, tarifas) {
        delay(700)
        val guardada = obra ?: return@LaunchedEffect
        if (Tarifas.desdeTexto(guardada.tarifas) != tarifas) {
            dao.actualizarObra(guardada.copy(tarifas = tarifas.aTexto()))
        }
    }

    val actual = obra
    val superficie = Presupuesto.suelo(muros, actual?.largo ?: 0.0, actual?.ancho ?: 0.0)
    val paredes = Presupuesto.paredes(
        muros,
        huecos,
        actual?.largo ?: 0.0,
        actual?.ancho ?: 0.0,
        actual?.altura ?: 2.5
    )
    val partidas = Presupuesto.partidas(
        muros,
        huecos,
        superficie,
        paredes,
        tarifas,
        altura = actual?.altura ?: 2.5
    )
    val base = Presupuesto.base(partidas)
    val iva = Presupuesto.iva(base, tarifas)
    val total = Presupuesto.total(base, tarifas)

    fun compartir() {
        val guardada = obra ?: return
        if (trabajando) return
        trabajando = true
        alcance.launch {
            val archivo = withContext(Dispatchers.IO) {
                PdfPresupuesto.generar(contexto, guardada, partidas, tarifas, base, iva, total)
            }
            trabajando = false
            Exportador.compartir(contexto, archivo, tipo = "application/pdf")
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
                    IconButton(onClick = { compartir() }, enabled = !trabajando) {
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

            if (partidas.isEmpty()) {
                Text(
                    "Todavía no hay nada que presupuestar. Mide la estancia y dibuja el plano; " +
                        "las partidas salen solas de ahí.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
            } else {
                Text(
                    "Lo que va a costar",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(12.dp))

                partidas.forEach { partida ->
                    FilaPartida(partida)
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

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { ajustando = !ajustando }) {
                Text(if (ajustando) "Ocultar precios" else "Ajustar precios")
            }

            if (ajustando) {
                Text(
                    "Precios con la mano de obra incluida. Cámbialos y se guardan en esta obra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                CampoPrecio("Tabique nuevo (€/m²)", tarifas.tabiqueNuevo) {
                    tarifas = tarifas.copy(tabiqueNuevo = it)
                }
                Spacer(Modifier.height(8.dp))
                CampoPrecio("Demolición (€/m²)", tarifas.demolicion) {
                    tarifas = tarifas.copy(demolicion = it)
                }
                Spacer(Modifier.height(8.dp))
                CampoPrecio("Suelo (€/m²)", tarifas.suelo) {
                    tarifas = tarifas.copy(suelo = it)
                }
                Spacer(Modifier.height(8.dp))
                CampoPrecio("Pintura (€/m²)", tarifas.pintura) {
                    tarifas = tarifas.copy(pintura = it)
                }
                Spacer(Modifier.height(8.dp))
                CampoPrecio("Puerta (€/ud)", tarifas.puerta) {
                    tarifas = tarifas.copy(puerta = it)
                }
                Spacer(Modifier.height(8.dp))
                CampoPrecio("Ventana (€/ud)", tarifas.ventana) {
                    tarifas = tarifas.copy(ventana = it)
                }
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
}

@Composable
private fun FilaPartida(partida: Partida) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(partida.concepto, style = MaterialTheme.typography.bodyLarge)
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
