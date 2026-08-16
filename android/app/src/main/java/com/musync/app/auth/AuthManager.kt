package com.musync.app.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AuthProviderType {
    GOOGLE,
    GITHUB,
    GUEST
}

data class MusyncUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val provider: AuthProviderType,
    val isAnonymous: Boolean = false
)

enum class CloudSyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    OFFLINE,
    ERROR
}

class AuthManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "AuthManager"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentUser = MutableStateFlow<MusyncUser?>(null)
    val currentUser: StateFlow<MusyncUser?> = _currentUser.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus.IDLE)
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    init {
        // Observe Firebase Auth state
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user?.toMusyncUser()
            Log.d(TAG, "Auth state changed: user=${user?.uid}")
        }
        _currentUser.value = auth.currentUser?.toMusyncUser()
    }

    fun setSyncStatus(status: CloudSyncStatus) {
        _syncStatus.value = status
    }

    suspend fun signInWithGoogleCredential(idToken: String): Result<MusyncUser> {
        _authLoading.value = true
        _authError.value = null
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user?.toMusyncUser()
                ?: throw IllegalStateException("Firebase User is null after Google sign-in")
            _currentUser.value = user
            _authLoading.value = false
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            _authLoading.value = false
            _authError.value = e.localizedMessage ?: "Google Sign-In failed"
            Result.failure(e)
        }
    }

    fun signInWithGitHub(activity: Activity, onComplete: (Result<MusyncUser>) -> Unit) {
        _authLoading.value = true
        _authError.value = null

        val provider = OAuthProvider.newBuilder("github.com")
        val scopes = listOf("read:user", "user:email")
        provider.scopes = scopes

        val pendingResultTask = auth.pendingAuthResult
        if (pendingResultTask != null) {
            pendingResultTask
                .addOnSuccessListener { authResult ->
                    _authLoading.value = false
                    val user = authResult.user?.toMusyncUser()
                    if (user != null) {
                        _currentUser.value = user
                        onComplete(Result.success(user))
                    } else {
                        onComplete(Result.failure(IllegalStateException("No user returned")))
                    }
                }
                .addOnFailureListener { e ->
                    _authLoading.value = false
                    _authError.value = e.localizedMessage ?: "GitHub auth error"
                    onComplete(Result.failure(e))
                }
        } else {
            auth.startActivityForSignInWithProvider(activity, provider.build())
                .addOnSuccessListener { authResult ->
                    _authLoading.value = false
                    val user = authResult.user?.toMusyncUser()
                    if (user != null) {
                        _currentUser.value = user
                        onComplete(Result.success(user))
                    } else {
                        onComplete(Result.failure(IllegalStateException("No user returned")))
                    }
                }
                .addOnFailureListener { e ->
                    _authLoading.value = false
                    _authError.value = e.localizedMessage ?: "GitHub sign-in failed"
                    onComplete(Result.failure(e))
                }
        }
    }

    fun signOut() {
        try {
            auth.signOut()
            _currentUser.value = null
            _syncStatus.value = CloudSyncStatus.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error", e)
        }
    }

    private fun FirebaseUser.toMusyncUser(): MusyncUser {
        val providerId = providerData.firstOrNull { it.providerId != "firebase" }?.providerId
        val providerType = when (providerId) {
            "google.com" -> AuthProviderType.GOOGLE
            "github.com" -> AuthProviderType.GITHUB
            else -> if (isAnonymous) AuthProviderType.GUEST else AuthProviderType.GOOGLE
        }
        return MusyncUser(
            uid = uid,
            displayName = displayName ?: if (providerType == AuthProviderType.GITHUB) "GitHub User" else "Musync Listener",
            email = email,
            photoUrl = photoUrl?.toString(),
            provider = providerType,
            isAnonymous = isAnonymous
        )
    }
}
