package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerSettings
import com.example.data.model.ServerState
import com.example.data.model.SubtitleStyle
import com.example.ui.MKVCastViewModel
import com.example.ui.theme.AirflowBackground
import com.example.ui.theme.AirflowBorder
import com.example.ui.theme.AirflowError
import com.example.ui.theme.AirflowPanel
import com.example.ui.theme.AirflowPanel2
import com.example.ui.theme.AirflowSuccess
import com.example.ui.theme.AirflowText
import com.example.ui.theme.AirflowTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MKVCastViewModel,
    state: ServerState?,
    modifier: Modifier = Modifier
) {
    val settings = state?.settings ?: ServerSettings()
    val devices = state?.devices ?: emptyList()
    val currentDevice = state?.device

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AirflowBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Ajustes",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AirflowText
            )
        }

        // Section: Chromecast Devices
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                border = BorderStroke(1.dp, AirflowBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            tint = Color(0xFF6D8DFF),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Dispositivo Chromecast",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AirflowText
                        )
                    }

                    if (devices.isEmpty()) {
                        Text(
                            text = "Buscando dispositivos Chromecast en la red...",
                            fontSize = 13.sp,
                            color = AirflowTextSecondary
                        )
                    } else {
                        Text(
                            text = "Elegí a qué pantalla transmitir:",
                            fontSize = 13.sp,
                            color = AirflowTextSecondary
                        )
                        devices.forEach { dev ->
                            val isSelected = dev.equals(currentDevice, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0x226D8DFF) else AirflowPanel2)
                                    .clickable { viewModel.selectChromecastDevice(dev) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectChromecastDevice(dev) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF6D8DFF),
                                        unselectedColor = AirflowTextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = dev,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = AirflowText
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "Conectado",
                                            fontSize = 11.sp,
                                            color = AirflowSuccess,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Subtitles Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                border = BorderStroke(1.dp, AirflowBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = Color(0xFF9A6DFF),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Subtítulos",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AirflowText
                            )
                        }

                        // Reset style button
                        TextButton(
                            onClick = {
                                viewModel.updateSettings(
                                    settings.copy(
                                        style = SubtitleStyle(
                                            color = "white",
                                            bg = "none",
                                            bgOpacity = 60,
                                            outline = 1,
                                            shadow = 0,
                                            pos = "bottom"
                                        )
                                    )
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF6D8DFF)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restablecer", fontSize = 12.sp, color = Color(0xFF6D8DFF))
                        }
                    }

                    // Auto Subtitles toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Subtítulos automáticos",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AirflowText
                            )
                            Text(
                                text = if (settings.autoSub) "Activar subtítulo al reproducir" else "Sin subtítulos al iniciar",
                                fontSize = 12.sp,
                                color = AirflowTextSecondary
                            )
                        }
                        Switch(
                            checked = settings.autoSub,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(autoSub = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6D8DFF)
                            )
                        )
                    }

                    // Subtitle Size
                    Column {
                        Text(
                            text = "Tamaño",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AirflowText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "small" to "Chico",
                                "normal" to "Normal",
                                "large" to "Grande",
                                "xlarge" to "Enorme"
                            ).forEach { (sizeKey, label) ->
                                val isSelected = settings.subSize.equals(sizeKey, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateSettings(settings.copy(subSize = sizeKey)) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF9A6DFF),
                                        selectedLabelColor = Color.White,
                                        containerColor = AirflowPanel2,
                                        labelColor = AirflowTextSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF9A6DFF) else AirflowBorder),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // ASS Style Respect
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Subtítulos con estilo (ASS)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AirflowText
                            )
                            Text(
                                text = if (settings.assRespect) "Respetar estilo original" else "Usar mi estilo personalizado",
                                fontSize = 12.sp,
                                color = AirflowTextSecondary
                            )
                        }
                        Switch(
                            checked = settings.assRespect,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(assRespect = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF9A6DFF)
                            )
                        )
                    }

                    // Subtitle Color (white, yellow, cyan, green, orange, pink, gray)
                    Column {
                        Text(
                            text = "Color del texto",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AirflowText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "white" to "Blanco",
                                "yellow" to "Amarillo",
                                "cyan" to "Cian",
                                "green" to "Verde",
                                "orange" to "Naranja",
                                "pink" to "Rosa",
                                "gray" to "Gris"
                            ).forEach { (colorKey, label) ->
                                val isSelected = settings.style.color.equals(colorKey, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateSettings(
                                            settings.copy(style = settings.style.copy(color = colorKey))
                                        )
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF6D8DFF),
                                        selectedLabelColor = Color.White,
                                        containerColor = AirflowPanel2,
                                        labelColor = AirflowTextSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF6D8DFF) else AirflowBorder)
                                )
                            }
                        }
                    }

                    // Background: none / box
                    Column {
                        Text(
                            text = "Fondo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AirflowText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("none" to "Sin fondo", "box" to "Caja de fondo").forEach { (bgKey, label) ->
                                val isSelected = settings.style.bg.equals(bgKey, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateSettings(
                                            settings.copy(style = settings.style.copy(bg = bgKey))
                                        )
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF6D8DFF),
                                        selectedLabelColor = Color.White,
                                        containerColor = AirflowPanel2,
                                        labelColor = AirflowTextSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF6D8DFF) else AirflowBorder),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Background Opacity (30, 50, 70, 100) if bg is box
                    if (settings.style.bg == "box") {
                        Column {
                            Text(
                                text = "Opacidad del fondo: ${settings.style.bgOpacity}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AirflowText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30, 50, 70, 100).forEach { op ->
                                    val isSelected = settings.style.bgOpacity == op
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.updateSettings(
                                                settings.copy(style = settings.style.copy(bgOpacity = op))
                                            )
                                        },
                                        label = { Text("$op%", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF6D8DFF),
                                            selectedLabelColor = Color.White,
                                            containerColor = AirflowPanel2,
                                            labelColor = AirflowTextSecondary
                                        ),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF6D8DFF) else AirflowBorder),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Outline (0 to 4)
                    Column {
                        var localOutline by remember(settings.style.outline) {
                            mutableFloatStateOf(settings.style.outline.toFloat())
                        }
                        Text(
                            text = "Borde: ${localOutline.toInt()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AirflowText
                        )
                        Slider(
                            value = localOutline,
                            onValueChange = { localOutline = it },
                            onValueChangeFinished = {
                                viewModel.updateSettings(
                                    settings.copy(style = settings.style.copy(outline = localOutline.toInt()))
                                )
                            },
                            valueRange = 0f..4f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6D8DFF),
                                activeTrackColor = Color(0xFF6D8DFF),
                                inactiveTrackColor = AirflowBorder
                            )
                        )
                    }

                    // Shadow (0 to 3, only if background is "none")
                    if (settings.style.bg == "none") {
                        Column {
                            var localShadow by remember(settings.style.shadow) {
                                mutableFloatStateOf(settings.style.shadow.toFloat())
                            }
                            Text(
                                text = "Sombra: ${localShadow.toInt()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AirflowText
                            )
                            Slider(
                                value = localShadow,
                                onValueChange = { localShadow = it },
                                onValueChangeFinished = {
                                    viewModel.updateSettings(
                                        settings.copy(style = settings.style.copy(shadow = localShadow.toInt()))
                                    )
                                },
                                valueRange = 0f..3f,
                                steps = 2,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF6D8DFF),
                                    activeTrackColor = Color(0xFF6D8DFF),
                                    inactiveTrackColor = AirflowBorder
                                )
                            )
                        }
                    }

                    // Position (bottom, raised, middle, top)
                    Column {
                        Text(
                            text = "Posición en pantalla",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AirflowText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "bottom" to "Abajo",
                                "raised" to "Elevado",
                                "middle" to "Medio",
                                "top" to "Arriba"
                            ).forEach { (posKey, label) ->
                                val isSelected = settings.style.pos.equals(posKey, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateSettings(
                                            settings.copy(style = settings.style.copy(pos = posKey))
                                        )
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF6D8DFF),
                                        selectedLabelColor = Color.White,
                                        containerColor = AirflowPanel2,
                                        labelColor = AirflowTextSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF6D8DFF) else AirflowBorder),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Imagen y Sonido
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                border = BorderStroke(1.dp, AirflowBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF6D8DFF),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Imagen y sonido",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AirflowText
                        )
                    }

                    // HDR Colors Fix
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Colores de videos HDR",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AirflowText
                            )
                            Text(
                                text = if (settings.hdrFix) "Corregir gama de colores" else "No tocar colores originales",
                                fontSize = 12.sp,
                                color = AirflowTextSecondary
                            )
                        }
                        Switch(
                            checked = settings.hdrFix,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(hdrFix = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6D8DFF)
                            )
                        )
                    }

                    // Audio Mode (normal, dialog, night)
                    Column {
                        Text(
                            text = "Modo de audio",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AirflowText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "normal" to "Normal",
                                "dialog" to "Diálogos claros",
                                "night" to "Modo noche"
                            ).forEach { (modeKey, label) ->
                                val isSelected = settings.audioMode.equals(modeKey, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateSettings(settings.copy(audioMode = modeKey)) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF6D8DFF),
                                        selectedLabelColor = Color.White,
                                        containerColor = AirflowPanel2,
                                        labelColor = AirflowTextSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF6D8DFF) else AirflowBorder),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Battery Optimization Suggestion
        item {
            val context = LocalContext.current
            val isIgnoringBatteryOptimizations = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                } else true
            }

            if (!isIgnoringBatteryOptimizations) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                    border = BorderStroke(1.dp, Color(0x66FFB74D)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Optimización de batería",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AirflowText
                            )
                        }

                        Text(
                            text = "Para que la notificación y el control remoto sigan respondiendo sin interrupciones con la pantalla apagada o bloqueada, podés permitir que MKVCast funcione en segundo plano.",
                            fontSize = 13.sp,
                            color = AirflowTextSecondary,
                            lineHeight = 18.sp
                        )

                        OutlinedButton(
                            onClick = {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    } catch (ex: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Desactivar optimización de batería", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Section: Server Info & Change Server
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                border = BorderStroke(1.dp, AirflowBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = AirflowTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Servidor MKVCast",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AirflowText
                        )
                    }

                    Text(
                        text = "Dirección: ${viewModel.apiClient.baseUrl}",
                        fontSize = 13.sp,
                        color = AirflowTextSecondary
                    )

                    if (!state?.encoder.isNullOrBlank()) {
                        Text(
                            text = "Codificador en la PC: ${state?.encoder}",
                            fontSize = 13.sp,
                            color = AirflowTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6D8DFF)),
                        border = BorderStroke(1.dp, Color(0xFF6D8DFF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_server_button")
                    ) {
                        Text("Cambiar de servidor", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
