package com.example.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.drive.DriveAuthOutcome
import com.example.data.drive.DriveUnauthorizedException
import com.example.data.drive.GoogleAuthManager
import com.example.data.drive.SignInOutcome
import com.example.data.local.DrivePreferences
import com.example.data.local.EventCategory
import com.example.data.local.EventReportWithPhotos
import com.example.data.local.LocalPhotoStorage
import com.example.data.local.SettingsDataStore
import com.example.data.local.StagedPhoto
import com.example.data.local.UploadStatus
import com.example.data.repository.ReportRepository
import com.example.data.repository.UploadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class AppTab {
    NEW_REPORT,
    HISTORY
}

enum class HistoryFilter(val labelPl: String) {
    ALL("Wszystkie"),
    UPLOADED("Wysłane"),
    PENDING("Szkice i błędy")
}

data class AccountDriveState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val userName: String? = null,
    val hasDrivePermission: Boolean = false,
    val isAuthenticating: Boolean = false
)

data class ReportFormState(
    val eventTimestampMillis: Long = System.currentTimeMillis(),
    val category: EventCategory = EventCategory.INCIDENT,
    val title: String = "",
    val description: String = "",
    val descriptionError: String? = null,
    val stagedPhotos: List<StagedPhoto> = emptyList(),
    val isStagingPhotos: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: UploadProgress? = null,
    val lastSuccessFolderUrl: String? = null,
    val lastSuccessFolderName: String? = null
)

sealed interface MainUiEvent {
    data class ShowMessage(val message: String) : MainUiEvent
    data class LaunchDriveConsent(val pendingIntent: PendingIntent) : MainUiEvent
    data class OpenExternalUrl(val url: String) : MainUiEvent
}

