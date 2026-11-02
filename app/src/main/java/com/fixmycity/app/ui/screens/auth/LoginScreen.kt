package com.fixmycity.app.ui.screens.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fixmycity.app.R
import com.fixmycity.app.ui.components.AppButton
import com.fixmycity.app.ui.components.AppTextField
import com.fixmycity.app.ui.components.PasswordTextField
import com.fixmycity.app.ui.components.TextOnlyButton
import com.fixmycity.app.ui.theme.Border
import com.fixmycity.app.ui.theme.Error
import com.fixmycity.app.ui.theme.Primary
import com.fixmycity.app.ui.theme.TextPrimary
import com.fixmycity.app.ui.theme.TextSecondary
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

private fun isValidEmail(email: String) =
    android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

@Composable
fun LoginScreen(
    initialRole: String = "Citizen",
    onLogin: () -> Unit,
    onSignup: () -> Unit,
    onForgotPassword: () -> Unit,
    onMunicipalLogin: () -> Unit = onLogin,
    onGoogleLogin: () -> Unit = onLogin,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val authState by viewModel.uiState.collectAsState()
    var selectedRole by remember(initialRole) { mutableStateOf(initialRole) } // "Citizen" or "Municipal"
    var email by remember(initialRole) { mutableStateOf(if (initialRole == "Municipal") "municipal@fixmycity.org" else "") }
    var password by remember(initialRole) { mutableStateOf(if (initialRole == "Municipal") "admin123" else "") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(authState.error) {
        authState.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    val gso = remember(context) {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
        val webClientIdResId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (webClientIdResId != 0) {
            builder.requestIdToken(context.getString(webClientIdResId))
        }
        builder.build()
    }
    val googleSignInClient = remember(context) { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                viewModel.loginWithGoogle(
                    idToken = account.idToken,
                    displayName = account.displayName,
                    email = account.email,
                    photoUrl = account.photoUrl?.toString(),
                    onSuccess = onLogin
                )
            }
        } catch (e: ApiException) {
            val errorMsg = when (e.statusCode) {
                10 -> "Google Sign-In (Code 10): SHA-1 fingerprint must be added to your Firebase project settings."
                7 -> "Network error during Google Sign-In. Please check your connection."
                12501 -> "Google Sign-In was cancelled."
                12500 -> "Google Sign-In failed (Code 12500). Please ensure Google Play Services is updated."
                else -> "Google Sign-In error (${e.statusCode}): ${e.message}"
            }
            scope.launch {
                snackbarHostState.showSnackbar(errorMsg)
            }
        } catch (e: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("Google sign-in error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun validate(): Boolean {
        var valid = true
        if (email.isBlank() || !isValidEmail(email)) {
            emailError = "Enter a valid email address"
            valid = false
        } else {
            emailError = null
        }
        if (password.length < 6) {
            passwordError = "Password must be at least 6 characters"
            valid = false
        } else {
            passwordError = null
        }
        return valid
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Branding
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(80.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Welcome Back",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (selectedRole == "Municipal") "Municipal Officer Administration Portal" else "Login to continue helping your city",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Role Selector Tabs (Citizen vs Municipal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE2E8F0))
                    .padding(4.dp)
            ) {
                // Citizen Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedRole == "Citizen") Primary else Color.Transparent)
                        .clickable {
                            selectedRole = "Citizen"
                            email = ""
                            password = ""
                            emailError = null
                            passwordError = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Citizen Login",
                        fontWeight = if (selectedRole == "Citizen") FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedRole == "Citizen") Color.White else TextSecondary,
                        fontSize = 13.sp
                    )
                }

                // Municipal Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedRole == "Municipal") Color(0xFF1E293B) else Color.Transparent)
                        .clickable {
                            selectedRole = "Municipal"
                            email = "municipal@fixmycity.org"
                            password = "admin123"
                            emailError = null
                            passwordError = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🏛️ Municipal Official",
                            fontWeight = if (selectedRole == "Municipal") FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedRole == "Municipal") Color.White else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedRole == "Municipal") {
                // Official municipal banner & Quick fill
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏛️", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Official Authority Access",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Demo Credentials: municipal@fixmycity.org / admin123",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                email = "municipal@fixmycity.org"
                                password = "admin123"
                                emailError = null
                                passwordError = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("Autofill Demo Credentials", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Google login (Citizen only)
                OutlinedButton(
                    onClick = {
                        googleSignInClient.signOut().addOnCompleteListener {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                    enabled = !authState.isLoading
                ) {
                    if (authState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // OR divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Border)
                    Text(
                        text = "  OR  ",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Border)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Email Field
            AppTextField(
                label = if (selectedRole == "Municipal") "Municipal Officer Email" else "Email Address",
                value = email,
                onValueChange = {
                    email = it
                    if (emailError != null) emailError = null
                },
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                isError = emailError != null,
                errorMessage = emailError,
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            PasswordTextField(
                label = "Password",
                value = password,
                onValueChange = {
                    password = it
                    if (passwordError != null) passwordError = null
                },
                imeAction = ImeAction.Done,
                isError = passwordError != null,
                errorMessage = passwordError,
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedRole == "Citizen") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onForgotPassword) {
                        Text("Forgot Password?", color = Primary, fontSize = 13.sp)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppButton(
                text = if (selectedRole == "Municipal") "Access Municipal Dashboard" else "Login",
                onClick = {
                    if (validate()) {
                        viewModel.login(email.trim(), password) { loggedInUser ->
                            if (loggedInUser?.role.equals("Municipal", ignoreCase = true) || selectedRole == "Municipal") {
                                onMunicipalLogin()
                            } else {
                                onLogin()
                            }
                        }
                    }
                },
                isLoading = authState.isLoading
            )

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = Border)

            Spacer(modifier = Modifier.height(16.dp))

            // Sign up row - ONLY for Citizen! NO sign-up option for Municipal!
            if (selectedRole == "Citizen") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Don't have an account?",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    TextOnlyButton(text = "Create Account", onClick = onSignup)
                }
            } else {
                // Official municipal advisory note instead of sign up
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Municipal accounts are strictly provisioned by City Administration. Public registration is not permitted.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
