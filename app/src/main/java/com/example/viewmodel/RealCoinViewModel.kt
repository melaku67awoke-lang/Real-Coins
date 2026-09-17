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
import com.example.ui.screens.REAL_COIN_USD_VALUE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RealCoinViewModel(application: Application) : AndroidViewModel(application) {

    val repository: RealCoinRepository = RealCoinRepository(AppDatabase.getInstance(application))
    private val passwordResetService = PasswordResetService()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Active User's Wallet Flow
    val currentWallet: StateFlow<WalletEntity?> = _currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(null)
            else repository.getWallet(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Active User's KYC Flow
    val currentKyc: StateFlow<KycEntity?> = _currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(null)
            else repository.getKycForUser(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Active User's Transactions / Ledger Flow
    val transactions: StateFlow<List<TransactionRecord>> = _currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else repository.getLedgerForUser(user.id).map { ledgerEntries ->
                ledgerEntries.map { entry ->
                    val txType = when (entry.type) {
                        "DEPOSIT" -> TransactionType.DEPOSIT
                        "WITHDRAWAL_LOCK", "WITHDRAWAL_CONFIRMED" -> TransactionType.WITHDRAWAL
                        "REWARD_CLAIM" -> TransactionType.SPIN_REWARD
                        "P2P_ESCROW_RELEASE" -> {
                            if (entry.amount > 0) TransactionType.P2P_BUY else TransactionType.P2P_SELL
                        }
                        else -> TransactionType.DEPOSIT
                    }
                    val absAmount = kotlin.math.abs(entry.amount)
                    TransactionRecord(
                        id = entry.id,
                        type = txType,
                        amountRealCoin = absAmount,
                        usdValue = absAmount * REAL_COIN_USD_VALUE,
                        txHashOrAddress = entry.notes.ifBlank { entry.referenceId },
                        network = "BEP20",
                        status = when (entry.type) {
                            "WITHDRAWAL_LOCK" -> "Pending Review"
                            "WITHDRAWAL_CONFIRMED" -> "Completed"
                            "WITHDRAWAL_REFUND" -> "Refunded"
                            "DEPOSIT" -> "Completed"
                            "REWARD_CLAIM" -> "Completed"
                            else -> "Completed"
                        },
                        timestamp = entry.timestamp
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active P2P Ads Flow (Mapped to P2POrder UI model)
    // The logged-in user can manage only their own active advertisements.
    val myActiveP2PAds: StateFlow<List<P2PAdEntity>> = _currentUser
        .flatMapLatest { user -> if (user == null) flowOf(emptyList()) else repository.getActiveP2PAdsForUser(user.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val p2pOrders: StateFlow<List<P2POrder>> = repository.getActiveP2PAds()
        .map { ads ->
            ads.map { ad ->
                P2POrder(
                    id = ad.id,
                    traderName = ad.sellerName, // Distinct trader name, not logged-in user
                    type = ad.type,
                    cryptoAmount = ad.cryptoAmount,
                    fiatPrice = ad.fiatPrice,
                    fiatCurrency = ad.fiatCurrency,
                    paymentMethod = ad.paymentMethod
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())


    fun register(
        username: String,
        email: String,
        pass: String,
        onSuccess: (UserEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.register(username, email, pass)
            result.onSuccess { user ->
                _currentUser.value = user
                onSuccess(user)
            }.onFailure { err ->
                onError(err.message ?: "Registration failed")
            }
        }
    }

    fun login(
        usernameOrEmail: String,
        pass: String,
        onSuccess: (UserEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.login(usernameOrEmail, pass)
            result.onSuccess { user ->
                _currentUser.value = user
                onSuccess(user)
            }.onFailure { err ->
                onError(err.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        _currentUser.value = null
    }

    fun resetPassword(
        email: String,
        resetAuthorization: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (newPassword.length < 6) {
            onError("Password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            passwordResetService.consumeResetAuthorization(resetAuthorization)
                .onFailure { onError(it.message ?: "Password reset authorization is invalid or expired") }
                .onSuccess { authorizedEmail ->
                    // Never trust the email typed at the beginning of the flow for the
                    // final account selection. The backend binds the authorization to
                    // the verified email and returns that value after consuming it.
                    repository.resetPasswordByEmail(authorizedEmail, newPassword)
                        .onSuccess { onSuccess() }
                        .onFailure { onError(it.message ?: "Password could not be updated") }
                }
        }
    }

    fun requestPasswordReset(
        email: String,
        onSuccess: (resetSessionId: String, expiresAtMs: Long?) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            passwordResetService.requestOtp(email)
                .onSuccess { response ->
                    val sessionId = response.resetSessionId?.takeIf { it.isNotBlank() }
                        ?: return@onSuccess
                    onSuccess(sessionId, response.expiresAtMs)
                }
                .onFailure { error -> onError(error.message ?: "Unable to send confirmation code") }
        }
    }


    fun verifyPasswordResetOtp(
        resetSessionId: String,
        otp: String,
        onSuccess: (resetAuthorization: String, expiresInSeconds: Long?) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            passwordResetService.verifyOtp(resetSessionId, otp)
                .onSuccess { response ->
                    val authorization = response.resetAuthorization?.takeIf { it.isNotBlank() }
                    if (authorization == null) {
                        onError("Invalid password reset response")
                    } else {
                        onSuccess(authorization, response.expiresInSeconds)
                    }
                }
                .onFailure { error -> onError(error.message ?: "Confirmation code is invalid or expired") }
        }
    }

    fun submitKyc(
        fullName: String,
        idNumber: String,
        documentAttached: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
        if (user == null) {
            onError("You must be logged in to submit KYC")
            return
        }
        viewModelScope.launch {
            val result = repository.submitKyc(user.id, fullName, idNumber, documentAttached)
            result.onSuccess {
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "KYC submission failed")
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
            val result = repository.submitDeposit(user.id, txHash, amount)
            result.onSuccess {
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "Deposit failed")
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
            val result = repository.submitWithdrawal(user.id, bep20Address, amount)
            result.onSuccess {
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "Withdrawal request failed")
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
            val result = repository.claimDailyReward(user.id, rewardAmount)
            result.onSuccess { amt ->
                onSuccess(amt)
            }.onFailure { err ->
                onError(err.message ?: "Could not claim daily reward")
            }
        }
    }

    fun deleteOwnP2PAd(adId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = _currentUser.value ?: return onError("Please log in first")
        viewModelScope.launch {
            repository.deleteOwnP2PAd(user.id, adId).onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Could not delete advertisement") }
        }
    }

    fun tradeP2P(
        order: P2POrder,
        onSuccess: (com.example.data.db.P2POrderEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
        if (user == null) {
            onError("Please log in first")
            return
        }
        viewModelScope.launch {
            val result = repository.startP2PTrade(user.id, order.id)
            result.onSuccess { createdOrder ->
                onSuccess(createdOrder)
            }.onFailure { err ->
                onError(err.message ?: "Trade initiation failed")
            }
        }
    }

    fun expireP2POrder(orderId: String, onExpired: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.expireP2POrderIfUnpaid(orderId)
                .onSuccess { onExpired() }
                .onFailure { onError(it.message ?: "Order has not expired yet") }
        }
    }
}
