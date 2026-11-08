package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AirflowPanel2

@Composable
fun AsyncThumbnail(
    url: String,
    token: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp)
) {
    val context = LocalContext.current

    val imageRequest = remember(url, token) {
        if (url.isBlank()) null
        else {
            val builder = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
            if (token.isNotBlank()) {
                builder.addHeader("x-token", token)
            }
            builder.build()
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(AirflowPanel2),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null) {
            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎬", fontSize = 28.sp)
                    }
                },
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎬", fontSize = 24.sp)
                    }
                }
            )
        } else {
            Text(text = "🎬", fontSize = 28.sp)
        }
    }
}
