package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.model.QueueItem
import com.example.data.model.ServerState
import com.example.ui.MKVCastViewModel
import com.example.ui.components.AsyncThumbnail
import com.example.ui.theme.AirflowBackground
import com.example.ui.theme.AirflowBorder
import com.example.ui.theme.AirflowPanel
import com.example.ui.theme.AirflowPanel2
import com.example.ui.theme.AirflowSuccess
import com.example.ui.theme.AirflowText
import com.example.ui.theme.AirflowTextSecondary

@Composable
fun QueueScreen(
    viewModel: MKVCastViewModel,
    state: ServerState?,
    modifier: Modifier = Modifier
) {
    val queue = state?.queue ?: emptyList()
    val currentIndex = state?.qi ?: -1

    var showSaveDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AirflowBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Cola de reproducción",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AirflowText
                )
                Text(
                    text = "${queue.size} videos en cola",
                    fontSize = 12.sp,
                    color = AirflowTextSecondary
                )
            }

            if (queue.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Save queue as playlist
                    OutlinedButton(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6D8DFF)),
                        border = BorderStroke(1.dp, Color(0x666D8DFF)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar", fontSize = 12.sp)
                    }

                    // Clear queue
                    OutlinedButton(
                        onClick = { viewModel.clearQueue() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AirflowTextSecondary),
                        border = BorderStroke(1.dp, AirflowBorder),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("clear_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vaciar", fontSize = 12.sp)
                    }
                }
            }
        }

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "La cola está vacía",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AirflowText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Agregá videos desde la biblioteca tocando '＋ Cola'.",
                        fontSize = 13.sp,
                        color = AirflowTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(queue) { index, item ->
                    val isCurrentPlaying = index == currentIndex

                    QueueItemRow(
                        item = item,
                        index = index,
                        isCurrentPlaying = isCurrentPlaying,
                        thumbnailUrl = viewModel.apiClient.getThumbnailUrl(item.r, item.p),
                        token = viewModel.apiClient.token,
                        onClick = { viewModel.playVideo(item.r, item.p) },
                        onRemove = { viewModel.removeFromQueue(index) }
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = AirflowPanel,
            title = {
                Text(
                    text = "Guardar como playlist",
                    color = AirflowText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ingresá un nombre para guardar los ${queue.size} videos como una playlist permanente:",
                        color = AirflowTextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Nombre de la playlist") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AirflowText,
                            unfocusedTextColor = AirflowText,
                            focusedBorderColor = Color(0xFF6D8DFF),
                            unfocusedBorderColor = AirflowBorder,
                            focusedContainerColor = AirflowPanel2,
                            unfocusedContainerColor = AirflowPanel2
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveQueueAsPlaylist(playlistName)
                        showSaveDialog = false
                        playlistName = ""
                    },
                    enabled = playlistName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D8DFF))
                ) {
                    Text("Guardar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancelar", color = AirflowTextSecondary)
                }
            }
        )
    }
}

@Composable
fun QueueItemRow(
    item: QueueItem,
    index: Int,
    isCurrentPlaying: Boolean,
    thumbnailUrl: String,
    token: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentPlaying) Color(0x226D8DFF) else AirflowPanel
        ),
        border = BorderStroke(
            1.dp,
            if (isCurrentPlaying) Color(0xFF6D8DFF) else AirflowBorder
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index number
            Text(
                text = "${index + 1}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrentPlaying) Color(0xFF6D8DFF) else AirflowTextSecondary,
                modifier = Modifier.width(24.dp)
            )

            // Video Thumbnail
            AsyncThumbnail(
                url = thumbnailUrl,
                token = token,
                contentDescription = item.fileName,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(width = 64.dp, height = 44.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Video details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = AirflowText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrentPlaying) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "▶ En reproducción",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6D8DFF)
                    )
                }
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Quitar de la cola",
                    tint = AirflowTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
