package com.notova.design.theme

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

private val NotovaPurple = Color(0xFF6750A4)
private val NotovaPurpleDark = Color(0xFFD0BCFF)
private val NotovaTeal = Color(0xFF03DAC5)

private val LightColors =
    lightColorScheme(
        primary = NotovaPurple,
        secondary = NotovaTeal,
    )

private val DarkColors =
    darkColorScheme(
        primary = NotovaPurpleDark,
        secondary = NotovaTeal,
    )

@Composable
fun NotovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NotovaTypography,
        content = content,
    )
}
