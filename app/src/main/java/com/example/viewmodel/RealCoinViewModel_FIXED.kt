package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.KycEntity
import com.example.data.db.UserEntity
import com.example.data.db.WalletEntity
import com.example.data.db.P2PAdEntity
import com.example.data.repository.RealCoinRepository
import com.example.data.passwordreset.PasswordResetService
import com.example.model.P2POrder
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RealCoinViewModel(application: Application) : AndroidViewModel(application) {

    val repository: RealCoinRepository =
        RealCoinRepository(AppDatabase.getInstance(application), application)

    private val passwordResetService = PasswordResetService()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)

    private val _referralSummary =
        MutableStateFlow<com.example.data.backend.ReferralResponse?>(null)

    val referralSummary: StateFlow<com.example.data.backend.ReferralResponse?> =
        _referralSummary.asStateFlow()

    val currentUser: StateFlow<UserEntity?> =
        _currentUser.asStateFlow()

    // Active User's Wallet Flow
    val currentWallet: StateFlow<WalletEntity?> = _currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(null)
            } else {
                repository.getWallet(user.id)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    // Active User's KYC Flow
    val currentKyc: StateFlow<KycEntity?> = _currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(null)
            } else {
                repository.getKycForUser(user.id)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    // Active User's Transactions / Ledger Flow
    val transactions: StateFlow<List<TransactionRecord>> = _currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                repository.getLedgerForUser(user.id).map { ledgerEntries ->
                    ledgerEntries.map { entry ->
                        val txType = when (entry.type) {
                            "DEPOSIT" ->
                                TransactionType.DEPOSIT

                            "WITHDRAWAL_LOCK",
                            "WITHDRAWAL_CONFIRMED" ->
                                TransactionType.WITHDRAWAL

                            "REWARD_CLAIM" ->
                                TransactionType.SPIN_REWARD

                            "P2P_ESCROW_RELEASE" -> {
                                if (entry.amount > 0) {
                                    TransactionType.P2P_BUY
                                } else {
                                    TransactionType.P2P_SELL
                                }
                            }

                            else ->
                                TransactionType.DEPOSIT
                        }

                        val absAmount = kotlin.math.abs(entry.amount)

                        TransactionRecord(
                            id = entry.id,
                            type = txType,
                            amountRealCoin = absAmount,
                            usdValue =
                                absAmount * repository.getRealCoinUsdPrice(),
                            txHashOrAddress =
                                entry.notes.ifBlank {
                                    entry.referenceId
                                },
                            network = "BEP20",
                            status = when (entry.type) {
                                "WITHDRAWAL_LOCK" ->
                                    "Pending Review"

                                "WITHDRAWAL_CONFIRMED" ->
                                    "Completed"

                                "WITHDRAWAL_REFUND" ->
                                    "Refunded"

                                "DEPOSIT" ->
                                    "Completed"

                                "REWARD_CLAIM" ->
                                    "Completed"

                                else ->
                                    "Completed"
                            },
                            timestamp = entry.timestamp
                        )
                    }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    // Active P2P Ads Flow
    // The logged-in user can manage only their own active advertisements.
    val myActiveP2PAds: StateFlow<List<P2PAdEntity>> = _currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                repository.getActiveP2PAdsForUser(user.id)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val p2pOrders: StateFlow<List<P2POrder>> =
        repository.getActiveP2PAds()
            .map { ads ->
                ads.map { ad ->
                    P2POrder(
                        id = ad.id,
                        traderName = ad.sellerName,
                        type = ad.type,
                        cryptoAmount = ad.cryptoAmount,
                        fiatPrice = ad.fiatPrice,
                        fiatCurrency = ad.fiatCurrency,
                        paymentMethod = ad.paymentMethod,
                        minOrderEtb = ad.minOrderEtb,
                        maxOrderEtb = ad.maxOrderEtb
                    )
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    // Combined User Profile for UI
    val userProfile: StateFlow<UserProfile> = combine(
        _currentUser,
        currentWallet,
        currentKyc
    ) { user, wallet, kyc ->
        if (user == null) {
            UserProfile()
        } else {
            UserProfile(
                id = user.id,
                username = user.username,
                email = user.normalizedEmail,
                realCoinBalance = wallet?.realBalance ?: 0.0,
                usdtBalance = wallet?.usdtBalance ?: 0.0,
                realLockedBalance = wallet?.realLockedBalance ?: 0.0,
                isKycVerified = kyc?.status == "VERIFIED",
                kycStatus = kyc?.status ?: "NOT_SUBMITTED",
                role = user.role
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserProfile()
    )

    fun register(
        username: String,
        email: String,
        pass: String,
        referralCode: String? = null,
        onSuccess: (UserEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.register(
                username,
                email,
                pass,
                referralCode
            )

            result.onSuccess { user ->
                repository.connectBackendSession(user, pass)
                    .onSuccess {
                        _currentUser.value = user

                        viewModelScope.launch {
                            repository.refreshP2PFromBackend()

                            repository.getReferralSummary()
                                .onSuccess {
                                    _referralSummary.value = it
                                }
                        }

                        onSuccess(user)
                    }
                    .onFailure { err ->

                        // Backend registration failed after the local
                        // Room registration succeeded. Remove only the
                        // newly-created local registration records so a
                        // failed registration does not leave the username
                        // or email permanently marked as taken.
                        repository.rollbackLocalRegistration(user.id)

                        onError(
                            err.message
                                ?: "Backend account setup failed"
                        )
                    }
            }.onFailure { err ->
                onError(
                    err.message
                        ?: "Registration failed"
                )
            }
        }
    }

    /**
     * LOGIN SECURITY:
     *
     * Normal users can log in ONLY when their KYC status is VERIFIED.
     *
     * Pending, rejected, and not-submitted users are rejected here
     * BEFORE a backend session is created and BEFORE _currentUser is set.
     *
     * This means Login never redirects an unverified user to KYC.
     *
     * Admin users are allowed to log in without KYC verification.
     */
    fun login(
        usernameOrEmail: String,
        pass: String,
        onSuccess: (UserEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {

            // First verify that the local account exists and that
            // the password is correct.
            val result = repository.login(
                usernameOrEmail,
                pass
            )

            result.onSuccess { user ->

                /*
                 * ADMIN:
                 * The repository has already authenticated the account against
                 * the backend and mirrored it locally when this is a fresh
                 * installation. ADMIN must never be blocked by KYC.
                 */
                if (user.role.trim().uppercase() == "ADMIN") {
                    _currentUser.value = user

                    viewModelScope.launch {
                        repository.refreshP2PFromBackend()

                        repository.getReferralSummary()
                            .onSuccess {
                                _referralSummary.value = it
                            }
                    }

                    onSuccess(user)
                    return@onSuccess
                }

                /*
                 * NORMAL USERS:
                 * Preserve the existing KYC security gate.
                 */
                repository.syncKycFromBackend(user.id)
                    .onFailure { /* preserve local KYC if the network is temporarily unavailable */ }

                val kyc =
                    repository.getKycForUserSync(user.id)

                val kycStatus =
                    kyc?.status
                        ?.trim()
                        ?.uppercase()
                        ?: "NOT_SUBMITTED"

                if (kycStatus != "VERIFIED") {

                    val message = when (kycStatus) {
                        "PENDING" ->
                            "Login blocked. Your KYC is still pending admin verification."

                        "REJECTED" ->
                            "Login blocked. Your KYC was rejected. Please submit your KYC again."

                        "NOT_SUBMITTED" ->
                            "Login blocked. Please complete KYC verification before logging in."

                        else ->
                            "Login blocked. Your KYC must be VERIFIED before you can log in."
                    }

                    onError(message)

                    // Do not establish a usable application session for an
                    // unverified normal user.
                    repository.disconnectBackendSession()
                    return@onSuccess
                }

                _currentUser.value = user

                viewModelScope.launch {
                    repository.refreshP2PFromBackend()

                    repository.getReferralSummary()
                        .onSuccess {
                            _referralSummary.value = it
                        }
                }

                onSuccess(user)

            }.onFailure { err ->
                onError(
                    err.message
                        ?: "Login failed"
                )
            }
        }
    }

    fun refreshReferralSummary() {
        viewModelScope.launch {
            repository.getReferralSummary()
                .onSuccess {
                    _referralSummary.value = it
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.disconnectBackendSession()
        }

        _currentUser.value = null
        _referralSummary.value = null
    }

    fun requestRecovery(
        email: String,
        onSuccess: (recoveryRequestId: String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            passwordResetService.requestRecovery(email)
                .onSuccess { response ->
                    val requestId =
                        response.recoveryRequestId
                            ?.takeIf { it.isNotBlank() }

                    if (requestId == null) {
                        onError(
                            "Invalid password recovery response"
                        )
                    } else {
                        onSuccess(requestId)
                    }
                }
                .onFailure { error ->
                    onError(
                        error.message
                            ?: "Unable to submit password recovery request"
                    )
                }
        }
    }

    fun checkRecoveryStatus(
        recoveryRequestId: String,
        onSuccess: (status: String, email: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            passwordResetService
                .checkRecoveryStatus(recoveryRequestId)
                .onSuccess { response ->
                    onSuccess(
                        response.status ?: "PENDING",
                        response.email
                    )
                }
                .onFailure { error ->
                    onError(
                        error.message
                            ?: "Could not check recovery request"
                    )
                }
        }
    }

    fun resetPassword(
        recoveryRequestId: String,
        email: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (newPassword.length < 8) {
            onError("Password must be at least 8 characters")
            return
        }

        viewModelScope.launch {
            passwordResetService
                .checkRecoveryStatus(recoveryRequestId)
                .onFailure { error ->
                    onError(
                        error.message
                            ?: "Could not verify admin approval"
                    )
                }
                .onSuccess { statusResponse ->

                    if (statusResponse.status != "APPROVED") {
                        onError(
                            "Admin approval is required before changing the password"
                        )
                        return@onSuccess
                    }

                    val approvedEmail =
                        statusResponse.email
                            ?.trim()
                            ?.lowercase()

                    if (
                        approvedEmail.isNullOrBlank() ||
                        approvedEmail != email.trim().lowercase()
                    ) {
                        onError(
                            "Recovery request email does not match the account"
                        )
                        return@onSuccess
                    }

                    repository
                        .resetPasswordByEmail(
                            approvedEmail,
                            newPassword
                        )
                        .onSuccess {

                            passwordResetService
                                .completeRecovery(
                                    recoveryRequestId,
                                    newPassword
                                )
                                .onSuccess {
                                    onSuccess()
                                }
                                .onFailure {
                                    onError(
                                        "Password changed, but recovery status could not be completed"
                                    )
                                }
                        }
                        .onFailure { error ->
                            onError(
                                error.message
                                    ?: "Password could not be updated"
                            )
                        }
                }
        }
    }

    fun submitKyc(
        fullName: String,
        idType: String,
        idNumber: String,
        frontIdUri: String,
        backIdUri: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value

        if (user == null) {
            onError(
                "You must be logged in to submit KYC"
            )
            return
        }

        viewModelScope.launch {
            val documentAttached =
                frontIdUri.isNotBlank() &&
                    backIdUri.isNotBlank()

            val result = repository.submitKyc(
                user.id, fullName, idType, idNumber, frontIdUri, backIdUri
            )

            result.onSuccess {
                onSuccess()
            }.onFailure { err ->
                onError(
                    err.message
                        ?: "KYC submission failed"
                )
            }
        }
    }

    fun submitDeposit(
        txHash: String,
        amount: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value

        if (user == null) {
            onError("Please log in first")
            return
        }

        viewModelScope.launch {
            val result = repository.submitDeposit(
                user.id,
                txHash,
                amount
            )

            result.onSuccess {
                onSuccess()
            }.onFailure { err ->
                onError(
                    err.message
                        ?: "Deposit failed"
                )
            }
        }
    }

    fun submitWithdrawal(
        bep20Address: String,
        amount: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value

        if (user == null) {
            onError("Please log in first")
            return
        }

        viewModelScope.launch {
            val result = repository.submitWithdrawal(
                user.id,
                bep20Address,
                amount
            )

            result.onSuccess {
                onSuccess()
            }.onFailure { err ->
                onError(
                    err.message
                        ?: "Withdrawal request failed"
                )
            }
        }
    }

    fun claimDailyReward(
        rewardAmount: Double,
        onSuccess: (Double) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value

        if (user == null) {
            onError("Please log in first")
            return
        }

        viewModelScope.launch {
            val result =
                repository.claimDailyReward(
                    user.id,
                    rewardAmount
                )

            result.onSuccess { amt ->
                onSuccess(amt)
            }.onFailure { err ->
                onError(
                    err.message
                        ?: "Could not claim daily reward"
                )
            }
        }
    }

    fun deleteOwnP2PAd(
        adId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
            ?: return onError("Please log in first")

        viewModelScope.launch {
            repository
                .deleteOwnP2PAd(user.id, adId)
                .onSuccess {
                    onSuccess()
                }
                .onFailure {
                    onError(
                        it.message
                            ?: "Could not delete advertisement"
                    )
                }
        }
    }

    fun tradeP2P(
        order: P2POrder,
        orderEtbAmount: Double,
        onSuccess: (com.example.data.db.P2POrderEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value

        if (user == null) {
            onError("Please log in first")
            return
        }

        viewModelScope.launch {
            val result =
                repository.startP2PTrade(
                    user.id,
                    order.id,
                    orderEtbAmount
                )

            result.onSuccess { createdOrder ->
                onSuccess(createdOrder)
            }.onFailure { err ->
                onError(
                    err.message
                        ?: "Trade initiation failed"
                )
            }
        }
    }

    fun markP2PPaymentPaid(
        orderId: String,
        proofUri: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
            ?: return onError("Please log in first")

        viewModelScope.launch {
            repository
                .markP2PPaymentPaid(
                    user.id,
                    orderId,
                    proofUri
                )
                .onSuccess {
                    onSuccess()
                }
                .onFailure {
                    onError(
                        it.message
                            ?: "Could not confirm payment"
                    )
                }
        }
    }

    fun releaseP2PEscrow(
        orderId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
            ?: return onError("Please log in first")

        viewModelScope.launch {
            repository
                .releaseP2PEscrow(
                    user.id,
                    orderId
                )
                .onSuccess {
                    onSuccess()
                }
                .onFailure {
                    onError(
                        it.message
                            ?: "Could not release escrow"
                    )
                }
        }
    }

    fun openP2PDispute(
        orderId: String,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
            ?: return onError("Please log in first")

        viewModelScope.launch {
            repository
                .openP2PDispute(
                    user.id,
                    orderId,
                    reason
                )
                .onSuccess {
                    onSuccess()
                }
                .onFailure {
                    onError(
                        it.message
                            ?: "Could not open dispute"
                    )
                }
        }
    }

    fun expireP2POrder(
        orderId: String,
        onExpired: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository
                .expireP2POrderIfUnpaid(orderId)
                .onSuccess {
                    onExpired()
                }
                .onFailure {
                    onError(
                        it.message
                            ?: "Order has not expired yet"
                    )
                }
        }
    }
}
