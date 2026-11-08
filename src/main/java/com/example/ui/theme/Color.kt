package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val AirflowBackground = Color(0xFF0B0D12)
val AirflowBars = Color(0xFF0F1218)
val AirflowPanel = Color(0xFF151922)
val AirflowPanel2 = Color(0xFF1B202B)
val AirflowBorder = Color(0xFF232A38)
val AirflowText = Color(0xFFE9ECF3)
val AirflowTextSecondary = Color(0xFF8A92A6)
val AirflowAccent1 = Color(0xFF6D8DFF)
val AirflowAccent2 = Color(0xFF9A6DFF)
val AirflowSuccess = Color(0xFF3DDC97)
val AirflowError = Color(0xFFFF6B6B)

val AccentGradient = Brush.horizontalGradient(
    colors = listOf(AirflowAccent1, AirflowAccent2)
)

val AccentGradientVertical = Brush.verticalGradient(
    colors = listOf(AirflowAccent1, AirflowAccent2)
)
