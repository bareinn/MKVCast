package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NowPlaying
import com.example.data.model.PlaybackStatus
import com.example.data.model.ServerSettings
import com.example.ui.MKVCastViewModel
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AirflowBackground
import com.example.ui.theme.AirflowBorder
import com.example.ui.theme.AirflowPanel
import com.example.ui.theme.AirflowPanel2
import com.example.ui.theme.AirflowSuccess
import com.example.ui.theme.AirflowText
import com.example.ui.theme.AirflowTextSecondary
import com.example.ui.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedPlayerSheet(
    viewModel: MKVCastViewModel,
    now: NowPlaying,
    status: PlaybackStatus,
    settings: ServerSettings,
    thumbnailUrl: String,
    token: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pendingSubDelay by viewModel.pendingSubDelay.collectAsState()

    // Local seek position during scrubbing
    var isDraggingSeek by remember { mutableStateOf(false) }
    var localSeekPosition by remember { mutableFloatStateOf(status.pos.toFloat()) }

    // Volume state
    var isDraggingVolume by remember { mutableStateOf(false) }
    var localVolume by remember { mutableFloatStateOf(status.volume ?: 1.0f) }

    LaunchedEffect(status.volume) {
        if (!isDraggingVolume) {
            status.volume?.let { serverVol ->
                localVolume = serverVol
            }
        }
    }

    val keyVolume by viewModel.uiVolume.collectAsState()
    LaunchedEffect(keyVolume) {
        if (!isDraggingVolume) keyVolume?.let { localVolume = it }
    }

    val displayPos = if (isDraggingSeek) localSeekPosition.toDouble() else status.pos
    val duration = now.duration.coerceAtLeast(1.0)

    val currentDelay = pendingSubDelay ?: now.delay

    val volumeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { volumeFocus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AirflowBackground,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .onPreviewKeyEvent { event ->
                    if ((event.key == Key.VolumeUp || event.key == Key.VolumeDown) &&
                        viewModel.shouldHandleVolumeKeys()) {
                        if (event.type == KeyEventType.KeyDown) {
                            viewModel.changeVolume(if (event.key == Key.VolumeUp) 0.05f else -0.05f)
                        }
                        true
                    } else false
                }
                .focusRequester(volumeFocus)
                .focusable()
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Header / Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Ocultar",
                        tint = AirflowText,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "REPRODUCIENDO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = AirflowTextSecondary
                )

                IconButton(
                    onClick = {
                        viewModel.stopPlayback()
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Detener reproducción",
                        tint = AirflowTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Big Thumbnail Card
            Card(
                colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                border = BorderStroke(1.dp, AirflowBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                AsyncThumbnail(
                    url = thumbnailUrl,
                    token = token,
                    contentDescription = now.name,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title & State Badge
            Text(
                text = now.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AirflowText,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (status.isPlaying) AirflowSuccess else Color(0xFFFFB020))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (status.state) {
                        "PLAYING" -> "En reproducción"
                        "PAUSED" -> "Pausado"
                        "BUFFERING" -> "Cargando video..."
                        else -> status.state
                    },
                    fontSize = 13.sp,
                    color = AirflowTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Slider & Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = displayPos.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = {
                        isDraggingSeek = true
                        localSeekPosition = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(localSeekPosition.toDouble())
                        isDraggingSeek = false
                    },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6D8DFF),
                        activeTrackColor = Color(0xFF6D8DFF),
                        inactiveTrackColor = AirflowBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playback_seek_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = FormatUtils.formatSeconds(displayPos),
                        fontSize = 12.sp,
                        color = AirflowTextSecondary
                    )
                    Text(
                        text = FormatUtils.formatSeconds(now.duration),
                        fontSize = 12.sp,
                        color = AirflowTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Playback Controls: Prev, -30s, Play/Pause, +30s, Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = AirflowText,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.seekRelative(-30.0) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay30,
                        contentDescription = "-30 segundos",
                        tint = AirflowText,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Play / Pause gradient button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(AccentGradient)
                        .clickable { viewModel.togglePlayPause() }
                        .testTag("expanded_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (status.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (status.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.seekRelative(30.0) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward30,
                        contentDescription = "+30 segundos",
                        tint = AirflowText,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = AirflowText,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Volume Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = null,
                    tint = AirflowTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = localVolume.coerceIn(0f, 1f),
                    onValueChange = {
                        isDraggingVolume = true
                        localVolume = it
                    },
                    onValueChangeFinished = {
                        isDraggingVolume = false
                        viewModel.setVolume(localVolume)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF9A6DFF),
                        activeTrackColor = Color(0xFF9A6DFF),
                        inactiveTrackColor = AirflowBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("volume_slider")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = AirflowTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Live Subtitles Section
            Card(
                colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                border = BorderStroke(1.dp, AirflowBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Subtítulos en vivo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AirflowText
                    )

                    // Subtitle Size Selector (small, normal, large, xlarge)
                    Column {
                        Text(
                            text = "Tamaño",
                            fontSize = 12.sp,
                            color = AirflowTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sizes = listOf(
                                "small" to "Chico",
                                "normal" to "Normal",
                                "large" to "Grande",
                                "xlarge" to "Enorme"
                            )
                            sizes.forEach { (sizeKey, label) ->
                                val isSelected = settings.subSize.equals(sizeKey, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateSettings(settings.copy(subSize = sizeKey))
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

                    // Subtitle Sync Controls (−, label, +)
                    Column {
                        Text(
                            text = "Sincronía (toque en la etiqueta para reiniciar a 0)",
                            fontSize = 12.sp,
                            color = AirflowTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Minus button (-0.5s)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AirflowPanel2)
                                    .border(1.dp, AirflowBorder, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.adjustSubDelay(-0.5) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "−0,5 s",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AirflowText
                                )
                            }

                            // Sync Label (clickable to reset to 0)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x226D8DFF))
                                    .border(1.dp, Color(0x556D8DFF), RoundedCornerShape(10.dp))
                                    .clickable { viewModel.resetSubDelay() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = FormatUtils.formatDelay(currentDelay),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6D8DFF)
                                )
                            }

                            // Plus button (+0.5s)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AirflowPanel2)
                                    .border(1.dp, AirflowBorder, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.adjustSubDelay(0.5) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+0,5 s",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AirflowText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
