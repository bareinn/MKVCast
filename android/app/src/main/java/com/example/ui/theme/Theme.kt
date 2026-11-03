package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MKVCastDarkColorScheme = darkColorScheme(
    primary = AirflowAccent1,
    onPrimary = AirflowBackground,
    primaryContainer = AirflowPanel2,
    onPrimaryContainer = AirflowText,
    secondary = AirflowAccent2,
    onSecondary = AirflowBackground,
    secondaryContainer = AirflowPanel2,
    onSecondaryContainer = AirflowText,
    tertiary = AirflowSuccess,
    onTertiary = AirflowBackground,
    error = AirflowError,
    onError = AirflowBackground,
    background = AirflowBackground,
    onBackground = AirflowText,
    surface = AirflowPanel,
    onSurface = AirflowText,
    surfaceVariant = AirflowPanel2,
    onSurfaceVariant = AirflowTextSecondary,
    outline = AirflowBorder,
    outlineVariant = AirflowBorder
)

val MKVCastShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MKVCastTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MKVCastDarkColorScheme,
        shapes = MKVCastShapes,
        typography = Typography,
        content = content
    )
}
