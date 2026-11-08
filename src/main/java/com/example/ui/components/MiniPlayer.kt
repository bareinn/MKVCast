package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NowPlaying
import com.example.data.model.PlaybackStatus
import com.example.ui.theme.AirflowBorder
import com.example.ui.theme.AirflowPanel
import com.example.ui.theme.AirflowSuccess
import com.example.ui.theme.AirflowText
import com.example.ui.theme.AirflowTextSecondary
import com.example.ui.util.FormatUtils

@Composable
fun MiniPlayer(
    now: NowPlaying,
    status: PlaybackStatus,
    thumbnailUrl: String,
    token: String,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (now.duration > 0) {
        (status.pos / now.duration).toFloat().coerceIn(0f, 1f)
    } else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = AirflowPanel),
        border = BorderStroke(1.dp, AirflowBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onExpand() }
            .testTag("mini_player")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video thumbnail
                AsyncThumbnail(
                    url = thumbnailUrl,
                    token = token,
                    contentDescription = now.name,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(width = 56.dp, height = 40.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title and progress info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = now.name,
                        color = AirflowText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (status.state) {
                                "PLAYING" -> "Reproduciendo"
                                "PAUSED" -> "Pausado"
                                "BUFFERING" -> "Cargando..."
                                else -> status.state
                            },
                            color = if (status.isPlaying) AirflowSuccess else AirflowTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = " · ${FormatUtils.formatSeconds(status.pos)} / ${FormatUtils.formatSeconds(now.duration)}",
                            color = AirflowTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Controls
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.testTag("mini_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (status.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (status.isPlaying) "Pausar" else "Reanudar",
                        tint = Color(0xFF6D8DFF),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onStop,
                    modifier = Modifier.testTag("mini_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Detener",
                        tint = AirflowTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Subtle bottom progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color(0xFF6D8DFF),
                trackColor = AirflowBorder,
            )
        }
    }
}
