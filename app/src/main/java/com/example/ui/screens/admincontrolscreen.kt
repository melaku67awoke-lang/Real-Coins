package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.data.db.*
import com.example.data.repository.RealCoinRepository
import com.example.util.AttachmentStorage
import kotlinx.coroutines.launch

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

    var saving by remember {
        mutableStateOf(false)
    }

    val deposits by repository
        .getAllDeposits()
        .collectAsState(initial = emptyList())

    val withdrawals by repository
        .getAllWithdrawals()
        .collectAsState(initial = emptyList())

    var kycs by remember { mutableStateOf<List<KycEntity>>(emptyList()) }

    val disputes by repository
        .getDisputedP2POrders()
        .collectAsState(initial = emptyList())

    val helps by repository
        .getPendingHelpRequests()
        .collectAsState(initial = emptyList())

    var pendingUsers by remember {
        mutableStateOf(0)
    }

    var verifiedUsers by remember {
        mutableStateOf(0)
    }

    fun message(text: String) {
        Toast.makeText(
            context,
            text,
            Toast.LENGTH_SHORT
        ).show()
    }

    suspend fun refreshKyc() {
        repository.getPendingKycRemote()
            .onSuccess { remote ->
                kycs = remote
                pendingUsers = remote.size
            }
            .onFailure { error ->
                message(error.message ?: "Could not load pending KYC")
            }
    }

    LaunchedEffect(Unit) {
        usdPrice = repository.getRealCoinUsdPrice().toString()
        refreshKyc()
        verifiedUsers = repository.verifiedKycCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // -----------------------------------------------------
        // HEADER
        // -----------------------------------------------------

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
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "Authorized admin area",
            color = Color(0xFF9FB0B5)
        )

        // -----------------------------------------------------
        // USER STATISTICS
        // -----------------------------------------------------

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdminStatCard(
                title = "Pending Users",
                value = pendingUsers.toString(),
                modifier = Modifier.weight(1f)
            )

            AdminStatCard(
                title = "Verified Users",
                value = verifiedUsers.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            "Pending Users are unverified. Verified Users have completed KYC.",
            color = Color(0xFF9FB0B5),
            style = MaterialTheme.typography.bodySmall
        )

        // -----------------------------------------------------
        // REALCOIN PRICE
        // -----------------------------------------------------

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    "RealCoin Price Management",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF80DEEA),
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Price changes update valuation only; REAL coin units stay unchanged.",
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = usdPrice,
                    onValueChange = {
                        usdPrice = it
                    },
                    label = {
                        Text("1 RC price in USD")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "1 USD = 186 ETB is used internally for calculations only.",
                    color = Color(0xFF9FB0B5)
                )

                Button(
                    enabled = !saving,
                    onClick = {
                        val price = usdPrice.toDoubleOrNull()

                        if (price == null || price <= 0.0) {
                            message("Enter a valid RC price")
                        } else {
                            scope.launch {
                                saving = true

                                repository
                                    .updatePricing(
                                        adminUserId,
                                        price,
                                        186.0
                                    )
                                    .onSuccess {
                                        message("RC price updated")
                                    }
                                    .onFailure {
                                        message(
                                            it.message
                                                ?: "Update failed"
                                        )
                                    }

                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (saving) {
                            "Saving..."
                        } else {
                            "Save Pricing"
                        }
                    )
                }
            }
        }

        // -----------------------------------------------------
        // PENDING DEPOSITS
        // -----------------------------------------------------

        ReviewSection(
            title = "Pending Deposits",
            hasItems = deposits.any {
                it.status == "PENDING"
            }
        ) {
            deposits
                .filter {
                    it.status == "PENDING"
                }
                .forEach { deposit ->

                    ReviewCard(
                        title = "${deposit.id} • ${deposit.amountReal} RC",
                        detail = "Tx: ${deposit.txHash}",

                        approve = {
                            scope.launch {
                                repository
                                    .processDeposit(
                                        adminUserId,
                                        deposit.id,
                                        true
                                    )
                                    .onSuccess {
                                        message(
                                            "Deposit approved"
                                        )
                                    }
                                    .onFailure {
                                        message(
                                            it.message
                                                ?: "Failed"
                                        )
                                    }
                            }
                        },

                        reject = {
                            scope.launch {
                                repository
                                    .processDeposit(
                                        adminUserId,
                                        deposit.id,
                                        false
                                    )
                                    .onSuccess {
                                        message(
                                            "Deposit rejected"
                                        )
                                    }
                                    .onFailure {
                                        message(
                                            it.message
                                                ?: "Failed"
                                        )
                                    }
                            }
                        }
                    )
                }
        }

        // -----------------------------------------------------
        // PENDING WITHDRAWALS
        // -----------------------------------------------------

        ReviewSection(
            title = "Pending Withdrawals",
            hasItems = withdrawals.any {
                it.status == "PENDING"
            }
        ) {
            withdrawals
                .filter {
                    it.status == "PENDING"
                }
                .forEach { withdrawal ->

                    ReviewCard(
                        title =
                            "${withdrawal.id} • ${withdrawal.amountReal} RC",

                        detail =
                            "${withdrawal.recipientAddress} • " +
                                "$${"%,.2f".format(withdrawal.usdValue)}",

                        approve = {
                            scope.launch {
                                repository
                                    .processWithdrawal(
                                        adminUserId,
                                        withdrawal.id,
                                        true
                                    )
                                    .onSuccess {
                                        message(
                                            "Withdrawal approved"
                                        )
                                    }
                                    .onFailure {
                                        message(
                                            it.message
                                                ?: "Failed"
                                        )
                                    }
                            }
                        },

                        reject = {
                            scope.launch {
                                repository
                                    .processWithdrawal(
                                        adminUserId,
                                        withdrawal.id,
                                        false,
                                        "Rejected by admin"
                                    )
                                    .onSuccess {
                                        message(
                                            "Withdrawal rejected and refunded"
                                        )
                                    }
                                    .onFailure {
                                        message(
                                            it.message
                                                ?: "Failed"
                                        )
                                    }
                            }
                        }
                    )
                }
        }

        // -----------------------------------------------------
        // PENDING KYC
        // -----------------------------------------------------

        ReviewSection(
            title = "Pending KYC",
            hasItems = kycs.isNotEmpty()
        ) {

            kycs.forEach { kyc ->

                val frontUri = kyc.frontIdUri
                val backUri = kyc.backIdUri

                ReviewCard(
                    title =
                        "${kyc.fullName} • ${kyc.idNumber}",

                    detail = buildString {
                        append(
                            "Full name: ${kyc.fullName}\n"
                        )

                        append(
                            "ID type: ${
                                kyc.idType.ifBlank {
                                    "Not provided"
                                }
                            }\n"
                        )

                        append(
                            "ID number: ${kyc.idNumber}\n"
                        )

                        append(
                            "Front ID URI: ${
                                frontUri ?: "Not provided"
                            }\n"
                        )

                        append(
                            "Back ID URI: ${
                                backUri ?: "Not provided"
                            }"
                        )
                    },

                    approve = {
                        scope.launch {
                            repository
                                .reviewKyc(
                                    adminUserId,
                                    kyc.userId,
                                    true
                                )
                                .onSuccess {
                                    message(
                                        "KYC approved"
                                    )
                                    refreshKyc()
                                }
                                .onFailure {
                                    message(
                                        it.message
                                            ?: "Failed"
                                    )
                                }
                        }
                    },

                    reject = {
                        scope.launch {
                            repository
                                .reviewKyc(
                                    adminUserId,
                                    kyc.userId,
                                    false,
                                    "KYC review rejected"
                                )
                                .onSuccess {
                                    message(
                                        "KYC rejected"
                                    )
                                    refreshKyc()
                                }
                                .onFailure {
                                    message(
                                        it.message
                                            ?: "Failed"
                                    )
                                }
                        }
                    },

                    openFront = if (frontUri != null) {
                        {
                            scope.launch {
                                repository.openRemoteKycDocument(kyc.userId, "front")
                                    .onSuccess { uri ->
                                        if (!AttachmentStorage.open(context, uri)) message("Cannot open front ID document")
                                    }
                                    .onFailure { message(it.message ?: "Cannot open front ID document") }
                            }
                        }
                    } else null,

                    openBack = if (backUri != null) {
                        {
                            scope.launch {
                                repository.openRemoteKycDocument(kyc.userId, "back")
                                    .onSuccess { uri ->
                                        if (!AttachmentStorage.open(context, uri)) message("Cannot open back ID document")
                                    }
                                    .onFailure { message(it.message ?: "Cannot open back ID document") }
                            }
                        }
                    } else null
                )
            }
        }

        // -----------------------------------------------------
        // P2P DISPUTES
        // -----------------------------------------------------

        ReviewSection(
            title = "P2P Disputes",
            hasItems = disputes.isNotEmpty()
        ) {

            disputes.forEach { dispute ->

                ReviewCard(
                    title =
                        "${dispute.id} • ${dispute.cryptoAmount} RC",

                    detail =
                        dispute.disputeReason
                            ?: "No reason",

                    approve = {
                        scope.launch {
                            repository
                                .resolveP2PDispute(
                                    adminUserId,
                                    dispute.id,
                                    true
                                )
                                .onSuccess {
                                    message(
                                        "Escrow released to buyer"
                                    )
                                }
                                .onFailure {
                                    message(
                                        it.message
                                            ?: "Failed"
                                    )
                                }
                        }
                    },

                    reject = {
                        scope.launch {
                            repository
                                .resolveP2PDispute(
                                    adminUserId,
                                    dispute.id,
                                    false
                                )
                                .onSuccess {
                                    message(
                                        "Escrow returned to seller"
                                    )
                                }
                                .onFailure {
                                    message(
                                        it.message
                                            ?: "Failed"
                                    )
                                }
                        }
                    }
                )
            }
        }

        // -----------------------------------------------------
        // HELP CENTER
        // -----------------------------------------------------

        ReviewSection(
            title = "Help Center",
            hasItems = helps.isNotEmpty()
        ) {

            helps.forEach { help ->

                ReviewCard(
                    title =
                        "${help.category} • ${help.id}",

                    detail = help.message,

                    approve = {
                        scope.launch {
                            repository
                                .resolveHelpRequest(
                                    adminUserId,
                                    help.id
                                )
                                .onSuccess {
                                    message(
                                        "Help request resolved"
                                    )
                                }
                                .onFailure {
                                    message(
                                        it.message
                                            ?: "Failed"
                                    )
                                }
                        }
                    },

                    reject = null
                )
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )
    }
}

