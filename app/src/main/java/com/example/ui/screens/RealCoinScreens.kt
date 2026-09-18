package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.P2POrder
import com.example.data.db.P2PAdEntity
import com.example.data.db.P2POrderEntity
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.example.data.repository.RealCoinRepository

// -------------------------------------------------------------
// LANDING PAGE
// -------------------------------------------------------------
@Composable
fun LandingScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(90.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MonetizationOn,
                    contentDescription = "RealCoin Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "RealCoin",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Decentralized Wallet & BEP-20 Ecosystem",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onNavigateToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("get_started_button")
        ) {
            Text("Login to Wallet", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onNavigateToRegister,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("register_nav_button")
        ) {
            Text("Create New Account", fontSize = 16.sp)
        }
    }
}

// -------------------------------------------------------------
// LOGIN PAGE
// -------------------------------------------------------------
@Composable
fun LoginScreen(
    onLoginSubmit: (usernameOrEmail: String, password: String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var usernameOrEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Login to Wallet",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Enter your credentials to access RealCoin",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = usernameOrEmail,
            onValueChange = { usernameOrEmail = it },
            label = { Text("Username or Email") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_username_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_password_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (usernameOrEmail.isNotBlank() && password.isNotBlank()) {
                    onLoginSubmit(usernameOrEmail.trim(), password)
                } else {
                    Toast.makeText(
                        context,
                        "Please fill in all fields",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("login_submit_button")
        ) {
            Text("Login")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onNavigateToForgotPassword,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("forgot_password_button")
        ) {
            Text("Forgot Password?")
        }

        TextButton(
            onClick = onNavigateToRegister,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Don't have an account? Register")
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Back to Welcome")
        }
    }
}

// -------------------------------------------------------------
// FORGOT PASSWORD — ADMIN VERIFICATION
// -------------------------------------------------------------
@Composable
fun ForgotPasswordEntryScreen(
    onRequestRecovery: (
        email: String,
        onSuccess: (recoveryRequestId: String) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onCheckRecoveryStatus: (
        recoveryRequestId: String,
        onSuccess: (status: String, email: String?) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onResetPassword: (
        email: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var recoveryRequestId by remember { mutableStateOf<String?>(null) }
    var recoveryStatus by remember { mutableStateOf("NONE") }

    var isSubmitting by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Forgot Password",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            when (recoveryStatus) {
                "PENDING" ->
                    "Your recovery request is waiting for admin verification."

                "APPROVED" ->
                    "Your recovery request was approved. You can now create a new password."

                "REJECTED" ->
                    "Your recovery request was rejected. You can submit a new request."

                "COMPLETED" ->
                    "Your password has been changed."

                else ->
                    "Enter the email address registered with your RealCoin account."
            },
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            enabled = !isSubmitting &&
                !isResetting &&
                recoveryStatus !in setOf("PENDING", "APPROVED"),
            label = { Text("Registered Email") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("forgot_password_email_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (recoveryStatus) {

            "NONE", "REJECTED" -> {
                Button(
                    onClick = {
                        isSubmitting = true

                        onRequestRecovery(
                            email.trim(),
                            { requestId ->
                                recoveryRequestId = requestId
                                recoveryStatus = "PENDING"
                                isSubmitting = false

                                Toast.makeText(
                                    context,
                                    "Recovery request submitted. Please wait for admin verification.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            { error ->
                                isSubmitting = false

                                Toast.makeText(
                                    context,
                                    error,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    },
                    enabled = email.isNotBlank() && !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("forgot_password_request_button")
                ) {
                    Text(
                        if (isSubmitting)
                            "Submitting..."
                        else
                            "Request Password Recovery"
                    )
                }
            }

            "PENDING" -> {
                Text(
                    "Request ID: ${recoveryRequestId ?: "Unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val requestId =
                            recoveryRequestId ?: return@Button

                        isChecking = true

                        onCheckRecoveryStatus(
                            requestId,
                            { status, returnedEmail ->
                                recoveryStatus = status

                                if (!returnedEmail.isNullOrBlank()) {
                                    email = returnedEmail
                                }

                                isChecking = false

                                Toast.makeText(
                                    context,
                                    when (status) {
                                        "APPROVED" ->
                                            "Your recovery request was approved."

                                        "REJECTED" ->
                                            "Your recovery request was rejected."

                                        else ->
                                            "Your request is still waiting for admin verification."
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            { error ->
                                isChecking = false

                                Toast.makeText(
                                    context,
                                    error,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    },
                    enabled = !isChecking &&
                        recoveryRequestId != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        if (isChecking)
                            "Checking..."
                        else
                            "Check Admin Approval"
                    )
                }
            }

            "APPROVED" -> {
                Text(
                    "Create a new password for your RealCoin account.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(
                        "forgot_password_approved_message"
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    enabled = !isResetting,
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            "forgot_password_new_password_input"
                        )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                    },
                    enabled = !isResetting,
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            "forgot_password_confirm_password_input"
                        )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (newPassword.length < 8) {
                            Toast.makeText(
                                context,
                                "Password must be at least 8 characters.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (newPassword != confirmPassword) {
                            Toast.makeText(
                                context,
                                "Passwords do not match.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            isResetting = true

                            onResetPassword(
                                email.trim(),
                                newPassword,
                                {
                                    newPassword = ""
                                    confirmPassword = ""
                                    recoveryRequestId = null
                                    recoveryStatus = "COMPLETED"
                                    isResetting = false

                                    Toast.makeText(
                                        context,
                                        "Password changed successfully. Please log in.",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    onBack()
                                },
                                { error ->
                                    isResetting = false

                                    Toast.makeText(
                                        context,
                                        error,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    },
                    enabled = !isResetting &&
                        newPassword.isNotEmpty() &&
                        confirmPassword.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag(
                            "forgot_password_reset_button"
                        )
                ) {
                    Text(
                        if (isResetting)
                            "Changing Password..."
                        else
                            "Change Password"
                    )
                }
            }

            "COMPLETED" -> {
                Text(
                    "Password recovery completed.",
                    color = Color(0xFF2E7D32)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Back to Login")
        }
    }
}

// -------------------------------------------------------------
// REGISTRATION PAGE
// -------------------------------------------------------------
@Composable
fun RegisterScreen(
    onRegisterSuccess: (username: String, email: String, password: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Join the RealCoin ecosystem",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_username_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_email_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_password_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (
                    username.isNotBlank() &&
                    email.isNotBlank() &&
                    password.isNotBlank()
                ) {
                    onRegisterSuccess(
                        username.trim(),
                        email.trim(),
                        password
                    )
                } else {
                    Toast.makeText(
                        context,
                        "Please fill in all fields",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("register_submit_button")
        ) {
            Text("Register")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Already have an account? Log In")
        }
    }
}

// -------------------------------------------------------------
// KYC VERIFICATION SCREEN
// -------------------------------------------------------------
@Composable
fun KYCScreen(
    kycStatus: String = "NOT_SUBMITTED",
    onKYCSubmitted: (
        fullName: String,
        idNumber: String,
        documentAttached: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var uploadedDoc by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "KYC Identity Verification",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = when (kycStatus) {
                "PENDING" ->
                    "Waiting for admin verification. You cannot enter the main app until KYC is approved."

                "REJECTED" ->
                    "Your KYC was rejected. Please review your information and submit again."

                "VERIFIED" ->
                    "Your KYC is approved."

                else ->
                    "Verify your identity to unlock higher limits"
            },
            color = when (kycStatus) {
                "PENDING" -> Color(0xFFF57F17)
                "REJECTED" -> Color(0xFFC62828)
                "VERIFIED" -> Color(0xFF2E7D32)
                else -> Color.Gray
            },
            style = MaterialTheme.typography.bodyMedium
        )

        if (kycStatus == "PENDING" || kycStatus == "VERIFIED") {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (kycStatus == "PENDING")
                    "Status: Pending admin review"
                else
                    "Status: Approved",
                fontWeight = FontWeight.Bold,
                color = if (kycStatus == "PENDING")
                    Color(0xFFF57F17)
                else
                    Color(0xFF2E7D32)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            enabled = kycStatus != "PENDING" &&
                kycStatus != "VERIFIED",
            label = { Text("Full Legal Name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kyc_fullname_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = idNumber,
            onValueChange = { idNumber = it },
            enabled = kycStatus != "PENDING" &&
                kycStatus != "VERIFIED",
            label = { Text("Government ID / Passport Number") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kyc_id_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = kycStatus != "PENDING" &&
                        kycStatus != "VERIFIED"
                ) {
                    uploadedDoc = true
                    Toast.makeText(
                        context,
                        "Document selected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .border(
                    1.dp,
                    if (uploadedDoc)
                        Color(0xFF2E7D32)
                    else
                        Color.LightGray,
                    RoundedCornerShape(10.dp)
                ),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
                .copy(alpha = 0.4f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (uploadedDoc)
                        Icons.Default.CheckCircle
                    else
                        Icons.Default.UploadFile,
                    contentDescription = "Upload",
                    tint = if (uploadedDoc)
                        Color(0xFF2E7D32)
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (uploadedDoc)
                        "ID Document Attached ✓"
                    else
                        "Tap to upload National ID / Passport",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            enabled = kycStatus != "PENDING" &&
                kycStatus != "VERIFIED",
            onClick = {
                if (
                    fullName.isNotBlank() &&
                    idNumber.isNotBlank() &&
                    uploadedDoc
                ) {
                    onKYCSubmitted(
                        fullName.trim(),
                        idNumber.trim(),
                        uploadedDoc
                    )
                } else {
                    Toast.makeText(
                        context,
                        "Please complete all fields and attach ID",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("kyc_submit_button")
        ) {
            Text("Submit for Verification")
        }
    }
}

// -------------------------------------------------------------
// HOME DASHBOARD
// -------------------------------------------------------------
@Composable
fun HomeScreen(
    userProfile: UserProfile,
    transactions: List<TransactionRecord>,
    realCoinUsdPrice: Double = REAL_COIN_USD_VALUE,
    onOpenDeposit: () -> Unit,
    onOpenWithdraw: () -> Unit
) {
    val realCoinUsd =
        userProfile.realCoinBalance * realCoinUsdPrice

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            WarningNoticeBox(
                message = "Notice: Users must use BEP-20 (BNB Smart Chain) address only for both Deposit and withdrawals. Min withdrawal is $50 USD."
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RealCoin Wallet",
                            color = MaterialTheme.colorScheme.onPrimary
                                .copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelLarge
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                                .copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "1 RC = \$${"%.4f".format(realCoinUsdPrice)} USD",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "${"%,.2f".format(userProfile.realCoinBalance)} RC",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    Text(
                        text = "≈ $${"%,.2f".format(realCoinUsd)} USD",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                            .copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onOpenDeposit,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_deposit_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.onPrimary,
                                contentColor =
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = "Deposit",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Deposit",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onOpenWithdraw,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_withdraw_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.onPrimary,
                                contentColor =
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Withdraw",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Withdraw",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            "Min Withdrawal",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            "$50.00 USD",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "~18,518 RC",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            "Network",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            "BSC BEP-20",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "BNB Smart Chain",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (transactions.isEmpty()) {
            item {
                Text(
                    text = "No transactions yet. Tap Deposit or Withdraw above to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(transactions) { tx ->
                TransactionCard(tx)
            }
        }
    }
}

@Composable
fun TransactionCard(tx: TransactionRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = when (tx.type) {
                    TransactionType.DEPOSIT ->
                        Color(0xFFE8F5E9)

                    TransactionType.WITHDRAWAL ->
                        Color(0xFFFFEBEE)

                    TransactionType.SPIN_REWARD ->
                        Color(0xFFFFF8E1)

                    else ->
                        MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (tx.type) {
                            TransactionType.DEPOSIT ->
                                Icons.Default.ArrowDownward

                            TransactionType.WITHDRAWAL ->
                                Icons.Default.ArrowUpward

                            TransactionType.SPIN_REWARD ->
                                Icons.Default.Celebration

                            else ->
                                Icons.Default.SwapHoriz
                        },
                        contentDescription = null,
                        tint = when (tx.type) {
                            TransactionType.DEPOSIT ->
                                Color(0xFF2E7D32)

                            TransactionType.WITHDRAWAL ->
                                Color(0xFFC62828)

                            TransactionType.SPIN_REWARD ->
                                Color(0xFFF57F17)

                            else ->
                                MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when (tx.type) {
                        TransactionType.DEPOSIT ->
                            "Deposit (BEP20)"

                        TransactionType.WITHDRAWAL ->
                            "Withdrawal (BEP20)"

                        TransactionType.SPIN_REWARD ->
                            "Spin Reward"

                        TransactionType.P2P_BUY ->
                            "P2P Buy"

                        TransactionType.P2P_SELL ->
                            "P2P Sell"
                    },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "${tx.network} • ${tx.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${
                        if (
                            tx.type == TransactionType.DEPOSIT ||
                            tx.type == TransactionType.SPIN_REWARD
                        ) "+" else "-"
                    }${"%,.2f".format(tx.amountRealCoin)} RC",
                    fontWeight = FontWeight.Bold,
                    color = if (
                        tx.type == TransactionType.DEPOSIT ||
                        tx.type == TransactionType.SPIN_REWARD
                    ) {
                        Color(0xFF2E7D32)
                    } else {
                        Color(0xFFC62828)
                    }
                )

                Text(
                    text = "≈ $${"%.2f".format(tx.usdValue)} USD",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// -------------------------------------------------------------
// P2P TRADING SCREEN
// -------------------------------------------------------------
@Composable
fun P2PScreen(
    p2pOrders: List<P2POrder>,
    myActiveAds: List<P2PAdEntity> = emptyList(),
    onDeleteAd: (String) -> Unit = {},
    onTradeAction: (P2POrder) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "P2P Trading Hub",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Trade RealCoin with local fiat currency (USDT / ETB)",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (myActiveAds.isNotEmpty()) {
            Text(
                "My Active Ads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            myActiveAds.forEach { ad ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "${ad.type} • ${"%,.0f".format(ad.cryptoAmount)} RC",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "${"%,.2f".format(ad.fiatPrice)} ${ad.fiatCurrency} • ${ad.paymentMethod}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )

                            Text(
                                "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        TextButton(
                            onClick = {
                                onDeleteAd(ad.id)
                            }
                        ) {
                            Text("Delete")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        TabRow(
            selectedTabIndex = selectedTab
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 }
            ) {
                Text(
                    "BUY",
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 }
            ) {
                Text(
                    "SELL",
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                p2pOrders.filter {
                    it.type == if (selectedTab == 0)
                        "SELL"
                    else
                        "BUY"
                }
            ) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                order.traderName,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                "Price: ${"%,.2f".format(order.fiatPrice)} ${order.fiatCurrency}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                "Amount: ${"%,.0f".format(order.cryptoAmount)} RC",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                "Payment: ${order.paymentMethod}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        Button(
                            onClick = {
                                onTradeAction(order)
                            }
                        ) {
                            Text(
                                if (selectedTab == 0)
                                    "Buy RC"
                                else
                                    "Sell RC"
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SPIN WHEEL SCREEN
// -------------------------------------------------------------
@Composable
fun SpinWheelScreen(
    onSpinWin: (Double) -> Unit
) {
    val context = LocalContext.current
    var isSpinning by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(durationMillis = 2500),
        label = "spin_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Daily Lucky Wheel",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Spin to win free RealCoin daily rewards!",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .size(240.dp)
                .rotate(animatedRotation)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primaryContainer
                )
                .border(
                    6.dp,
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Celebration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(50.dp)
                )

                Text(
                    "RealCoin",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    "Prize Pool",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = {
                if (!isSpinning) {
                    isSpinning = true
                    val randomAdd = 360f * 4 + 180
                    rotationAngle += randomAdd
                    val reward = 0.0

                    android.os.Handler(
                        android.os.Looper.getMainLooper()
                    ).postDelayed({
                        isSpinning = false
                        onSpinWin(reward)

                        Toast.makeText(
                            context,
                            "Congratulations! You won ${"%,.0f".format(reward)} RealCoin!",
                            Toast.LENGTH_LONG
                        ).show()
                    }, 2600)
                }
            },
            enabled = !isSpinning,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(50.dp)
                .testTag("spin_wheel_button")
        ) {
            Text(
                if (isSpinning)
                    "Spinning..."
                else
                    "Spin Wheel"
            )
        }
    }
}

// -------------------------------------------------------------
// ACTIVE P2P ORDER
// -------------------------------------------------------------
@Composable
fun ActiveP2POrderScreen(
    order: P2POrderEntity,
    onExpire: () -> Unit,
    onClose: () -> Unit
) {
    var remainingMs by remember(order.id) {
        mutableLongStateOf(
            (
                order.expiresAt -
                    System.currentTimeMillis()
            ).coerceAtLeast(0L)
        )
    }

    LaunchedEffect(order.id) {
        while (remainingMs > 0L) {
            kotlinx.coroutines.delay(1000L)

            remainingMs = (
                order.expiresAt -
                    System.currentTimeMillis()
                ).coerceAtLeast(0L)
        }

        if (order.status == "ESCROW_LOCKED") {
            onExpire()
        }
    }

    val minutes = remainingMs / 60000L
    val seconds = (remainingMs / 1000L) % 60L

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text("Active P2P Order")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Buyer: ${order.buyerName}")
                Text("Seller: ${order.sellerName}")
                Text("Bank: ${order.paymentMethod}")
                Text("Account name: ${order.paymentName}")
                Text("Account number: ${order.accountNumber}")
                Text(
                    "REAL amount: ${"%,.2f".format(order.cryptoAmount)} RC"
                )
                Text(
                    "ETB amount: ${"%,.2f".format(order.fiatPrice)} ETB"
                )
                Text(
                    "Payment deadline: %02d:%02d".format(
                        minutes,
                        seconds
                    ),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Pay the seller and confirm payment before the timer ends."
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onClose
            ) {
                Text("Close")
            }
        }
    )
}

// -------------------------------------------------------------
// HELP CENTER
// -------------------------------------------------------------
@Composable
fun HelpCenterScreen(
    onBack: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                "Help Center",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Text(
            "Choose a topic or send a message to support.",
            color = Color.Gray
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Common help",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "• Deposit and withdrawal requests are reviewed by authorized admins."
                )

                Text(
                    "• KYC approval or rejection is handled by authorized admins."
                )

                Text(
                    "• P2P disputes remain locked until review."
                )

                Text(
                    "• Never share your password or private keys."
                )
            }
        }

        OutlinedTextField(
            value = message,
            onValueChange = {
                message = it
                submitted = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Describe your problem")
            },
            minLines = 4
        )

        Button(
            onClick = {
                submitted = true
            },
            enabled = message.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Help Request")
        }

        if (submitted) {
            Text(
                "Your message is prepared. Connect this screen to the support backend before production use.",
                color = Color(0xFFF57F17)
            )
        }
    }
}

// -------------------------------------------------------------
// ADMIN CONTROL PANEL
// -------------------------------------------------------------
@Composable
fun AdminControlScreen(
    onBack: () -> Unit,
    adminUserId: String,
    repository: RealCoinRepository
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var usdPrice by remember {
        mutableStateOf("0.0027")
    }

    var usdToEtb by remember {
        mutableStateOf("187")
    }

    var saving by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        usdPrice =
            repository.getRealCoinUsdPrice().toString()

        usdToEtb =
            repository.getUsdToEtbRate().toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                "Admin Control Panel",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Text(
            "Authorized admin area",
            color = Color.Gray
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "REAL price controls",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Changing price updates USD/ETB valuation only. It never increases REAL coin units.",
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = usdPrice,
                    onValueChange = {
                        usdPrice = it
                    },
                    label = {
                        Text("1 REAL in USD")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = usdToEtb,
                    onValueChange = {
                        usdToEtb = it
                    },
                    label = {
                        Text("1 USD in ETB")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = !saving,
                    onClick = {
                        val price =
                            usdPrice.toDoubleOrNull()

                        val rate =
                            usdToEtb.toDoubleOrNull()

                        if (
                            price == null ||
                            rate == null ||
                            price <= 0.0 ||
                            rate <= 0.0
                        ) {
                            Toast.makeText(
                                context,
                                "Enter valid positive values",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            saving = true

                            scope.launch {
                                repository
                                    .updatePricing(
                                        adminUserId,
                                        price,
                                        rate
                                    )
                                    .onSuccess {
                                        Toast.makeText(
                                            context,
                                            "Pricing updated",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message
                                                ?: "Update failed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }

                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (saving)
                            "Saving..."
                        else
                            "Save pricing"
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Admin review areas",
                    style = MaterialTheme.typography.titleMedium
                )

                Text("• Deposit requests")
                Text("• Withdrawal requests")
                Text("• KYC approve / reject")
                Text("• P2P dispute orders")
                Text("• Help Center requests")
            }
        }
    }
}
