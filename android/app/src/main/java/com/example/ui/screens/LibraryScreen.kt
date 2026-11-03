package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.data.model.FolderContent
import com.example.data.model.RecentItem
import com.example.data.model.ServerState
import com.example.data.model.VideoFile
import com.example.ui.MKVCastViewModel
import com.example.ui.components.AsyncThumbnail
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AirflowBackground
import com.example.ui.theme.AirflowBorder
import com.example.ui.theme.AirflowPanel
import com.example.ui.theme.AirflowPanel2
import com.example.ui.theme.AirflowSuccess
import com.example.ui.theme.AirflowText
import com.example.ui.theme.AirflowTextSecondary
import com.example.ui.util.FormatUtils

@Composable
fun LibraryScreen(
    viewModel: MKVCastViewModel,
    state: ServerState?,
    modifier: Modifier = Modifier
) {
    val currentFolderIndex by viewModel.currentFolderIndex.collectAsState()
    val currentFolderPath by viewModel.currentFolderPath.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val folderContent by viewModel.currentFolderContent.collectAsState()
    val isFolderLoading by viewModel.isFolderLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()

    // File picker launcher for uploading video files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadVideoFromUri(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AirflowBackground)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Biblioteca",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AirflowText
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Upload button ("＋ Archivos")
                Button(
                    onClick = { filePickerLauncher.launch("video/*") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AirflowPanel2,
                        contentColor = Color(0xFF6D8DFF)
                    ),
                    border = BorderStroke(1.dp, Color(0x666D8DFF)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    enabled = !isUploading,
                    modifier = Modifier.testTag("upload_video_button")
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            color = Color(0xFF6D8DFF),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Subiendo...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Archivos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (currentFolderIndex != null) {
                            viewModel.refreshCurrentFolder()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar",
                        tint = AirflowTextSecondary
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Buscar videos y carpetas...", color = AirflowTextSecondary, fontSize = 13.sp) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AirflowTextSecondary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpiar", tint = AirflowTextSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AirflowText,
                unfocusedTextColor = AirflowText,
                focusedBorderColor = Color(0xFF6D8DFF),
                unfocusedBorderColor = AirflowBorder,
                focusedContainerColor = AirflowPanel,
                unfocusedContainerColor = AirflowPanel
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area: Root level vs Inside a folder
        if (currentFolderIndex == null) {
            RootLibraryView(
                viewModel = viewModel,
                state = state,
                searchQuery = searchQuery
            )
        } else {
            FolderExplorerView(
                viewModel = viewModel,
                folderIndex = currentFolderIndex!!,
                folderContent = folderContent,
                breadcrumbs = breadcrumbs,
                isLoading = isFolderLoading,
                searchQuery = searchQuery
            )
        }
    }
}

@Composable
fun RootLibraryView(
    viewModel: MKVCastViewModel,
    state: ServerState?,
    searchQuery: String
) {
    val recentItems = state?.recent ?: emptyList()
    val folders = state?.folders ?: emptyList()
    val uploadsIdx = state?.uploadsIdx ?: -1

    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) folders
        else folders.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // "Continuar viendo" Row
        if (recentItems.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                    Text(
                        text = "Continuar viendo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AirflowText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recentItems) { item ->
                            RecentVideoCard(
                                item = item,
                                thumbnailUrl = viewModel.apiClient.getThumbnailUrl(item.r, item.p),
                                token = viewModel.apiClient.token,
                                onResume = { viewModel.resumeVideo(item.r, item.p) },
                                onRemove = { viewModel.removeFromRecent(item.r, item.p) }
                            )
                        }
                    }
                }
            }
        }

        // Root Folders Header
        item {
            Text(
                text = "Carpetas de la PC",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AirflowText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (filteredFolders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron carpetas.",
                        color = AirflowTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(filteredFolders.size) { index ->
                val fullPath = filteredFolders[index]
                val originalIndex = folders.indexOf(fullPath)
                val isUploads = originalIndex == uploadsIdx
                val displayName = if (isUploads) "Soltados" else fullPath.replace('\\', '/').substringAfterLast('/').ifBlank { fullPath }

                Card(
                    colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                    border = BorderStroke(1.dp, AirflowBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { viewModel.openFolder(originalIndex, "") }
                        .testTag("root_folder_$originalIndex")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isUploads) Color(0x339A6DFF) else Color(0x336D8DFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isUploads) Icons.Default.CloudUpload else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isUploads) Color(0xFF9A6DFF) else Color(0xFF6D8DFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AirflowText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = fullPath,
                                fontSize = 12.sp,
                                color = AirflowTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentVideoCard(
    item: RecentItem,
    thumbnailUrl: String,
    token: String,
    onResume: () -> Unit,
    onRemove: () -> Unit
) {
    val progress = if (item.dur > 0) (item.pos / item.dur).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = AirflowPanel),
        border = BorderStroke(1.dp, AirflowBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(180.dp)
            .clickable { onResume() }
    ) {
        Column {
            // Thumbnail container with close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
            ) {
                AsyncThumbnail(
                    url = thumbnailUrl,
                    token = token,
                    contentDescription = item.name,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    modifier = Modifier.fillMaxSize()
                )

                // Dismiss ✕ button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Quitar de recientes",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Resume Play Indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xBB000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Progress Bar overlay
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFF6D8DFF),
                    trackColor = Color(0x66000000)
                )
            }

            // Text Info
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AirflowText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Continuar ${FormatUtils.formatSeconds(item.pos)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6D8DFF)
                )
            }
        }
    }
}

