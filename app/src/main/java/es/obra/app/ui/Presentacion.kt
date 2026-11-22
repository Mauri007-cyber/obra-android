package es.obra.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import es.obra.app.render.Escena
import es.obra.app.render.Render3D
import kotlinx.coroutines.delay

/**
 * Enseñar la reforma con el móvil en la mano, delante del cliente.
 *
 * Sin barras, sin menús y sin que se apague la pantalla: la estancia gira despacio y un toque
 * cambia entre el antes y el después, que es la conversación que hay que tener en la obra.
 */
@Composable
fun Presentacion(
    escena: Escena,
    despues: Boolean,
    giroInicial: Double,
    inclinacion: Double,
    alTocar: () -> Unit,
    alSalir: () -> Unit
) {
    val contexto = LocalContext.current
    val vista = LocalView.current
    var giro by remember { mutableStateOf(giroInicial) }

    DisposableEffect(Unit) {
        val actividad = contexto as? Activity
        val ventana = actividad?.window
        ventana?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (ventana != null) {
            WindowCompat.getInsetsController(ventana, vista).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        onDispose {
            ventana?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (ventana != null) {
                WindowCompat.getInsetsController(ventana, vista)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            giro += 0.010
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBF9F5))
            .pointerInput(Unit) {
                detectTapGestures { alTocar() }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            Render3D.dibujar(
                this,
                Render3D.proyectar(
                    escena,
                    Render3D.Vista(size.width.toInt(), size.height.toInt(), giro, inclinacion)
                )
            )
        }

        Text(
            if (despues) "DESPUÉS" else "ANTES",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .background(Tinta.papel)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        TextButton(
            onClick = alSalir,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Text("Salir")
        }

        Text(
            "Toca la pantalla para ver el antes y el después",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
