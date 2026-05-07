package com.scoot.transit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5DAE),
    onPrimary = Color.White,
    secondary = Color(0xFFE85D2A),
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFFAF9F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2F7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BA9DD),
    onPrimary = Color(0xFF002E5E),
    secondary = Color(0xFFFFA37A),
    tertiary = Color(0xFF8FC993),
    background = Color(0xFF101418),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF222831),
)

@Composable
fun ScootTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScootTypography,
        content = content
    )
}
