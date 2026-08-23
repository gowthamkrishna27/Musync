package com.musync.app.auth

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
    private val context: Context,
    private val supabase: SupabaseClient
) {
    companion object {
        private const val TAG = "AuthManager"
    }

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
        // Observe Supabase auth session state changes
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = supabase.auth.currentUserOrNull()?.toMusyncUser()
                        _currentUser.value = user
                        Log.d(TAG, "AUTH: Authenticated uid=${user?.uid ?: "null"}")
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _currentUser.value = null
                        Log.d(TAG, "AUTH: Not authenticated")
                    }
                    is SessionStatus.Initializing -> {
                        Log.d(TAG, "AUTH: Loading session from storage...")
                    }
                    is SessionStatus.RefreshFailure -> {
                        Log.w(TAG, "AUTH: Session refresh failed — ${status.cause}")
                        // Keep the last known user so the app doesn't log out unexpectedly on flaky networks
                    }
                    else -> {}
                }
            }
        }
        // Set initial state immediately (avoids flash of logged-out state)
        scope.launch(Dispatchers.IO) {
            _currentUser.value = supabase.auth.currentUserOrNull()?.toMusyncUser()
        }
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
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            val user = supabase.auth.currentUserOrNull()?.toMusyncUser()
                ?: throw IllegalStateException("User not found after sign in")
            _currentUser.value = user
            _authLoading.value = false
            Result.success(user)
        } catch (e: BadRequestRestException) {
            _authLoading.value = false
            _authError.value = decodeSupabaseAuthError(e, "Email Sign-In")
            Result.failure(e)
        } catch (e: RestException) {
            _authLoading.value = false
            _authError.value = decodeSupabaseAuthError(e, "Email Sign-In")
            Result.failure(e)
        } catch (e: Exception) {
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
            supabase.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                if (displayName.isNotBlank()) {
                    data = buildJsonObject {
                        put("display_name", displayName.trim())
                        put("name", displayName.trim())
                    }
                }
            }
            // After sign-up Supabase may require email confirmation; try to get current user
            val user = supabase.auth.currentUserOrNull()?.toMusyncUser()
                ?: MusyncUser(
                    uid = "pending_${email.trim().hashCode()}",
                    displayName = displayName.ifBlank { email.substringBefore("@") },
                    email = email.trim(),
                    photoUrl = null,
                    provider = AuthProviderType.EMAIL,
                    isAnonymous = false
                )
            _currentUser.value = user
            _authLoading.value = false
            Result.success(user)
        } catch (e: BadRequestRestException) {
            _authLoading.value = false
            _authError.value = decodeSupabaseAuthError(e, "Account creation")
            Result.failure(e)
        } catch (e: RestException) {
            _authLoading.value = false
            _authError.value = decodeSupabaseAuthError(e, "Account creation")
            Result.failure(e)
        } catch (e: Exception) {
            _authLoading.value = false
            _authError.value = e.localizedMessage ?: "Account creation failed"
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  GOOGLE SIGN-IN
    //  The Google ID token is obtained by AuthBottomSheet.kt using
    //  the Google Sign-In SDK, then passed here for the Supabase exchange.
    // ──────────────────────────────────────────────────────────────

    suspend fun signInWithGoogleCredential(idToken: String): Result<MusyncUser> {
        _authLoading.value = true
        _authError.value = null
        return try {
            Log.d(TAG, "AUTH: Google — Supabase IDToken exchange started")
            supabase.auth.signInWith(IDToken) {
                provider = Google
                this.idToken = idToken
            }
            val user = supabase.auth.currentUserOrNull()?.toMusyncUser()
                ?: throw IllegalStateException("Supabase user is null after Google sign-in")
            _currentUser.value = user
            _authLoading.value = false
            Log.d(TAG, "AUTH: Google — success. uid=${user.uid}")
            Result.success(user)
        } catch (e: RestException) {
            Log.e(TAG, "AUTH: Google — RestException: ${e.message}", e)
            _authLoading.value = false
            _authError.value = decodeSupabaseAuthError(e, "Google Sign-In")
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

    private fun decodeSupabaseAuthError(e: Exception, context: String): String {
        Log.e(TAG, "AUTH: $context error: ${e.message}")
        val msg = e.message ?: ""
        return when {
            msg.contains("invalid_credentials", ignoreCase = true) ||
            msg.contains("Invalid login credentials", ignoreCase = true) ->
                "Invalid email or password."
            msg.contains("email not confirmed", ignoreCase = true) ->
                "Please confirm your email address before signing in."
            msg.contains("user already registered", ignoreCase = true) ||
            msg.contains("User already registered", ignoreCase = true) ->
                "This email is already registered. Please sign in instead."
            msg.contains("email_address_not_authorized", ignoreCase = true) ->
                "This email is not authorized."
            msg.contains("weak_password", ignoreCase = true) ->
                "Password is too weak. Use at least 6 characters."
            msg.contains("over_email_send_rate_limit", ignoreCase = true) ->
                "Too many requests. Please wait before trying again."
            msg.contains("network", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ->
                "Network error. Check your internet connection."
            else -> "$context failed: $msg"
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  GUEST / ANONYMOUS SIGN-IN
    // ──────────────────────────────────────────────────────────────

    suspend fun signInAnonymously(): Result<MusyncUser> {
        _authLoading.value = true
        _authError.value = null
        return try {
            // Use a local guest session — no Supabase anonymous sign-in needed
            val localGuest = createLocalGuestUser()
            _currentUser.value = localGuest
            _authLoading.value = false
            Result.success(localGuest)
        } catch (e: Exception) {
            Log.w(TAG, "Guest session error (${e.message}), using local guest session")
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
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "AUTH: signing out")
                supabase.auth.signOut()
            } catch (e: Exception) {
                Log.w(TAG, "AUTH: sign-out remote error (ignored): ${e.message}")
            } finally {
                _currentUser.value = null
                _authError.value = null
                _syncStatus.value = CloudSyncStatus.IDLE
            }
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        supabase.auth.resetPasswordForEmail(
            email = email.trim(),
            redirectUrl = "musync://reset-password"
        )
    }

    suspend fun updateProfileName(newName: String): Result<MusyncUser> = runCatching {
        val current = supabase.auth.currentUserOrNull()
        if (current != null) {
            supabase.auth.updateUser {
                data = buildJsonObject {
                    put("display_name", newName.trim())
                    put("name", newName.trim())
                }
            }
            val updated = supabase.auth.currentUserOrNull()?.toMusyncUser()
                ?: _currentUser.value?.copy(displayName = newName.trim())
                ?: throw IllegalStateException("No active user session")
            _currentUser.value = updated
            updated
        } else {
            // Guest or local user: update in-memory only
            val cur = _currentUser.value ?: throw IllegalStateException("No active user session")
            val updated = cur.copy(displayName = newName.trim())
            setDirectUser(updated)
            updated
        }
    }

    suspend fun updatePassword(newPass: String): Result<Unit> = runCatching {
        supabase.auth.currentUserOrNull()
            ?: throw IllegalStateException("Not signed in to a Supabase account")
        supabase.auth.updateUser {
            password = newPass.trim()
        }
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        // NOTE: Full account deletion requires the Supabase secret key (admin privilege).
        // This operation is intentionally handled as a sign-out here. To fully delete the
        // account, create a backend endpoint that uses SUPABASE_SECRET_KEY with supabase-admin.
        Log.w(TAG, "AUTH: deleteAccount — signing out (server-side deletion requires admin API)")
        supabase.auth.signOut()
        _currentUser.value = null
        _syncStatus.value = CloudSyncStatus.IDLE
    }

    // ──────────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────────

    private fun UserInfo.toMusyncUser(): MusyncUser {
        // Provider from app metadata (set by Supabase: "email", "google", "github", etc.)
        val providerStr = appMetadata?.get("provider")?.jsonPrimitive?.contentOrNull ?: "email"
        val providerType = when {
            providerStr.contains("google") -> AuthProviderType.GOOGLE
            providerStr == "email" -> AuthProviderType.EMAIL
            else -> AuthProviderType.EMAIL
        }

        // Display name from user metadata (set on sign-up or updated via updateUser)
        val displayName = userMetadata?.get("display_name")?.jsonPrimitive?.contentOrNull
            ?: userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
            ?: userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?: email?.substringBefore("@")
            ?: "Musync Listener"

        // Avatar URL (Google / OAuth providers set this in user metadata)
        val photoUrl = userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
            ?: userMetadata?.get("picture")?.jsonPrimitive?.contentOrNull

        return MusyncUser(
            uid = id,
            displayName = displayName,
            email = email,
            photoUrl = photoUrl,
            provider = providerType,
            isAnonymous = false
        )
    }
}
