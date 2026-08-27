package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricIndigoLight,
    onPrimary = Slate950,
    primaryContainer = ElectricIndigoDark,
    onPrimaryContainer = Slate200,
    secondary = CyanAccentLight,
    onSecondary = Slate950,
    secondaryContainer = Slate800,
    onSecondaryContainer = CyanAccentLight,
    tertiary = CoralAccent,
    background = Slate950,
    onBackground = Slate200,
    surface = Slate900,
    onSurface = Slate200,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricIndigo,
    onPrimary = Slate50,
    primaryContainer = Slate100,
    onPrimaryContainer = ElectricIndigoDark,
    secondary = CyanAccent,
    onSecondary = Slate50,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    tertiary = CoralAccent,
    background = Slate50,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