// -------------------------------------------------------------
// ADMIN STAT CARD
// -------------------------------------------------------------

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF9FB0B5)
            )

            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF80DEEA)
            )
        }
    }
}

// -------------------------------------------------------------
// REVIEW SECTION
// -------------------------------------------------------------

@Composable
private fun ReviewSection(
    title: String,
    hasItems: Boolean,
    content: @Composable () -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    if (hasItems) {
        content()
    } else {
        Text(
            "No pending items",
            color = Color.Gray
        )
    }
}

// -------------------------------------------------------------
// REVIEW CARD
// -------------------------------------------------------------

@Composable
private fun ReviewCard(
    title: String,
    detail: String,
    approve: (() -> Unit)?,
    reject: (() -> Unit)?,
    openFront: (() -> Unit)? = null,
    openBack: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            Text(
                title,
                fontWeight = FontWeight.Bold
            )

            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            // -------------------------------------------------
            // KYC DOCUMENT BUTTONS
            // -------------------------------------------------

            if (openFront != null || openBack != null) {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    if (openFront != null) {
                        OutlinedButton(
                            onClick = openFront
                        ) {
                            Text("Open Front ID")
                        }
                    }

                    if (openBack != null) {
                        OutlinedButton(
                            onClick = openBack
                        ) {
                            Text("Open Back ID")
                        }
                    }
                }
            }

            // -------------------------------------------------
            // APPROVE / REJECT
            // -------------------------------------------------

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                if (approve != null) {
                    Button(
                        onClick = approve
                    ) {
                        Text(
                            if (reject == null) {
                                "Resolve"
                            } else {
                                "Approve"
                            }
                        )
                    }
                }

                if (reject != null) {
                    OutlinedButton(
                        onClick = reject
                    ) {
                        Text(
                            if (
                                title.contains(
                                    "dispute",
                                    ignoreCase = true
                                )
                            ) {
                                "Return to Seller"
                            } else {
                                "Reject"
                            }
                        )
                    }
                }
            }
        }
    }
}
