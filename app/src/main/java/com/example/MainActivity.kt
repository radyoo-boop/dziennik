package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.drive.GoogleAuthManager
import com.example.data.drive.GoogleDriveService
import com.example.data.local.AppDatabase
import com.example.data.local.LocalPhotoStorage
import com.example.data.local.SettingsDataStore
import com.example.data.repository.ReportRepository
import com.example.ui.AppTab
import com.example.ui.MainUiEvent
import com.example.ui.MainViewModel
import com.example.ui.components.SettingsModalDialog
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ReportFormScreen
import com.example.ui.theme.DziennikZdarzenTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appCtx = applicationContext
        val db = AppDatabase.getInstance(appCtx)
        val photoStorage = LocalPhotoStorage(appCtx)
        val settingsDataStore = SettingsDataStore(appCtx)
        val driveService = GoogleDriveService()
        val authManager = GoogleAuthManager()
        val repository = ReportRepository(
            dao = db.eventReportDao(),
            driveService = driveService,
            photoStorage = photoStorage
        )

        setContent {
            DziennikZdarzenTheme {
                val vm: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(
                        appContext = appCtx,
                        authManager = authManager,
                        repository = repository,
                        photoStorage = photoStorage,
                        settingsDataStore = settingsDataStore
                    )
                )
                DziennikZdarzenApp(
                    viewModel = vm,
                    activity = this
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DziennikZdarzenApp(
    viewModel: MainViewModel,
    activity: Activity
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val filteredReports by viewModel.filteredReports.collectAsStateWithLifecycle()
    val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDriveConsentActivityResult(activity, result.data)
    }

    LaunchedEffect(Unit) {
        viewModel.initializeSession(activity)
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is MainUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is MainUiEvent.LaunchDriveConsent -> {
                    driveConsentLauncher.launch(
                        IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
                    )
                }
                is MainUiEvent.OpenExternalUrl -> {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                        context.startActivity(intent)
                    }.onFailure {
                        snackbarHostState.showSnackbar("Nie można otworzyć linku Dysku Google.")
                    }
                }
            }
        }
    }

    if (selectedTab == AppTab.HISTORY) {
        BackHandler {
            viewModel.selectTab(AppTab.NEW_REPORT)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedTab) {
                                AppTab.NEW_REPORT -> "Dziennik Zdarzeń"
                                AppTab.HISTORY -> "Historia raportów"
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("topbar_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Ustawienia Dysku Google"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == AppTab.NEW_REPORT,
                            onClick = { viewModel.selectTab(AppTab.NEW_REPORT) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == AppTab.NEW_REPORT) {
                                        Icons.Filled.EditNote
                                    } else {
                                        Icons.Outlined.EditNote
                                    },
                                    contentDescription = "Nowy raport"
                                )
                            },
                            label = { Text("Nowy raport") },
                            modifier = Modifier.testTag("nav_new_report")
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppTab.HISTORY,
                            onClick = { viewModel.selectTab(AppTab.HISTORY) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (pendingCount > 0) {
                                            Badge { Text("$pendingCount") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == AppTab.HISTORY) {
                                            Icons.Filled.History
                                        } else {
                                            Icons.Outlined.History
                                        },
                                        contentDescription = "Historia"
                                    )
                                }
                            },
                            label = { Text("Historia") },
                            modifier = Modifier.testTag("nav_history")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        NavigationRailItem(
                            selected = selectedTab == AppTab.NEW_REPORT,
                            onClick = { viewModel.selectTab(AppTab.NEW_REPORT) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == AppTab.NEW_REPORT) {
                                        Icons.Filled.EditNote
                                    } else {
                                        Icons.Outlined.EditNote
                                    },
                                    contentDescription = "Nowy raport"
                                )
                            },
                            label = { Text("Formularz") },
                            modifier = Modifier.testTag("rail_new_report")
                        )
                        NavigationRailItem(
                            selected = selectedTab == AppTab.HISTORY,
                            onClick = { viewModel.selectTab(AppTab.HISTORY) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (pendingCount > 0) {
                                            Badge { Text("$pendingCount") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == AppTab.HISTORY) {
                                            Icons.Filled.History
                                        } else {
                                            Icons.Outlined.History
                                        },
                                        contentDescription = "Historia"
                                    )
                                }
                            },
                            label = { Text("Historia") },
                            modifier = Modifier.testTag("rail_history")
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                when (selectedTab) {
                    AppTab.NEW_REPORT -> {
                        ReportFormScreen(
                            formState = formState,
                            accountState = accountState,
                            rootFolderName = preferences.rootFolderName,
                            onConnectDrive = { viewModel.connectGoogleAccountAndDrive(activity) },
                            onOpenSettings = { showSettingsDialog = true },
                            onTimestampChange = viewModel::updateEventTimestamp,
                            onCategoryChange = viewModel::updateCategory,
                            onTitleChange = viewModel::updateTitle,
                            onDescriptionChange = viewModel::updateDescription,
                            onCreateCameraCaptureUri = viewModel::createCameraCaptureUri,
                            onCameraPhotoCaptured = viewModel::addCameraPhoto,
                            onPhotosPicked = viewModel::addPickedPhotos,
                            onRemovePhoto = viewModel::removeStagedPhoto,
                            onSaveDraft = viewModel::saveAsDraft,
                            onSubmitToDrive = { viewModel.submitReportToDrive(activity) },
                            onOpenDriveFolder = viewModel::openExternalUrl,
                            onDismissSuccessBanner = viewModel::dismissSuccessBanner,
                            onShowMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AppTab.HISTORY -> {
                        HistoryScreen(
                            reports = filteredReports,
                            selectedFilter = historyFilter,
                            isUploading = formState.isUploading,
                            onSelectFilter = viewModel::setHistoryFilter,
                            onRetryUpload = { reportId ->
                                viewModel.retryReportUpload(activity, reportId)
                            },
                            onOpenDriveLink = viewModel::openExternalUrl,
                            onDeleteReport = viewModel::deleteReport,
                            onGoToNewReport = { viewModel.selectTab(AppTab.NEW_REPORT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsModalDialog(
            accountState = accountState,
            preferences = preferences,
            onDismiss = { showSettingsDialog = false },
            onSaveSettings = viewModel::updateSettings,
            onConnectDrive = { viewModel.connectGoogleAccountAndDrive(activity) },
            onSignOut = viewModel::signOut
        )
    }
}
