package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.db.P2POrderEntity
import com.example.data.repository.syncKycFromBackend
import com.example.model.TransactionType
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RealCoinViewModel

private const val APP_STATE_PREFS = "real_coins_app_state"
private const val KYC_PENDING = "kyc_pending"

enum class AppDestination {
    LANDING,
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD,
    KYC,
    HOME,
    LEVELS,
    P2P,
    SPIN,
    PROFILE,
    REFERRAL,
    HELP_CENTER,
    ADMIN
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

private fun isVerified(status: String): Boolean = status == "VERIFIED"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealCoinApp(viewModel: RealCoinViewModel = viewModel()) {
    val context = LocalContext.current

    val currentUser by viewModel.currentUser.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val currentKyc by viewModel.currentKyc.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val p2pOrders by viewModel.p2pOrders.collectAsState()
    val myActiveP2PAds by viewModel.myActiveP2PAds.collectAsState()

    val realCoinUsdPrice by viewModel.repository
        .observeRealCoinUsdPrice()
        .collectAsState(initial = REAL_COIN_USD_VALUE)

    val usdToEtbRate by viewModel.repository
        .observeUsdToEtbRate()
        .collectAsState(initial = USD_TO_ETB)

    val preferences = remember {
        context.getSharedPreferences(
            APP_STATE_PREFS,
            android.content.Context.MODE_PRIVATE
        )
    }

    /*
     * This is intentionally NOT a "force landing" flag.
     * Once KYC is submitted, the app must remain on the KYC waiting page.
     * The flag is also used to restore that waiting page after an app restart.
     */
    val kycPendingOnRestart = preferences.getBoolean(KYC_PENDING, false)

    var loginKycCheckInProgress by remember { mutableStateOf(false) }

    var currentDestination by remember {
        mutableStateOf(
            when {
                kycPendingOnRestart -> AppDestination.KYC
                currentUser != null -> AppDestination.KYC
                else -> AppDestination.LANDING
            }
        )
    }

    val effectiveKycStatus = currentKyc?.status ?: userProfile.kycStatus

    val canEnterMainApp =
        currentUser?.role == "ADMIN" || isVerified(effectiveKycStatus)

    LaunchedEffect(currentUser?.id) {
        val user = currentUser
        if (user != null && user.role.uppercase() != "ADMIN") {
            viewModel.repository.syncKycFromBackend(user.id)
        }
    }

    /*
     * Navigation rules:
     * - Logged-out users start at Landing.
     * - Normal users cannot enter the main app until VERIFIED.
     * - PENDING/REJECTED/NOT_SUBMITTED users stay at KYC.
     * - Admins may enter the main app without KYC.
     * - VERIFIED users move from KYC to Home.
     */
    LaunchedEffect(
        currentUser,
        userProfile.kycStatus,
        currentKyc?.status,
        currentDestination,
        kycPendingOnRestart
    ) {
        when {
            currentUser == null -> {
                if (
                    !kycPendingOnRestart &&
                    currentDestination != AppDestination.LOGIN &&
                    currentDestination != AppDestination.REGISTER &&
                    currentDestination != AppDestination.FORGOT_PASSWORD
                ) {
                    currentDestination = AppDestination.LANDING
                }
            }

            currentUser?.role == "ADMIN" -> {
                if (
                    currentDestination == AppDestination.LANDING ||
                    currentDestination == AppDestination.KYC
                ) {
                    currentDestination = AppDestination.HOME
                }
            }

            effectiveKycStatus == "VERIFIED" -> {
                preferences.edit().remove(KYC_PENDING).apply()
                if (currentDestination == AppDestination.KYC) {
                    currentDestination = AppDestination.HOME
                }
            }

            !canEnterMainApp -> {
                if (
                    currentDestination in setOf(
                        AppDestination.HOME,
                        AppDestination.LEVELS,
                        AppDestination.P2P,
                        AppDestination.SPIN,
                        AppDestination.PROFILE,
                        AppDestination.REFERRAL,
                        AppDestination.HELP_CENTER,
                        AppDestination.ADMIN
                    )
                ) {
                    currentDestination = AppDestination.KYC
                }
            }
        }
    }

    /*
     * LOGIN SECURITY GATE
     *
     * A normal user who has valid credentials is NOT sent to KYC.
     * We wait for that user's KYC record, then allow the login only
     * when the backend/local verification state is VERIFIED.
     *
     * PENDING, REJECTED and NOT_SUBMITTED users are logged back out
     * and remain on the Login screen.
     */
    LaunchedEffect(
        currentUser,
        loginKycCheckInProgress
    ) {
        if (!loginKycCheckInProgress || currentUser == null) return@LaunchedEffect

        val user = currentUser ?: return@LaunchedEffect

        if (user.role == "ADMIN") {
            loginKycCheckInProgress = false
            currentDestination = AppDestination.HOME
            return@LaunchedEffect
        }

        /*
         * RealCoinViewModel.login() has already performed the authoritative
         * backend authentication and KYC check before exposing currentUser.
         *
         * Do not read currentKyc here: its Room Flow can briefly be null while
         * the newly logged-in user's Flow is being attached. Reading that
         * transient null used to log an already-verified user back out.
         *
         * Read the persisted Room record directly instead.
         */
        val kycStatus =
            viewModel.repository
                .getKycForUserSync(user.id)
                ?.status
                ?.trim()
                ?.uppercase()
                ?: "NOT_SUBMITTED"

        if (kycStatus == "VERIFIED") {
            loginKycCheckInProgress = false
            preferences.edit().remove(KYC_PENDING).apply()
            currentDestination = AppDestination.HOME

            Toast.makeText(
                context,
                "Welcome back, ${user.username}!",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            loginKycCheckInProgress = false
            viewModel.logout()
            currentDestination = AppDestination.LOGIN

            Toast.makeText(
                context,
                "Login blocked. Your KYC must be VERIFIED before you can enter the app.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var activeP2POrder by remember { mutableStateOf<P2POrderEntity?>(null) }

    if (showDepositDialog) {
        DepositDialog(
            realCoinUsdPrice = realCoinUsdPrice,
            usdToEtbRate = usdToEtbRate,
            onDismiss = { showDepositDialog = false },
            onSubmitDeposit = { txHash, amount ->
                viewModel.submitDeposit(
                    txHash = txHash,
                    amount = amount,
                    onSuccess = {
                        showDepositDialog = false
                        Toast.makeText(
                            context,
                            "Deposit request submitted and is pending blockchain verification.",
                            Toast.LENGTH_SHORT
                        ).show()
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
            userBalanceRealCoin = (
                userProfile.realCoinBalance - userProfile.realLockedBalance
            ).coerceAtLeast(0.0),
            realCoinUsdPrice = realCoinUsdPrice,
            usdToEtbRate = usdToEtbRate,
            onDismiss = { showWithdrawDialog = false },
            onSubmitWithdrawal = { bep20Address, amount ->
                val usdVal = amount * realCoinUsdPrice
                viewModel.submitWithdrawal(
                    bep20Address = bep20Address,
                    amount = amount,
                    onSuccess = {
                        showWithdrawDialog = false
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

    when (currentDestination) {
        AppDestination.LANDING -> {
            LandingScreen(
                onNavigateToLogin = {
                    currentDestination = AppDestination.LOGIN
                },
                onNavigateToRegister = {
                    currentDestination = AppDestination.REGISTER
                }
            )
        }

        AppDestination.LOGIN -> {
            LoginScreen(
                onLoginSubmit = { usernameOrEmail, password ->
                    loginKycCheckInProgress = true
                    currentDestination = AppDestination.LOGIN

                    viewModel.login(
                        usernameOrEmail = usernameOrEmail,
                        pass = password,
                        onSuccess = {
                            /*
                             * Do NOT navigate to KYC here.
                             * The login KYC security gate above checks the
                             * user's KYC status first. Only VERIFIED users
                             * continue to HOME.
                             */
                        },
                        onError = { errorMsg ->
                            loginKycCheckInProgress = false
                            currentDestination = AppDestination.LOGIN
                            Toast.makeText(
                                context,
                                errorMsg,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                },
                onNavigateToRegister = {
                    currentDestination = AppDestination.REGISTER
                },
                onNavigateToForgotPassword = {
                    currentDestination = AppDestination.FORGOT_PASSWORD
                },
                onBack = {
                    currentDestination = AppDestination.LANDING
                }
            )
        }

        AppDestination.FORGOT_PASSWORD -> {
            ForgotPasswordEntryScreen(
                onRequestRecovery = { email, onSuccess, onError ->
                    viewModel.requestRecovery(
                        email,
                        onSuccess,
                        onError
                    )
                },
                onCheckRecoveryStatus = { recoveryRequestId, onSuccess, onError ->
                    viewModel.checkRecoveryStatus(
                        recoveryRequestId,
                        onSuccess,
                        onError
                    )
                },
                onResetPassword = {
                    recoveryRequestId,
                    email,
                    newPassword,
                    onSuccess,
                    onError ->
                    viewModel.resetPassword(
                        recoveryRequestId,
                        email,
                        newPassword,
                        onSuccess,
                        onError
                    )
                },
                onBack = {
                    currentDestination = AppDestination.LOGIN
                }
            )
        }

        AppDestination.REGISTER -> {
            RegisterScreen(
                onRegisterSuccess = {
                    username,
                    email,
                    password,
                    referralCode ->
                    viewModel.register(
                        username = username,
                        email = email,
                        pass = password,
                        referralCode = referralCode,
                        onSuccess = {
                            currentDestination = AppDestination.KYC
                            Toast.makeText(
                                context,
                                "Account created! Please submit KYC verification.",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onError = { errorMsg ->
                            Toast.makeText(
                                context,
                                errorMsg,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                },
                onBack = {
                    currentDestination = AppDestination.LANDING
                }
            )
        }

        AppDestination.KYC -> {
            /*
             * If the app was reopened after KYC submission and the local
             * session has not yet been restored, explicitly show the waiting
             * state instead of showing the submission form or Landing page.
             */
            val displayedKycStatus = when {
                effectiveKycStatus == "VERIFIED" -> "VERIFIED"
                effectiveKycStatus == "PENDING" -> "PENDING"
                kycPendingOnRestart -> "PENDING"
                else -> effectiveKycStatus
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                KYCScreen(
                    kycStatus = displayedKycStatus,
                    onKYCSubmitted = {
                        fullName,
                        idType,
                        idNumber,
                        frontIdUri,
                        backIdUri ->

                        viewModel.submitKyc(
                            fullName = fullName,
                            idType = idType,
                            idNumber = idNumber,
                            frontIdUri = frontIdUri,
                            backIdUri = backIdUri,
                            onSuccess = {
                                /*
                                 * NEVER navigate to Landing or Home here.
                                 * Stay on KYC and show its PENDING waiting state.
                                 */
                                preferences.edit()
                                    .putBoolean(KYC_PENDING, true)
                                    .apply()

                                currentDestination = AppDestination.KYC

                                Toast.makeText(
                                    context,
                                    "KYC submitted. Please wait for admin verification.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onError = { errorMsg ->
                                Toast.makeText(
                                    context,
                                    errorMsg,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                )

                // DEBUG BUILD ONLY: lets a tester leave the locked KYC waiting
                // state and authenticate as the existing ADMIN account. This
                // control is not shown in release builds and does not change
                // the production KYC gate.
                if (BuildConfig.DEBUG && displayedKycStatus == "PENDING") {
                    OutlinedButton(
                        onClick = {
                            viewModel.logout()
                            preferences.edit()
                                .remove(KYC_PENDING)
                                .apply()

                            currentDestination = AppDestination.LOGIN

                            Toast.makeText(
                                context,
                                "DEBUG: KYC test switch — please log in as ADMIN.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Text("DEBUG: Admin Test Login")
                    }
                }
            }
        }

        else -> {
            MainAppContent(
                currentDestination = currentDestination,
                onDestinationChange = { destination ->
                    if (canEnterMainApp) {
                        currentDestination = destination
                    }
                },
                userProfile = userProfile,
                currentUserRole = currentUser?.role,
                currentKycStatus = effectiveKycStatus,
                currentUserReferralCode = currentUser?.referralCode,
                transactions = transactions,
                p2pOrders = p2pOrders,
                myActiveP2PAds = myActiveP2PAds,
                realCoinUsdPrice = realCoinUsdPrice,
                usdToEtbRate = usdToEtbRate,
                viewModel = viewModel,
                activeP2POrder = activeP2POrder,
                onActiveP2POrderChange = {
                    activeP2POrder = it
                },
                showNotifications = showNotifications,
                onShowNotificationsChange = {
                    showNotifications = it
                },
                showDepositDialog = {
                    showDepositDialog = true
                },
                showWithdrawDialog = {
                    showWithdrawDialog = true
                },
                context = context
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppContent(
    currentDestination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    userProfile: com.example.model.UserProfile,
    currentUserRole: String?,
    currentKycStatus: String,
    currentUserReferralCode: String?,
    transactions: List<com.example.model.TransactionRecord>,
    p2pOrders: List<com.example.model.P2POrder>,
    myActiveP2PAds: List<com.example.data.db.P2PAdEntity>,
    realCoinUsdPrice: Double,
    usdToEtbRate: Double,
    viewModel: RealCoinViewModel,
    activeP2POrder: P2POrderEntity?,
    onActiveP2POrderChange: (P2POrderEntity?) -> Unit,
    showNotifications: Boolean,
    onShowNotificationsChange: (Boolean) -> Unit,
    showDepositDialog: () -> Unit,
    showWithdrawDialog: () -> Unit,
    context: android.content.Context
) {
    val canEnterMainApp =
        currentUserRole == "ADMIN" || currentKycStatus == "VERIFIED"

    if (!canEnterMainApp) return

    if (activeP2POrder != null) {
        ActiveP2POrderScreen(
            order = activeP2POrder,
            currentUserId = userProfile.id,
            repository = viewModel.repository,
            onExpire = {
                viewModel.expireP2POrder(activeP2POrder.id) {
                    onActiveP2POrderChange(null)
                }
            },
            onPaid = { proofUri ->
                viewModel.markP2PPaymentPaid(
                    activeP2POrder.id,
                    proofUri,
                    onSuccess = {
                        Toast.makeText(
                            context,
                            "Payment marked as paid",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = {
                        Toast.makeText(
                            context,
                            it,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            },
            onReleaseEscrow = {
                viewModel.releaseP2PEscrow(
                    activeP2POrder.id,
                    onSuccess = {
                        onActiveP2POrderChange(null)
                        Toast.makeText(
                            context,
                            "Escrow released",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = {
                        Toast.makeText(
                            context,
                            it,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            },
            onDispute = { reason ->
                viewModel.openP2PDispute(
                    activeP2POrder.id,
                    reason,
                    onSuccess = {
                        Toast.makeText(
                            context,
                            "Dispute submitted for admin review",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = {
                        Toast.makeText(
                            context,
                            it,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            },
            onClose = {
                onActiveP2POrderChange(null)
            }
        )
        return
    }

    if (showNotifications) {
        AlertDialog(
            onDismissRequest = {
                onShowNotificationsChange(false)
            },
            title = {
                Text("Notifications")
            },
            text = {
                if (transactions.isEmpty()) {
                    Text("No notifications yet.")
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        transactions.take(12).forEach { tx ->
                            val message = when (tx.type) {
                                TransactionType.SPIN_REWARD ->
                                    if (tx.amountRealCoin > 0) {
                                        "You won ${"%,.2f".format(tx.amountRealCoin)} RC in Spin Wheel."
                                    } else {
                                        "Spin Wheel result recorded."
                                    }

                                TransactionType.DAILY_REWARD ->
                                    "Daily reward credited: +${"%,.2f".format(tx.amountRealCoin)} RC."

                                TransactionType.DEPOSIT ->
                                    "You successfully deposited ${"%,.2f".format(tx.amountRealCoin)} RC."

                                TransactionType.WITHDRAWAL ->
                                    "Withdrawal request: ${"%,.2f".format(tx.amountRealCoin)} RC."

                                TransactionType.P2P_BUY ->
                                    "P2P buy completed: ${"%,.2f".format(tx.amountRealCoin)} RC."

                                TransactionType.P2P_SELL ->
                                    "P2P sell completed: ${"%,.2f".format(tx.amountRealCoin)} RC."
                            }

                            Text(message)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onShowNotificationsChange(false)
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF141414),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color(0xFFFFC107)
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MonetizationOn,
                            contentDescription = "RealCoin",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(28.dp)
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            "Real-Coins",
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onShowNotificationsChange(true)
                        }
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color(0xFFFFC107)
                        )
                    }

                    IconButton(
                        onClick = {
                            onDestinationChange(AppDestination.PROFILE)
                        }
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination == AppDestination.HOME,
                    onClick = {
                        onDestinationChange(AppDestination.HOME)
                    },
                    icon = {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Dashboard"
                        )
                    },
                    label = {
                        Text("Dashboard")
                    },
                    modifier = Modifier.testTag("nav_home")
                )

                NavigationBarItem(
                    selected = currentDestination == AppDestination.P2P,
                    onClick = {
                        onDestinationChange(AppDestination.P2P)
                    },
                    icon = {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "P2P"
                        )
                    },
                    label = {
                        Text("P2P")
                    },
                    modifier = Modifier.testTag("nav_p2p")
                )

                NavigationBarItem(
                    selected = currentDestination == AppDestination.SPIN,
                    onClick = {
                        onDestinationChange(AppDestination.SPIN)
                    },
                    icon = {
                        Icon(
                            Icons.Default.Casino,
                            contentDescription = "Spin Wheel"
                        )
                    },
                    label = {
                        Text("Spin Wheel")
                    },
                    modifier = Modifier.testTag("nav_spin")
                )

                NavigationBarItem(
                    selected = currentDestination == AppDestination.PROFILE,
                    onClick = {
                        onDestinationChange(AppDestination.PROFILE)
                    },
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = {
                        Text("Settings")
                    },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                AppDestination.HOME -> {
                    HomeScreen(
                        userProfile = userProfile,
                        transactions = transactions,
                        repository = viewModel.repository,
                        realCoinUsdPrice = realCoinUsdPrice,
                        onOpenDeposit = showDepositDialog,
                        onOpenWithdraw = showWithdrawDialog,
                        onOpenReferral = {
                            onDestinationChange(AppDestination.REFERRAL)
                        },
                        onOpenP2P = {
                            onDestinationChange(AppDestination.P2P)
                        },
                        onOpenSpin = {
                            onDestinationChange(AppDestination.SPIN)
                        },
                        onOpenHelp = {
                            onDestinationChange(AppDestination.HELP_CENTER)
                        },
                        onOpenLevels = {
                            onDestinationChange(AppDestination.LEVELS)
                        }
                    )
                }

                AppDestination.LEVELS -> {
                    LevelsScreen(
                        onBack = {
                            onDestinationChange(AppDestination.HOME)
                        }
                    )
                }

                AppDestination.P2P -> {
                    P2PScreen(
                        p2pOrders = p2pOrders,
                        myActiveAds = myActiveP2PAds,
                        userId = userProfile.id,
                        repository = viewModel.repository,
                        onDeleteAd = { adId ->
                            viewModel.deleteOwnP2PAd(
                                adId = adId,
                                onSuccess = {
                                    Toast.makeText(
                                        context,
                                        "Advertisement deleted.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onError = {
                                    Toast.makeText(
                                        context,
                                        it,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        },
                        onTradeAction = { order, orderEtbAmount ->
                            viewModel.tradeP2P(
                                order = order,
                                orderEtbAmount = orderEtbAmount,
                                onSuccess = { createdOrder ->
                                    onActiveP2POrderChange(createdOrder)
                                },
                                onError = {
                                    Toast.makeText(
                                        context,
                                        it,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    )
                }

                AppDestination.SPIN -> {
                    SpinWheelScreen(
                        userId = userProfile.id,
                        repository = viewModel.repository,
                        onSpinWin = { }
                    )
                }

                AppDestination.REFERRAL -> {
                    val referralSummary by
                        viewModel.referralSummary.collectAsState()

                    ReferralScreen(
                        referralCode =
                            referralSummary?.referralCode
                                ?: currentUserReferralCode,
                        referredCount =
                            referralSummary?.referredCount ?: 0,
                        onBack = {
                            onDestinationChange(AppDestination.PROFILE)
                        }
                    )
                }

                AppDestination.HELP_CENTER -> {
                    HelpCenterScreen(
                        onBack = {
                            onDestinationChange(AppDestination.PROFILE)
                        },
                        userId = userProfile.id,
                        repository = viewModel.repository
                    )
                }

                AppDestination.ADMIN -> {
                    AdminControlScreen(
                        onBack = {
                            onDestinationChange(AppDestination.PROFILE)
                        },
                        adminUserId = userProfile.id,
                        repository = viewModel.repository
                    )
                }

                AppDestination.PROFILE -> {
                    ProfileSettingsContent(
                        userProfile = userProfile,
                        currentUserRole = currentUserRole,
                        onDestinationChange = onDestinationChange,
                        onLogout = {
                            viewModel.logout()

                            context.getSharedPreferences(
                                APP_STATE_PREFS,
                                android.content.Context.MODE_PRIVATE
                            )
                                .edit()
                                .remove(KYC_PENDING)
                                .apply()

                            onDestinationChange(AppDestination.LANDING)

                            Toast.makeText(
                                context,
                                "Logged out successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        viewModel = viewModel
                    )
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun ProfileSettingsContent(
    userProfile: com.example.model.UserProfile,
    currentUserRole: String?,
    onDestinationChange: (AppDestination) -> Unit,
    onLogout: () -> Unit,
    viewModel: RealCoinViewModel
) {
    var currentLevel by remember(userProfile.id) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(userProfile.id) {
        currentLevel =
            viewModel.repository
                .getRewardStatus(userProfile.id)
                ?.level
                ?.name
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(16.dp)
            ) {
                Text(
                    "Username: ${userProfile.username.ifBlank { "Guest" }}",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Email: ${userProfile.email.ifBlank { "Not registered" }}",
                    color = Color.Gray
                )

                Text(
                    "Current Level: ${currentLevel ?: "Loading..."}",
                    color = Color(0xFFD89E00),
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                val displayedKycStatus =
                    if (currentUserRole == "ADMIN") {
                        "ADMIN — KYC not required"
                    } else {
                        userProfile.kycStatus
                    }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("KYC Status: ")

                    Text(
                        displayedKycStatus,
                        color = when {
                            currentUserRole == "ADMIN" ->
                                Color(0xFF2E7D32)

                            userProfile.kycStatus == "VERIFIED" ->
                                Color(0xFF2E7D32)

                            userProfile.kycStatus == "PENDING" ->
                                Color(0xFFF57F17)

                            userProfile.kycStatus == "REJECTED" ->
                                Color(0xFFC62828)

                            else ->
                                Color.Gray
                        }
                    )
                }

                if (userProfile.realLockedBalance > 0) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Locked in Escrow/Withdrawal: ${"%,.2f".format(userProfile.realLockedBalance)} RC",
                        color = Color(0xFFC62828)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        PaymentAccountsSettings(
            userId = userProfile.id,
            repository = viewModel.repository
        )

        if (
            currentUserRole != "ADMIN" &&
            (
                userProfile.kycStatus == "NOT_SUBMITTED" ||
                userProfile.kycStatus == "REJECTED"
            )
        ) {
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    onDestinationChange(AppDestination.KYC)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit KYC Verification")
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("logout_button")
        ) {
            Icon(
                Icons.Default.ExitToApp,
                contentDescription = "Log Out"
            )

            Spacer(Modifier.width(8.dp))

            Text("Log Out")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                onDestinationChange(AppDestination.REFERRAL)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_referral_button")
        ) {
            Icon(
                Icons.Default.People,
                contentDescription = "Referral"
            )

            Spacer(Modifier.width(8.dp))

            Text("Referral")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                onDestinationChange(AppDestination.HELP_CENTER)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.HelpOutline,
                contentDescription = "Help Center"
            )

            Spacer(Modifier.width(8.dp))

            Text("Help Center")
        }

        if (currentUserRole == "ADMIN") {
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    onDestinationChange(AppDestination.ADMIN)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = "Admin"
                )

                Spacer(Modifier.width(8.dp))

                Text("Admin Control Panel")
            }
        }

        Spacer(Modifier.height(20.dp))

        WarningNoticeBox(
            message = "Important Security Notice: For all transactions, ensure you only send and receive via BEP-20 (BNB Smart Chain). Minimum withdrawal is calculated from the current admin-set REAL/USD price."
        )
    }
}
