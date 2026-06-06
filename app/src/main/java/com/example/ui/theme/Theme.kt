package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = ProfPrimaryDark,
    onPrimary = ProfOnPrimaryDark,
    primaryContainer = ProfPrimaryContainerDark,
    onPrimaryContainer = ProfOnPrimaryContainerDark,
    secondary = ProfPrimaryDark,
    background = ProfBgDark,
    onBackground = ProfOnBgDark,
    surface = ProfSurfaceDark,
    onSurface = ProfOnSurfaceDark,
    surfaceVariant = ProfSurfaceVariantDark,
    onSurfaceVariant = ProfOnSurfaceVariantDark,
    outline = ProfOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = ProfPrimaryLight,
    onPrimary = ProfOnPrimaryLight,
    primaryContainer = ProfPrimaryContainerLight,
    onPrimaryContainer = ProfOnPrimaryContainerLight,
    secondary = ProfPrimaryLight,
    background = ProfBgLight,
    onBackground = ProfOnBgLight,
    surface = ProfSurfaceLight,
    onSurface = ProfOnSurfaceLight,
    surfaceVariant = ProfSurfaceVariantLight,
    onSurfaceVariant = ProfOnSurfaceVariantLight,
    outline = ProfOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