class MainViewModel(
    private val appContext: Context,
    private val authManager: GoogleAuthManager,
    private val repository: ReportRepository,
    private val photoStorage: LocalPhotoStorage,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(AppTab.NEW_REPORT)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _accountState = MutableStateFlow(AccountDriveState())
    val accountState: StateFlow<AccountDriveState> = _accountState.asStateFlow()

    private val _formState = MutableStateFlow(ReportFormState())
    val formState: StateFlow<ReportFormState> = _formState.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _uiEvents = MutableSharedFlow<MainUiEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<MainUiEvent> = _uiEvents.asSharedFlow()

    val preferences: StateFlow<DrivePreferences> = settingsDataStore.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DrivePreferences()
        )

    private val allReports: StateFlow<List<EventReportWithPhotos>> = repository.allReports
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val filteredReports: StateFlow<List<EventReportWithPhotos>> =
        combine(allReports, _historyFilter) { reports, filter ->
            when (filter) {
                HistoryFilter.ALL -> reports
                HistoryFilter.UPLOADED -> reports.filter { it.report.status == UploadStatus.UPLOADED }
                HistoryFilter.PENDING -> reports.filter {
                    it.report.status == UploadStatus.DRAFT ||
                        it.report.status == UploadStatus.FAILED ||
                        it.report.status == UploadStatus.UPLOADING
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val pendingCount: StateFlow<Int> = combine(allReports) { array ->
        array[0].count {
            it.report.status == UploadStatus.DRAFT || it.report.status == UploadStatus.FAILED
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0
    )

    private var cachedAccessToken: String? = null
    private var pendingUploadReportId: String? = null
    private var initializedSession = false

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun initializeSession(activity: Activity) {
        if (initializedSession) return
        initializedSession = true
        viewModelScope.launch {
            val user = authManager.attemptSilentSignIn(activity)
            if (user != null) {
                _accountState.update {
                    it.copy(
                        isSignedIn = true,
                        userEmail = user.email,
                        userName = user.displayName
                    )
                }
                settingsDataStore.updateConnectedAccountEmail(user.email)
                checkDrivePermissionSilently(activity)
            }
        }
    }

    private suspend fun checkDrivePermissionSilently(activity: Activity) {
        when (val outcome = authManager.requestDriveAccess(activity)) {
            is DriveAuthOutcome.Authorized -> {
                cachedAccessToken = outcome.accessToken
                _accountState.update { it.copy(hasDrivePermission = true) }
            }
            is DriveAuthOutcome.NeedsConsent,
            is DriveAuthOutcome.Failed -> {
                _accountState.update { it.copy(hasDrivePermission = false) }
            }
        }
    }

    fun connectGoogleAccountAndDrive(activity: Activity) {
        viewModelScope.launch {
            _accountState.update { it.copy(isAuthenticating = true) }
            val currentUser = authManager.currentUser()
            val signedInUser = if (currentUser != null) {
                currentUser
            } else {
                when (val signIn = authManager.signInWithGoogle(activity)) {
                    is SignInOutcome.Success -> signIn.user
                    SignInOutcome.Cancelled -> {
                        _accountState.update { it.copy(isAuthenticating = false) }
                        return@launch
                    }
                    is SignInOutcome.Error -> {
                        _accountState.update { it.copy(isAuthenticating = false) }
                        _uiEvents.emit(MainUiEvent.ShowMessage(signIn.message))
                        return@launch
                    }
                }
            }

            _accountState.update {
                it.copy(
                    isSignedIn = true,
                    userEmail = signedInUser.email,
                    userName = signedInUser.displayName
                )
            }
            settingsDataStore.updateConnectedAccountEmail(signedInUser.email)

            when (val driveOutcome = authManager.requestDriveAccess(activity)) {
                is DriveAuthOutcome.Authorized -> {
                    cachedAccessToken = driveOutcome.accessToken
                    _accountState.update {
                        it.copy(hasDrivePermission = true, isAuthenticating = false)
                    }
                    _uiEvents.emit(MainUiEvent.ShowMessage("Połączono z Dyskiem Google."))
                    resumePendingUploadIfAny(activity)
                }
                is DriveAuthOutcome.NeedsConsent -> {
                    _accountState.update { it.copy(isAuthenticating = false) }
                    _uiEvents.emit(MainUiEvent.LaunchDriveConsent(driveOutcome.pendingIntent))
                }
                is DriveAuthOutcome.Failed -> {
                    _accountState.update { it.copy(isAuthenticating = false) }
                    _uiEvents.emit(MainUiEvent.ShowMessage(driveOutcome.message))
                }
            }
        }
    }

    fun onDriveConsentActivityResult(activity: Activity, intentData: Intent?) {
        viewModelScope.launch {
            when (val outcome = authManager.extractTokenFromConsentResult(activity, intentData)) {
                is DriveAuthOutcome.Authorized -> {
                    cachedAccessToken = outcome.accessToken
                    _accountState.update {
                        it.copy(hasDrivePermission = true, isAuthenticating = false)
                    }
                    _uiEvents.emit(MainUiEvent.ShowMessage("Dysk Google został autoryzowany."))
                    resumePendingUploadIfAny(activity)
                }
                is DriveAuthOutcome.Failed -> {
                    pendingUploadReportId = null
                    _formState.update { it.copy(isUploading = false, uploadProgress = null) }
                    _accountState.update { it.copy(isAuthenticating = false) }
                    _uiEvents.emit(MainUiEvent.ShowMessage(outcome.message))
                }
                is DriveAuthOutcome.NeedsConsent -> Unit
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            cachedAccessToken?.let { authManager.clearCachedToken(appContext, it) }
            cachedAccessToken = null
            pendingUploadReportId = null
            authManager.signOut(appContext)
            settingsDataStore.updateConnectedAccountEmail(null)
            _accountState.value = AccountDriveState()
            _uiEvents.emit(MainUiEvent.ShowMessage("Wylogowano z konta Google."))
        }
    }

    fun updateEventTimestamp(timestampMillis: Long) {
        _formState.update { it.copy(eventTimestampMillis = timestampMillis) }
    }

    fun updateCategory(category: EventCategory) {
        _formState.update { it.copy(category = category) }
    }

    fun updateTitle(title: String) {
        _formState.update { it.copy(title = title) }
    }

    fun updateDescription(description: String) {
        _formState.update {
            it.copy(
                description = description,
                descriptionError = if (description.isNotBlank()) null else it.descriptionError
            )
        }
    }

    fun dismissSuccessBanner() {
        _formState.update {
            it.copy(lastSuccessFolderUrl = null, lastSuccessFolderName = null)
        }
    }

    fun addPickedPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val existingCount = _formState.value.stagedPhotos.size
        val remainingSlots = (MAX_PHOTOS_PER_REPORT - existingCount).coerceAtLeast(0)
        if (remainingSlots == 0) {
            viewModelScope.launch {
                _uiEvents.emit(MainUiEvent.ShowMessage("Maksymalnie $MAX_PHOTOS_PER_REPORT zdjęć w jednym raporcie."))
            }
            return
        }
        val toProcess = uris.take(remainingSlots)
        viewModelScope.launch {
            _formState.update { it.copy(isStagingPhotos = true) }
            val optimize = preferences.value.optimizePhotoSize
            val added = mutableListOf<StagedPhoto>()
            var failures = 0
            toProcess.forEach { uri ->
                photoStorage.stagePhotoFromUri(uri, optimize)
                    .onSuccess { added.add(it) }
                    .onFailure { failures++ }
            }
            _formState.update { state ->
                state.copy(
                    stagedPhotos = state.stagedPhotos + added,
                    isStagingPhotos = false
                )
            }
            if (failures > 0) {
                _uiEvents.emit(MainUiEvent.ShowMessage("Nie udało się przetworzyć części wybranych zdjęć."))
            }
        }
    }

    fun createCameraCaptureUri(): Pair<Uri, File> = photoStorage.createCameraCaptureUri()

    fun addCameraPhoto(cameraFile: File) {
        viewModelScope.launch {
            _formState.update { it.copy(isStagingPhotos = true) }
            val optimize = preferences.value.optimizePhotoSize
            photoStorage.stagePhotoFromCameraFile(cameraFile, optimize)
                .onSuccess { staged ->
                    _formState.update { state ->
                        state.copy(
                            stagedPhotos = state.stagedPhotos + staged,
                            isStagingPhotos = false
                        )
                    }
                }
                .onFailure {
                    _formState.update { it.copy(isStagingPhotos = false) }
                    _uiEvents.emit(MainUiEvent.ShowMessage("Nie udało się zapisać zdjęcia z aparatu."))
                }
        }
    }

    fun removeStagedPhoto(photoId: String) {
        val removed = _formState.value.stagedPhotos.firstOrNull { it.id == photoId }
        if (removed != null) {
            photoStorage.deleteStagedFile(removed.localFilePath)
        }
        _formState.update { state ->
            state.copy(stagedPhotos = state.stagedPhotos.filterNot { it.id == photoId })
        }
    }

    fun saveAsDraft() {
        val state = _formState.value
        if (state.description.isBlank() && state.stagedPhotos.isEmpty()) {
            _formState.update {
                it.copy(descriptionError = "Wpisz opis zdarzenia lub dodaj zdjęcie przed zapisaniem.")
            }
            return
        }
        viewModelScope.launch {
            repository.createLocalReport(
                eventTimestampMillis = state.eventTimestampMillis,
                category = state.category,
                title = state.title,
                description = state.description.ifBlank { "(Szkic bez opisu)" },
                stagedPhotos = state.stagedPhotos
            )
            resetFormAfterSave(folderUrl = null, folderName = null)
            _uiEvents.emit(MainUiEvent.ShowMessage("Zapisano szkic w zakładce Historia."))
        }
    }

    fun submitReportToDrive(activity: Activity) {
        val state = _formState.value
        if (state.description.isBlank()) {
            _formState.update {
                it.copy(descriptionError = "Opis zdarzenia jest wymagany.")
            }
            return
        }
        if (state.isUploading) return

        viewModelScope.launch {
            val savedBundle = repository.createLocalReport(
                eventTimestampMillis = state.eventTimestampMillis,
                category = state.category,
                title = state.title,
                description = state.description,
                stagedPhotos = state.stagedPhotos
            )
            resetFormAfterSave(folderUrl = null, folderName = null)
            startDriveUploadForReport(activity, savedBundle.report.id)
        }
    }

    fun retryReportUpload(activity: Activity, reportId: String) {
        if (_formState.value.isUploading) {
            viewModelScope.launch {
                _uiEvents.emit(MainUiEvent.ShowMessage("Trwa inne wysyłanie na Dysk Google..."))
            }
            return
        }
        viewModelScope.launch {
            startDriveUploadForReport(activity, reportId)
        }
    }

    private suspend fun resumePendingUploadIfAny(activity: Activity) {
        val reportId = pendingUploadReportId ?: return
        pendingUploadReportId = null
        startDriveUploadForReport(activity, reportId)
    }

    private suspend fun startDriveUploadForReport(activity: Activity, reportId: String) {
        _formState.update {
            it.copy(
                isUploading = true,
                uploadProgress = UploadProgress("Łączenie z kontem Google...", 0, 1)
            )
        }

        // 1. Ensure user is signed in
        val user = authManager.currentUser() ?: when (val signIn = authManager.signInWithGoogle(activity)) {
            is SignInOutcome.Success -> {
                _accountState.update {
                    it.copy(
                        isSignedIn = true,
                        userEmail = signIn.user.email,
                        userName = signIn.user.displayName
                    )
                }
                settingsDataStore.updateConnectedAccountEmail(signIn.user.email)
                signIn.user
            }
            SignInOutcome.Cancelled -> {
                _formState.update { it.copy(isUploading = false, uploadProgress = null) }
                _uiEvents.emit(MainUiEvent.ShowMessage("Raport zapisano lokalnie. Zaloguj się, aby wysłać na Dysk."))
                return
            }
            is SignInOutcome.Error -> {
                _formState.update { it.copy(isUploading = false, uploadProgress = null) }
                _uiEvents.emit(MainUiEvent.ShowMessage("Raport zapisano jako szkic. ${signIn.message}"))
                return
            }
        }

        // 2. Ensure Drive OAuth token is ready
        val token = cachedAccessToken ?: when (val driveAuth = authManager.requestDriveAccess(activity)) {
            is DriveAuthOutcome.Authorized -> {
                cachedAccessToken = driveAuth.accessToken
                _accountState.update { it.copy(hasDrivePermission = true) }
                driveAuth.accessToken
            }
            is DriveAuthOutcome.NeedsConsent -> {
                pendingUploadReportId = reportId
                _formState.update {
                    it.copy(uploadProgress = UploadProgress("Oczekiwanie na zgodę Dysku Google...", 0, 1))
                }
                _uiEvents.emit(MainUiEvent.LaunchDriveConsent(driveAuth.pendingIntent))
                return
            }
            is DriveAuthOutcome.Failed -> {
                _formState.update { it.copy(isUploading = false, uploadProgress = null) }
                _uiEvents.emit(MainUiEvent.ShowMessage("Raport zapisano lokalnie. ${driveAuth.message}"))
                return
            }
        }

        // 3. Upload with automatic single retry on 401 expired token
        executeUploadWithTokenRefresh(
            activity = activity,
            reportId = reportId,
            initialToken = token,
            accountEmail = user.email
        )
    }

    private suspend fun executeUploadWithTokenRefresh(
        activity: Activity,
        reportId: String,
        initialToken: String,
        accountEmail: String?
    ) {
        val rootFolder = preferences.value.rootFolderName
        try {
            val result = repository.uploadReportToDrive(
                reportId = reportId,
                accessToken = initialToken,
                accountEmail = accountEmail,
                rootFolderName = rootFolder,
                onProgress = { progress ->
                    _formState.update { it.copy(uploadProgress = progress) }
                }
            )
            _formState.update {
                it.copy(
                    isUploading = false,
                    uploadProgress = null,
                    lastSuccessFolderUrl = result.getOrNull(),
                    lastSuccessFolderName = rootFolder
                )
            }
            result.onSuccess {
                _uiEvents.emit(MainUiEvent.ShowMessage("Raport wysłano na Dysk Google!"))
            }.onFailure { error ->
                _uiEvents.emit(
                    MainUiEvent.ShowMessage(
                        error.localizedMessage ?: "Błąd podczas wysyłania. Możesz ponowić z Historii."
                    )
                )
            }
        } catch (unauthorized: DriveUnauthorizedException) {
            authManager.clearCachedToken(activity, initialToken)
            cachedAccessToken = null
            when (val refreshed = authManager.requestDriveAccess(activity)) {
                is DriveAuthOutcome.Authorized -> {
                    cachedAccessToken = refreshed.accessToken
                    val retryResult = runCatching {
                        repository.uploadReportToDrive(
                            reportId = reportId,
                            accessToken = refreshed.accessToken,
                            accountEmail = accountEmail,
                            rootFolderName = rootFolder,
                            onProgress = { progress ->
                                _formState.update { it.copy(uploadProgress = progress) }
                            }
                        )
                    }.getOrElse { Result.failure(it) }

                    _formState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = null,
                            lastSuccessFolderUrl = retryResult.getOrNull(),
                            lastSuccessFolderName = rootFolder
                        )
                    }
                    retryResult.onSuccess {
                        _uiEvents.emit(MainUiEvent.ShowMessage("Raport wysłano na Dysk Google!"))
                    }.onFailure { err ->
                        _uiEvents.emit(MainUiEvent.ShowMessage(err.localizedMessage ?: "Błąd wysyłki."))
                    }
                }
                is DriveAuthOutcome.NeedsConsent -> {
                    pendingUploadReportId = reportId
                    _uiEvents.emit(MainUiEvent.LaunchDriveConsent(refreshed.pendingIntent))
                }
                is DriveAuthOutcome.Failed -> {
                    _formState.update { it.copy(isUploading = false, uploadProgress = null) }
                    _uiEvents.emit(MainUiEvent.ShowMessage(refreshed.message))
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun deleteReport(bundle: EventReportWithPhotos) {
        viewModelScope.launch {
            repository.deleteReport(bundle)
            _uiEvents.emit(MainUiEvent.ShowMessage("Usunięto raport z historii lokalnej."))
        }
    }

    fun updateSettings(rootFolderName: String, optimizePhotoSize: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateRootFolderName(rootFolderName)
            settingsDataStore.updateOptimizePhotoSize(optimizePhotoSize)
            _uiEvents.emit(MainUiEvent.ShowMessage("Zapisano ustawienia Dysku Google."))
        }
    }

    fun openExternalUrl(url: String) {
        viewModelScope.launch {
            _uiEvents.emit(MainUiEvent.OpenExternalUrl(url))
        }
    }

    private fun resetFormAfterSave(folderUrl: String?, folderName: String?) {
        _formState.update {
            ReportFormState(
                eventTimestampMillis = System.currentTimeMillis(),
                category = EventCategory.INCIDENT,
                title = "",
                description = "",
                stagedPhotos = emptyList(),
                isUploading = it.isUploading,
                uploadProgress = it.uploadProgress,
                lastSuccessFolderUrl = folderUrl,
                lastSuccessFolderName = folderName
            )
        }
    }

    companion object {
        const val MAX_PHOTOS_PER_REPORT = 12
    }

    class Factory(
        private val appContext: Context,
        private val authManager: GoogleAuthManager,
        private val repository: ReportRepository,
        private val photoStorage: LocalPhotoStorage,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                appContext = appContext,
                authManager = authManager,
                repository = repository,
                photoStorage = photoStorage,
                settingsDataStore = settingsDataStore
            ) as T
        }
    }
}
