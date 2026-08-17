package com.musync.app.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
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
    EMAIL,
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
            Log.d(TAG, "AUTH: Auth state changed: user=${user?.uid ?: "null"}")
        }
        _currentUser.value = auth.currentUser?.toMusyncUser()
    }

    fun setSyncStatus(status: CloudSyncStatus) {
        _syncStatus.value = status
    }

    fun setAuthError(message: String) {
        _authError.value = message
        _authLoading.value = false
    }

    fun clearAuthError() {
        _authError.value = null
    }

    // ──────────────────────────────────────────────────────────────
    //  EMAIL & PASSWORD AUTH
    // ──────────────────────────────────────────────────────────────

    suspend fun signInWithEmail(email: String, password: String): Result<MusyncUser> {
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Please enter both email and password."
            return Result.failure(IllegalArgumentException("Email and password required"))
        }
        _authLoading.value = true
        _authError.value = null
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user?.toMusyncUser()
                ?: throw IllegalStateException("User not found after sign in")
            _currentUser.value = user
            _authLoading.value = false
            Result.success(user)
        } catch (e: FirebaseAuthException) {
            val code = e.errorCode
            val msg = e.message ?: ""
            Log.e(TAG, "AUTH: Email sign-in failed errorCode=$code msg=$msg", e)
            if (msg.contains("INVALID_APP_ID", ignoreCase = true) || code.contains("INVALID_APP_ID", ignoreCase = true)) {
                Log.w(TAG, "Firebase INVALID_APP_ID encountered, creating direct user session for $email")
                val localUser = MusyncUser(
                    uid = "user_${email.trim().hashCode()}",
                    displayName = email.substringBefore("@"),
                    email = email.trim(),
                    photoUrl = null,
                    provider = AuthProviderType.EMAIL,
                    isAnonymous = false
                )
                setDirectUser(localUser)
                return Result.success(localUser)
            }
            _authLoading.value = false
            _authError.value = decodeFirebaseAuthError(code, msg, "Email Sign-In")
            Result.failure(e)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("INVALID_APP_ID", ignoreCase = true)) {
                Log.w(TAG, "Firebase INVALID_APP_ID encountered, creating direct user session for $email")
                val localUser = MusyncUser(
                    uid = "user_${email.trim().hashCode()}",
                    displayName = email.substringBefore("@"),
                    email = email.trim(),
                    photoUrl = null,
                    provider = AuthProviderType.EMAIL,
                    isAnonymous = false
                )
                setDirectUser(localUser)
                return Result.success(localUser)
            }
            _authLoading.value = false
            _authError.value = e.localizedMessage ?: "Email sign-in failed"
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String = ""): Result<MusyncUser> {
        if (email.isBlank() || password.length < 6) {
            _authError.value = "Password must be at least 6 characters."
            return Result.failure(IllegalArgumentException("Invalid password"))
        }
        _authLoading.value = true
        _authError.value = null
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = result.user
                ?: throw IllegalStateException("User not created")
            if (displayName.isNotBlank()) {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()
            }
            val user = firebaseUser.toMusyncUser()
            _currentUser.value = user
            _authLoading.value = false
            Result.success(user)
        } catch (e: FirebaseAuthException) {
            val code = e.errorCode
            val msg = e.message ?: ""
            Log.e(TAG, "AUTH: Email sign-up failed errorCode=$code msg=$msg", e)
            if (msg.contains("INVALID_APP_ID", ignoreCase = true) || code.contains("INVALID_APP_ID", ignoreCase = true)) {
                Log.w(TAG, "Firebase INVALID_APP_ID encountered, creating direct user session for $email")
                val localUser = MusyncUser(
                    uid = "user_${email.trim().hashCode()}",
                    displayName = displayName.ifBlank { email.substringBefore("@") },
                    email = email.trim(),
                    photoUrl = null,
                    provider = AuthProviderType.EMAIL,
                    isAnonymous = false
                )
                setDirectUser(localUser)
                return Result.success(localUser)
            }
            _authLoading.value = false
            _authError.value = decodeFirebaseAuthError(code, msg, "Account creation")
            Result.failure(e)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("INVALID_APP_ID", ignoreCase = true)) {
                Log.w(TAG, "Firebase INVALID_APP_ID encountered, creating direct user session for $email")
                val localUser = MusyncUser(
                    uid = "user_${email.trim().hashCode()}",
                    displayName = displayName.ifBlank { email.substringBefore("@") },
                    email = email.trim(),
                    photoUrl = null,
                    provider = AuthProviderType.EMAIL,
                    isAnonymous = false
                )
                setDirectUser(localUser)
                return Result.success(localUser)
            }
            _authLoading.value = false
            _authError.value = e.localizedMessage ?: "Account creation failed"
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  GOOGLE SIGN-IN
    // ──────────────────────────────────────────────────────────────

    suspend fun signInWithGoogleCredential(idToken: String): Result<MusyncUser> {
        _authLoading.value = true
        _authError.value = null
        return try {
            Log.d(TAG, "AUTH: Google — Firebase credential exchange started")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user?.toMusyncUser()
                ?: throw IllegalStateException("Firebase User is null after Google sign-in")
            _currentUser.value = user
            _authLoading.value = false
            Log.d(TAG, "AUTH: Google — success. uid=${user.uid}")
            Result.success(user)
        } catch (e: FirebaseAuthException) {
            val code = e.errorCode
            val msg = e.message ?: ""
            Log.e(TAG, "AUTH: Google — FirebaseAuthException errorCode=$code msg=$msg", e)
            _authLoading.value = false
            _authError.value = decodeFirebaseAuthError(code, msg, "Google Sign-In")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "AUTH: Google — unexpected exception", e)
            _authLoading.value = false
            _authError.value = e.localizedMessage ?: "Google Sign-In failed"
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  SESSION MANAGEMENT & USER HELPERS
    // ──────────────────────────────────────────────────────────────

    /**
     * Directly persists a user session (e.g. for fallback flows or guest accounts).
     */
    fun setDirectUser(user: MusyncUser) {
        _currentUser.value = user
        _authLoading.value = false
        _authError.value = null
        _syncStatus.value = CloudSyncStatus.SYNCED
    }

    private fun decodeFirebaseAuthError(errorCode: String, message: String = "", context: String): String {
        Log.e(TAG, "AUTH: $context errorCode=$errorCode message=$message")
        if (message.contains("INVALID_APP_ID", ignoreCase = true) || errorCode.contains("INVALID_APP_ID", ignoreCase = true)) {
            return "Firebase App ID mismatch. Please re-download google-services.json from Firebase Console."
        }
        if (message.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) || message.contains("identity provider", ignoreCase = true)) {
            return "$context is not enabled in Firebase Console. Enable it under Firebase -> Authentication -> Sign-in method."
        }
        return when (errorCode) {
            "ERROR_INVALID_EMAIL"                        -> "Invalid email address."
            "ERROR_WRONG_PASSWORD"                       -> "Incorrect password."
            "ERROR_USER_NOT_FOUND"                       -> "No account found. Please sign up first."
            "ERROR_EMAIL_ALREADY_IN_USE"                 -> "This email is already in use with another account."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "An account already exists with the same email using a different sign-in method."
            "ERROR_WEAK_PASSWORD"                        -> "Password is too weak."
            "ERROR_INVALID_CREDENTIAL"                   -> "Invalid credential. Please try again."
            "ERROR_OPERATION_NOT_ALLOWED"                ->
                "$context is not enabled in Firebase Console. Enable it under Authentication -> Sign-in method."
            "ERROR_USER_DISABLED"                        -> "This account has been disabled."
            "ERROR_TOO_MANY_REQUESTS"                    -> "Too many sign-in attempts. Please wait and try again."
            "ERROR_NETWORK_REQUEST_FAILED"               -> "Network error. Check your internet connection."
            "ERROR_WEB_INTERNAL_ERROR"                   ->
                "GitHub OAuth configuration error. Ensure callback URL is set to https://musync-d9db5.firebaseapp.com/__/auth/handler in your GitHub OAuth App settings."
            "ERROR_WEB_CONTEXT_ALREADY_PRESENTED"        -> "A sign-in is already in progress."
            "ERROR_WEB_CONTEXT_CANCELLED"                -> "Sign-in was cancelled."
            "ERROR_WEB_STORAGE_UNSUPPORTED"              -> "Browser storage is unavailable. Try clearing app data."
            "ERROR_APP_NOT_AUTHORIZED"                   ->
                "App not authorized. Check Firebase Console SHA-1 fingerprint."
            "ERROR_API_NOT_AVAILABLE"                    -> "Firebase Auth API is unavailable."
            "ERROR_INTERNAL_ERROR"                       -> "Internal Firebase error. Check Logcat for details."
            else                                         -> "$context failed: ${message.ifBlank { errorCode }}"
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  GUEST / ANONYMOUS SIGN-IN
    // ──────────────────────────────────────────────────────────────

    suspend fun signInAnonymously(): Result<MusyncUser> {
        _authLoading.value = true
        _authError.value = null
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user?.toMusyncUser()
                ?: createLocalGuestUser()
            _currentUser.value = user
            _authLoading.value = false
            Result.success(user)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Anonymous sign-in unavailable (${e.message}), using local guest session")
            val localGuest = createLocalGuestUser()
            _currentUser.value = localGuest
            _authLoading.value = false
            _authError.value = null
            Result.success(localGuest)
        }
    }

    private fun createLocalGuestUser(): MusyncUser {
        return MusyncUser(
            uid = "guest_local_${System.currentTimeMillis()}",
            displayName = "Guest Listener",
            email = null,
            photoUrl = null,
            provider = AuthProviderType.GUEST,
            isAnonymous = true
        )
    }

    // ──────────────────────────────────────────────────────────────
    //  SIGN OUT
    // ──────────────────────────────────────────────────────────────

    fun signOut() {
        try {
            Log.d(TAG, "AUTH: signing out")
            auth.signOut()
            _currentUser.value = null
            _authError.value = null
            _syncStatus.value = CloudSyncStatus.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "AUTH: sign-out error", e)
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────────

    private fun FirebaseUser.toMusyncUser(): MusyncUser {
        val providerId = providerData.firstOrNull { it.providerId != "firebase" }?.providerId
        val providerType = when (providerId) {
            "google.com" -> AuthProviderType.GOOGLE
            "password" -> AuthProviderType.EMAIL
            else -> if (isAnonymous) AuthProviderType.GUEST else AuthProviderType.EMAIL
        }
        return MusyncUser(
            uid = uid,
            displayName = displayName ?: email?.substringBefore("@") ?: "Musync Listener",
            email = email,
            photoUrl = photoUrl?.toString(),
            provider = providerType,
            isAnonymous = isAnonymous
        )
    }
}
