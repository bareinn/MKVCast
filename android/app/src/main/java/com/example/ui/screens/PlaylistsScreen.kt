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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
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
import com.example.data.model.PlaylistItem
import com.example.data.model.QueueItem
import com.example.data.model.ServerState
import com.example.ui.MKVCastViewModel
import com.example.ui.components.AsyncThumbnail
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AirflowBackground
import com.example.ui.theme.AirflowBorder
import com.example.ui.theme.AirflowError
import com.example.ui.theme.AirflowPanel
import com.example.ui.theme.AirflowPanel2
import com.example.ui.theme.AirflowText
import com.example.ui.theme.AirflowTextSecondary

@Composable
fun PlaylistsScreen(
    viewModel: MKVCastViewModel,
    state: ServerState?,
    modifier: Modifier = Modifier
) {
    val playlists = state?.playlists ?: emptyList()

    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val activePlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AirflowBackground)
    ) {
        if (activePlaylist == null) {
            // All Playlists List View
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Playlists",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AirflowText
                    )
                    Text(
                        text = "${playlists.size} listas guardadas",
                        fontSize = 12.sp,
                        color = AirflowTextSecondary
                    )
                }

                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AirflowPanel2,
                        contentColor = Color(0xFF6D8DFF)
                    ),
                    border = BorderStroke(1.dp, Color(0x666D8DFF)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("new_playlist_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No tenés playlists",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AirflowText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Creá una nueva o guardá la cola actual desde la pestaña Cola.",
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
                    items(playlists) { pl ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                            border = BorderStroke(1.dp, AirflowBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPlaylistId = pl.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x339A6DFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Color(0xFF9A6DFF),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AirflowText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${pl.items.size} videos",
                                        fontSize = 13.sp,
                                        color = AirflowTextSecondary
                                    )
                                }

                                Text(
                                    text = "›",
                                    fontSize = 20.sp,
                                    color = AirflowTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Playlist Detail View
            PlaylistDetailView(
                playlist = activePlaylist,
                viewModel = viewModel,
                onBack = { selectedPlaylistId = null }
            )
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = AirflowPanel,
            title = {
                Text(text = "Nueva playlist", color = AirflowText, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nombre") },
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPlaylist(newPlaylistName)
                        showCreateDialog = false
                        newPlaylistName = ""
                    },
                    enabled = newPlaylistName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D8DFF))
                ) {
                    Text("Crear", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar", color = AirflowTextSecondary)
                }
            }
        )
    }
}

@Composable
fun PlaylistDetailView(
    playlist: PlaylistItem,
    viewModel: MKVCastViewModel,
    onBack: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(playlist.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Detail Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = AirflowText
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AirflowText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.items.size} videos",
                    fontSize = 12.sp,
                    color = AirflowTextSecondary
                )
            }

            IconButton(onClick = { showRenameDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Renombrar",
                    tint = AirflowTextSecondary
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar playlist",
                    tint = AirflowError
                )
            }
        }

        // Action Buttons: Reproducir, Mezclar 🔀, Agregar a la cola
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { viewModel.loadPlaylist(playlist.id, mode = "replace", play = true, shuffle = false) },
                    enabled = playlist.items.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reproducir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Shuffle Button 🔀
            OutlinedButton(
                onClick = { viewModel.loadPlaylist(playlist.id, mode = "replace", play = true, shuffle = true) },
                enabled = playlist.items.isNotEmpty(),
                border = BorderStroke(1.dp, Color(0xFF9A6DFF)),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF9A6DFF)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Mezclar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Append to queue Button
            OutlinedButton(
                onClick = { viewModel.loadPlaylist(playlist.id, mode = "append", play = false) },
                enabled = playlist.items.isNotEmpty(),
                border = BorderStroke(1.dp, AirflowBorder),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AirflowText),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Queue, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Cola", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Playlist items list with reorder and remove
        if (playlist.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay videos en esta playlist.",
                    color = AirflowTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(playlist.items) { index, item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                        border = BorderStroke(1.dp, AirflowBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playVideo(item.r, item.p) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AirflowTextSecondary,
                                modifier = Modifier.width(22.dp)
                            )

                            AsyncThumbnail(
                                url = viewModel.apiClient.getThumbnailUrl(item.r, item.p),
                                token = viewModel.apiClient.token,
                                contentDescription = item.fileName,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(width = 60.dp, height = 40.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = item.fileName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AirflowText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Move Up
                            if (index > 0) {
                                IconButton(
                                    onClick = { viewModel.movePlaylistItem(playlist.id, index, index - 1) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Subir",
                                        tint = AirflowTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Move Down
                            if (index < playlist.items.size - 1) {
                                IconButton(
                                    onClick = { viewModel.movePlaylistItem(playlist.id, index, index + 1) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Bajar",
                                        tint = AirflowTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Remove item
                            IconButton(
                                onClick = { viewModel.removePlaylistItem(playlist.id, index) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Quitar",
                                    tint = AirflowTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = AirflowPanel,
            title = {
                Text(text = "Renombrar playlist", color = AirflowText, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Nuevo nombre") },
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renamePlaylist(playlist.id, renameInput)
                        showRenameDialog = false
                    },
                    enabled = renameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D8DFF))
                ) {
                    Text("Renombrar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar", color = AirflowTextSecondary)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = AirflowPanel,
            title = {
                Text(text = "Borrar playlist", color = AirflowText, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "¿Seguro que querés borrar '${playlist.name}'?",
                    color = AirflowTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(playlist.id)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AirflowError)
                ) {
                    Text("Borrar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = AirflowTextSecondary)
                }
            }
        )
    }
}
