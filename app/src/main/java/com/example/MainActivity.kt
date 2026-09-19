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
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.example.data.db.P2POrderEntity
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RealCoinViewModel

enum class AppDestination {
    LANDING,
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD,
    KYC,
    HOME,
    P2P,
    SPIN,
    PROFILE,
    REFERRAL,
    HELP_CENTER,
    ADMIN
}

class MainActivity : ComponentActivity() {

    companion object {
        private const val APP_STATE_PREFS = "real_coins_app_state"
        private const val FORCE_LANDING_AFTER_KYC =
            "force_landing_after_kyc"
    }

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

private fun currentKycStatusIsVerified(status: String): Boolean {
    return status == "VERIFIED"
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
    val myActiveP2PAds by viewModel.myActiveP2PAds.collectAsState()

    val realCoinUsdPrice by viewModel.repository
        .observeRealCoinUsdPrice()
        .collectAsState(
            initial = REAL_COIN_USD_VALUE
        )

    val usdToEtbRate by viewModel.repository
        .observeUsdToEtbRate()
        .collectAsState(
            initial = USD_TO_ETB
        )

    /*
     * ---------------------------------------------------------
     * KYC -> LANDING PAGE ON NEXT APP LAUNCH
     * ---------------------------------------------------------
     *
     * We store a small local flag after successful KYC submission.
     *
     * The current KYC screen remains visible after submission so
     * the user can see:
     *
     * "Waiting for admin verification"
     *
     * When the application is completely closed and opened again,
     * the flag causes the first screen to be LANDING.
     *
     * The flag is consumed only once.
     */
    val preferences = remember {
        context.getSharedPreferences(
            "real_coins_app_state",
            android.content.Context.MODE_PRIVATE
        )
    }

    val forceLandingAfterKyc = remember {
        preferences.getBoolean(
            "force_landing_after_kyc",
            false
        )
    }

    /*
     * App Navigation State
     *
     * If KYC was just submitted and the application is being
     * opened again, start at the public landing page.
     *
     * Otherwise preserve the existing authenticated behavior.
     */
    var currentDestination by remember {
        mutableStateOf(
            if (forceLandingAfterKyc) {
                AppDestination.LANDING
            } else if (currentUser != null) {
                AppDestination.HOME
            } else {
                AppDestination.LANDING
            }
        )
    }

    /*
     * Consume the one-time KYC landing flag.
     *
     * We deliberately do NOT log the user out here.
     * The account/session can remain stored, while the UI starts
     * from the public landing page as requested.
     */
    LaunchedEffect(forceLandingAfterKyc) {
        if (forceLandingAfterKyc) {
            preferences.edit()
                .remove("force_landing_after_kyc")
                .apply()
        }
    }

    /*
     * Authentication/KYC gate:
     *
     * ADMIN users may enter the main application.
     *
     * Normal users may enter only after KYC is VERIFIED.
     *
     * PENDING, REJECTED, and NOT_SUBMITTED users remain outside
     * the main application.
     */
    val canEnterMainApp =
        currentUser?.role == "ADMIN" ||
        currentKycStatusIsVerified(
            userProfile.kycStatus
        )

    LaunchedEffect(
        currentUser,
        userProfile.kycStatus,
        currentDestination
    ) {

        /*
         * No authenticated user:
         * only LOGIN and REGISTER are allowed outside landing.
         */
        if (
            currentUser == null &&
            currentDestination != AppDestination.LOGIN &&
            currentDestination != AppDestination.REGISTER &&
            currentDestination != AppDestination.FORGOT_PASSWORD
        ) {
            currentDestination = AppDestination.LANDING
        }

        /*
         * Authenticated but KYC is not approved:
         * prevent access to the main application.
         */
        else if (
            currentUser != null &&
            currentDestination in setOf(
                AppDestination.HOME,
                AppDestination.P2P,
                AppDestination.SPIN,
                AppDestination.PROFILE,
                AppDestination.REFERRAL,
                AppDestination.HELP_CENTER,
                AppDestination.ADMIN
            ) &&
            !canEnterMainApp
        ) {
            currentDestination = AppDestination.KYC
        }
    }

    // ---------------------------------------------------------
    // Dialog state
    // ---------------------------------------------------------

    var showDepositDialog by remember {
        mutableStateOf(false)
    }

    var showWithdrawDialog by remember {
        mutableStateOf(false)
    }

    var showNotifications by remember {
        mutableStateOf(false)
    }

    var activeP2POrder by remember {
        mutableStateOf<P2POrderEntity?>(null)
    }

    // ---------------------------------------------------------
    // Deposit dialog
    // ---------------------------------------------------------

    if (showDepositDialog) {

        DepositDialog(
            realCoinUsdPrice = realCoinUsdPrice,
            usdToEtbRate = usdToEtbRate,

            onDismiss = {
                showDepositDialog = false
            },

            onSubmitDeposit = { txHash, amount ->

                viewModel.submitDeposit(
                    txHash = txHash,
                    amount = amount,

                    onSuccess = {

                        Toast.makeText(
                            context,
                            "Deposit request submitted and is pending blockchain verification.",
                            Toast.LENGTH_SHORT
                        ).show()

                        showDepositDialog = false
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
    }

    // ---------------------------------------------------------
    // Withdrawal dialog
    // ---------------------------------------------------------

    if (showWithdrawDialog) {

        WithdrawDialog(

            userBalanceRealCoin = (
                userProfile.realCoinBalance -
                    userProfile.realLockedBalance
                ).coerceAtLeast(0.0),

            realCoinUsdPrice = realCoinUsdPrice,

            usdToEtbRate = usdToEtbRate,

            onDismiss = {
                showWithdrawDialog = false
            },

            onSubmitWithdrawal = {
                    bep20Address,
                    amount
                ->

                val usdVal =
                    amount * realCoinUsdPrice

                viewModel.submitWithdrawal(

                    bep20Address = bep20Address,

                    amount = amount,

                    onSuccess = {

                        Toast.makeText(
                            context,
                            "Withdrawal of $${"%.2f".format(usdVal)} USD submitted. Funds locked pending review.",
                            Toast.LENGTH_LONG
                        ).show()

                        showWithdrawDialog = false
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
    }

    // =========================================================
    // LANDING
    // =========================================================

    if (currentDestination == AppDestination.LANDING) {

        LandingScreen(

            onNavigateToLogin = {
                currentDestination =
                    AppDestination.LOGIN
            },

            onNavigateToRegister = {
                currentDestination =
                    AppDestination.REGISTER
            }
        )
    }

    // =========================================================
    // LOGIN
    // =========================================================

    else if (currentDestination == AppDestination.LOGIN) {

        LoginScreen(

            onLoginSubmit = {
                    usernameOrEmail,
                    password
                ->

                viewModel.login(

                    usernameOrEmail = usernameOrEmail,

                    pass = password,

                    onSuccess = {

                        currentDestination =
                            if (it.role == "ADMIN") {
                                AppDestination.HOME
                            } else {
                                AppDestination.KYC
                            }

                        Toast.makeText(

                            context,

                            if (it.role == "ADMIN") {
                                "Welcome back, ${it.username}!"
                            } else {
                                "KYC verification is required before entering the app."
                            },

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

            onNavigateToRegister = {

                currentDestination =
                    AppDestination.REGISTER
            },

            onNavigateToForgotPassword = {

                currentDestination =
                    AppDestination.FORGOT_PASSWORD
            },

            onBack = {

                currentDestination =
                    AppDestination.LANDING
            }
        )
    }

    // =========================================================
    // FORGOT PASSWORD
    // =========================================================

    else if (
        currentDestination ==
        AppDestination.FORGOT_PASSWORD
    ) {

        ForgotPasswordEntryScreen(

            onRequestRecovery = {
                    email,
                    onSuccess,
                    onError
                ->

                viewModel.requestRecovery(

                    email = email,

                    onSuccess = onSuccess,

                    onError = onError
                )
            },

            onCheckRecoveryStatus = {
                    recoveryRequestId,
                    onSuccess,
                    onError
                ->

                viewModel.checkRecoveryStatus(

                    recoveryRequestId =
                        recoveryRequestId,

                    onSuccess = onSuccess,

                    onError = onError
                )
            },

            onResetPassword = {
                    recoveryRequestId,
                    email,
                    newPassword,
                    onSuccess,
                    onError
                ->

                viewModel.resetPassword(

                    recoveryRequestId =
                        recoveryRequestId,

                    email = email,

                    newPassword =
                        newPassword,

                    onSuccess = onSuccess,

                    onError = onError
                )
            },

            onBack = {

                currentDestination =
                    AppDestination.LOGIN
            }
        )
    }

    // =========================================================
    // REGISTER
    // =========================================================

    else if (
        currentDestination ==
        AppDestination.REGISTER
    ) {

        RegisterScreen(

            onRegisterSuccess = {
                    username,
                    email,
                    password,
                    referralCode
                ->

                viewModel.register(

                    username = username,

                    email = email,

                    pass = password,

                    referralCode = referralCode,

                    onSuccess = {

                        /*
                         * New accounts must go directly to KYC.
                         */
                        currentDestination =
                            AppDestination.KYC

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

                currentDestination =
                    AppDestination.LANDING
            }
        )
    }

    // =========================================================
    // KYC
    // =========================================================

    else if (
        currentDestination ==
        AppDestination.KYC
    ) {

        KYCScreen(

            kycStatus =
                userProfile.kycStatus,

            onKYCSubmitted = {
                    fullName,
                    idNumber,
                    docAttached,
                    documentUri
                ->

                viewModel.submitKyc(

                    fullName = fullName,

                    idNumber = idNumber,

                    documentAttached =
                        docAttached,

                    onSuccess = {

                        /*
                         * IMPORTANT:
                         *
                         * Do NOT send the user into HOME.
                         *
                         * The user must wait for admin verification.
                         */
                        Toast.makeText(

                            context,

                            "KYC submitted. Please wait for admin verification.",

                            Toast.LENGTH_SHORT

                        ).show()

                        /*
                         * Remember that KYC was successfully submitted.
                         *
                         * The current screen remains KYC.
                         *
                         * When the user closes and opens the app again,
                         * MainActivity will start at LANDING.
                         */
                        preferences.edit()
                            .putBoolean(
                                "force_landing_after_kyc",
                                true
                            )
                            .apply()
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
    }

    // =========================================================
    // MAIN APPLICATION
    // =========================================================

    else {

        activeP2POrder?.let { order ->

            ActiveP2POrderScreen(

                order = order,

                currentUserId =
                    userProfile.id,

                repository =
                    viewModel.repository,

                onExpire = {

                    viewModel.expireP2POrder(
                        order.id
                    ) {

                        activeP2POrder =
                            null
                    }
                },

                onPaid = { proofUri ->

                    viewModel.markP2PPaymentPaid(

                        order.id,

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

                        order.id,

                        onSuccess = {

                            activeP2POrder =
                                null

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

                        order.id,

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

                    activeP2POrder =
                        null
                }
            )
        }
    }

    // =========================================================
    // NOTIFICATIONS
    // =========================================================

    if (showNotifications) {

        AlertDialog(

            onDismissRequest = {

                showNotifications =
                    false
            },

            title = {

                Text("Notifications")
            },

            text = {

                if (transactions.isEmpty()) {

                    Text(
                        "No notifications yet."
                    )

                } else {

                    Column(

                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)

                    ) {

                        transactions
                            .take(12)
                            .forEach { tx ->

                                val message =
                                    when (tx.type) {

                                        TransactionType.SPIN_REWARD ->

                                            if (
                                                tx.amountRealCoin > 0
                                            ) {

                                                "You won ${"%,.2f".format(tx.amountRealCoin)} RC in Spin Wheel."

                                            } else {

                                                "Spin Wheel result recorded."
                                            }

                                        TransactionType.DEPOSIT ->

                                            "You successfully deposited ${"%,.2f".format(tx.amountRealCoin)} RC."

                                        TransactionType.WITHDRAWAL ->

                                            "Withdrawal request: ${"%,.2f".format(tx.amountRealCoin)} RC."

                                        TransactionType.P2P_BUY ->

                                            "P2P buy completed: ${"%,.2f".format(tx.amountRealCoin)} RC."

                                        TransactionType.P2P_SELL ->

                                            "P2P sell completed: ${"%,.2f".format(tx.amountRealCoin)} RC."
                                    }

                                Text(
                                    message,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium
                                )
                            }
                    }
                }
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        showNotifications =
                            false
                    }

                ) {

                    Text("Close")
                }
            }
        )
    }

    // =========================================================
    // MAIN APP SCAFFOLD
    // =========================================================

    if (

        currentDestination in setOf(

            AppDestination.HOME,

            AppDestination.P2P,

            AppDestination.SPIN,

            AppDestination.PROFILE,

            AppDestination.REFERRAL,

            AppDestination.HELP_CENTER,

            AppDestination.ADMIN

        ) && canEnterMainApp

    ) {

        Scaffold(

            topBar = {

                TopAppBar(

                    title = {

                        Row(

                            verticalAlignment =
                                Alignment.CenterVertically

                        ) {

                            Icon(

                                Icons.Default.MonetizationOn,

                                contentDescription =
                                    "RealCoin",

                                tint =
                                    Color(0xFFFFC107),

                                modifier =
                                    Modifier.size(28.dp)
                            )

                            Spacer(
                                Modifier.width(8.dp)
                            )

                            Text(

                                "Real-Coins",

                                style =
                                    MaterialTheme
                                        .typography
                                        .titleLarge,

                                color =
                                    Color.Black
                            )
                        }
                    },

                    actions = {

                        IconButton(

                            onClick = {

                                showNotifications =
                                    true
                            }

                        ) {

                            Icon(

                                Icons.Default.Notifications,

                                contentDescription =
                                    "Notifications",

                                tint =
                                    Color.Black
                            )
                        }

                        IconButton(

                            onClick = {

                                currentDestination =
                                    AppDestination.PROFILE
                            }

                        ) {

                            Icon(

                                Icons.Default.Person,

                                contentDescription =
                                    "Profile",

                                tint =
                                    Color.Black
                            )
                        }
                    }
                )
            },

            bottomBar = {

                NavigationBar {

                    NavigationBarItem(

                        selected =
                            currentDestination ==
                                AppDestination.HOME,

                        onClick = {

                            currentDestination =
                                AppDestination.HOME
                        },

                        icon = {

                            Icon(

                                Icons.Default.Home,

                                contentDescription =
                                    "Dashboard"
                            )
                        },

                        label = {

                            Text("Dashboard")
                        },

                        modifier =
                            Modifier.testTag(
                                "nav_home"
                            )
                    )

                    NavigationBarItem(

                        selected =
                            currentDestination ==
                                AppDestination.P2P,

                        onClick = {

                            currentDestination =
                                AppDestination.P2P
                        },

                        icon = {

                            Icon(

                                Icons.Default.SwapHoriz,

                                contentDescription =
                                    "P2P"
                            )
                        },

                        label = {

                            Text("P2P")
                        },

                        modifier =
                            Modifier.testTag(
                                "nav_p2p"
                            )
                    )

                    NavigationBarItem(

                        selected =
                            currentDestination ==
                                AppDestination.SPIN,

                        onClick = {

                            currentDestination =
                                AppDestination.SPIN
                        },

                        icon = {

                            Icon(

                                Icons.Default.Casino,

                                contentDescription =
                                    "Spin Wheel"
                            )
                        },

                        label = {

                            Text("Spin Wheel")
                        },

                        modifier =
                            Modifier.testTag(
                                "nav_spin"
                            )
                    )

                    NavigationBarItem(

                        selected =
                            currentDestination ==
                                AppDestination.PROFILE,

                        onClick = {

                            currentDestination =
                                AppDestination.PROFILE
                        },

                        icon = {

                            Icon(

                                Icons.Default.Settings,

                                contentDescription =
                                    "Settings"
                            )
                        },

                        label = {

                            Text("Settings")
                        },

                        modifier =
                            Modifier.testTag(
                                "nav_settings"
                            )
                    )
                }
            }

        ) { innerPadding ->

            Box(

                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)

            ) {

                when (currentDestination) {

                    // -------------------------------------------------
                    // HOME
                    // -------------------------------------------------

                    AppDestination.HOME -> {

                        HomeScreen(

                            userProfile =
                                userProfile,

                            transactions =
                                transactions,

                            realCoinUsdPrice =
                                realCoinUsdPrice,

                            onOpenDeposit = {

                                showDepositDialog =
                                    true
                            },

                            onOpenWithdraw = {

                                showWithdrawDialog =
                                    true
                            },

                            onOpenReferral = {

                                currentDestination =
                                    AppDestination.REFERRAL
                            },

                            onOpenP2P = {

                                currentDestination =
                                    AppDestination.P2P
                            },

                            onOpenSpin = {

                                currentDestination =
                                    AppDestination.SPIN
                            },

                            onOpenHelp = {

                                currentDestination =
                                    AppDestination.HELP_CENTER
                            }
                        )
                    }

                    // -------------------------------------------------
                    // P2P
                    // -------------------------------------------------

                    AppDestination.P2P -> {

                        P2PScreen(

                            p2pOrders =
                                p2pOrders,

                            myActiveAds =
                                myActiveP2PAds,

                            userId =
                                userProfile.id,

                            repository =
                                viewModel.repository,

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

                            onTradeAction = {
                                    order,
                                    orderEtbAmount
                                ->

                                viewModel.tradeP2P(

                                    order =
                                        order,

                                    orderEtbAmount =
                                        orderEtbAmount,

                                    onSuccess = {
                                        createdOrder ->

                                        activeP2POrder =
                                            createdOrder
                                    },

                                    onError = {
                                        errorMsg ->

                                        Toast.makeText(

                                            context,

                                            errorMsg,

                                            Toast.LENGTH_LONG

                                        ).show()
                                    }
                                )
                            }
                        )
                    }

                    // -------------------------------------------------
                    // SPIN
                    // -------------------------------------------------

                    AppDestination.SPIN -> {

                        SpinWheelScreen(

                            userId =
                                userProfile.id,

                            repository =
                                viewModel.repository,

                            onSpinWin = { }
                        )
                    }

                    // -------------------------------------------------
                    // REFERRAL
                    // -------------------------------------------------

                    AppDestination.REFERRAL -> {

                        val referralSummary by
                            viewModel.referralSummary
                                .collectAsState()

                        ReferralScreen(

                            referralCode =
                                referralSummary
                                    ?.referralCode
                                    ?: currentUser?.referralCode,

                            referredCount =
                                referralSummary
                                    ?.referredCount
                                    ?: 0,

                            onBack = {

                                currentDestination =
                                    AppDestination.PROFILE
                            }
                        )
                    }

                    // -------------------------------------------------
                    // HELP CENTER
                    // -------------------------------------------------

                    AppDestination.HELP_CENTER -> {

                        HelpCenterScreen(

                            onBack = {

                                currentDestination =
                                    AppDestination.PROFILE
                            },

                            userId =
                                userProfile.id,

                            repository =
                                viewModel.repository
                        )
                    }

                    // -------------------------------------------------
                    // ADMIN
                    // -------------------------------------------------

                    AppDestination.ADMIN -> {

                        AdminControlScreen(

                            onBack = {

                                currentDestination =
                                    AppDestination.PROFILE
                            },

                            adminUserId =
                                userProfile.id,

                            repository =
                                viewModel.repository
                        )
                    }

                    // -------------------------------------------------
                    // PROFILE / SETTINGS
                    // -------------------------------------------------

                    AppDestination.PROFILE -> {

                        Column(

                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)

                        ) {

                            Text(

                                "Settings",

                                style =
                                    MaterialTheme
                                        .typography
                                        .headlineMedium
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(16.dp)
                            )

                            Card(

                                modifier =
                                    Modifier.fillMaxWidth()

                            ) {

                                Column(

                                    modifier =
                                        Modifier.padding(16.dp)

                                ) {

                                    Text(

                                        "Username: ${
                                            userProfile.username.ifBlank {
                                                "Guest"
                                            }
                                        }",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleMedium
                                    )

                                    Text(

                                        "Email: ${
                                            userProfile.email.ifBlank {
                                                "Not registered"
                                            }
                                        }",

                                        color =
                                            Color.Gray
                                    )

                                    Spacer(
                                        modifier =
                                            Modifier.height(8.dp)
                                    )

                                    Row(

                                        verticalAlignment =
                                            Alignment.CenterVertically

                                    ) {

                                        Text(

                                            "KYC Status: ",

                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodyMedium
                                        )

                                        Text(

                                            userProfile.kycStatus,

                                            color =
                                                when (
                                                    userProfile.kycStatus
                                                ) {

                                                    "VERIFIED" ->
                                                        Color(
                                                            0xFF2E7D32
                                                        )

                                                    "PENDING" ->
                                                        Color(
                                                            0xFFF57F17
                                                        )

                                                    "REJECTED" ->
                                                        Color(
                                                            0xFFC62828
                                                        )

                                                    else ->
                                                        Color.Gray
                                                },

                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodyMedium
                                        )
                                    }

                                    if (
                                        userProfile
                                            .realLockedBalance > 0
                                    ) {

                                        Spacer(
                                            modifier =
                                                Modifier.height(4.dp)
                                        )

                                        Text(

                                            "Locked in Escrow/Withdrawal: ${
                                                "%,.2f".format(
                                                    userProfile
                                                        .realLockedBalance
                                                )
                                            } RC",

                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall,

                                            color =
                                                Color(
                                                    0xFFC62828
                                                )
                                        )
                                    }
                                }
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )

                            PaymentAccountsSettings(

                                userId =
                                    userProfile.id,

                                repository =
                                    viewModel.repository
                            )

                            if (

                                userProfile.kycStatus ==
                                    "NOT_SUBMITTED" ||

                                userProfile.kycStatus ==
                                    "REJECTED"

                            ) {

                                Spacer(
                                    modifier =
                                        Modifier.height(12.dp)
                                )

                                Button(

                                    onClick = {

                                        currentDestination =
                                            AppDestination.KYC
                                    },

                                    modifier =
                                        Modifier.fillMaxWidth()

                                ) {

                                    Text(
                                        "Submit KYC Verification"
                                    )
                                }
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )

                            // -------------------------------------------------
                            // LOGOUT
                            // -------------------------------------------------

                            OutlinedButton(

                                onClick = {

                                    viewModel.logout()

                                    currentDestination =
                                        AppDestination.LANDING

                                    Toast.makeText(

                                        context,

                                        "Logged out successfully",

                                        Toast.LENGTH_SHORT

                                    ).show()
                                },

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag(
                                            "logout_button"
                                        )

                            ) {

                                Icon(

                                    Icons.Default.ExitToApp,

                                    contentDescription =
                                        "Log Out"
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(8.dp)
                                )

                                Text("Log Out")
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )

                            // -------------------------------------------------
                            // REFERRAL
                            // -------------------------------------------------

                            OutlinedButton(

                                onClick = {

                                    currentDestination =
                                        AppDestination.REFERRAL
                                },

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag(
                                            "settings_referral_button"
                                        )

                            ) {

                                Icon(

                                    Icons.Default.People,

                                    contentDescription =
                                        "Referral"
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(8.dp)
                                )

                                Text("Referral")
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )

                            // -------------------------------------------------
                            // HELP CENTER
                            // -------------------------------------------------

                            OutlinedButton(

                                onClick = {

                                    currentDestination =
                                        AppDestination.HELP_CENTER
                                },

                                modifier =
                                    Modifier.fillMaxWidth()

                            ) {

                                Icon(

                                    Icons.Default.HelpOutline,

                                    contentDescription =
                                        "Help Center"
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(8.dp)
                                )

                                Text("Help Center")
                            }

                            // -------------------------------------------------
                            // ADMIN
                            // -------------------------------------------------

                            if (
                                currentUser?.role ==
                                    "ADMIN"
                            ) {

                                Spacer(
                                    modifier =
                                        Modifier.height(8.dp)
                                )

                                OutlinedButton(

                                    onClick = {

                                        currentDestination =
                                            AppDestination.ADMIN
                                    },

                                    modifier =
                                        Modifier.fillMaxWidth()

                                ) {

                                    Icon(

                                        Icons.Default.AdminPanelSettings,

                                        contentDescription =
                                            "Admin"
                                    )

                                    Spacer(
                                        modifier =
                                            Modifier.width(8.dp)
                                    )

                                    Text(
                                        "Admin Control Panel"
                                    )
                                }
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(20.dp)
                            )

                            WarningNoticeBox(

                                message =
                                    "Important Security Notice: For all transactions, ensure you only send and receive via BEP-20 (BNB Smart Chain). Minimum withdrawal is calculated from the current admin-set REAL/USD price."
                            )
                        }
                    }

                    else -> {
                        // Other destinations are handled above.
                    }
                }
            }
        }
    }
}
