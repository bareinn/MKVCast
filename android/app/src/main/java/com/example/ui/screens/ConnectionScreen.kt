package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ConnectionStatus
import com.example.ui.MKVCastViewModel
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AirflowBackground
import com.example.ui.theme.AirflowBorder
import com.example.ui.theme.AirflowError
import com.example.ui.theme.AirflowPanel
import com.example.ui.theme.AirflowPanel2
import com.example.ui.theme.AirflowText
import com.example.ui.theme.AirflowTextSecondary

@Composable
fun ConnectionScreen(
    viewModel: MKVCastViewModel,
    modifier: Modifier = Modifier
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var serverUrlInput by remember {
        mutableStateOf(
            viewModel.preferences.serverUrl.ifBlank { "192.168.1.20:8099" }
        )
    }
    var pinInput by remember { mutableStateOf(viewModel.preferences.lastPin) }
    var pinVisible by remember { mutableStateOf(false) }

    var modalPinInput by remember { mutableStateOf("") }

    val isConnecting = connectionStatus == ConnectionStatus.CONNECTING

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AirflowBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Icon
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "MKVCast 2.3",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AirflowText
            )

            Text(
                text = "Control remoto para Windows y Chromecast",
                fontSize = 14.sp,
                color = AirflowTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Main Connection Card
            Card(
                colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                border = BorderStroke(1.dp, AirflowBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Conectar con la PC",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AirflowText
                    )

                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Dirección del servidor") },
                        placeholder = { Text("192.168.1.20:8099") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = AirflowTextSecondary
                            )
                        },
                        supportingText = {
                            Text(
                                "Si no escribís http://, se agrega automáticamente",
                                color = AirflowTextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AirflowText,
                            unfocusedTextColor = AirflowText,
                            focusedBorderColor = Color(0xFF6D8DFF),
                            unfocusedBorderColor = AirflowBorder,
                            focusedContainerColor = AirflowPanel2,
                            unfocusedContainerColor = AirflowPanel2
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input")
                    )

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("PIN (opcional)") },
                        placeholder = { Text("Vacío si no tiene PIN") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AirflowTextSecondary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { pinVisible = !pinVisible }) {
                                Icon(
                                    imageVector = if (pinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (pinVisible) "Ocultar PIN" else "Mostrar PIN",
                                    tint = AirflowTextSecondary
                                )
                            }
                        },
                        visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                viewModel.connectServer(serverUrlInput, pinInput)
                            }
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AirflowText,
                            unfocusedTextColor = AirflowText,
                            focusedBorderColor = Color(0xFF6D8DFF),
                            unfocusedBorderColor = AirflowBorder,
                            focusedContainerColor = AirflowPanel2,
                            unfocusedContainerColor = AirflowPanel2
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_pin_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Connect button with gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                viewModel.connectServer(serverUrlInput, pinInput)
                            },
                            enabled = !isConnecting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("connect_button")
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Conectando...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CastConnected,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Conectar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // Error display card
            if (!errorMessage.isNullOrBlank() && connectionStatus != ConnectionStatus.AUTH_REQUIRED) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = AirflowPanel),
                    border = BorderStroke(1.dp, AirflowError.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = AirflowError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "No se pudo conectar",
                                color = AirflowError,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = AirflowText,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal PIN dialog if server answered 401
    if (connectionStatus == ConnectionStatus.AUTH_REQUIRED) {
        AlertDialog(
            onDismissRequest = { /* enforce input or cancel */ },
            containerColor = AirflowPanel,
            title = {
                Text(
                    text = "PIN de acceso",
                    color = AirflowText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "El servidor requiere un PIN para permitir el control remoto.",
                        color = AirflowTextSecondary,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = modalPinInput,
                        onValueChange = { modalPinInput = it },
                        label = { Text("Ingresá el PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AirflowText,
                            unfocusedTextColor = AirflowText,
                            focusedBorderColor = Color(0xFF6D8DFF),
                            unfocusedBorderColor = AirflowBorder,
                            focusedContainerColor = AirflowPanel2,
                            unfocusedContainerColor = AirflowPanel2
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage ?: "",
                            color = AirflowError,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitPin(modalPinInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D8DFF))
                ) {
                    Text("Ingresar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                    }
                ) {
                    Text("Cancelar", color = AirflowTextSecondary)
                }
            }
        )
    }
}
