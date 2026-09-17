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
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.model.UserProfile

// -------------------------------------------------------------
// LANDING PAGE (Untouched UI / layout preserved)
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
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("get_started_button")
        ) {
            Text("Login to Wallet", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onNavigateToRegister,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("register_nav_button")
        ) {
            Text("Create New Account", fontSize = 16.sp)
        }
    }
}

// -------------------------------------------------------------
// LOGIN PAGE (Preserves visual consistency)
// -------------------------------------------------------------
@Composable
fun LoginScreen(
    onLoginSubmit: (usernameOrEmail: String, password: String) -> Unit,
    onNavigateToRegister: () -> Unit,
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
        Text("Login to Wallet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Enter your credentials to access RealCoin", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = usernameOrEmail,
            onValueChange = { usernameOrEmail = it },
            label = { Text("Username or Email") },
            modifier = Modifier.fillMaxWidth().testTag("login_username_input")
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("login_password_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (usernameOrEmail.isNotBlank() && password.isNotBlank()) {
                    onLoginSubmit(usernameOrEmail.trim(), password)
                } else {
                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_submit_button")
        ) {
            Text("Login")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onNavigateToRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Don't have an account? Register")
        }

        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back to Welcome")
        }
    }
}

// -------------------------------------------------------------
// REGISTRATION PAGE (Layout preserved)
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
        Text("Create Account", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Join the RealCoin ecosystem", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth().testTag("register_username_input")
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag("register_email_input")
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("register_password_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (username.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                    onRegisterSuccess(username.trim(), email.trim(), password)
                } else {
                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("register_submit_button")
        ) {
            Text("Register")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Already have an account? Log In")
        }
    }
}