@Composable
fun FolderExplorerView(
    viewModel: MKVCastViewModel,
    folderIndex: Int,
    folderContent: FolderContent,
    breadcrumbs: List<com.example.ui.Breadcrumb>,
    isLoading: Boolean,
    searchQuery: String
) {
    val filteredDirs = remember(folderContent.dirs, searchQuery) {
        if (searchQuery.isBlank()) folderContent.dirs
        else folderContent.dirs.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val filteredFiles = remember(folderContent.files, searchQuery) {
        if (searchQuery.isBlank()) folderContent.files
        else folderContent.files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Breadcrumb Navigation Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateUp() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = AirflowText
                )
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(breadcrumbs) { crumb ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AirflowPanel)
                            .clickable { viewModel.openFolder(folderIndex, crumb.path) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = crumb.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AirflowText
                        )
                    }
                    if (crumb != breadcrumbs.last()) {
                        Text(text = "›", color = AirflowTextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF6D8DFF))
            }
        } else if (filteredDirs.isEmpty() && filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No hay resultados para '$searchQuery'" else "Esta carpeta está vacía",
                    color = AirflowTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Subdirectories
                items(filteredDirs) { dirName ->
                    val path = viewModel.currentFolderPath.value
                    val nextPath = if (path.isEmpty()) dirName else "$path/$dirName"

                    Card(
                        colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                        border = BorderStroke(1.dp, AirflowBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openFolder(folderIndex, nextPath) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFF6D8DFF),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = dirName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AirflowText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Video Files in Grid
                items(filteredFiles) { video ->
                    VideoGridCard(
                        video = video,
                        thumbnailUrl = viewModel.apiClient.getThumbnailUrl(folderIndex, video.p),
                        token = viewModel.apiClient.token,
                        onClick = { viewModel.openVideoOptions(folderIndex, video) },
                        onPlayDirect = { viewModel.playVideo(folderIndex, video.p) },
                        onAddToQueue = { viewModel.addToQueue(folderIndex, video.p) },
                        onAddToPlaylist = { viewModel.openAddToPlaylist(folderIndex, video.p) },
                        onToggleSeen = { viewModel.toggleSeen(folderIndex, video.p, video.seen) }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoGridCard(
    video: VideoFile,
    thumbnailUrl: String,
    token: String,
    onClick: () -> Unit,
    onPlayDirect: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleSeen: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AirflowPanel),
        border = BorderStroke(1.dp, AirflowBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            // Thumbnail with badge overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
            ) {
                AsyncThumbnail(
                    url = thumbnailUrl,
                    token = token,
                    contentDescription = video.name,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    modifier = Modifier.fillMaxSize()
                )

                // "✓ Visto" badge
                if (video.seen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xDD3DDC97))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✓ Visto",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0B0D12)
                        )
                    }
                }

                // 3-dots context menu
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(AirflowPanel2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reproducir", color = AirflowText) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF6D8DFF)) },
                            onClick = {
                                menuExpanded = false
                                onPlayDirect()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Agregar a la cola", color = AirflowText) },
                            leadingIcon = { Icon(Icons.Default.Queue, null, tint = AirflowTextSecondary) },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Agregar a playlist", color = AirflowText) },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = AirflowTextSecondary) },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (video.seen) "Marcar como no visto" else "Marcar como visto", color = AirflowText) },
                            leadingIcon = {
                                Icon(
                                    if (video.seen) Icons.Outlined.CheckCircle else Icons.Default.CheckCircle,
                                    null,
                                    tint = AirflowSuccess
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleSeen()
                            }
                        )
                    }
                }
            }

            // Info below thumbnail
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AirflowText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                if (video.pos > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Continuar ${FormatUtils.formatSeconds(video.pos)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6D8DFF)
                    )
                }
            }
        }
    }
}
