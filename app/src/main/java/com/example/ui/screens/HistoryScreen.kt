package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.EventCategory
import com.example.data.local.EventReportWithPhotos
import com.example.data.local.ReportPhotoEntity
import com.example.data.local.UploadStatus
import com.example.ui.Formatters
import com.example.ui.HistoryFilter
import com.example.ui.components.PhotoLightboxDialog
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusUploaded
import com.example.ui.theme.StatusUploadedBg
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    reports: List<EventReportWithPhotos>,
    selectedFilter: HistoryFilter,
    isUploading: Boolean,
    onSelectFilter: (HistoryFilter) -> Unit,
    onRetryUpload: (String) -> Unit,
    onOpenDriveLink: (String) -> Unit,
    onDeleteReport: (EventReportWithPhotos) -> Unit,
    onGoToNewReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var detailReport by remember { mutableStateOf<EventReportWithPhotos?>(null) }
    var deleteCandidate by remember { mutableStateOf<EventReportWithPhotos?>(null) }
    var lightboxPhoto by remember { mutableStateOf<ReportPhotoEntity?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(filter.labelPl) },
                        modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                    )
                }
            }

            if (reports.isEmpty()) {
                EmptyHistoryView(
                    isFiltered = selectedFilter != HistoryFilter.ALL,
                    onGoToNewReport = onGoToNewReport,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = reports,
                        key = { it.report.id }
                    ) { bundle ->
                        ReportHistoryCard(
                            bundle = bundle,
                            isUploading = isUploading,
                            onCardClick = { detailReport = bundle },
                            onRetryClick = { onRetryUpload(bundle.report.id) },
                            onOpenDriveClick = {
                                bundle.report.driveFolderWebLink?.let(onOpenDriveLink)
                            },
                            onDeleteClick = { deleteCandidate = bundle }
                        )
                    }
                }
            }
        }
    }

    detailReport?.let { bundle ->
        BackHandler { detailReport = null }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { detailReport = null },
            sheetState = sheetState
        ) {
            ReportDetailSheetContent(
                bundle = bundle,
                isUploading = isUploading,
                onPhotoClick = { lightboxPhoto = it },
                onRetryClick = {
                    detailReport = null
                    onRetryUpload(bundle.report.id)
                },
                onOpenDriveClick = {
                    bundle.report.driveFolderWebLink?.let(onOpenDriveLink)
                },
                onCloseClick = { detailReport = null }
            )
        }
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Usunąć raport z historii?") },
            text = {
                Text(
                    "Usunięcie wpisu z urządzenia nie usuwa plików już wysłanych na Dysk Google."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteReport(candidate)
                        deleteCandidate = null
                    },
                    modifier = Modifier.testTag("confirm_delete_report")
                ) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Anuluj")
                }
            }
        )
    }

    lightboxPhoto?.let { photo ->
        PhotoLightboxDialog(
            filePath = photo.localFilePath,
            title = photo.displayName,
            onDismiss = { lightboxPhoto = null }
        )
    }
}

@Composable
private fun EmptyHistoryView(
    isFiltered: Boolean,
    onGoToNewReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_drive_archive_empty),
            contentDescription = "Ilustracja pustego archiwum raportów",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .height(190.dp)
                .clip(MaterialTheme.shapes.large)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (isFiltered) "Brak pasujących raportów" else "Twoje archiwum jest puste",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isFiltered) {
                "Zmień filtr powyżej, aby zobaczyć pozostałe wpisy."
            } else {
                "Dodaj datę, opis zdarzenia i zdjęcia w formularzu, a następnie wyślij je bezpośrednio na Dysk Google."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!isFiltered) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGoToNewReport,
                modifier = Modifier.testTag("empty_new_report_button")
            ) {
                Text("Utwórz pierwszy raport")
            }
        }
    }
}

