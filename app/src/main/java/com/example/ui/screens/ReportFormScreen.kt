package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.local.EventCategory
import com.example.data.local.StagedPhoto
import com.example.ui.AccountDriveState
import com.example.ui.Formatters
import com.example.ui.MainViewModel
import com.example.ui.ReportFormState
import com.example.ui.components.DriveStatusHeaderCard
import com.example.ui.components.PhotoLightboxDialog
import com.example.ui.theme.StatusUploaded
import com.example.ui.theme.StatusUploadedBg
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFormScreen(
    formState: ReportFormState,
    accountState: AccountDriveState,
    rootFolderName: String,
    onConnectDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    onTimestampChange: (Long) -> Unit,
    onCategoryChange: (EventCategory) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCreateCameraCaptureUri: () -> Pair<android.net.Uri, File>,
    onCameraPhotoCaptured: (File) -> Unit,
    onPhotosPicked: (List<android.net.Uri>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSubmitToDrive: () -> Unit,
    onOpenDriveFolder: (String) -> Unit,
    onDismissSuccessBanner: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var previewPhoto by remember { mutableStateOf<StagedPhoto?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = MainViewModel.MAX_PHOTOS_PER_REPORT
        )
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPhotosPicked(uris)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null && file.exists()) {
            onCameraPhotoCaptured(file)
        } else {
            file?.delete()
        }
    }

    val launchCameraCapture = {
        val (uri, file) = onCreateCameraCaptureUri()
        pendingCameraFile = file
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            onShowMessage("Brak zgody na aparat. Możesz wybrać zdjęcia z galerii.")
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DriveStatusHeaderCard(
                accountState = accountState,
                rootFolderName = rootFolderName,
                onConnectClick = onConnectDrive,
                onOpenSettingsClick = onOpenSettings
            )

            AnimatedVisibility(visible = formState.lastSuccessFolderName != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StatusUploadedBg,
                    border = BorderStroke(1.dp, StatusUploaded.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = StatusUploaded
                            )
                            Text(
                                text = "Raport wysłany na Dysk Google",
                                style = MaterialTheme.typography.titleSmall,
                                color = StatusUploaded,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissSuccessBanner,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Ukryj powiadomienie",
                                    tint = StatusUploaded,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val url = formState.lastSuccessFolderUrl
                            if (!url.isNullOrBlank()) {
                                FilledTonalSuccessButton(
                                    text = "Otwórz folder na Dysku",
                                    onClick = { onOpenDriveFolder(url) }
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: Data i godzina zdarzenia
            SectionContainer(title = "Data i godzina zdarzenia") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DateTimeSelectorTile(
                        icon = Icons.Outlined.CalendarToday,
                        label = "Data",
                        value = Formatters.formatDate(formState.eventTimestampMillis),
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("date_picker_button")
                    )
                    DateTimeSelectorTile(
                        icon = Icons.Outlined.Schedule,
                        label = "Godzina",
                        value = Formatters.formatTime(formState.eventTimestampMillis),
                        onClick = { showTimePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("time_picker_button")
                    )
                    TextButton(
                        onClick = { onTimestampChange(System.currentTimeMillis()) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("set_now_button")
                    ) {
                        Text("Teraz")
                    }
                }
            }

            // Section 2: Kategoria i opis zdarzenia
            SectionContainer(title = "Szczegóły zdarzenia") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EventCategory.entries.forEach { category ->
                        FilterChip(
                            selected = formState.category == category,
                            onClick = { onCategoryChange(category) },
                            label = { Text(category.labelPl) },
                            modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = formState.title,
                    onValueChange = onTitleChange,
                    label = { Text("Tytuł / lokalizacja (opcjonalnie)") },
                    placeholder = { Text("np. Magazyn B, Brama nr 2") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = formState.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Opis zdarzenia *") },
                    placeholder = {
                        Text("Opisz dokładnie przebieg zdarzenia, obserwacje lub podjęte działania...")
                    },
                    isError = formState.descriptionError != null,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formState.descriptionError ?: "Pole wymagane do wysyłki",
                                color = if (formState.descriptionError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = "${formState.description.length}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("description_input")
                )
            }

            // Section 3: Zdjęcia
            SectionContainer(
                title = "Zdjęcia (${formState.stagedPhotos.size}/${MainViewModel.MAX_PHOTOS_PER_REPORT})",
                trailingLabel = if (formState.stagedPhotos.isNotEmpty()) {
                    Formatters.formatFileSize(formState.stagedPhotos.sumOf { it.sizeBytes })
                } else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !formState.isUploading &&
                            formState.stagedPhotos.size < MainViewModel.MAX_PHOTOS_PER_REPORT,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pick_photos_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Z galerii")
                    }

                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                launchCameraCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !formState.isUploading &&
                            formState.stagedPhotos.size < MainViewModel.MAX_PHOTOS_PER_REPORT,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("take_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aparat")
                    }
                }

                if (formState.isStagingPhotos) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Przygotowywanie zdjęć...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (formState.stagedPhotos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        itemsIndexed(
                            items = formState.stagedPhotos,
                            key = { _, photo -> photo.id }
                        ) { index, photo ->
                            StagedPhotoCard(
                                photo = photo,
                                index = index,
                                onPreviewClick = { previewPhoto = photo },
                                onRemoveClick = { onRemovePhoto(photo.id) }
                            )
                        }
                    }
                } else if (!formState.isStagingPhotos) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Opcjonalnie dodaj zdjęcia z galerii lub zrób nowe aparatem.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Upload progress card
            AnimatedVisibility(visible = formState.isUploading && formState.uploadProgress != null) {
                val progress = formState.uploadProgress
                if (progress != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = progress.stepLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${progress.completedSteps}/${progress.totalSteps}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }

            // Primary actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveDraft,
                    enabled = !formState.isUploading,
                    modifier = Modifier
                        .weight(0.9f)
                        .height(52.dp)
                        .testTag("save_draft_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Szkic")
                }

                Button(
                    onClick = onSubmitToDrive,
                    enabled = !formState.isUploading && !formState.isStagingPhotos,
                    modifier = Modifier
                        .weight(1.6f)
                        .height(52.dp)
                        .testTag("submit_drive_button")
                ) {
                    if (formState.isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wysyłanie...")
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wyślij na Dysk")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val calendar = remember(formState.eventTimestampMillis) {
            Calendar.getInstance().apply { timeInMillis = formState.eventTimestampMillis }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.eventTimestampMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedUtcMillis ->
                            val pickedCal = Calendar.getInstance().apply {
                                timeInMillis = pickedUtcMillis
                            }
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = formState.eventTimestampMillis
                                set(Calendar.YEAR, pickedCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, pickedCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, pickedCal.get(Calendar.DAY_OF_MONTH))
                            }
                            onTimestampChange(updated.timeInMillis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Ustaw")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Anuluj")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val calendar = remember(formState.eventTimestampMillis) {
            Calendar.getInstance().apply { timeInMillis = formState.eventTimestampMillis }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Wybierz godzinę zdarzenia") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = Calendar.getInstance().apply {
                            timeInMillis = formState.eventTimestampMillis
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                        }
                        onTimestampChange(updated.timeInMillis)
                        showTimePicker = false
                    }
                ) {
                    Text("Ustaw")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    previewPhoto?.let { photo ->
        PhotoLightboxDialog(
            filePath = photo.localFilePath,
            title = photo.displayName,
            onDismiss = { previewPhoto = null }
        )
    }
}

@Composable
private fun SectionContainer(
    title: String,
    trailingLabel: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (trailingLabel != null) {
                    Text(
                        text = trailingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DateTimeSelectorTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StagedPhotoCard(
    photo: StagedPhoto,
    index: Int,
    onPreviewClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(108.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onPreviewClick)
            .testTag("staged_photo_$index")
    ) {
        AsyncImage(
            model = File(photo.localFilePath),
            contentDescription = "Załączone zdjęcie ${index + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = Formatters.formatFileSize(photo.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
        IconButton(
            onClick = onRemoveClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .testTag("remove_photo_$index")
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Usuń zdjęcie ${index + 1}",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun FilledTonalSuccessButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, StatusUploaded),
        modifier = Modifier.testTag("open_drive_folder_button")
    ) {
        Icon(
            imageVector = Icons.Outlined.OpenInNew,
            contentDescription = null,
            tint = StatusUploaded,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = StatusUploaded)
    }
}
