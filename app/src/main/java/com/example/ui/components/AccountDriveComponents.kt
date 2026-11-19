package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.DrivePreferences
import com.example.ui.AccountDriveState
import com.example.ui.theme.StatusUploaded
import com.example.ui.theme.StatusUploadedBg
import java.io.File

@Composable
fun DriveStatusHeaderCard(
    accountState: AccountDriveState,
    rootFolderName: String,
    onConnectClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isReady = accountState.isSignedIn && accountState.hasDrivePermission

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = if (isReady) onOpenSettingsClick else onConnectClick)
            .testTag("drive_status_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isReady) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isReady) StatusUploadedBg
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (accountState.isAuthenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (isReady) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                        contentDescription = if (isReady) "Dysk Google połączony" else "Dysk Google niepołączony",
                        tint = if (isReady) StatusUploaded else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isReady -> "Dysk Google gotowy"
                        accountState.isSignedIn -> "Wymagana zgoda Dysku Google"
                        else -> "Połącz z Dyskiem Google"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isReady -> "${accountState.userEmail ?: "Konto Google"} • Folder: $rootFolderName"
                        accountState.isSignedIn -> "Dotknij, aby autoryzować zapis na Dysku (${accountState.userEmail ?: ""})"
                        else -> "Zaloguj się kontem Google, aby wysyłać raporty"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isReady) {
                IconButton(
                    onClick = onOpenSettingsClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Ustawienia Dysku Google",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onConnectClick,
                    enabled = !accountState.isAuthenticating,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("connect_drive_button")
                ) {
                    Text(if (accountState.isSignedIn) "Autoryzuj" else "Połącz")
                }
            }
        }
    }
}

@Composable
fun SettingsModalDialog(
    accountState: AccountDriveState,
    preferences: DrivePreferences,
    onDismiss: () -> Unit,
    onSaveSettings: (rootFolderName: String, optimizePhotos: Boolean) -> Unit,
    onConnectDrive: () -> Unit,
    onSignOut: () -> Unit
) {
    var folderName by remember(preferences.rootFolderName) {
        mutableStateOf(preferences.rootFolderName)
    }
    var optimizePhotos by remember(preferences.optimizePhotoSize) {
        mutableStateOf(preferences.optimizePhotoSize)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ustawienia Dysku Google",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Konto Google",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (accountState.isSignedIn) {
                        Text(
                            text = accountState.userName?.takeIf { it.isNotBlank() }
                                ?.let { "$it (${accountState.userEmail})" }
                                ?: accountState.userEmail.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (accountState.hasDrivePermission) {
                                "Uprawnienia Dysku Google aktywne"
                            } else {
                                "Brak autoryzacji zapisu na Dysku Google"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (accountState.hasDrivePermission) StatusUploaded
                            else MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text(
                            text = "Nie zalogowano do konta Google.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!accountState.isSignedIn || !accountState.hasDrivePermission) {
                            FilledTonalButton(
                                onClick = {
                                    onDismiss()
                                    onConnectDrive()
                                },
                                modifier = Modifier.testTag("dialog_connect_drive_button")
                            ) {
                                Text("Połącz konto")
                            }
                        }
                        if (accountState.isSignedIn) {
                            OutlinedButton(
                                onClick = {
                                    onSignOut()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wyloguj")
                            }
                        }
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder główny na Dysku") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    supportingText = {
                        Text("Każdy raport utworzy tu osobny podfolder ze zdjęciami i opisem.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("root_folder_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Optymalizuj rozmiar zdjęć",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Skaluje duże zdjęcia do maks. 2048 px (szybsza wysyłka w terenie)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = optimizePhotos,
                        onCheckedChange = { optimizePhotos = it },
                        modifier = Modifier.testTag("optimize_photos_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(folderName, optimizePhotos)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_settings_button")
            ) {
                Text("Anuluj")
            }
        }
    )
}

@Composable
fun PhotoLightboxDialog(
    filePath: String,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            AsyncImage(
                model = File(filePath),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .testTag("close_photo_preview")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Zamknij podgląd",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
