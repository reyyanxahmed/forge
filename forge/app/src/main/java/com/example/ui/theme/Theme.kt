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
    primary = BentoPrimary,
    secondary = BentoPrimary,
    tertiary = BentoOnBg,
    background = BentoBg,
    surface = BentoBg,
    surfaceVariant = BentoCardNormal,
    onPrimary = BentoOnPrimary,
    onSecondary = BentoOnPrimary,
    onBackground = BentoOnBg,
    onSurface = BentoOnBg,
    onSurfaceVariant = Color(0xFF938F99),
    outline = BentoBorder,
    outlineVariant = BentoBorder
)

private val LightColorScheme = darkColorScheme( // Enforce dark Bento aesthetic for both modes to stay on-theme
    primary = BentoPrimary,
    secondary = BentoPrimary,
    tertiary = BentoOnBg,
    background = BentoBg,
    surface = BentoBg,
    surfaceVariant = BentoCardNormal,
    onPrimary = BentoOnPrimary,
    onSecondary = BentoOnPrimary,
    onBackground = BentoOnBg,
    onSurface = BentoOnBg,
    onSurfaceVariant = Color(0xFF938F99),
    outline = BentoBorder,
    outlineVariant = BentoBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to dark theme for Bento Grid style
    dynamicColor: Boolean = false, // Disable dynamic colors by default to enforce Bento Grid design
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