// -------------------------------------------------------------
// KYC VERIFICATION SCREEN (Layout preserved)
// -------------------------------------------------------------
@Composable
fun KYCScreen(
    onKYCSubmitted: (fullName: String, idNumber: String, documentAttached: Boolean) -> Unit
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
        Text("KYC Identity Verification", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Verify your identity to unlock higher limits", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Legal Name") },
            modifier = Modifier.fillMaxWidth().testTag("kyc_fullname_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = idNumber,
            onValueChange = { idNumber = it },
            label = { Text("Government ID / Passport Number") },
            modifier = Modifier.fillMaxWidth().testTag("kyc_id_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uploadedDoc = true
                    Toast.makeText(context, "Document selected", Toast.LENGTH_SHORT).show()
                }
                .border(1.dp, if (uploadedDoc) Color(0xFF2E7D32) else Color.LightGray, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (uploadedDoc) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                    contentDescription = "Upload",
                    tint = if (uploadedDoc) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (uploadedDoc) "ID Document Attached ✓" else "Tap to upload National ID / Passport",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                if (fullName.isNotBlank() && idNumber.isNotBlank() && uploadedDoc) {
                    onKYCSubmitted(fullName.trim(), idNumber.trim(), uploadedDoc)
                } else {
                    Toast.makeText(context, "Please complete all fields and attach ID", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("kyc_submit_button")
        ) {
            Text("Submit for Verification")
        }
    }
}

// -------------------------------------------------------------
// HOME DASHBOARD (With RealCoin balance, ticker, BEP-20 notice)
// -------------------------------------------------------------
@Composable
fun HomeScreen(
    userProfile: UserProfile,
    transactions: List<TransactionRecord>,
    onOpenDeposit: () -> Unit,
    onOpenWithdraw: () -> Unit
) {
    val realCoinUsd = userProfile.realCoinBalance * REAL_COIN_USD_VALUE

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Warning Banner
        item {
            WarningNoticeBox(
                message = "Notice: Users must use BEP-20 (BNB Smart Chain) address only for both Deposit and withdrawals. Min withdrawal is $50 USD."
            )
        }

        // Wallet Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
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
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "1 RC = $0.0027 USD",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onOpenDeposit,
                            modifier = Modifier.weight(1f).testTag("home_deposit_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Deposit", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Deposit", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onOpenWithdraw,
                            modifier = Modifier.weight(1f).testTag("home_withdraw_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Withdraw", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Withdraw", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick Stats / Requirements
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Min Withdrawal", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("$50.00 USD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("~18,518 RC", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Network", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("BSC BEP-20", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("BNB Smart Chain", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // Transactions Header
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = when (tx.type) {
                    TransactionType.DEPOSIT -> Color(0xFFE8F5E9)
                    TransactionType.WITHDRAWAL -> Color(0xFFFFEBEE)
                    TransactionType.SPIN_REWARD -> Color(0xFFFFF8E1)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (tx.type) {
                            TransactionType.DEPOSIT -> Icons.Default.ArrowDownward
                            TransactionType.WITHDRAWAL -> Icons.Default.ArrowUpward
                            TransactionType.SPIN_REWARD -> Icons.Default.Celebration
                            else -> Icons.Default.SwapHoriz
                        },
                        contentDescription = null,
                        tint = when (tx.type) {
                            TransactionType.DEPOSIT -> Color(0xFF2E7D32)
                            TransactionType.WITHDRAWAL -> Color(0xFFC62828)
                            TransactionType.SPIN_REWARD -> Color(0xFFF57F17)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (tx.type) {
                        TransactionType.DEPOSIT -> "Deposit (BEP20)"
                        TransactionType.WITHDRAWAL -> "Withdrawal (BEP20)"
                        TransactionType.SPIN_REWARD -> "Spin Reward"
                        TransactionType.P2P_BUY -> "P2P Buy"
                        TransactionType.P2P_SELL -> "P2P Sell"
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

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (tx.type == TransactionType.DEPOSIT || tx.type == TransactionType.SPIN_REWARD) "+" else "-"}${"%,.2f".format(tx.amountRealCoin)} RC",
                    fontWeight = FontWeight.Bold,
                    color = if (tx.type == TransactionType.DEPOSIT || tx.type == TransactionType.SPIN_REWARD) Color(0xFF2E7D32) else Color(0xFFC62828)
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
// P2P TRADING SCREEN (Keeps USDT/ETB as explicitly requested)
// -------------------------------------------------------------
@Composable
fun P2PScreen(
    p2pOrders: List<P2POrder>,
    onTradeAction: (P2POrder) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Buy, 1: Sell

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("P2P Trading Hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Trade RealCoin with local fiat currency (USDT / ETB)", color = Color.Gray, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("BUY", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("SELL", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(p2pOrders.filter { it.type == (if (selectedTab == 0) "BUY" else "SELL") }) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(order.traderName, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Price: ${"%,.2f".format(order.fiatPrice)} ${order.fiatCurrency}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text("Amount: ${"%,.0f".format(order.cryptoAmount)} RC", style = MaterialTheme.typography.bodySmall)
                            Text("Payment: ${order.paymentMethod}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }

                        Button(
                            onClick = { onTradeAction(order) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTab == 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        ) {
                            Text(if (selectedTab == 0) "Buy RC" else "Sell RC")
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
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Daily Lucky Wheel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Spin to win free RealCoin daily rewards!", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .size(240.dp)
                .rotate(animatedRotation)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(6.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(50.dp))
                Text("RealCoin", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Prize Pool", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = {
                if (!isSpinning) {
                    isSpinning = true
                    val randomAdd = 360f * 4 + 180
                    rotationAngle += randomAdd
                    val reward = 0.0 // Reward is determined and validated by trusted reward logic
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isSpinning = false
                        onSpinWin(reward)
                        Toast.makeText(context, "Congratulations! You won ${"%,.0f".format(reward)} RealCoin!", Toast.LENGTH_LONG).show()
                    }, 2600)
                }
            },
            enabled = !isSpinning,
            modifier = Modifier.fillMaxWidth(0.7f).height(50.dp).testTag("spin_wheel_button")
        ) {
            Text(if (isSpinning) "Spinning..." else "Spin Wheel")
        }
    }
}
