package com.example.ui.screens

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.P2POrder
import com.example.data.db.P2PAdEntity
import com.example.data.db.DepositEntity
import com.example.data.db.WithdrawalEntity
import com.example.data.db.KycEntity
import com.example.data.db.HelpRequestEntity
import com.example.data.db.P2POrderEntity
import com.example.data.db.PaymentAccountEntity
import com.example.data.BankOptions
import com.example.data.reward.RewardStatus
import com.example.data.reward.RewardPolicy
import com.example.data.repository.RealCoinRepository
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.example.util.AttachmentStorage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// -------------------------------------------------------------
// LANDING PAGE
// -------------------------------------------------------------
@Composable
fun LandingScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val black = Color(0xFF080808)
    val gold = Color(0xFFFFC107)
    val goldDark = Color(0xFFD89E00)
    val surface = Color(0xFF141414)
    val muted = Color(0xFFB9B9B9)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = CircleShape,
                color = gold,
                shadowElevation = 18.dp,
                modifier = Modifier.size(112.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = black,
                        modifier = Modifier.size(88.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Real-Coins logo",
                                tint = gold,
                                modifier = Modifier.size(58.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "REAL-COINS",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "DIGITAL VALUE. REAL POSSIBILITIES.",
                color = gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Your secure gateway to the Real-Coins ecosystem.",
                color = muted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            Surface(
                color = surface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color(0xFF3A300F)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2A2108),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = gold,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Secure by design",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Built for your digital assets",
                                color = muted,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2A2108),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = gold,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "One Real-Coins wallet",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Access your ecosystem from one place",
                                color = muted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onNavigateToLogin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = gold,
                    contentColor = black
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("get_started_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Login,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "LOGIN TO REAL-COINS",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onNavigateToRegister,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = gold
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    goldDark
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("register_nav_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "CREATE NEW ACCOUNT",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "BEP-20 • REAL-COINS ECOSYSTEM",
                color = Color(0xFF777777),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
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
    var referralCode by remember { mutableStateOf("") }

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
        recoveryRequestId: String,
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
                                recoveryRequestId ?: return@Button,
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
    onRegisterSuccess: (
        username: String,
        email: String,
        password: String,
        referralCode: String?
    ) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // FIX: missing state for referralCode
    var referralCode by remember { mutableStateOf("") }

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

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = referralCode,
            onValueChange = {
                referralCode = it.uppercase()
            },
            label = {
                Text("Referral Code (Optional)")
            },
            supportingText = {
                Text(
                    "Enter a friend's referral code if you were invited."
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_referral_input")
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
                        password,
                        referralCode.trim().takeIf {
                            it.isNotBlank()
                        }
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
            modifier = Modifier.align(
                Alignment.CenterHorizontally
            )
        ) {
            Text("Already have an account? Log In")
        }
    }
}

// -------------------------------------------------------------
// REFERRAL PAGE
// -------------------------------------------------------------
@Composable
fun ReferralScreen(
    referralCode: String?,
    referredCount: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val code = referralCode ?: "Loading..."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                "Referral",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    "Your Referral Code",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (!referralCode.isNullOrBlank()) {
                            val clipboard =
                                context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE
                                ) as android.content.ClipboardManager

                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "Referral Code",
                                    referralCode
                                )
                            )

                            Toast.makeText(
                                context,
                                "Referral code copied",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = !referralCode.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null
                    )

                    Spacer(Modifier.width(8.dp))

                    Text("Copy Referral Code")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    "Referral Rewards",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "When a referred user makes their first qualifying confirmed deposit, the one-time referral reward is applied."
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Referrer: 200 RC • Referred user: 100 RC"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "People referred: $referredCount"
                )
            }
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
        idType: String,
        idNumber: String,
        frontIdUri: String,
        backIdUri: String
    ) -> Unit
) {
    val context = LocalContext.current

    val black = Color(0xFF080808)
    val gold = Color(0xFFFFC107)
    val surface = Color(0xFF141414)
    val muted = Color(0xFFB9B9B9)

    var fullName by remember { mutableStateOf("") }
    var idType by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var frontIdUri by remember { mutableStateOf<String?>(null) }
    var backIdUri by remember { mutableStateOf<String?>(null) }
    var idTypeMenuExpanded by remember { mutableStateOf(false) }

    val canEdit =
        kycStatus != "PENDING" &&
            kycStatus != "VERIFIED"

    val frontSelected = frontIdUri != null
    val backSelected = backIdUri != null

    fun persistReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    val frontPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                persistReadPermission(uri)
                frontIdUri = uri.toString()
                Toast.makeText(
                    context,
                    "Front of ID selected",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    val backPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                persistReadPermission(uri)
                backIdUri = uri.toString()
                Toast.makeText(
                    context,
                    "Back of ID selected",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "KYC Identity Verification",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = gold
        )

        Text(
            text = when (kycStatus) {
                "PENDING" ->
                    "Your KYC is waiting for admin verification. You cannot enter the main app until it is approved."

                "REJECTED" ->
                    "Your KYC was rejected. Review your information and submit again."

                "VERIFIED" ->
                    "Your KYC has been approved."

                else ->
                    "Complete all identity information below."
            },
            color = when (kycStatus) {
                "PENDING" -> Color(0xFFFFB300)
                "REJECTED" -> Color(0xFFEF5350)
                "VERIFIED" -> Color(0xFF66BB6A)
                else -> muted
            },
            style = MaterialTheme.typography.bodyMedium
        )

        if (kycStatus == "PENDING" || kycStatus == "VERIFIED") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = surface)
            ) {
                Text(
                    text = if (kycStatus == "PENDING")
                        "Status: Pending admin review"
                    else
                        "Status: Approved",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    color = if (kycStatus == "PENDING")
                        Color(0xFFFFB300)
                    else
                        Color(0xFF66BB6A)
                )
            }
        }

        Text(
            "Full Legal Name",
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            enabled = canEdit,
            label = { Text("Full Legal Name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kyc_fullname_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = gold,
                focusedLabelColor = gold,
                cursorColor = gold
            )
        )

        Text(
            "ID Type",
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = idType,
                onValueChange = {},
                enabled = canEdit,
                readOnly = true,
                label = { Text("Select ID Type") },
                placeholder = {
                    Text("Choose National ID, Passport, or Driving License")
                },
                trailingIcon = {
                    Icon(
                        imageVector = if (idTypeMenuExpanded)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select ID type",
                        tint = gold
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = gold,
                    focusedLabelColor = gold,
                    cursorColor = gold
                )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(enabled = canEdit) {
                        idTypeMenuExpanded = true
                    }
            )

            DropdownMenu(
                expanded = idTypeMenuExpanded,
                onDismissRequest = {
                    idTypeMenuExpanded = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "National ID",
                    "Passport",
                    "Driving License"
                ).forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            idType = type
                            idTypeMenuExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = idNumber,
            onValueChange = { idNumber = it },
            enabled = canEdit,
            label = { Text("ID Number") },
            placeholder = {
                Text(
                    when (idType) {
                        "Passport" -> "Enter passport number"
                        "Driving License" -> "Enter driving license number"
                        "National ID" -> "Enter national ID number"
                        else -> "Select ID type first"
                    }
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kyc_id_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = gold,
                focusedLabelColor = gold,
                cursorColor = gold
            )
        )

        Text(
            "Front of ID",
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canEdit) {
                    frontPicker.launch(arrayOf("image/*"))
                },
            colors = CardDefaults.cardColors(containerColor = surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (frontSelected) gold else Color.DarkGray
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (frontSelected)
                        Icons.Default.CheckCircle
                    else
                        Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = if (frontSelected) gold else muted,
                    modifier = Modifier.size(34.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (frontSelected)
                            "Front ID selected ✓"
                        else
                            "Upload front of your ID",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        if (frontSelected)
                            "Tap to replace the selected image"
                        else
                            "Take a clear photo or choose an image",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
            }
        }

        Text(
            "Back of ID",
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canEdit) {
                    backPicker.launch(arrayOf("image/*"))
                },
            colors = CardDefaults.cardColors(containerColor = surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (backSelected) gold else Color.DarkGray
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (backSelected)
                        Icons.Default.CheckCircle
                    else
                        Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = if (backSelected) gold else muted,
                    modifier = Modifier.size(34.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (backSelected)
                            "Back ID selected ✓"
                        else
                            "Upload back of your ID",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        if (backSelected)
                            "Tap to replace the selected image"
                        else
                            "Both front and back are required",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Submission checklist",
                    fontWeight = FontWeight.Bold,
                    color = gold
                )

                Text(
                    "✓ ID type selected: ${if (idType.isBlank()) "No" else "Yes"}",
                    color = if (idType.isBlank()) muted else gold
                )

                Text(
                    "✓ ID number entered: ${if (idNumber.isBlank()) "No" else "Yes"}",
                    color = if (idNumber.isBlank()) muted else gold
                )

                Text(
                    "✓ Front of ID: ${if (frontSelected) "Selected" else "Required"}",
                    color = if (frontSelected) gold else muted
                )

                Text(
                    "✓ Back of ID: ${if (backSelected) "Selected" else "Required"}",
                    color = if (backSelected) gold else muted
                )
            }
        }

        Button(
            enabled = canEdit,
            onClick = {
                val cleanName = fullName.trim()
                val cleanNumber = idNumber.trim()
                val front = frontIdUri
                val back = backIdUri

                if (
                    cleanName.isBlank() ||
                    idType.isBlank() ||
                    cleanNumber.isBlank() ||
                    front.isNullOrBlank() ||
                    back.isNullOrBlank()
                ) {
                    Toast.makeText(
                        context,
                        "Select ID type, enter ID number, and upload both front and back of your ID",
                        Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }

                onKYCSubmitted(
                    cleanName,
                    idType,
                    cleanNumber,
                    front,
                    back
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("kyc_submit_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = gold,
                contentColor = black
            )
        ) {
            Text(
                "Submit for Verification",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------
// HOME DASHBOARD
// -------------------------------------------------------------
@Composable
fun HomeScreen(
    userProfile: UserProfile,
    transactions: List<TransactionRecord>,
    repository: RealCoinRepository,
    realCoinUsdPrice: Double = REAL_COIN_USD_VALUE,
    usdToEtbRate: Double = USD_TO_ETB,
    onOpenDeposit: () -> Unit,
    onOpenWithdraw: () -> Unit,
    onOpenReferral: () -> Unit,
    onOpenP2P: () -> Unit,
    onOpenSpin: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenLevels: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val realCoinUsd =
        userProfile.realCoinBalance * realCoinUsdPrice

    val realCoinEtb =
        realCoinUsd * usdToEtbRate

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A2108)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    GOLD.copy(alpha = 0.85f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFC107),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = "RC balance",
                                    tint = Color.Black,
                                    modifier = Modifier.size(31.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Text(
                            "${"%,.2f".format(userProfile.realCoinBalance)} RC",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.AttachMoney,
                            contentDescription = "USD balance",
                            tint = GOLD,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(Modifier.width(4.dp))

                        Text(
                            "${"%,.2f".format(realCoinUsd)} USD",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.width(18.dp))

                        Text(
                            "|",
                            color = GOLD.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.width(18.dp))

                        Icon(
                            Icons.Default.Payments,
                            contentDescription = "ETB balance",
                            tint = GOLD,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(Modifier.width(4.dp))

                        Text(
                            "${"%,.2f".format(realCoinEtb)} ETB",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            var rewardStatus by remember(userProfile.id) {
                mutableStateOf<RewardStatus?>(null)
            }
            var rewardNextAt by remember(userProfile.id) {
                mutableLongStateOf(0L)
            }
            var rewardNow by remember {
                mutableLongStateOf(System.currentTimeMillis())
            }

            LaunchedEffect(userProfile.id) {
                while (true) {
                    rewardStatus = repository.getRewardStatus(userProfile.id)
                    rewardNextAt = repository.getDailyRewardState(userProfile.id).nextClaimAt
                    rewardNow = System.currentTimeMillis()
                    delay(1000L)
                }
            }

            val status = rewardStatus
            val remaining = (rewardNextAt - rewardNow).coerceAtLeast(0L)
            val totalSeconds = remaining / 1000L
            val hh = totalSeconds / 3600L
            val mm = (totalSeconds % 3600L) / 60L
            val ss = totalSeconds % 60L
            val rewardCountdown = String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss)

            if (status != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GOLD.copy(alpha = 0.65f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Level: ${status.level.name}",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${"%,.0f".format(status.level.dailyRewardReal)} RC/day",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = onOpenLevels,
                                modifier = Modifier.testTag("dashboard_levels_button")
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = "Levels",
                                    tint = Color(0xFFFFA000)
                                )
                            }
                        }

                        if (status.nextLevel != null) {
                            Text(
                                "Deposit: $${"%,.2f".format(status.totalDepositUsd)} / $${"%,.2f".format(status.nextLevel.minimumDepositUsd)}",
                                color = Color(0xFF333333)
                            )
                            LinearProgressIndicator(
                                progress = { status.progressToNext.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Next level: ${status.nextLevel.name}",
                                color = Color(0xFF333333),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "Deposit: $${"%,.2f".format(status.totalDepositUsd)} • Maximum level",
                                color = Color(0xFF333333)
                            )
                        }

                        Text(
                            when {
                                !status.rewardActive ->
                                    "Daily reward paused after 7 claimed days"
                                remaining == 0L ->
                                    "Daily reward available now"
                                else ->
                                    "Next daily reward in $rewardCountdown"
                            },
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )

                        if (!status.rewardActive) {
                            Text(
                                "Complete 1 BUY and 1 SELL with different users to restart rewards.",
                                color = Color(0xFF8A5A00),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        val canClaimDailyReward =
                            remaining == 0L &&
                                status.rewardActive

                        Button(
                            onClick = {
                                scope.launch {
                                    val result =
                                        repository.claimDailyReward(userProfile.id)

                                    result.onSuccess { amount ->
                                        rewardNextAt =
                                            System.currentTimeMillis() +
                                                RealCoinRepository.DAILY_REWARD_COOLDOWN_MS
                                        rewardNow = System.currentTimeMillis()

                                        Toast.makeText(
                                            context,
                                            "Daily reward: +${"%,.0f".format(amount)} RC",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }.onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            error.message ?: "Could not claim daily reward",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            enabled = canClaimDailyReward,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFC107),
                                contentColor = Color(0xFF080808),
                                disabledContainerColor = Color(0xFFBDBDBD),
                                disabledContentColor = Color(0xFF666666)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 2.dp,
                                disabledElevation = 0.dp
                            )
                        ) {
                            Text(
                                "Claim Daily Reward",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardTile(
                    "Deposit",
                    Icons.Default.ArrowDownward,
                    "Add funds",
                    onOpenDeposit,
                    Modifier
                        .weight(1f)
                        .testTag("home_deposit_button")
                )

                DashboardTile(
                    "Withdraw",
                    Icons.Default.ArrowUpward,
                    "Cash out",
                    onOpenWithdraw,
                    Modifier
                        .weight(1f)
                        .testTag("home_withdraw_button")
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardTile(
                    "P2P",
                    Icons.Default.SwapHoriz,
                    "Buy & Sell",
                    onOpenP2P,
                    Modifier
                        .weight(1f)
                        .testTag("dashboard_p2p_button")
                )

                DashboardTile(
                    "Spin Wheel",
                    Icons.Default.Casino,
                    "Win RC",
                    onOpenSpin,
                    Modifier
                        .weight(1f)
                        .testTag("dashboard_spin_button")
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardTile(
                    "Levels",
                    Icons.Default.EmojiEvents,
                    "Rewards & progress",
                    onOpenLevels,
                    Modifier
                        .weight(1f)
                        .testTag("dashboard_levels_tile")
                )

                DashboardTile(
                    "Referral & Earn",
                    Icons.Default.People,
                    "Invite friends",
                    onOpenReferral,
                    Modifier
                        .weight(1f)
                        .testTag("dashboard_referral_button")
                )
            }
        }

        item {
            DashboardTile(
                "Help Center",
                Icons.Default.HelpOutline,
                "Get support",
                onOpenHelp,
                Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_help_button")
            )
        }
    }
}

private val GOLD = Color(0xFFFFC107)

@Composable
private fun DashboardTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(128.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            GOLD.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black,
                modifier = Modifier.size(42.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = GOLD,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                title,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )

            Text(
                subtitle,
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodySmall
            )
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
    userId: String = "",
    repository: RealCoinRepository? = null,
    onDeleteAd: (String) -> Unit = {},
    onTradeAction: (P2POrder, Double) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var tradeOrder by remember { mutableStateOf<P2POrder?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedAccounts by (
        if (
            repository != null &&
            userId.isNotBlank()
        ) {
            repository.getSavedPaymentAccounts(userId)
        } else {
            flowOf(emptyList())
        }
    ).collectAsState(initial = emptyList())

    val userP2POrders by (
        if (
            repository != null &&
            userId.isNotBlank()
        ) {
            repository.getP2POrdersForUser(userId)
        } else {
            flowOf(emptyList())
        }
    ).collectAsState(initial = emptyList())

    val adsWithActiveOrders = remember(userP2POrders) {
        userP2POrders
            .filter {
                it.status == "ESCROW_LOCKED" ||
                    it.status == "PAID" ||
                    it.status == "DISPUTED"
            }
            .map { it.adId }
            .toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "P2P Trading Hub",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Trade RealCoin with local fiat currency (ETB)",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    showCreate = true
                },
                enabled = repository != null &&
                    userId.isNotBlank()
            ) {
                Text("Post Ad")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (myActiveAds.isNotEmpty()) {
            Text(
                "My Active Ads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            myActiveAds.forEach { ad ->
                Card(
                    Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                "${ad.type} • ${"%,.2f".format(ad.cryptoAmount)} RC",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "${"%,.2f".format(ad.fiatPrice)} ${ad.fiatCurrency} • ${ad.paymentMethod}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )

                            Text(
                                "Order limit: ${"%,.2f".format(ad.minOrderEtb)}–${"%,.2f".format(ad.maxOrderEtb)} ETB",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                "Payment: ${ad.paymentMethod} • ${ad.paymentName} • ${ad.accountNumber}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        val hasActiveOrder =
                            ad.id in adsWithActiveOrders

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            TextButton(
                                onClick = {
                                    onDeleteAd(ad.id)
                                },
                                enabled = !hasActiveOrder
                            ) {
                                Text(
                                    if (hasActiveOrder)
                                        "Order Active"
                                    else
                                        "Delete"
                                )
                            }

                            if (hasActiveOrder) {
                                Text(
                                    "Cannot delete while order is active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
            }
        }

        TabRow(
            selectedTabIndex = selectedTab
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                }
            ) {
                Text(
                    "BUY",
                    Modifier.padding(12.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                }
            ) {
                Text(
                    "SELL",
                    Modifier.padding(12.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                p2pOrders.filter {
                    it.type ==
                        if (selectedTab == 0)
                            "SELL"
                        else
                            "BUY"
                }
            ) { order ->
                Card(
                    Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                order.traderName,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Price: ${"%,.2f".format(order.fiatPrice)} ETB / RC",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                "Amount: ${"%,.2f".format(order.cryptoAmount)} RC",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                "Order limit: ${"%,.2f".format(order.minOrderEtb)}–${"%,.2f".format(order.maxOrderEtb)} ETB",
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
                                tradeOrder = order
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

    tradeOrder?.let { order ->
        TradeAmountDialog(
            order = order,
            onDismiss = {
                tradeOrder = null
            },
            onConfirm = { etb ->
                tradeOrder = null
                onTradeAction(order, etb)
            }
        )
    }

    if (
        showCreate &&
        repository != null
    ) {
        CreateP2PAdDialog(
            isBuy = selectedTab == 0,
            accounts = savedAccounts,
            onDismiss = {
                showCreate = false
            },
            onCreate = {
                    isBuy,
                    amount,
                    price,
                    min,
                    max,
                    account ->
                scope.launch {
                    val result =
                        if (isBuy) {
                            repository.createP2PBuyAdUsingPaymentAccount(
                                userId,
                                amount,
                                price,
                                account.id,
                                min,
                                max
                            )
                        } else {
                            repository.createP2PSellAdUsingPaymentAccount(
                                userId,
                                amount,
                                price,
                                account.id,
                                min,
                                max
                            )
                        }

                    result.onSuccess {
                        showCreate = false

                        Toast.makeText(
                            context,
                            "P2P advertisement created",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            it.message ?: "Could not create ad",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun TradeAmountDialog(
    order: P2POrder,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amount by remember { mutableStateOf("") }

    val min = order.minOrderEtb
    val max = order.maxOrderEtb
    val value = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Enter order amount")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Allowed: ${"%,.2f".format(min)} – ${"%,.2f".format(max)} ETB"
                )

                Text(
                    "Price: ${"%,.2f".format(order.fiatPrice)} ETB per RC",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = {
                        Text("Amount to trade (ETB)")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true
                )

                if (
                    value != null &&
                    (value < min || value > max)
                ) {
                    Text(
                        "Amount must be between ${"%,.2f".format(min)} and ${"%,.2f".format(max)} ETB",
                        color = Color(0xFFC62828)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (
                        value != null &&
                        value >= min &&
                        value <= max
                    ) {
                        onConfirm(value)
                    }
                },
                enabled = value != null &&
                    value >= min &&
                    value <= max
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CreateP2PAdDialog(
    isBuy: Boolean,
    accounts: List<PaymentAccountEntity>,
    onDismiss: () -> Unit,
    onCreate: (
        Boolean,
        Double,
        Double,
        Double,
        Double,
        PaymentAccountEntity
    ) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("1") }
    var max by remember { mutableStateOf("") }

    var selected by remember {
        mutableStateOf<PaymentAccountEntity?>(
            accounts.firstOrNull()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isBuy)
                    "Post BUY advertisement"
                else
                    "Post SELL advertisement"
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (accounts.isEmpty()) {
                    Text(
                        "Save a payment account in Settings before posting an ad.",
                        color = Color(0xFFC62828)
                    )
                }

                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = {
                        Text("Total REAL amount")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    price,
                    { price = it },
                    label = {
                        Text("Price per 1 RC (ETB)")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    min,
                    { min = it },
                    label = {
                        Text("Minimum order (ETB)")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    max,
                    { max = it },
                    label = {
                        Text("Maximum order (ETB)")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true
                )

                Text(
                    "Payment account",
                    fontWeight = FontWeight.Bold
                )

                accounts.forEach { account ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = account
                            }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected =
                                selected?.id == account.id,
                            onClick = {
                                selected = account
                            }
                        )

                        Column {
                            Text(account.paymentMethod)

                            Text(
                                account.accountNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = amount.toDoubleOrNull()
                    val p = price.toDoubleOrNull()
                    val mn = min.toDoubleOrNull()
                    val mx = max.toDoubleOrNull()
                    val acc = selected

                    if (
                        a != null &&
                        p != null &&
                        mn != null &&
                        mx != null &&
                        a > 0 &&
                        p > 0 &&
                        mn > 0 &&
                        mx >= mn &&
                        mx <= a * p &&
                        acc != null
                    ) {
                        onCreate(
                            isBuy,
                            a,
                            p,
                            mn,
                            mx,
                            acc
                        )
                    }
                },
                enabled = accounts.isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// -------------------------------------------------------------
// LEVELS SCREEN
// -------------------------------------------------------------
@Composable
fun LevelsScreen(
    onBack: () -> Unit,
    userId: String? = null,
    repository: RealCoinRepository? = null
) {
    var rewardStatus by remember(userId) {
        mutableStateOf<RewardStatus?>(null)
    }

    LaunchedEffect(userId) {
        if (userId != null && repository != null) {
            rewardStatus = repository.getRewardStatus(userId)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                "RealCoin Levels",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Text(
            "Confirmed deposits determine the qualifying level.",
            color = Color.Gray
        )

        rewardStatus?.let { status ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A2108)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    GOLD.copy(alpha = 0.85f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFC107),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = "Current level",
                                    tint = Color.Black
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Current level: ${status.level.name}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${"%,.0f".format(status.level.dailyRewardReal)} RC/day",
                                color = Color(0xFFFFC107),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        "Confirmed deposits: $${"%,.2f".format(status.totalDepositUsd)}",
                        color = Color.White
                    )

                    if (status.nextLevel != null) {
                        LinearProgressIndicator(
                            progress = { status.progressToNext.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Next level: ${status.nextLevel.name} — $${"%,.0f".format(status.nextLevel.minimumDepositUsd)}",
                            color = Color.White
                        )
                    } else {
                        Text(
                            "Maximum level reached.",
                            color = Color(0xFFFFC107),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!status.rewardActive) {
                        Text(
                            "7 daily reward days completed. Complete 1 BUY and 1 SELL with different users to restart rewards.",
                            color = Color(0xFFFFC107),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "Daily reward cycle active.",
                            color = Color(0xFF9CCC65)
                        )
                    }
                }
            }
        }

        RewardPolicy.levels.forEach { level ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8E1)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    GOLD.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFC107),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color.Black
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            level.name,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black
                        )
                        Text(
                            "Requirement: $${"%,.0f".format(level.minimumDepositUsd)} confirmed deposits",
                            color = Color(0xFF333333)
                        )
                        Text(
                            "Daily reward: ${"%,.0f".format(level.dailyRewardReal)} RC",
                            color = Color(0xFF333333),
                            fontWeight = FontWeight.Bold
                        )
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
    userId: String,
    repository: RealCoinRepository,
    onSpinWin: (Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isSpinning by remember { mutableStateOf(false) }
    var rotationAngle by remember {
        mutableFloatStateOf(0f)
    }

    var nextFreeSpinAt by remember {
        mutableLongStateOf(0L)
    }

    var rewardStatus by remember {
        mutableStateOf<RewardStatus?>(null)
    }

    var nowMs by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(userId) {
        nextFreeSpinAt =
            repository.getSpinState(userId).second

        rewardStatus =
            repository.getRewardStatus(userId)

        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val freeSpinAvailable =
        nowMs >= nextFreeSpinAt

    val dailyRewardActive =
        rewardStatus?.rewardActive != false

    val freeSpinReady =
        freeSpinAvailable &&
            dailyRewardActive

    val remainingMs =
        (nextFreeSpinAt - nowMs)
            .coerceAtLeast(0L)

    val totalSeconds =
        remainingMs / 1000L

    val hours =
        totalSeconds / 3600L

    val minutes =
        (totalSeconds % 3600L) / 60L

    val seconds =
        totalSeconds % 60L

    val countdown =
        String.format(
            Locale.US,
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )

    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(
            durationMillis = 2500
        ),
        label = "spin_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(
                rememberScrollState()
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {
        Text(
            "Daily Lucky Wheel",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color =
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    if (freeSpinReady)
                        "Free spin is ready"
                    else if (!dailyRewardActive)
                        "Daily reward is paused"
                    else
                        "Next free spin in $countdown",
                    fontWeight = FontWeight.Bold
                )

            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(260.dp)
                .rotate(animatedRotation)
                .clip(CircleShape)
                .border(
                    6.dp,
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val prizes = listOf(
                "TRY AGAIN",
                "5 RC",
                "10 RC",
                "20 RC",
                "50 RC",
                "100 RC"
            )

            val wheelColors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.secondaryContainer
            )

            val wheelOutlineColor = MaterialTheme.colorScheme.outline

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val radius =
                    size.minDimension / 2f

                prizes.forEachIndexed { index, prize ->
                    val start =
                        index * 60f - 90f

                    drawArc(
                        wheelColors[index],
                        start,
                        60f,
                        true
                    )

                    drawArc(
                        wheelOutlineColor,
                        start,
                        60f,
                        true,
                        style = Stroke(width = 2f)
                    )

                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.rotate(
                        start + 30f,
                        centerX,
                        centerY
                    )
                    val textPaint =
                        android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 24f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            color = android.graphics.Color.BLACK
                        }
                    drawContext.canvas.nativeCanvas.drawText(
                        prize,
                        centerX,
                        centerY - radius * 0.60f,
                        textPaint
                    )
                    drawContext.canvas.nativeCanvas.restore()
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.size(74.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SPIN",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = {
                if (!isSpinning) {
                    isSpinning = true

                    rotationAngle +=
                        360f * 4 +
                            (180..540)
                                .random()
                                .toFloat()

                    scope.launch {
                        delay(2600L)

                        val result =
                            repository.spinWheel(userId)

                        isSpinning = false

                        result.onSuccess { spin ->
                            nextFreeSpinAt =
                                spin.nextFreeSpinAt

                            onSpinWin(
                                spin.rewardRc
                            )

                            val mode =
                                if (spin.isFreeSpin)
                                    "Free spin"
                                else
                                    "Paid spin (10 RC)"

                            Toast.makeText(
                                context,
                                "$mode: You won ${"%,.0f".format(spin.rewardRc)} RC",
                                Toast.LENGTH_LONG
                            ).show()
                        }.onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: "Spin failed",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            enabled = !isSpinning,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(50.dp)
                .testTag("spin_wheel_button")
        ) {
            Text(
                when {
                    isSpinning ->
                        "Spinning..."

                    freeSpinReady ->
                        "FREE SPIN"

                    else ->
                        "SPIN — 10 RC"
                }
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
    currentUserId: String,
    repository: RealCoinRepository,
    onExpire: () -> Unit,
    onPaid: (String) -> Unit = {},
    onReleaseEscrow: () -> Unit = {},
    onDispute: (String) -> Unit = {},
    onClose: () -> Unit
) {
    var remainingMs by remember(
        order.id,
        order.status
    ) {
        mutableLongStateOf(
            (
                order.expiresAt -
                    System.currentTimeMillis()
            ).coerceAtLeast(0L)
        )
    }

    var showChat by remember {
        mutableStateOf(false)
    }

    var proofUri by remember(order.id) {
        mutableStateOf(order.paymentProofUri)
    }

    val context = LocalContext.current

    val proofPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                val saved =
                    AttachmentStorage.persistImage(
                        context,
                        uri,
                        "payment"
                    )

                if (saved != null) {
                    proofUri = saved
                } else {
                    Toast.makeText(
                        context,
                        "Could not save payment screenshot",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    LaunchedEffect(
        order.id,
        order.status
    ) {
        while (
            order.status == "ESCROW_LOCKED" &&
            remainingMs > 0L
        ) {
            kotlinx.coroutines.delay(1000L)

            remainingMs =
                (
                    order.expiresAt -
                        System.currentTimeMillis()
                    ).coerceAtLeast(0L)
        }

        if (
            order.status == "ESCROW_LOCKED" &&
            remainingMs <= 0L
        ) {
            onExpire()
        }
    }

    val minutes =
        remainingMs / 60000L

    val seconds =
        (remainingMs / 1000L) % 60L

    val messages by repository
        .getP2PChatMessages(order.id)
        .collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                "P2P Trade • ${order.status}"
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    "Buyer: ${order.buyerName}"
                )

                Text(
                    "Seller: ${order.sellerName}"
                )

                Text(
                    "Bank: ${order.paymentMethod}"
                )

                Text(
                    "Account name: ${order.paymentName}"
                )

                Text(
                    "Account number: ${order.accountNumber}"
                )

                Text(
                    "REAL amount: ${"%,.2f".format(order.cryptoAmount)} RC"
                )

                // File 5 will provide fiatOrderAmount on P2POrderEntity.
                Text(
                    "ETB amount: ${"%,.2f".format(order.fiatOrderAmount)} ETB"
                )

                if (
                    order.status == "ESCROW_LOCKED"
                ) {
                    Text(
                        "Payment deadline: %02d:%02d"
                            .format(minutes, seconds),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (
                    order.paymentProofUri != null
                ) {
                    Text(
                        "Payment screenshot attached ✓",
                        color = Color(0xFF2E7D32)
                    )

                    TextButton(
                        onClick = {
                            if (
                                !AttachmentStorage.open(
                                    context,
                                    order.paymentProofUri
                                )
                            ) {
                                Toast.makeText(
                                    context,
                                    "Cannot open payment screenshot",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Text(
                            "Open payment screenshot"
                        )
                    }
                }

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showChat = true
                        }
                    ) {
                        Text("Chat")
                    }

                    if (
                        currentUserId ==
                            order.buyerId &&
                        order.status ==
                            "ESCROW_LOCKED"
                    ) {
                        OutlinedButton(
                            onClick = {
                                proofPicker.launch(
                                    arrayOf("image/*")
                                )
                            }
                        ) {
                            Text(
                                if (proofUri == null)
                                    "Attach screenshot"
                                else
                                    "Change screenshot"
                            )
                        }
                    }
                }

                if (
                    currentUserId ==
                        order.buyerId &&
                    order.status ==
                        "ESCROW_LOCKED"
                ) {
                    Button(
                        onClick = {
                            proofUri?.let(onPaid)
                        },
                        enabled =
                            !proofUri.isNullOrBlank(),
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "I Paid — Confirm Payment"
                        )
                    }
                }

                if (
                    currentUserId ==
                        order.sellerId &&
                    order.status == "PAID"
                ) {
                    Button(
                        onClick = onReleaseEscrow,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Release Escrow"
                        )
                    }
                }

                if (
                    order.status ==
                        "ESCROW_LOCKED" ||
                    order.status == "PAID"
                ) {
                    OutlinedButton(
                        onClick = {
                            onDispute(
                                "P2P payment dispute"
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text("Open Dispute")
                    }
                }
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

    if (showChat) {
        P2PChatDialog(
            orderId = order.id,
            currentUserId = currentUserId,
            repository = repository,
            messages = messages,
            onClose = {
                showChat = false
            }
        )
    }
}

// -------------------------------------------------------------
// P2P CHAT
// -------------------------------------------------------------
@Composable
private fun P2PChatDialog(
    orderId: String,
    currentUserId: String,
    repository: RealCoinRepository,
    messages: List<com.example.data.db.P2PChatMessageEntity>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var message by remember {
        mutableStateOf("")
    }

    var attachment by remember {
        mutableStateOf<String?>(null)
    }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                val saved =
                    AttachmentStorage.persistImage(
                        context,
                        uri,
                        "chat"
                    )

                if (saved != null) {
                    attachment = saved
                } else {
                    Toast.makeText(
                        context,
                        "Could not save image attachment",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text("Trade Chat")
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                LazyColumn(
                    Modifier.heightIn(
                        max = 260.dp
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    items(messages) { item ->
                        Card(
                            Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(8.dp)
                            ) {
                                Text(
                                    item.senderName,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                if (
                                    item.message.isNotBlank()
                                ) {
                                    Text(
                                        item.message
                                    )
                                }

                                if (
                                    item.attachmentUri != null
                                ) {
                                    TextButton(
                                        onClick = {
                                            if (
                                                !AttachmentStorage.open(
                                                    context,
                                                    item.attachmentUri
                                                )
                                            ) {
                                                Toast.makeText(
                                                    context,
                                                    "Cannot open attachment",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    ) {
                                        Text(
                                            "Open attachment"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    message,
                    { message = it },
                    label = {
                        Text("Message")
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                arrayOf("image/*")
                            )
                        }
                    ) {
                        Text(
                            if (attachment == null)
                                "Attach"
                            else
                                "Attached ✓"
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                repository
                                    .sendP2PChatMessage(
                                        currentUserId,
                                        orderId,
                                        message,
                                        attachment
                                    )
                                    .onSuccess {
                                        message = ""
                                        attachment = null
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message
                                                ?: "Could not send",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                        },
                        enabled =
                            message.trim().isNotEmpty() ||
                                attachment != null
                    ) {
                        Text("Send")
                    }
                }
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
    onBack: () -> Unit,
    userId: String,
    repository: RealCoinRepository
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val requests by repository
        .getHelpRequestsForUser(userId)
        .collectAsState(initial = emptyList())

    val helpCategories = listOf(
        "P2P DISPUTE",
        "ACCOUNT LOGIN ISSUE",
        "WITHDRAW",
        "DEPOSIT",
        "OTHER"
    )

    var category by remember {
        mutableStateOf(helpCategories.first())
    }

    var categoryMenuExpanded by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    var submitted by remember {
        mutableStateOf(false)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    "Back"
                )
            }

            Text(
                "Help Center",
                style =
                    MaterialTheme.typography.headlineMedium
            )
        }

        Text(
            "Support requests are saved on this device for admin review.",
            color = Color.Gray
        )

        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = if (categoryMenuExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (categoryMenuExpanded) "Close category list" else "Open category list"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { categoryMenuExpanded = !categoryMenuExpanded }
            )

            DropdownMenu(
                expanded = categoryMenuExpanded,
                onDismissRequest = { categoryMenuExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                helpCategories.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            category = option
                            categoryMenuExpanded = false
                            submitted = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            message,
            {
                message = it
                submitted = false
            },
            label = {
                Text("Describe your problem")
            },
            minLines = 4,
            modifier =
                Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                scope.launch {
                    repository
                        .submitHelpRequest(
                            userId,
                            category,
                            message
                        )
                        .onSuccess {
                            message = ""
                            submitted = true

                            Toast.makeText(
                                context,
                                "Help request submitted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .onFailure {
                            Toast.makeText(
                                context,
                                it.message
                                    ?: "Could not submit request",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            },
            enabled =
                message.trim().length >= 5,
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                "Submit Help Request"
            )
        }

        if (submitted) {
            Text(
                "Submitted successfully. An admin can review it.",
                color = Color(0xFF2E7D32)
            )
        }

        if (requests.isNotEmpty()) {
            Text(
                "My requests",
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            requests.forEach { r ->
                Card(
                    Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(12.dp)
                    ) {
                        Text(
                            r.category,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(r.message)

                        Text(
                            "Status: ${r.status}",
                            color =
                                if (
                                    r.status ==
                                        "RESOLVED"
                                ) {
                                    Color(0xFF2E7D32)
                                } else {
                                    Color(0xFFF57F17)
                                }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
@Composable
fun PaymentAccountsSettings(
    userId: String,
    repository: RealCoinRepository
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accounts by repository.getSavedPaymentAccounts(userId).collectAsState(initial = emptyList())
    val kyc by repository.getKycForUser(userId).collectAsState(initial = null)
    val accountName = kyc?.fullName?.trim().orEmpty()

    var selectedBank by remember { mutableStateOf("") }
    var bankMenuExpanded by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Payment Accounts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                "You can save up to 5 bank accounts.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )

            Box(
                Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedBank,
                    onValueChange = {},
                    readOnly = true,
                    enabled = true,
                    label = { Text("Bank Name") },
                    placeholder = { Text("Select Ethiopian bank") },
                    trailingIcon = {
                        Icon(
                            imageVector = if (bankMenuExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (bankMenuExpanded) "Close bank list" else "Open bank list"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { bankMenuExpanded = true },
                    singleLine = true
                )

                DropdownMenu(
                    expanded = bankMenuExpanded,
                    onDismissRequest = { bankMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BankOptions.all.forEach { bank ->
                        DropdownMenuItem(
                            text = { Text(bank) },
                            onClick = {
                                selectedBank = bank
                                bankMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = accountName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Account Name") },
                placeholder = { Text("Verified KYC name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Account number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        repository.savePaymentAccount(userId, accountName, selectedBank, number)
                            .onSuccess {
                                selectedBank = ""
                                number = ""
                                Toast.makeText(context, "Payment account saved", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(context, it.message ?: "Could not save account", Toast.LENGTH_LONG).show()
                            }
                    }
                },
                enabled = selectedBank.isNotBlank() && accountName.isNotBlank() && number.isNotBlank() && accounts.size < 5,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (accounts.size >= 5) "Maximum 5 Accounts" else "Save Payment Account")
            }

            accounts.forEach { account ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(account.paymentMethod, fontWeight = FontWeight.Bold)
                        Text(
                            "${account.paymentName} • ${account.accountNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                repository.deleteSavedPaymentAccount(userId, account.id)
                                    .onSuccess {
                                        Toast.makeText(context, "Payment account deleted", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure {
                                        Toast.makeText(context, it.message ?: "Could not delete account", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    ) { Text("Delete") }
                }
            }
        }
    }
}
