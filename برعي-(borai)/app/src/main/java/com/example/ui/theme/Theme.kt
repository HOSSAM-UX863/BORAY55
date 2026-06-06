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

private val DarkColorScheme =
  darkColorScheme(
    primary = DeepGold,
    secondary = LightCreamGold,
    tertiary = Cream,
    background = MatteBlack,
    surface = DarkCardBg,
    onPrimary = MatteBlack,
    onSecondary = MatteBlack,
    onBackground = Cream,
    onSurface = Cream,
    outline = DeepGold.copy(alpha = 0.5f)
  )

private val LightColorScheme = DarkColorScheme // Keep it consistent for Dark Academia branding

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled to preserve strict Borai branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
