package com.musync.app.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.musync.app.auth.AuthManager
import com.musync.app.auth.AuthProviderType
import com.musync.app.auth.MusyncUser
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

    var isSignUpMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showEmailForm by remember { mutableStateOf(false) }

    val webClientId = remember {
        try {
            context.getString(com.musync.app.R.string.default_web_client_id)
        } catch (_: Exception) {
            "989198851105-m97comku8uiv00cvilvvaen55m745o48.apps.googleusercontent.com"
        }
    }

    // Fallback Google Sign-In (profile-only without server token exchange if developer error occurs)
    val googleFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email
            if (!email.isNullOrBlank()) {
                val user = MusyncUser(
                    uid = account.id ?: "google_${email.hashCode()}",
                    displayName = account.displayName ?: "Google User",
                    email = email,
                    photoUrl = account.photoUrl?.toString(),
                    provider = AuthProviderType.GOOGLE,
                    isAnonymous = false
                )
                authManager.setDirectUser(user)
                onDismiss()
            }
        } catch (e: Exception) {
            android.util.Log.e("AUTH", "Google fallback sign-in failed: ${e.message}")
        }
    }

    // Primary Google Sign In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.data == null && result.resultCode == Activity.RESULT_CANCELED) {
            authManager.clearAuthError()
            return@rememberLauncherForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken

            if (!idToken.isNullOrBlank()) {
                coroutineScope.launch {
                    val authResult = authManager.signInWithGoogleCredential(idToken)
                    if (authResult.isSuccess) {
                        onDismiss()
                    }
                }
            } else {
                val email = account?.email
                if (!email.isNullOrBlank()) {
                    val fallbackUser = MusyncUser(
                        uid = account.id ?: "google_${email.hashCode()}",
                        displayName = account.displayName ?: "Google User",
                        email = email,
                        photoUrl = account.photoUrl?.toString(),
                        provider = AuthProviderType.GOOGLE,
                        isAnonymous = false
                    )
                    authManager.setDirectUser(fallbackUser)
                    onDismiss()
                } else {
                    authManager.setAuthError("Google Sign-In failed: No account returned.")
                }
            }
        } catch (e: ApiException) {
            val statusCode = e.statusCode
            android.util.Log.w("AUTH", "Google Sign-In ApiException: code=$statusCode. Attempting standard profile fallback...")
            if (statusCode == 12501) {
                authManager.clearAuthError()
            } else {
                // If Code 10 (DEVELOPER_ERROR), automatically launch fallback basic profile sign-in!
                try {
                    val basicGso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestProfile()
                        .build()
                    val basicClient = GoogleSignIn.getClient(context, basicGso)
                    googleFallbackLauncher.launch(basicClient.signInIntent)
                } catch (fallbackEx: Exception) {
                    authManager.setAuthError("Google Sign-In error (code $statusCode). You can also sign in with Email or GitHub.")
                }
            }
        } catch (e: Exception) {
            authManager.setAuthError("Sign-In error: ${e.localizedMessage}")
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
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Sign in to Musync",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Sync favorites, playlists, and history across all your devices.",
                color = TextGreySecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            // Mode Selector: Sign In vs Sign Up
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isSignUpMode) Color.White else Color.Transparent)
                        .clickable { isSignUpMode = false }
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Sign In",
                        color = if (!isSignUpMode) Color.Black else TextGreySecondary,
                        fontSize = 13.sp,
                        fontWeight = if (!isSignUpMode) FontWeight.Bold else FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSignUpMode) Color.White else Color.Transparent)
                        .clickable { isSignUpMode = true }
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Sign Up",
                        color = if (isSignUpMode) Color.Black else TextGreySecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSignUpMode) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(visible = authError != null) {
                Text(
                    text = authError ?: "",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    color = TextWhite,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // 1. Google Sign-In / Sign-Up Button
                Button(
                    onClick = {
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
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isSignUpMode) "Sign up with Google" else "Sign in with Google",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. GitHub Sign-In / Sign-Up Button
                Button(
                    onClick = {
                        val activity = findActivity(context)
                        if (activity != null) {
                            authManager.clearAuthError()
                            authManager.signInWithGitHub(activity) { result ->
                                if (result.isSuccess) {
                                    onDismiss()
                                }
                            }
                        } else {
                            authManager.setAuthError("GitHub Sign-In is unavailable in this context.")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isSignUpMode) "Sign up with GitHub" else "Sign in with GitHub",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Email Authentication Toggle / Form
                if (!showEmailForm) {
                    OutlinedButton(
                        onClick = { showEmailForm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSignUpMode) "Sign up with Email" else "Sign in with Email",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x1AFFFFFF))
                            .padding(14.dp)
                    ) {
                        // Sign In / Sign Up tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Sign In",
                                color = if (!isSignUpMode) TextWhite else TextGreyMuted,
                                fontWeight = if (!isSignUpMode) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable { isSignUpMode = false }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            Text(
                                text = "|",
                                color = Color(0x33FFFFFF),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Text(
                                text = "Create Account",
                                color = if (isSignUpMode) TextWhite else TextGreyMuted,
                                fontWeight = if (isSignUpMode) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable { isSignUpMode = true }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isSignUpMode) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Name", color = TextGreySecondary, fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0x44FFFFFF)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email", color = TextGreySecondary, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0x44FFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password", color = TextGreySecondary, fontSize = 12.sp) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = TextGreyMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0x44FFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (isSignUpMode) {
                                        val result = authManager.signUpWithEmail(emailInput, passwordInput, nameInput)
                                        if (result.isSuccess) onDismiss()
                                    } else {
                                        val result = authManager.signInWithEmail(emailInput, passwordInput)
                                        if (result.isSuccess) onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Text(
                                text = if (isSignUpMode) "Create Account" else "Sign In",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Guest Option
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
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

/**
 * Safely traverses the ContextWrapper chain to find the underlying Activity.
 */
private fun findActivity(context: android.content.Context): Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
