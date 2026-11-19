package com.example.data.drive

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface SignInOutcome {
    data class Success(val user: FirebaseUser) : SignInOutcome
    data object Cancelled : SignInOutcome
    data class Error(val message: String) : SignInOutcome
}

sealed interface DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthOutcome
    data class Failed(val message: String) : DriveAuthOutcome
}

class GoogleAuthManager {

    companion object {
        private const val TAG = "GoogleAuthManager"
        private val DRIVE_SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/drive.file"),
            Scope("https://www.googleapis.com/auth/userinfo.email")
        )
    }

    fun currentUser(): FirebaseUser? = Firebase.auth.currentUser

    suspend fun attemptSilentSignIn(context: Context): FirebaseUser? {
        Firebase.auth.currentUser?.let { return it }
        val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
            ?: return null
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authResult = Firebase.auth
                    .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                    .await()
                authResult.user
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign-in unavailable: ${e.message}")
            null
        }
    }

    suspend fun signInWithGoogle(activity: Activity): SignInOutcome {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.e(TAG, "Missing default_web_client_id", e)
            return SignInOutcome.Error("Brak konfiguracji OAuth (default_web_client_id).")
        }

        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authResult = Firebase.auth
                    .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                    .await()
                val user = authResult.user
                if (user != null) {
                    SignInOutcome.Success(user)
                } else {
                    SignInOutcome.Error("Nie udało się utworzyć sesji użytkownika.")
                }
            } else {
                SignInOutcome.Error("Nieobsługiwany typ poświadczeń Google.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            SignInOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            SignInOutcome.Error(e.localizedMessage ?: "Logowanie przez Google nie powiodło się.")
        }
    }

    suspend fun requestDriveAccess(activity: Activity): DriveAuthOutcome {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(DRIVE_SCOPES)
                .build()
            val result = Identity.getAuthorizationClient(activity)
                .authorize(request)
                .awaitGmsTask()

            val pendingIntent = result.pendingIntent
            val token = result.accessToken
            when {
                result.hasResolution() && pendingIntent != null -> {
                    DriveAuthOutcome.NeedsConsent(pendingIntent)
                }
                !token.isNullOrBlank() -> {
                    DriveAuthOutcome.Authorized(token)
                }
                else -> {
                    DriveAuthOutcome.Failed("Nie otrzymano tokenu dostępu do Dysku Google.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            Log.e(TAG, "AuthorizationClient ApiException status=${e.statusCode}", e)
            DriveAuthOutcome.Failed("Błąd autoryzacji Dysku Google (kod ${e.statusCode}).")
        } catch (e: Exception) {
            Log.e(TAG, "AuthorizationClient failed", e)
            DriveAuthOutcome.Failed(e.localizedMessage ?: "Autoryzacja Dysku Google nie powiodła się.")
        }
    }

    fun extractTokenFromConsentResult(context: Context, intentData: Intent?): DriveAuthOutcome {
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(intentData)
            val token = result.accessToken
            if (!token.isNullOrBlank()) {
                DriveAuthOutcome.Authorized(token)
            } else {
                DriveAuthOutcome.Failed("Brak tokenu po wyrażeniu zgody.")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "Drive consent ApiException status=${e.statusCode}", e)
            DriveAuthOutcome.Failed("Nie udzielono dostępu do Dysku Google (kod ${e.statusCode}).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse authorization intent", e)
            DriveAuthOutcome.Failed(e.localizedMessage ?: "Anulowano autoryzację Dysku Google.")
        }
    }

    suspend fun clearCachedToken(context: Context, token: String) {
        if (token.isBlank()) return
        try {
            withContext(Dispatchers.IO) {
                GoogleAuthUtil.clearToken(context, token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cached Drive token: ${e.message}")
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                CredentialManager.create(context)
                    .clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear CredentialManager state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
            }
        }
    }

    private suspend fun <T> Task<T>.awaitGmsTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }
}
