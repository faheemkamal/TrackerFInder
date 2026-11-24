package com.example.trackerfinder.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF5B5D72),
    secondaryContainer = Color(0xFFE0E0F9),
    tertiary = Color(0xFFE65100),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    surfaceVariant = Color(0xFFE3E1EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF2A3A9F),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC4C5DD),
    secondaryContainer = Color(0xFF434659),
    tertiary = Color(0xFFFFB68E),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF46464F),
)

private val AppTypography = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** App-wide theme choice, saved on this phone. */
object AppTheme {
    val mode = MutableStateFlow(ThemeMode.SYSTEM)
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val v = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("theme", null)
        mode.value = ThemeMode.entries.firstOrNull { it.name == v } ?: ThemeMode.SYSTEM
    }

    fun set(ctx: Context, m: ThemeMode) {
        mode.value = m
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("theme", m.name).apply()
    }
}

@Composable
fun isAppInDarkTheme(): Boolean {
    val mode by AppTheme.mode.collectAsState()
    return when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

@Composable
fun TrackerFinderTheme(content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}

/** Cold blue → amber → hot red. Deliberately fixed (not themed): it carries meaning. */
fun heatColor(heat: Float): Color {
    val cold = Color(0xFF1E88E5)
    val mid = Color(0xFFFFB300)
    val hot = Color(0xFFE53935)
    return if (heat < 0.5f) lerp(cold, mid, heat * 2) else lerp(mid, hot, (heat - 0.5f) * 2)
}

val NumberStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
