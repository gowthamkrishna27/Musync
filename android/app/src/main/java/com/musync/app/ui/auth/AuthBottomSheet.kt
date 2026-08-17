package com.musync.app.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.musync.app.auth.AuthManager
import com.musync.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthBottomSheet(
    authManager: AuthManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isLoading by authManager.authLoading.collectAsState()
    val authError by authManager.authError.collectAsState()

    // Resolve the web client ID at composition time with a safe fallback.
    val webClientId = remember {
        try {
            context.getString(com.musync.app.R.string.default_web_client_id)
        } catch (_: Exception) {
            "989198851105-m97comku8uiv00cvilvvaen55m745o48.apps.googleusercontent.com"
        }
    }

    // Google Sign In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("AUTH", "Google Sign-In activity result: resultCode=${result.resultCode}")

        if (result.resultCode != Activity.RESULT_OK) {
            // User cancelled or back-pressed — surface a friendly message, don't log silently.
            android.util.Log.w("AUTH", "Google Sign-In cancelled or failed (resultCode=${result.resultCode})")
            // Only set error if not a plain cancel to avoid annoying the user
            if (result.resultCode != Activity.RESULT_CANCELED) {
                authManager.setAuthError("Google Sign-In was interrupted. Please try again.")
            }
            return@rememberLauncherForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken

            android.util.Log.d("AUTH", "Google Sign-In account obtained. idToken present: ${idToken != null}")

            if (idToken != null) {
                coroutineScope.launch {
                    android.util.Log.d("AUTH", "AUTH: Firebase credential exchange started")
                    val authResult = authManager.signInWithGoogleCredential(idToken)
                    if (authResult.isSuccess) {
                        android.util.Log.d("AUTH", "AUTH: success — currentUser present")
                        onDismiss()
                    } else {
                        android.util.Log.e("AUTH", "AUTH: Firebase credential exchange failed", authResult.exceptionOrNull())
                    }
                }
            } else {
                // idToken is null — this means requestIdToken() was not called or web client ID is wrong.
                val errorMsg = "Google Sign-In succeeded but returned no ID token. " +
                    "Verify the Web Client ID in Firebase Console and ensure the SHA-1 fingerprint " +
                    "is registered. webClientId=$webClientId"
                android.util.Log.e("AUTH", errorMsg)
                authManager.setAuthError("Sign-in failed: Could not obtain token. Please try again.")
            }
        } catch (e: ApiException) {
            // Map common Google API status codes to actionable messages for debugging.
            val statusCode = e.statusCode
            val description = when (statusCode) {
                7 -> "NETWORK_ERROR — No internet connection"
                8 -> "INTERNAL_ERROR"
                10 -> "DEVELOPER_ERROR — SHA-1 mismatch, wrong package name, or disabled Google provider in Firebase Console"
                12500 -> "SIGN_IN_FAILED — Google Play Services update required"
                12501 -> "SIGN_IN_CANCELLED — User cancelled"
                12502 -> "SIGN_IN_CURRENTLY_IN_PROGRESS"
                else -> "Unknown status code $statusCode"
            }
            android.util.Log.e("AUTH", "AUTH: Google Sign-In ApiException — code=$statusCode ($description)", e)

            val userMessage = when (statusCode) {
                7 -> "No internet connection. Please check your network."
                10 -> "Sign-in configuration error (code 10). Check Firebase Console."
                12501 -> null  // User intentionally cancelled — don't show error
                else -> "Google Sign-In failed (code $statusCode). Please try again."
            }
            if (userMessage != null) {
                authManager.setAuthError(userMessage)
            }
        } catch (e: Exception) {
            android.util.Log.e("AUTH", "AUTH: Google Sign-In unexpected error: ${e.message}", e)
            authManager.setAuthError("Unexpected error: ${e.localizedMessage}")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF5161822),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0x66FFFFFF))
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sign in to Musync",
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Backup and sync your favorites, custom playlists, and listening history across all your devices seamlessly.",
                color = TextGreySecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = authError != null) {
                Text(
                    text = authError ?: "",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    color = TextWhite,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // 1. Google Sign-In Button
                Button(
                    onClick = {
                        // Always build GSO with the resolved webClientId.
                        // requestIdToken() MUST be called or idToken will be null.
                        android.util.Log.d("AUTH", "AUTH: Google Sign-In requested. webClientId=$webClientId")
                        authManager.clearAuthError()
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(webClientId)
                            .requestEmail()
                            .requestProfile()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                        try {
                            googleSignInClient.signOut()
                        } catch (_: Exception) {}
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.musync.app.R.drawable.ic_google_logo),
                            contentDescription = "Google",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. GitHub Sign-In Button
                Button(
                    onClick = {
                        // Cast context to Activity safely — find the underlying Activity
                        // since LocalContext may be a ContextWrapper in some Compose trees.
                        val activity = findActivity(context)
                        if (activity != null) {
                            android.util.Log.d("AUTH", "AUTH: GitHub Sign-In requested")
                            authManager.clearAuthError()
                            authManager.signInWithGitHub(activity) { result ->
                                if (result.isSuccess) {
                                    onDismiss()
                                } else {
                                    android.util.Log.e("AUTH", "GitHub Sign-In failed", result.exceptionOrNull())
                                }
                            }
                        } else {
                            android.util.Log.e("AUTH", "GitHub Sign-In failed: could not resolve Activity from context")
                            authManager.setAuthError("GitHub Sign-In is not available in this context.")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF24292E),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.musync.app.R.drawable.ic_github_logo),
                            contentDescription = "GitHub",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with GitHub",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Guest / Skip Option
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            authManager.signInAnonymously()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Continue as Guest",
                        color = TextGreyMuted,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Safely traverses the ContextWrapper chain to find the underlying Activity.
 * Needed because LocalContext in Compose can return a ContextWrapper, not a raw Activity.
 */
private fun findActivity(context: android.content.Context): Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
