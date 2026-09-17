package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.TransactionRecord
import com.example.model.UserProfile
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RealCoinViewModel

enum class AppDestination {
    LANDING,
    LOGIN,
    REGISTER,
    KYC,
    HOME,
    P2P,
    SPIN,
    PROFILE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                RealCoinApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealCoinApp(
    viewModel: RealCoinViewModel = viewModel()
) {
    val context = LocalContext.current

    val currentUser by viewModel.currentUser.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val p2pOrders by viewModel.p2pOrders.collectAsState()

    // App Navigation State - starts at LANDING when not authenticated
    var currentDestination by remember {
        mutableStateOf(if (currentUser != null) AppDestination.HOME else AppDestination.LANDING)
    }

    // React to user logout/login transitions
    LaunchedEffect(currentUser) {
        if (currentUser == null && currentDestination != AppDestination.LOGIN && currentDestination != AppDestination.REGISTER) {
            currentDestination = AppDestination.LANDING
        }
    }

    // Dialog Visibility State
    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    // Dialogs
    if (showDepositDialog) {
        DepositDialog(
            onDismiss = { showDepositDialog = false },
            onSubmitDeposit = { txHash, amount ->
                viewModel.submitDeposit(
                    txHash = txHash,
                    amount = amount,
                    onSuccess = {
                        Toast.makeText(context, "Deposit request submitted and is pending blockchain verification.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    if (showWithdrawDialog) {
        WithdrawDialog(
            userBalanceRealCoin = (userProfile.realCoinBalance - userProfile.realLockedBalance).coerceAtLeast(0.0),
            onDismiss = { showWithdrawDialog = false },
            onSubmitWithdrawal = { bep20Address, amount ->
                val usdVal = amount * REAL_COIN_USD_VALUE
                viewModel.submitWithdrawal(
                    bep20Address = bep20Address,
                    amount = amount,
                    onSuccess = {
                        Toast.makeText(
                            context,
                            "Withdrawal of $${"%.2f".format(usdVal)} USD submitted. Funds locked pending review.",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onError = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // Screen Layout
    if (currentDestination == AppDestination.LANDING) {
        LandingScreen(
            onNavigateToLogin = { currentDestination = AppDestination.LOGIN },
            onNavigateToRegister = { currentDestination = AppDestination.REGISTER }
        )
    } else if (currentDestination == AppDestination.LOGIN) {
        LoginScreen(
            onLoginSubmit = { usernameOrEmail, password ->
                viewModel.login(
                    usernameOrEmail = usernameOrEmail,
                    pass = password,
                    onSuccess = {
                        currentDestination = AppDestination.HOME
                        Toast.makeText(context, "Welcome back, ${it.username}!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onNavigateToRegister = { currentDestination = AppDestination.REGISTER },
            onBack = { currentDestination = AppDestination.LANDING }
        )
    } else if (currentDestination == AppDestination.REGISTER) {
        RegisterScreen(
            onRegisterSuccess = { username, email, password ->
                viewModel.register(
                    username = username,
                    email = email,
                    pass = password,
                    onSuccess = {
                        currentDestination = AppDestination.KYC
                        Toast.makeText(context, "Account created! Please submit KYC verification.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onBack = { currentDestination = AppDestination.LANDING }
        )
    } else if (currentDestination == AppDestination.KYC) {
        KYCScreen(
            onKYCSubmitted = { fullName, idNumber, docAttached ->
                viewModel.submitKyc(
                    fullName = fullName,
                    idNumber = idNumber,
                    documentAttached = docAttached,
                    onSuccess = {
                        Toast.makeText(context, "KYC Submitted. Status: Pending Verification by Compliance.", Toast.LENGTH_SHORT).show()
                        currentDestination = AppDestination.HOME
                    },
                    onError = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RealCoin Wallet", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        IconButton(onClick = { currentDestination = AppDestination.KYC }) {
                            val tint = when (userProfile.kycStatus) {
                                "VERIFIED" -> Color(0xFF2E7D32)
                                "PENDING" -> Color(0xFFF57F17)
                                else -> Color.Gray
                            }
                            Icon(Icons.Default.VerifiedUser, contentDescription = "KYC Status", tint = tint)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.HOME,
                        onClick = { currentDestination = AppDestination.HOME },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Home") },
                        label = { Text("Wallet") },
                        modifier = Modifier.testTag("nav_home")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.P2P,
                        onClick = { currentDestination = AppDestination.P2P },
                        icon = { Icon(Icons.Default.SwapHoriz, contentDescription = "P2P") },
                        label = { Text("P2P") },
                        modifier = Modifier.testTag("nav_p2p")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.SPIN,
                        onClick = { currentDestination = AppDestination.SPIN },
                        icon = { Icon(Icons.Default.Casino, contentDescription = "Spin") },
                        label = { Text("Rewards") },
                        modifier = Modifier.testTag("nav_spin")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.PROFILE,
                        onClick = { currentDestination = AppDestination.PROFILE },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        modifier = Modifier.testTag("nav_profile")
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (currentDestination) {
                    AppDestination.HOME -> {
                        HomeScreen(
                            userProfile = userProfile,
                            transactions = transactions,
                            onOpenDeposit = { showDepositDialog = true },
                            onOpenWithdraw = { showWithdrawDialog = true }
                        )
                    }
                    AppDestination.P2P -> {
                        P2PScreen(
                            p2pOrders = p2pOrders,
                            onTradeAction = { order ->
                                viewModel.tradeP2P(
                                    order = order,
                                    onSuccess = { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    },
                                    onError = { errorMsg ->
                                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        )
                    }
                    AppDestination.SPIN -> {
                        SpinWheelScreen(
                            onSpinWin = { reward ->
                                viewModel.claimDailyReward(
                                    rewardAmount = reward,
                                    onSuccess = { claimedAmount ->
                                        // Toast is handled in screen or callback
                                    },
                                    onError = { errorMsg ->
                                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        )
                    }
                    AppDestination.PROFILE -> {
                        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                            Text("Account Profile", style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Username: ${userProfile.username.ifBlank { "Guest" }}", style = MaterialTheme.typography.titleMedium)
                                    Text("Email: ${userProfile.email.ifBlank { "Not registered" }}", color = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("KYC Status: ", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            userProfile.kycStatus,
                                            color = when (userProfile.kycStatus) {
                                                "VERIFIED" -> Color(0xFF2E7D32)
                                                "PENDING" -> Color(0xFFF57F17)
                                                else -> Color.Gray
                                            },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (userProfile.realLockedBalance > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Locked in Escrow/Withdrawal: ${"%,.2f".format(userProfile.realLockedBalance)} RC",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFC62828)
                                        )
                                    }
                                }
                            }

                            if (userProfile.kycStatus == "NOT_SUBMITTED" || userProfile.kycStatus == "REJECTED") {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { currentDestination = AppDestination.KYC },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Submit KYC Verification")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.logout()
                                    currentDestination = AppDestination.LANDING
                                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("logout_button")
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Log Out")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Log Out")
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            WarningNoticeBox(
                                message = "Important Security Notice: For all transactions, ensure you only send and receive via BEP-20 (BNB Smart Chain). Minimum withdrawal limit is $50.00 USD at $0.0027 per RealCoin."
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