@Composable
private fun ReportHistoryCard(
    bundle: EventReportWithPhotos,
    isUploading: Boolean,
    onCardClick: () -> Unit,
    onRetryClick: () -> Unit,
    onOpenDriveClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val report = bundle.report
    val photos = bundle.sortedPhotos
    val categoryLabel = enumValues<EventCategory>()
        .firstOrNull { it.name == report.category }?.labelPl ?: "Zdarzenie"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("history_report_card_${report.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = report.status)
                Text(
                    text = Formatters.formatDateTime(report.eventTimestampMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (report.title.isNotBlank()) "$categoryLabel • ${report.title}" else categoryLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!report.errorMessage.isNullOrBlank() && report.status == UploadStatus.FAILED) {
                Text(
                    text = report.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (photos.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    photos.take(4).forEach { photo ->
                        AsyncImage(
                            model = File(photo.localFilePath),
                            contentDescription = photo.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                    if (photos.size > 4) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${photos.size - 4}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${photos.size} zdjęć",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (report.status == UploadStatus.DRAFT || report.status == UploadStatus.FAILED) {
                        OutlinedButton(
                            onClick = onRetryClick,
                            enabled = !isUploading,
                            modifier = Modifier.testTag("retry_upload_${report.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (report.status == UploadStatus.DRAFT) "Wyślij" else "Ponów")
                        }
                    } else if (report.status == UploadStatus.UPLOADED &&
                        !report.driveFolderWebLink.isNullOrBlank()
                    ) {
                        TextButton(
                            onClick = onOpenDriveClick,
                            modifier = Modifier.testTag("open_drive_${report.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dysk")
                        }
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_report_${report.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Usuń raport",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: UploadStatus) {
    val (bg, fg, label, icon) = when (status) {
        UploadStatus.UPLOADED -> Quad(
            StatusUploadedBg,
            StatusUploaded,
            "Wysłano na Dysk",
            Icons.Outlined.CloudDone
        )
        UploadStatus.UPLOADING -> Quad(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Wysyłanie...",
            Icons.Outlined.CloudUpload
        )
        UploadStatus.DRAFT -> Quad(
            StatusPendingBg,
            StatusPending,
            "Szkic lokalny",
            Icons.Outlined.CloudUpload
        )
        UploadStatus.FAILED -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Błąd wysyłki",
            Icons.Outlined.ErrorOutline
        )
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

@Composable
private fun ReportDetailSheetContent(
    bundle: EventReportWithPhotos,
    isUploading: Boolean,
    onPhotoClick: (ReportPhotoEntity) -> Unit,
    onRetryClick: () -> Unit,
    onOpenDriveClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val report = bundle.report
    val photos = bundle.sortedPhotos
    val categoryLabel = enumValues<EventCategory>()
        .firstOrNull { it.name == report.category }?.labelPl ?: "Zdarzenie"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(status = report.status)
            IconButton(onClick = onCloseClick) {
                Icon(Icons.Outlined.Close, contentDescription = "Zamknij szczegóły")
            }
        }

        Text(
            text = if (report.title.isNotBlank()) "$categoryLabel — ${report.title}" else categoryLabel,
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Data zdarzenia: ${Formatters.formatDateTime(report.eventTimestampMillis)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (report.driveFolderName.isNotBlank()) {
            Text(
                text = "Folder na Dysku: ${report.driveFolderName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = report.description,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(14.dp)
            )
        }

        if (photos.isNotEmpty()) {
            Text(
                text = "Zdjęcia (${photos.size})",
                style = MaterialTheme.typography.titleSmall
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(photos, key = { _, p -> p.id }) { idx, photo ->
                    AsyncImage(
                        model = File(photo.localFilePath),
                        contentDescription = "Zdjęcie ${idx + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(116.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPhotoClick(photo) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (report.status == UploadStatus.DRAFT || report.status == UploadStatus.FAILED) {
                Button(
                    onClick = onRetryClick,
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Wyślij na Dysk Google")
                }
            } else if (!report.driveFolderWebLink.isNullOrBlank()) {
                Button(
                    onClick = onOpenDriveClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Otwórz na Dysku Google")
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
