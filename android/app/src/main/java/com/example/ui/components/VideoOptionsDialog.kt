package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AudioTrack
import com.example.data.model.ServerSettings
import com.example.data.model.SubtitleTrack
import com.example.data.model.VideoFile
import com.example.data.model.VideoInfo
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AirflowBackground
import com.example.ui.theme.AirflowBorder
import com.example.ui.theme.AirflowError
import com.example.ui.theme.AirflowPanel
import com.example.ui.theme.AirflowPanel2
import com.example.ui.theme.AirflowText
import com.example.ui.theme.AirflowTextSecondary
import com.example.ui.util.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoOptionsDialog(
    r: Int,
    video: VideoFile,
    videoInfo: VideoInfo?,
    isLoading: Boolean,
    settings: ServerSettings,
    onDismiss: () -> Unit,
    onPlay: (audio: Int?, sub: Int?) -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var selectedAudioIndex by remember { mutableIntStateOf(0) }
    var selectedSubIndex by remember { mutableIntStateOf(-1) }

    // Calculate default audio and subtitle when videoInfo loads
    LaunchedEffect(videoInfo, settings) {
        if (videoInfo != null) {
            // Audio: default to first audio track index if available
            if (videoInfo.audio.isNotEmpty()) {
                selectedAudioIndex = videoInfo.audio.first().i
            }
            // Subtitle preselection rule
            if (settings.autoSub) {
                val compatible = videoInfo.subs.filter { !it.isImageSubtitle }
                val defSub = compatible.firstOrNull { it.def }
                    ?: compatible.firstOrNull { it.forced }
                    ?: compatible.firstOrNull {
                        val l = it.lang.lowercase()
                        l.startsWith("spa") || l.startsWith("es")
                    }
                selectedSubIndex = defSub?.i ?: -1
            } else {
                selectedSubIndex = -1
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = AirflowPanel,
            border = BorderStroke(1.dp, AirflowBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with title and close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AirflowText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (video.pos > 0) {
                            Text(
                                text = "Continuar ${FormatUtils.formatSeconds(video.pos)}",
                                fontSize = 12.sp,
                                color = Color(0xFF6D8DFF),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = AirflowTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color(0xFF6D8DFF),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Cargando información del video...",
                                fontSize = 13.sp,
                                color = AirflowTextSecondary
                            )
                        }
                    }
                } else if (videoInfo != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Chips Section
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (videoInfo.vcodec.isNotBlank()) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(videoInfo.vcodec.uppercase()) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = AirflowPanel2,
                                        labelColor = AirflowText
                                    ),
                                    border = BorderStroke(1.dp, AirflowBorder)
                                )
                            }
                            if (videoInfo.resolutionLabel.isNotBlank()) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(videoInfo.resolutionLabel) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = AirflowPanel2,
                                        labelColor = AirflowText
                                    ),
                                    border = BorderStroke(1.dp, AirflowBorder)
                                )
                            }
                            if (videoInfo.fps > 0) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(String.format("%.1f fps", videoInfo.fps)) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = AirflowPanel2,
                                        labelColor = AirflowText
                                    ),
                                    border = BorderStroke(1.dp, AirflowBorder)
                                )
                            }
                            if (videoInfo.duration > 0) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(FormatUtils.formatSeconds(videoInfo.duration)) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = AirflowPanel2,
                                        labelColor = AirflowText
                                    ),
                                    border = BorderStroke(1.dp, AirflowBorder)
                                )
                            }
                            if (videoInfo.isHevc) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Se convierte en vivo", fontWeight = FontWeight.Bold) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color(0x33FFB020),
                                        labelColor = Color(0xFFFFB020)
                                    ),
                                    border = BorderStroke(1.dp, Color(0x66FFB020))
                                )
                            }
                        }

                        // Audio Track Selector
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AirflowPanel2),
                            border = BorderStroke(1.dp, AirflowBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = Color(0xFF6D8DFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Pista de audio",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = AirflowText
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                if (videoInfo.audio.isEmpty()) {
                                    Text(
                                        text = "Audio predeterminado",
                                        fontSize = 13.sp,
                                        color = AirflowTextSecondary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    videoInfo.audio.forEach { track ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { selectedAudioIndex = track.i }
                                                .padding(vertical = 4.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = selectedAudioIndex == track.i,
                                                onClick = { selectedAudioIndex = track.i },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = Color(0xFF6D8DFF),
                                                    unselectedColor = AirflowTextSecondary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = track.displayLabel,
                                                fontSize = 13.sp,
                                                color = AirflowText
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Subtitle Track Selector
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AirflowPanel2),
                            border = BorderStroke(1.dp, AirflowBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = Color(0xFF9A6DFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Subtítulos",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = AirflowText
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                // Option "Sin subtítulos"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedSubIndex = -1 }
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedSubIndex == -1,
                                        onClick = { selectedSubIndex = -1 },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF9A6DFF),
                                            unselectedColor = AirflowTextSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sin subtítulos",
                                        fontSize = 13.sp,
                                        color = if (selectedSubIndex == -1) AirflowText else AirflowTextSecondary,
                                        fontWeight = if (selectedSubIndex == -1) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                // Subtitle Tracks
                                videoInfo.subs.forEach { sub ->
                                    val isSupported = !sub.isImageSubtitle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = isSupported) {
                                                if (isSupported) selectedSubIndex = sub.i
                                            }
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedSubIndex == sub.i,
                                            onClick = { if (isSupported) selectedSubIndex = sub.i },
                                            enabled = isSupported,
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF9A6DFF),
                                                unselectedColor = AirflowTextSecondary,
                                                disabledSelectedColor = AirflowBorder,
                                                disabledUnselectedColor = AirflowBorder
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = sub.displayLabel,
                                            fontSize = 13.sp,
                                            color = when {
                                                !isSupported -> AirflowTextSecondary.copy(alpha = 0.5f)
                                                selectedSubIndex == sub.i -> AirflowText
                                                else -> AirflowTextSecondary
                                            },
                                            fontWeight = if (selectedSubIndex == sub.i) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddToQueue,
                        border = BorderStroke(1.dp, AirflowBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Queue,
                            contentDescription = null,
                            tint = AirflowText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cola", color = AirflowText, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onAddToPlaylist,
                        border = BorderStroke(1.dp, AirflowBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = AirflowText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Playlist", color = AirflowText, fontSize = 12.sp)
                    }

                    // Play Button with gradient
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                onPlay(
                                    selectedAudioIndex.takeIf { videoInfo?.audio?.isNotEmpty() == true },
                                    selectedSubIndex
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reproducir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
