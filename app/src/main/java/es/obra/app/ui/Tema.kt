package es.obra.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Dirección visual: herramienta de obra, no app de decoración.
 * Papel de plano cálido, tinta, un solo acento (amarillo de obra) y dos colores con significado:
 * rojo = tirar, verde = nuevo.
 */
object Tinta {
    val papel = Color(0xFFF7F5F0)
    val papelHundido = Color(0xFFEDE9E1)
    val tinta = Color(0xFF14181C)
    val gris = Color(0xFF6B7280)
    val linea = Color(0xFFD6D1C7)
    val obra = Color(0xFFF2B01E)
    val rojo = Color(0xFFC0392B)
    val verde = Color(0xFF2E7D4F)
}

private val esquema = lightColorScheme(
    primary = Tinta.tinta,
    onPrimary = Tinta.papel,
    secondary = Tinta.obra,
    onSecondary = Tinta.tinta,
    background = Tinta.papel,
    onBackground = Tinta.tinta,
    surface = Tinta.papel,
    onSurface = Tinta.tinta,
    surfaceVariant = Tinta.papelHundido,
    onSurfaceVariant = Tinta.gris,
    outline = Tinta.linea,
    error = Tinta.rojo
)

private fun cifras(tamano: Int, peso: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = peso,
    fontSize = tamano.sp,
    lineHeight = (tamano * 1.15f).sp,
    // Las medidas se alinean entre sí: cifras de ancho fijo.
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.3).sp
)

private val tipografia = Typography(
    displayLarge = cifras(40),
    displayMedium = cifras(32),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    // Los títulos de bloque tienen que leerse como títulos: por defecto Material los deja en
    // 14 sp, más pequeños que el texto normal.
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp
    )
)

@Composable
fun TemaObra(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquema, typography = tipografia, content = content)
}
