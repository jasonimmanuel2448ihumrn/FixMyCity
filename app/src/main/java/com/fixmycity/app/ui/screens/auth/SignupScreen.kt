package com.fixmycity.app.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.fixmycity.app.ui.theme.Primary
import com.fixmycity.app.ui.theme.TextPrimary
import com.fixmycity.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private fun isValidEmailSignup(email: String) =
    android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

@Composable
fun SignupScreen(
    onSignup: () -> Unit,
    onLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val authState by viewModel.uiState.collectAsState()

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var fullNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    var agreeToTerms by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(authState.error) {
        authState.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    fun validate(): Boolean {
        var valid = true
        if (fullName.isBlank()) {
            fullNameError = "Full name is required"; valid = false
        } else fullNameError = null

        if (email.isBlank() || !isValidEmailSignup(email)) {
            emailError = "Enter a valid email address"; valid = false
        } else emailError = null

        if (phone.isBlank() || phone.length < 7) {
            phoneError = "Enter a valid phone number"; valid = false
        } else phoneError = null

        if (password.length < 6) {
            passwordError = "Password must be at least 6 characters"; valid = false
        } else passwordError = null

        if (confirmPassword != password) {
            confirmPasswordError = "Passwords do not match"; valid = false
        } else confirmPasswordError = null

        if (!agreeToTerms) {
            scope.launch { snackbarHostState.showSnackbar("Please agree to Terms & Conditions") }
            valid = false
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
            // Header
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(70.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Create Your Account",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Join thousands making their city better",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Form fields
            AppTextField(
                label = "Full Name",
                value = fullName,
                onValueChange = { fullName = it; fullNameError = null },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                isError = fullNameError != null,
                errorMessage = fullNameError,
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(modifier = Modifier.height(14.dp))
            AppTextField(
                label = "Email Address",
                value = email,
                onValueChange = { email = it; emailError = null },
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                isError = emailError != null,
                errorMessage = emailError,
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(modifier = Modifier.height(14.dp))
            AppTextField(
                label = "Phone Number",
                value = phone,
                onValueChange = { phone = it; phoneError = null },
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
                isError = phoneError != null,
                errorMessage = phoneError,
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(modifier = Modifier.height(14.dp))
            PasswordTextField(
                label = "Password",
                value = password,
                onValueChange = { password = it; passwordError = null },
                imeAction = ImeAction.Next,
                isError = passwordError != null,
                errorMessage = passwordError,
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(modifier = Modifier.height(14.dp))
            PasswordTextField(
                label = "Confirm Password",
                value = confirmPassword,
                onValueChange = { confirmPassword = it; confirmPasswordError = null },
                imeAction = ImeAction.Done,
                isError = confirmPasswordError != null,
                errorMessage = confirmPasswordError,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Terms checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = agreeToTerms,
                    onCheckedChange = { agreeToTerms = it },
                    colors = CheckboxDefaults.colors(checkedColor = Primary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "I agree to Terms & Conditions and Privacy Policy",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            AppButton(
                text = "Create Account",
                onClick = {
                    if (validate()) {
                        viewModel.signup(
                            name = fullName.trim(),
                            email = email.trim(),
                            phone = phone.trim(),
                            password = password,
                            onSuccess = onSignup
                        )
                    }
                },
                isLoading = authState.isLoading
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Already have an account?", fontSize = 14.sp, color = TextSecondary)
                TextOnlyButton(text = "Login", onClick = onLogin)
            }
        }
    }
}
