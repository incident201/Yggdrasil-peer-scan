package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyberGreen,
    onPrimary = DeepSlate,
    secondary = TechBlue,
    onSecondary = DeepSlate,
    tertiary = SignalOrange,
    background = DeepSlate,
    surface = CardSlate,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = StrokeSlate,
    error = SignalRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force immersive dark mode for console vibe
    dynamicColor: Boolean = false, // Use our curated cyber colors instead of random ones
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
