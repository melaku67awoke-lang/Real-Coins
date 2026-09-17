package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.*
import com.example.BuildConfig
import com.example.security.PasswordHasher
import com.example.ui.screens.BEP20_DEPOSIT_ADDRESS
import com.example.ui.screens.MIN_WITHDRAWAL_USD
import com.example.ui.screens.REAL_COIN_USD_VALUE
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class RealCoinRepository(private val db: AppDatabase) {

    private val userDao = db.userDao()
    private val walletDao = db.walletDao()
    private val kycDao = db.kycDao()
    private val depositDao = db.depositDao()
    private val withdrawalDao = db.withdrawalDao()
    private val ledgerDao = db.ledgerDao()
    private val p2pDao = db.p2pDao()
    private val rewardDao = db.rewardDao()
    private val paymentAccountDao = db.paymentAccountDao()

    // -------------------------------------------------------------
    // 1. AUTHENTICATION & REGISTRATION
    // -------------------------------------------------------------

    suspend fun register(username: String, email: String, password: String): Result<UserEntity> {
        val cleanUsername = username.trim()
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)

        if (cleanUsername.length < 3) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 characters"))
        }
        if (!normalizedEmail.contains("@") || !normalizedEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Invalid email format"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        return db.withTransaction {
            // Reject duplicate username
            if (userDao.getUserByUsername(cleanUsername) != null) {
                return@withTransaction Result.failure(IllegalStateException("Username already taken"))
            }
            // Reject duplicate email case-insensitively
            if (userDao.getUserByNormalizedEmail(normalizedEmail) != null) {
                return@withTransaction Result.failure(IllegalStateException("Email is already registered"))
            }

            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword(password, salt)
            val userId = "RC_USER_${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}"

            // Every new user starts with role = USER (never auto-admin)
            val newUser = UserEntity(
                id = userId,
                username = cleanUsername,
                normalizedEmail = normalizedEmail,
                passwordHash = hash,
                passwordSalt = salt,
                role = "USER",
                createdAt = System.currentTimeMillis()
            )
            userDao.insertUser(newUser)

            // Every new user starts with REAL = 0, USDT = 0, locked = 0
            val initialWallet = WalletEntity(
                userId = userId,
                realBalance = 0.0,
                usdtBalance = 0.0,
                realLockedBalance = 0.0,
                usdtLockedBalance = 0.0,
                updatedAt = System.currentTimeMillis()
            )
            walletDao.insertWallet(initialWallet)

            // Every new user starts with KYC = NOT_SUBMITTED (never auto-verified)
            val initialKyc = KycEntity(
                userId = userId,
                fullName = "",
                idNumber = "",
                documentAttached = false,
                status = "NOT_SUBMITTED"
            )
            kycDao.insertOrUpdateKyc(initialKyc)

            Result.success(newUser)
        }
    }

    suspend fun login(usernameOrEmail: String, password: String): Result<UserEntity> {
        val query = usernameOrEmail.trim()
        val normalized = query.lowercase(Locale.ROOT)

        val user = userDao.getUserByNormalizedEmail(normalized)
            ?: userDao.getUserByUsername(query)
            ?: return Result.failure(IllegalArgumentException("Account does not exist. Please register first."))

        val isPasswordValid = PasswordHasher.verifyPassword(password, user.passwordSalt, user.passwordHash)
        if (!isPasswordValid) {
            return Result.failure(IllegalArgumentException("Invalid password. Please check your credentials."))
        }

        return Result.success(user)
    }

    suspend fun getUserById(userId: String): UserEntity? {
        return userDao.getUserById(userId)
    }

    // -------------------------------------------------------------
    // 2. KYC IDENTITY VERIFICATION
    // -------------------------------------------------------------

    fun getKycForUser(userId: String): Flow<KycEntity?> {
        return kycDao.getKycForUser(userId)
    }

    suspend fun getKycForUserSync(userId: String): KycEntity? {
        return kycDao.getKycForUserSync(userId)
    }

    suspend fun submitKyc(
        userId: String,
        fullName: String,
        idNumber: String,
        documentAttached: Boolean
    ): Result<KycEntity> {
        val user = userDao.getUserById(userId)
            ?: return Result.failure(IllegalStateException("User not found"))

        if (fullName.isBlank() || idNumber.isBlank() || !documentAttached) {
            return Result.failure(IllegalArgumentException("All fields and ID document attachment are required"))
        }

        return db.withTransaction {
            val existingKyc = kycDao.getKycForUserSync(userId)
            if (existingKyc?.status == "VERIFIED") {
                return@withTransaction Result.failure(IllegalStateException("KYC is already verified"))
            }
            if (existingKyc?.status == "PENDING") {
                return@withTransaction Result.failure(IllegalStateException("KYC submission is already pending review"))
            }

            // Submission changes status to PENDING only (never auto-approves)
            val updatedKyc = KycEntity(
                userId = userId,
                fullName = fullName.trim(),
                idNumber = idNumber.trim(),
                documentAttached = documentAttached,
                status = "PENDING",
                submittedAt = System.currentTimeMillis()
            )
            kycDao.insertOrUpdateKyc(updatedKyc)
            Result.success(updatedKyc)
        }
    }

    suspend fun reviewKyc(
        adminUserId: String,
        targetUserId: String,
        approve: Boolean,
        reason: String? = null
    ): Result<KycEntity> {
        return db.withTransaction {
            val adminUser = userDao.getUserById(adminUserId)
            if (adminUser == null || adminUser.role != "ADMIN") {
                return@withTransaction Result.failure(SecurityException("Unauthorized: Only an authorized admin can review KYC"))
            }

            // Users must never approve their own KYC
            if (adminUserId == targetUserId) {
                return@withTransaction Result.failure(SecurityException("Security violation: Admin cannot review their own KYC"))
            }

            val targetKyc = kycDao.getKycForUserSync(targetUserId)
                ?: return@withTransaction Result.failure(IllegalStateException("Target KYC not found"))

            val newStatus = if (approve) "VERIFIED" else "REJECTED"
            val updatedKyc = targetKyc.copy(
                status = newStatus,
                reviewedAt = System.currentTimeMillis(),
                reviewedByAdminId = adminUserId,
                rejectionReason = if (!approve) (reason ?: "Document verification failed") else null
            )
            kycDao.insertOrUpdateKyc(updatedKyc)

            // Record audit log
            val auditLog = KycAuditLogEntity(
                id = UUID.randomUUID().toString(),
                userId = targetUserId,
                adminId = adminUserId,
                action = if (approve) "APPROVED" else "REJECTED",
                notes = reason ?: (if (approve) "Approved by compliance" else "Rejected"),
                timestamp = System.currentTimeMillis()
            )
            kycDao.insertAuditLog(auditLog)

            Result.success(updatedKyc)
        }
    }

    // -------------------------------------------------------------
    // 3. WALLET & BALANCES
    // -------------------------------------------------------------

    fun getWallet(userId: String): Flow<WalletEntity?> {
        return walletDao.getWallet(userId)
    }

    suspend fun getWalletSync(userId: String): WalletEntity? {
        return walletDao.getWalletSync(userId)
    }

    fun getLedgerForUser(userId: String): Flow<List<LedgerEntryEntity>> {
        return ledgerDao.getLedgerForUser(userId)
    }

    // -------------------------------------------------------------
    // 4. DEPOSITS (BEP-20)
    // -------------------------------------------------------------

    fun getDepositsForUser(userId: String): Flow<List<DepositEntity>> {
        return depositDao.getDepositsForUser(userId)
    }

    suspend fun submitDeposit(
        userId: String,
        txHash: String,
        amountReal: Double
    ): Result<DepositEntity> {
        if (BuildConfig.USDT_BEP20_CONTRACT.isBlank()) {
            return Result.failure(IllegalStateException("USDT BEP-20 contract is not configured"))
        }
        val cleanTxHash = txHash.trim().lowercase(Locale.ROOT)
        if (cleanTxHash.length < 10) {
            return Result.failure(IllegalArgumentException("Invalid transaction hash"))
        }
        if (!amountReal.isFinite() || amountReal <= 0.0) {
            return Result.failure(IllegalArgumentException("Deposit amount must be a finite number greater than zero"))
        }

        return db.withTransaction {
            val user = userDao.getUserById(userId)
                ?: return@withTransaction Result.failure(IllegalStateException("User not found"))

            // Duplicate TxHash protection
            val existing = depositDao.getDepositByTxHash(cleanTxHash)
            if (existing != null) {
                return@withTransaction Result.failure(IllegalStateException("Duplicate TxHash: This transaction hash has already been processed"))
            }

            val depositId = "DEP_${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}"
            val deposit = DepositEntity(
                id = depositId,
                userId = userId,
                txHash = cleanTxHash,
                amountReal = amountReal,
                recipientAddress = BEP20_DEPOSIT_ADDRESS,
                network = "BEP20",
                tokenContract = BuildConfig.USDT_BEP20_CONTRACT,
                confirmations = 0,
                status = "PENDING",
                createdAt = System.currentTimeMillis(),
                confirmedAt = null
            )
            depositDao.insertDeposit(deposit)

            // Do not credit funds here. A trusted blockchain verifier must confirm the deposit first.

            Result.success(deposit)
        }
    }

    // -------------------------------------------------------------
    // 5. WITHDRAWALS
    // -------------------------------------------------------------

    fun getWithdrawalsForUser(userId: String): Flow<List<WithdrawalEntity>> {
        return withdrawalDao.getWithdrawalsForUser(userId)
    }

    suspend fun submitWithdrawal(
        userId: String,
        bep20Address: String,
        amountReal: Double
    ): Result<WithdrawalEntity> {
        val cleanAddress = bep20Address.trim().lowercase(Locale.ROOT)
        if (!cleanAddress.startsWith("0x") || cleanAddress.length != 42) {
            return Result.failure(IllegalArgumentException("Invalid BEP-20 recipient address"))
        }

        val usdValue = amountReal * REAL_COIN_USD_VALUE
        if (!amountReal.isFinite() || amountReal <= 0.0 || !usdValue.isFinite()) {
            return Result.failure(IllegalArgumentException("Invalid withdrawal amount"))
        }
        if (usdValue < MIN_WITHDRAWAL_USD) {
            return Result.failure(IllegalArgumentException("Minimum withdrawal limit is $50.00 USD"))
        }

        return db.withTransaction {
            val wallet = walletDao.getWalletSync(userId)
                ?: return@withTransaction Result.failure(IllegalStateException("Wallet not found"))

            // Validate available balance on trusted logic side
            if (amountReal > wallet.availableRealBalance || amountReal > wallet.realBalance - wallet.realLockedBalance) {
                return@withTransaction Result.failure(IllegalStateException("Insufficient available RealCoin balance"))
            }

            // Atomically lock funds
            val updatedWallet = wallet.copy(
                realLockedBalance = wallet.realLockedBalance + amountReal,
                updatedAt = System.currentTimeMillis()
            )
            walletDao.updateWallet(updatedWallet)

            val withdrawalId = "WTH_${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}"
            val withdrawal = WithdrawalEntity(
                id = withdrawalId,
                userId = userId,
                recipientAddress = cleanAddress,
                amountReal = amountReal,
                usdValue = usdValue,
                network = "BEP20",
                status = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            withdrawalDao.insertWithdrawal(withdrawal)

            // Record fund lock in ledger
            val ledgerEntry = LedgerEntryEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = "WITHDRAWAL_LOCK",
                currency = "REAL",
                amount = -amountReal,
                balanceBefore = wallet.realBalance,
                balanceAfter = wallet.realBalance, // Total balance unchanged, available reduced by lock
                referenceId = withdrawalId,
                notes = "Withdrawal request locked: $amountReal RC to $cleanAddress",
                timestamp = System.currentTimeMillis()
            )
            ledgerDao.insertLedgerEntry(ledgerEntry)

            Result.success(withdrawal)
        }
    }

    suspend fun processWithdrawal(
        adminUserId: String,
        withdrawalId: String,
        approve: Boolean,
        reason: String? = null
    ): Result<WithdrawalEntity> {
        return db.withTransaction {
            val adminUser = userDao.getUserById(adminUserId)
            if (adminUser == null || adminUser.role != "ADMIN") {
                return@withTransaction Result.failure(SecurityException("Unauthorized: Admin access required"))
            }

            val withdrawal = withdrawalDao.getWithdrawalById(withdrawalId)
                ?: return@withTransaction Result.failure(IllegalStateException("Withdrawal not found"))

            if (withdrawal.status != "PENDING") {
                return@withTransaction Result.failure(IllegalStateException("Withdrawal is already processed (${withdrawal.status})"))
            }

            if (withdrawal.amountReal <= 0.0 || !withdrawal.amountReal.isFinite()) {
                return@withTransaction Result.failure(IllegalArgumentException("Invalid withdrawal amount"))
            }

            val wallet = walletDao.getWalletSync(withdrawal.userId)
                ?: return@withTransaction Result.failure(IllegalStateException("Wallet not found"))

            val updatedWithdrawal: WithdrawalEntity
            if (approve) {
                // Admin approval is not proof of an on-chain payout.
                if (wallet.realLockedBalance < withdrawal.amountReal || wallet.realBalance < withdrawal.amountReal) {
                    return@withTransaction Result.failure(IllegalStateException("Insufficient locked funds"))
                }
                val balanceBefore = wallet.realBalance
                val balanceAfter = balanceBefore - withdrawal.amountReal
                val newLocked = (wallet.realLockedBalance - withdrawal.amountReal).coerceAtLeast(0.0)

                walletDao.updateWallet(
                    wallet.copy(
                        realBalance = balanceAfter,
                        realLockedBalance = newLocked,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                updatedWithdrawal = withdrawal.copy(
                    status = "CONFIRMED",
                    processedAt = System.currentTimeMillis()
                )
                withdrawalDao.updateWithdrawal(updatedWithdrawal)

                ledgerDao.insertLedgerEntry(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        userId = withdrawal.userId,
                        type = "WITHDRAWAL_CONFIRMED",
                        currency = "REAL",
                        amount = -withdrawal.amountReal,
                        balanceBefore = balanceBefore,
                        balanceAfter = balanceAfter,
                        referenceId = withdrawalId,
                        notes = "Withdrawal confirmed and deducted from locked balance to ${withdrawal.recipientAddress}",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                // Refund locked balance back to available
                val newLocked = (wallet.realLockedBalance - withdrawal.amountReal).coerceAtLeast(0.0)
                walletDao.updateWallet(
                    wallet.copy(
                        realLockedBalance = newLocked,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                updatedWithdrawal = withdrawal.copy(
                    status = "REJECTED",
                    processedAt = System.currentTimeMillis(),
                    rejectionReason = reason ?: "Compliance review failed"
                )
                withdrawalDao.updateWithdrawal(updatedWithdrawal)

                ledgerDao.insertLedgerEntry(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        userId = withdrawal.userId,
                        type = "WITHDRAWAL_REFUND",
                        currency = "REAL",
                        amount = withdrawal.amountReal,
                        balanceBefore = wallet.realBalance,
                        balanceAfter = wallet.realBalance,
                        referenceId = withdrawalId,
                        notes = "Withdrawal rejected and refunded: ${reason ?: "Compliance rejection"}",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            Result.success(updatedWithdrawal)
        }
    }

    // -------------------------------------------------------------
    // 6. REWARDS (EXPLOIT-PROOF DAILY WHEEL)
    // -------------------------------------------------------------

    private fun getCurrentPeriodKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    suspend fun canClaimRewardToday(userId: String): Boolean {
        val todayKey = getCurrentPeriodKey()
        return rewardDao.getClaimForPeriod(userId, todayKey) == null
    }

    suspend fun claimDailyReward(userId: String, rewardAmount: Double): Result<Double> {
        val user = userDao.getUserById(userId)
            ?: return Result.failure(IllegalStateException("User not found"))
        val periodKey = getCurrentPeriodKey()
        val confirmedDepositTotal = depositDao.getConfirmedDepositTotal(userId)
        val sanitizedReward = when {
            confirmedDepositTotal >= 200.0 -> 500.0 // Diamond
            confirmedDepositTotal >= 150.0 -> 100.0 // Platinum
            confirmedDepositTotal >= 100.0 -> 50.0  // Gold
            confirmedDepositTotal >= 50.0 -> 25.0   // Silver
            else -> 10.0                             // Beginner
        }

        return db.withTransaction {
            val existingClaim = rewardDao.getClaimForPeriod(userId, periodKey)
            if (existingClaim != null) {
                return@withTransaction Result.failure(IllegalStateException("Daily reward already claimed today. Please try again tomorrow!"))
            }

            val claimId = "REW_${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}"
            rewardDao.insertRewardClaim(
                RewardClaimEntity(
                    id = claimId,
                    userId = userId,
                    amountReal = sanitizedReward,
                    claimTimestamp = System.currentTimeMillis(),
                    periodKey = periodKey
                )
            )

            val wallet = walletDao.getWalletSync(userId) ?: WalletEntity(userId = userId)
            val balanceBefore = wallet.realBalance
            val balanceAfter = balanceBefore + sanitizedReward

            walletDao.updateWallet(
                wallet.copy(
                    realBalance = balanceAfter,
                    updatedAt = System.currentTimeMillis()
                )
            )

            ledgerDao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = "REWARD_CLAIM",
                    currency = "REAL",
                    amount = sanitizedReward,
                    balanceBefore = balanceBefore,
                    balanceAfter = balanceAfter,
                    referenceId = claimId,
                    notes = "Daily Wheel Reward: $periodKey",
                    timestamp = System.currentTimeMillis()
                )
            )

            Result.success(sanitizedReward)
        }
    }

    // Saved payment accounts: ownership is always enforced by userId.
    fun getSavedPaymentAccounts(userId: String): Flow<List<PaymentAccountEntity>> =
        paymentAccountDao.getForUser(userId)

    suspend fun savePaymentAccount(
        userId: String, paymentName: String, paymentMethod: String, accountNumber: String
    ): Result<PaymentAccountEntity> {
        val name = paymentName.trim()
        val method = paymentMethod.trim()
        val number = accountNumber.trim()
        if (name.isBlank() || method.isBlank() || number.isBlank()) {
            return Result.failure(IllegalArgumentException("Payment name, method and account number are required"))
        }
        if (userDao.getUserById(userId) == null) return Result.failure(IllegalStateException("User not found"))
        val account = PaymentAccountEntity(UUID.randomUUID().toString(), userId, name, method, number)
        paymentAccountDao.insert(account)
        return Result.success(account)
    }

    suspend fun deleteSavedPaymentAccount(userId: String, accountId: String): Result<Unit> {
        val deleted = paymentAccountDao.deleteOwned(accountId, userId)
        return if (deleted == 1) Result.success(Unit)
        else Result.failure(SecurityException("Payment account not found or not owned by user"))
    }

    // -------------------------------------------------------------
    // 7. P2P TRADING & ESCROW (STRICT ETB ONLY)
    // -------------------------------------------------------------

    fun getActiveP2PAds(): Flow<List<P2PAdEntity>> {
        return p2pDao.getActiveAds()
    }

    suspend fun createP2PSellAd(
        sellerId: String,
        amountReal: Double,
        fiatPriceEtb: Double,
        paymentMethod: String
    ): Result<P2PAdEntity> {
        if (!amountReal.isFinite() || !fiatPriceEtb.isFinite() || amountReal <= 0.0 || fiatPriceEtb <= 0.0) {
            return Result.failure(IllegalArgumentException("Amount and price must be greater than zero"))
        }

        return db.withTransaction {
            val seller = userDao.getUserById(sellerId)
                ?: return@withTransaction Result.failure(IllegalStateException("Seller not found"))

            val wallet = walletDao.getWalletSync(sellerId)
                ?: return@withTransaction Result.failure(IllegalStateException("Wallet not found"))

            // Enforce seller balance limits
            if (amountReal > wallet.availableRealBalance || amountReal > wallet.realBalance - wallet.realLockedBalance) {
                return@withTransaction Result.failure(IllegalStateException("Insufficient balance to create sell ad"))
            }

            // Lock funds in escrow atomically
            walletDao.updateWallet(
                wallet.copy(
                    realLockedBalance = wallet.realLockedBalance + amountReal,
                    updatedAt = System.currentTimeMillis()
                )
            )

            val adId = "P2P_AD_${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}"
            val ad = P2PAdEntity(
                id = adId,
                sellerId = sellerId,
                sellerName = seller.username,
                type = "SELL",
                cryptoAmount = amountReal,
                fiatPrice = fiatPriceEtb,
                fiatCurrency = "ETB", // ETB ONLY
                paymentMethod = paymentMethod,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )
            p2pDao.insertAd(ad)

            ledgerDao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = sellerId,
                    type = "P2P_ESCROW_LOCK",
                    currency = "REAL",
                    amount = -amountReal,
                    balanceBefore = wallet.realBalance,
                    balanceAfter = wallet.realBalance,
                    referenceId = adId,
                    notes = "Escrow locked for P2P ad: $amountReal RC",
                    timestamp = System.currentTimeMillis()
                )
            )

            Result.success(ad)
        }
    }


    suspend fun createP2PSellAdUsingPaymentAccount(
        sellerId: String, amountReal: Double, fiatPriceEtb: Double, accountId: String
    ): Result<P2PAdEntity> {
        val account = paymentAccountDao.getOwned(accountId, sellerId)
            ?: return Result.failure(SecurityException("Payment account not found or not owned by seller"))
        return createP2PSellAdWithDetails(sellerId, amountReal, fiatPriceEtb, account.paymentMethod, account.paymentName, account.accountNumber)
    }

    private suspend fun createP2PSellAdWithDetails(sellerId: String, amountReal: Double, fiatPriceEtb: Double, paymentMethod: String, paymentName: String, accountNumber: String): Result<P2PAdEntity> {
        val result = createP2PSellAd(sellerId, amountReal, fiatPriceEtb, paymentMethod)
        return result.map { it.copy(paymentName = paymentName, accountNumber = accountNumber).also { updated -> p2pDao.updateAd(updated) } }
    }

    suspend fun createP2PBuyAd(
        buyerId: String,
        amountReal: Double,
        fiatPriceEtb: Double,
        paymentMethod: String
    ): Result<P2PAdEntity> {
        if (!amountReal.isFinite() || !fiatPriceEtb.isFinite() || amountReal <= 0.0 || fiatPriceEtb <= 0.0) {
            return Result.failure(IllegalArgumentException("Amount and price must be greater than zero"))
        }

        return db.withTransaction {
            val buyer = userDao.getUserById(buyerId)
                ?: return@withTransaction Result.failure(IllegalStateException("User not found"))

            val adId = "P2P_AD_${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}"
            val ad = P2PAdEntity(
                id = adId,
                sellerId = buyerId, // Owner of the ad
                sellerName = buyer.username,
                type = "BUY",
                cryptoAmount = amountReal,
                fiatPrice = fiatPriceEtb,
                fiatCurrency = "ETB",
                paymentMethod = paymentMethod,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )
            p2pDao.insertAd(ad)
            Result.success(ad)
        }
    }

    suspend fun startP2PTrade(buyerId: String, adId: String): Result<P2POrderEntity> {
        return db.withTransaction {
            val buyer = userDao.getUserById(buyerId)
                ?: return@withTransaction Result.failure(IllegalStateException("Buyer account not found"))

            val ad = p2pDao.getAdById(adId)
                ?: return@withTransaction Result.failure(IllegalStateException("Trade ad not found"))

            if (!ad.isActive) {
                return@withTransaction Result.failure(IllegalStateException("Ad is no longer active"))
            }

            // BUY advertisements do not place REAL in seller escrow in this MVP.
            // Do not route them through the SELL escrow release flow.
            if (ad.type != "SELL") {
                return@withTransaction Result.failure(IllegalStateException("Buy advertisements are not supported by this escrow flow yet"))
            }

            // Users cannot buy their own ad
            if (ad.sellerId == buyerId) {
                return@withTransaction Result.failure(IllegalStateException("You cannot trade against your own advertisement"))
            }

            if (!ad.cryptoAmount.isFinite() || ad.cryptoAmount <= 0.0) {
                return@withTransaction Result.failure(IllegalArgumentException("Invalid REAL amount in advertisement"))
            }

            val sellerWallet = walletDao.getWalletSync(ad.sellerId)
                ?: return@withTransaction Result.failure(IllegalStateException("Seller wallet not found"))
            // SELL ad creation already locked this amount. Do not lock it again here.
            if (sellerWallet.realLockedBalance < ad.cryptoAmount || sellerWallet.realBalance < ad.cryptoAmount) {
                return@withTransaction Result.failure(IllegalStateException("Seller escrow funds are insufficient"))
            }

            val orderId = "ORD_${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}"
            val order = P2POrderEntity(
                id = orderId,
                adId = ad.id,
                sellerId = ad.sellerId,
                sellerName = ad.sellerName,
                buyerId = buyerId,
                buyerName = buyer.username,
                cryptoAmount = ad.cryptoAmount,
                fiatPrice = ad.fiatPrice,
                fiatCurrency = "ETB",
                paymentMethod = ad.paymentMethod,
                paymentName = ad.paymentName,
                accountNumber = ad.accountNumber,
                status = "ESCROW_LOCKED",
                createdAt = System.currentTimeMillis()
            )

            // The seller's funds remain locked from ad creation; no second lock is applied.
            p2pDao.insertOrder(order)
            // Mark ad as inactive while order is in progress
            p2pDao.updateAd(ad.copy(isActive = false))

            Result.success(order)
        }
    }

    suspend fun releaseP2PEscrow(callerUserId: String, orderId: String): Result<P2POrderEntity> {
        return db.withTransaction {
            val order = p2pDao.getOrderById(orderId)
                ?: return@withTransaction Result.failure(IllegalStateException("Order not found"))

            // Only seller or admin can release escrow
            val caller = userDao.getUserById(callerUserId)
            val isAuthorized = callerUserId == order.sellerId || caller?.role == "ADMIN"
            if (!isAuthorized) {
                return@withTransaction Result.failure(SecurityException("Unauthorized to release escrow for this trade"))
            }

            if (order.status != "ESCROW_LOCKED" && order.status != "PAID") {
                return@withTransaction Result.failure(IllegalStateException("Order cannot be released (status: ${order.status})"))
            }

            val sellerWallet = walletDao.getWalletSync(order.sellerId)
                ?: return@withTransaction Result.failure(IllegalStateException("Seller wallet not found"))
            val buyerWallet = walletDao.getWalletSync(order.buyerId)
                ?: return@withTransaction Result.failure(IllegalStateException("Buyer wallet not found"))

            // Deduct locked funds from seller only when the escrow is actually funded.
            if (sellerWallet.realLockedBalance < order.cryptoAmount || sellerWallet.realBalance < order.cryptoAmount) {
                return@withTransaction Result.failure(IllegalStateException("Seller escrow funds are insufficient"))
            }
            val sellerBalBefore = sellerWallet.realBalance
            val sellerBalAfter = sellerBalBefore - order.cryptoAmount
            val newSellerLocked = (sellerWallet.realLockedBalance - order.cryptoAmount).coerceAtLeast(0.0)

            walletDao.updateWallet(
                sellerWallet.copy(
                    realBalance = sellerBalAfter,
                    realLockedBalance = newSellerLocked,
                    updatedAt = System.currentTimeMillis()
                )
            )

            // Credit buyer
            val buyerBalBefore = buyerWallet.realBalance
            val buyerBalAfter = buyerBalBefore + order.cryptoAmount

            walletDao.updateWallet(
                buyerWallet.copy(
                    realBalance = buyerBalAfter,
                    updatedAt = System.currentTimeMillis()
                )
            )

            // Update order status
            val completedOrder = order.copy(
                status = "COMPLETED",
                completedAt = System.currentTimeMillis()
            )
            p2pDao.updateOrder(completedOrder)

            // Ledger entries for both parties
            ledgerDao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = order.sellerId,
                    type = "P2P_ESCROW_RELEASE",
                    currency = "REAL",
                    amount = -order.cryptoAmount,
                    balanceBefore = sellerBalBefore,
                    balanceAfter = sellerBalAfter,
                    referenceId = orderId,
                    notes = "P2P Sell completed to ${order.buyerName}",
                    timestamp = System.currentTimeMillis()
                )
            )

            ledgerDao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = order.buyerId,
                    type = "P2P_ESCROW_RELEASE",
                    currency = "REAL",
                    amount = order.cryptoAmount,
                    balanceBefore = buyerBalBefore,
                    balanceAfter = buyerBalAfter,
                    referenceId = orderId,
                    notes = "P2P Buy completed from ${order.sellerName}",
                    timestamp = System.currentTimeMillis()
                )
            )

            Result.success(completedOrder)
        }
    }

    // Sample marketplace ads removed. Marketplace starts empty for new installations.
}
