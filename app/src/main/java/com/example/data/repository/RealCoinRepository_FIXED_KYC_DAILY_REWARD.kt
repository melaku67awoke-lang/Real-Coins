package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.reward.RewardPolicy
import com.example.data.reward.RewardStatus
import com.example.data.db.*
import com.example.data.backend.*
import com.example.BuildConfig
import com.example.security.PasswordHasher
import com.example.ui.screens.BEP20_DEPOSIT_ADDRESS
import com.example.ui.screens.MIN_WITHDRAWAL_USD
import com.example.ui.screens.MIN_DEPOSIT_USD
import com.example.ui.screens.REAL_COIN_USD_VALUE
import com.example.util.AttachmentStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class RealCoinRepository(private val db: AppDatabase, context: Context) {

    private val userDao = db.userDao()
    private val walletDao = db.walletDao()
    private val kycDao = db.kycDao()
    private val depositDao = db.depositDao()
    private val withdrawalDao = db.withdrawalDao()
    private val ledgerDao = db.ledgerDao()
    private val p2pDao = db.p2pDao()
    private val rewardDao = db.rewardDao()
    private val spinStateDao = db.spinStateDao()
    private val paymentAccountDao = db.paymentAccountDao()
    private val appSettingsDao = db.appSettingsDao()
    private val helpRequestDao = db.helpRequestDao()
    private val p2pChatDao = db.p2pChatDao()
    private val backendApi = BackendNetwork.api
    private val backendSession = BackendSession(context.applicationContext)
    private val appContext = context.applicationContext

    private fun backendAuth(): String? = backendSession.authHeader()

    private fun backendFailure(
        responseCode: Int,
        fallback: String
    ): IllegalStateException =
        IllegalStateException("$fallback (HTTP $responseCode)")

    suspend fun connectBackendSession(
        user: UserEntity,
        password: String
    ): Result<Unit> {
        if (password.length < 8) {
            return Result.failure(
                IllegalArgumentException("Password must be at least 8 characters")
            )
        }

        return runCatching {
            val login = backendApi.login(
                AuthRequest(
                    email = user.normalizedEmail,
                    password = password
                )
            )

            val auth = if (
                login.isSuccessful &&
                login.body()?.sessionToken != null
            ) {
                login.body()!!
            } else if (
                login.code() == 401 ||
                login.code() == 404
            ) {
                val registration = backendApi.register(
                    AuthRequest(
                        username = user.username,
                        email = user.normalizedEmail,
                        password = password,
                        referralCode = user.referralCode
                    )
                )

                if (
                    !registration.isSuccessful ||
                    registration.body()?.sessionToken.isNullOrBlank()
                ) {
                    throw backendFailure(
                        registration.code(),
                        "Backend account setup failed"
                    )
                }

                registration.body()!!
            } else {
                throw backendFailure(
                    login.code(),
                    "Backend login failed"
                )
            }

            val account = auth.account
                ?: throw IllegalStateException(
                    "Backend returned no account"
                )

            backendSession.token = auth.sessionToken
            backendSession.accountId = account.id
            backendSession.localUserId = user.id

            runCatching {
                refreshPricingFromBackend()
            }
        }
    }

    suspend fun disconnectBackendSession() {
        backendAuth()?.let {
            runCatching {
                backendApi.logout(it)
            }
        }

        backendSession.clear()
    }

    private fun mapAd(ad: BackendAd): P2PAdEntity =
        P2PAdEntity(
            id = ad.id,
            sellerId =
                if (ad.sellerId == backendSession.accountId) {
                    backendSession.localUserId ?: ad.sellerId
                } else {
                    ad.sellerId
                },
            sellerName = ad.sellerName,
            type = ad.type,
            cryptoAmount = ad.cryptoAmount,
            fiatPrice = ad.fiatPrice,
            fiatCurrency = ad.fiatCurrency,
            paymentMethod = ad.paymentMethod,
            paymentName = ad.paymentName,
            accountNumber = ad.accountNumber,
            isActive = ad.isActive != 0,
            minOrderEtb = ad.minOrderEtb,
            maxOrderEtb = ad.maxOrderEtb,
            originalMaxOrderEtb = ad.originalMaxOrderEtb,
            createdAt = ad.createdAt
        )

    private fun mapOrder(order: BackendOrder): P2POrderEntity {
        val currentServerId = backendSession.accountId
        val localId = backendSession.localUserId

        return P2POrderEntity(
            id = order.id,
            adId = order.adId,
            sellerId =
                if (order.sellerId == currentServerId) {
                    localId ?: order.sellerId
                } else {
                    order.sellerId
                },
            sellerName = order.sellerName,
            buyerId =
                if (order.buyerId == currentServerId) {
                    localId ?: order.buyerId
                } else {
                    order.buyerId
                },
            buyerName = order.buyerName,
            cryptoAmount = order.cryptoAmount,
            fiatPrice = order.fiatPrice,

            // Preserved for the P2P order entity and UI.
            fiatOrderAmount = order.fiatOrderAmount,

            fiatCurrency = order.fiatCurrency,
            paymentMethod = order.paymentMethod,
            paymentName = order.paymentName,
            accountNumber = order.accountNumber,
            paymentProofUri = order.paymentProofUrl,
            paidAt = order.paidAt,
            status = order.status,
            createdAt = order.createdAt,
            expiresAt = order.expiresAt,
            completedAt = order.completedAt,
            disputeReason = order.disputeReason,
            disputedAt = order.disputedAt,
            resolvedAt = order.resolvedAt,
            resolvedByAdminId = order.resolvedByAdminId
        )
    }

    private suspend fun uploadAttachmentIfNeeded(
        uri: String?
    ): String? {
        if (uri.isNullOrBlank()) return null

        if (uri.startsWith("https://")) {
            return uri
        }

        val auth = backendAuth() ?: return null

        val source = android.net.Uri.parse(uri)

        val payload = AttachmentStorage.readImageBase64(
            appContext,
            source
        ) ?: return null

        val response = backendApi.uploadAttachment(
            auth,
            AttachmentUploadRequest(
                payload.first,
                payload.second
            )
        )

        if (!response.isSuccessful) {
            throw backendFailure(
                response.code(),
                "Could not upload image"
            )
        }

        return response.body()?.url
            ?: throw IllegalStateException(
                "Backend returned no attachment URL"
            )
    }

    private fun mapMessage(
        message: BackendMessage
    ): P2PChatMessageEntity =
        P2PChatMessageEntity(
            id = message.id,
            orderId = message.orderId,
            senderId =
                if (message.senderId == backendSession.accountId) {
                    backendSession.localUserId ?: message.senderId
                } else {
                    message.senderId
                },
            senderName = message.senderName,
            message = message.message,
            attachmentUri = message.attachmentUrl,
            createdAt = message.createdAt
        )

    suspend fun refreshP2PFromBackend(): Result<Unit> {
        val auth = backendAuth()
            ?: return Result.failure(
                SecurityException(
                    "Backend session is not connected"
                )
            )

        return runCatching {
            val ads = backendApi.ads(auth)

            if (!ads.isSuccessful) {
                throw backendFailure(
                    ads.code(),
                    "Could not load P2P advertisements"
                )
            }

            val serverAds =
                ads.body()?.ads.orEmpty().map(::mapAd)

            db.withTransaction {
                p2pDao.deactivateAllAds()

                serverAds.forEach {
                    p2pDao.insertAd(it)
                }
            }

            val orders = backendApi.orders(auth)

            if (!orders.isSuccessful) {
                throw backendFailure(
                    orders.code(),
                    "Could not load P2P orders"
                )
            }

            val serverOrders =
                orders.body()?.orders.orEmpty().map(::mapOrder)

            db.withTransaction {
                p2pDao.clearOrders()

                serverOrders.forEach {
                    p2pDao.insertOrder(it)
                }
            }
        }
    }

    // -------------------------------------------------------------
    // ADMIN PRICING SETTINGS
    // Price changes affect USD/ETB valuation only; they never mint REAL.
    // -------------------------------------------------------------

    suspend fun getRealCoinUsdPrice(): Double =
        appSettingsDao.get("REAL_COIN_USD_VALUE")?.value
            ?: REAL_COIN_USD_VALUE

    suspend fun getUsdToEtbRate(): Double =
        appSettingsDao.get("USD_TO_ETB")?.value
            ?: 186.0

    fun observeRealCoinUsdPrice(): Flow<Double> =
        appSettingsDao
            .observe("REAL_COIN_USD_VALUE")
            .map {
                it?.value ?: REAL_COIN_USD_VALUE
            }

    fun observeUsdToEtbRate(): Flow<Double> =
        appSettingsDao
            .observe("USD_TO_ETB")
            .map {
                it?.value ?: 186.0
            }

    suspend fun updatePricing(
        adminUserId: String,
        realCoinUsdPrice: Double,
        usdToEtbRate: Double
    ): Result<Unit> {
        if (
            !realCoinUsdPrice.isFinite() ||
            realCoinUsdPrice <= 0.0
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Enter a valid RC price"
                )
            )
        }

        val auth = backendAuth()
            ?: return Result.failure(
                SecurityException(
                    "Admin backend session is required"
                )
            )

        return runCatching {
            val response = backendApi.updatePricing(
                auth,
                PricingResponse(
                    true,
                    realCoinUsdPrice,
                    186.0
                )
            )

            if (!response.isSuccessful) {
                throw backendFailure(
                    response.code(),
                    "Could not update RC price"
                )
            }

            appSettingsDao.upsert(
                AppSettingEntity(
                    "REAL_COIN_USD_VALUE",
                    response.body()?.realCoinUsdPrice
                        ?: realCoinUsdPrice
                )
            )

            appSettingsDao.upsert(
                AppSettingEntity(
                    "USD_TO_ETB",
                    186.0
                )
            )
        }
    }

    suspend fun refreshPricingFromBackend(): Result<Unit> =
        runCatching {
            val response = backendApi.pricing()

            if (!response.isSuccessful) {
                throw backendFailure(
                    response.code(),
                    "Could not load RC price"
                )
            }

            val pricing = response.body()
                ?: throw IllegalStateException(
                    "No pricing data"
                )

            appSettingsDao.upsert(
                AppSettingEntity(
                    "REAL_COIN_USD_VALUE",
                    pricing.realCoinUsdPrice
                )
            )

            appSettingsDao.upsert(
                AppSettingEntity(
                    "USD_TO_ETB",
                    186.0
                )
            )
        }

    suspend fun getReferralSummary():
        Result<com.example.data.backend.ReferralResponse> {
        val auth = backendAuth()
            ?: return Result.failure(
                IllegalStateException(
                    "Please log in first"
                )
            )

        return runCatching {
            val response = backendApi.referral(auth)

            if (
                !response.isSuccessful ||
                response.body() == null
            ) {
                throw backendFailure(
                    response.code(),
                    "Unable to load referral information"
                )
            }

            response.body()!!
        }
    }

    // -------------------------------------------------------------
    // 1. AUTHENTICATION & REGISTRATION
    // -------------------------------------------------------------

    suspend fun register(
        username: String,
        email: String,
        password: String,
        referralCode: String? = null
    ): Result<UserEntity> {
        val cleanUsername = username.trim()

        val normalizedEmail =
            email.trim().lowercase(Locale.ROOT)

        val cleanReferralCode =
            referralCode
                ?.trim()
                ?.uppercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }

        if (cleanUsername.length < 3) {
            return Result.failure(
                IllegalArgumentException(
                    "Username must be at least 3 characters"
                )
            )
        }

        if (
            !normalizedEmail.contains("@") ||
            !normalizedEmail.contains(".")
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid email format"
                )
            )
        }

        if (password.length < 8) {
            return Result.failure(
                IllegalArgumentException(
                    "Password must be at least 8 characters"
                )
            )
        }

        return db.withTransaction {
            if (
                userDao.getUserByUsername(cleanUsername) != null
            ) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Username already taken"
                    )
                )
            }

            if (
                userDao.getUserByNormalizedEmail(
                    normalizedEmail
                ) != null
            ) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Email is already registered"
                    )
                )
            }

            val salt = PasswordHasher.generateSalt()

            val hash = PasswordHasher.hashPassword(
                password,
                salt
            )

            val userId =
                "RC_USER_${
                    UUID.randomUUID()
                        .toString()
                        .take(8)
                        .uppercase(Locale.ROOT)
                }"

            val newUser = UserEntity(
                id = userId,
                username = cleanUsername,
                normalizedEmail = normalizedEmail,
                passwordHash = hash,
                passwordSalt = salt,
                role = "USER",
                referralCode = cleanReferralCode,
                createdAt = System.currentTimeMillis()
            )

            userDao.insertUser(newUser)

            val initialWallet = WalletEntity(
                userId = userId,
                realBalance = 0.0,
                usdtBalance = 0.0,
                realLockedBalance = 0.0,
                usdtLockedBalance = 0.0,
                updatedAt = System.currentTimeMillis()
            )

            walletDao.insertWallet(initialWallet)

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

    /**
     * Removes the local records created for a brand-new registration
     * when backend account setup fails.
     *
     * This is intentionally separate from connectBackendSession()
     * so an existing local user is never deleted just because a
     * backend login/connection fails.
     */
    suspend fun rollbackLocalRegistration(
        userId: String
    ) {
        db.withTransaction {
            kycDao.deleteKycByUserId(userId)
            walletDao.deleteWalletByUserId(userId)
            userDao.deleteUserById(userId)
        }
    }

    suspend fun login(
        usernameOrEmail: String,
        password: String
    ): Result<UserEntity> {
        val query = usernameOrEmail.trim()

        val normalized =
            query.lowercase(Locale.ROOT)

        val user =
            userDao.getUserByNormalizedEmail(normalized)
                ?: userDao.getUserByUsername(query)
                ?: return Result.failure(
                    IllegalArgumentException(
                        "Account does not exist. Please register first."
                    )
                )

        val isPasswordValid =
            PasswordHasher.verifyPassword(
                password,
                user.passwordSalt,
                user.passwordHash
            )

        if (!isPasswordValid) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid password. Please check your credentials."
                )
            )
        }

        return Result.success(user)
    }

    suspend fun getUserById(
        userId: String
    ): UserEntity? {
        return userDao.getUserById(userId)
    }

    suspend fun resetPasswordByEmail(
        normalizedEmail: String,
        newPassword: String
    ): Result<Unit> {
        val email =
            normalizedEmail.trim().lowercase(Locale.ROOT)

        if (newPassword.length < 8) {
            return Result.failure(
                IllegalArgumentException(
                    "Password must be at least 8 characters"
                )
            )
        }

        val user =
            userDao.getUserByNormalizedEmail(email)
                ?: return Result.failure(
                    IllegalArgumentException(
                        "Account does not exist"
                    )
                )

        val salt = PasswordHasher.generateSalt()

        val hash =
            PasswordHasher.hashPassword(
                newPassword,
                salt
            )

        val updated =
            userDao.updatePassword(
                user.id,
                hash,
                salt
            )

        return if (updated == 1) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    "Password could not be updated"
                )
            )
        }
    }

    // -------------------------------------------------------------
    // 2. KYC IDENTITY VERIFICATION
    // -------------------------------------------------------------

    fun getKycForUser(
        userId: String
    ): Flow<KycEntity?> {
        return kycDao.getKycForUser(userId)
    }

    suspend fun getKycForUserSync(
        userId: String
    ): KycEntity? {
        return kycDao.getKycForUserSync(userId)
    }

    suspend fun submitKyc(
        userId: String,
        fullName: String,
        idNumber: String,
        documentAttached: Boolean,
        documentUri: String? = null
    ): Result<KycEntity> {
        val user =
            userDao.getUserById(userId)
                ?: return Result.failure(
                    IllegalStateException(
                        "User not found"
                    )
                )

        if (
            fullName.isBlank() ||
            idNumber.isBlank() ||
            !documentAttached
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "All fields and ID document attachment are required"
                )
            )
        }

        return db.withTransaction {
            val existingKyc =
                kycDao.getKycForUserSync(userId)

            if (existingKyc?.status == "VERIFIED") {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "KYC is already verified"
                    )
                )
            }

            if (existingKyc?.status == "PENDING") {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "KYC submission is already pending review"
                    )
                )
            }

            val updatedKyc = KycEntity(
                userId = userId,
                fullName = fullName.trim(),
                idNumber = idNumber.trim(),
                documentAttached = documentAttached,
                documentUri = documentUri,
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
            val adminUser =
                userDao.getUserById(adminUserId)

            if (
                adminUser == null ||
                adminUser.role != "ADMIN"
            ) {
                return@withTransaction Result.failure(
                    SecurityException(
                        "Unauthorized: Only an authorized admin can review KYC"
                    )
                )
            }

            if (adminUserId == targetUserId) {
                return@withTransaction Result.failure(
                    SecurityException(
                        "Security violation: Admin cannot review their own KYC"
                    )
                )
            }

            if (backendAuth().isNullOrBlank()) {
                return@withTransaction Result.failure(
                    SecurityException(
                        "Secure admin backend session is required"
                    )
                )
            }

            val targetKyc =
                kycDao.getKycForUserSync(targetUserId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Target KYC not found"
                        )
                    )

            val newStatus =
                if (approve) "VERIFIED" else "REJECTED"

            val updatedKyc =
                targetKyc.copy(
                    status = newStatus,
                    reviewedAt = System.currentTimeMillis(),
                    reviewedByAdminId = adminUserId,
                    rejectionReason =
                        if (!approve) {
                            reason
                                ?: "Document verification failed"
                        } else {
                            null
                        }
                )

            kycDao.insertOrUpdateKyc(updatedKyc)

            val auditLog = KycAuditLogEntity(
                id = UUID.randomUUID().toString(),
                userId = targetUserId,
                adminId = adminUserId,
                action =
                    if (approve) "APPROVED" else "REJECTED",
                notes =
                    reason
                        ?: if (approve) {
                            "Approved by compliance"
                        } else {
                            "Rejected"
                        },
                timestamp = System.currentTimeMillis()
            )

            kycDao.insertAuditLog(auditLog)

            Result.success(updatedKyc)
        }
    }

    // -------------------------------------------------------------
    // 3. WALLET & BALANCES
    // -------------------------------------------------------------

    fun getWallet(
        userId: String
    ): Flow<WalletEntity?> {
        return walletDao.getWallet(userId)
    }

    suspend fun getWalletSync(
        userId: String
    ): WalletEntity? {
        return walletDao.getWalletSync(userId)
    }

    fun getLedgerForUser(
        userId: String
    ): Flow<List<LedgerEntryEntity>> {
        return ledgerDao.getLedgerForUser(userId)
    }

    // -------------------------------------------------------------
    // 4. DEPOSITS (BEP-20)
    // -------------------------------------------------------------

    fun getDepositsForUser(
        userId: String
    ): Flow<List<DepositEntity>> {
        return depositDao.getDepositsForUser(userId)
    }

    suspend fun submitDeposit(
        userId: String,
        txHash: String,
        amountReal: Double
    ): Result<DepositEntity> {
        if (BuildConfig.USDT_BEP20_CONTRACT.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "USDT BEP-20 contract is not configured"
                )
            )
        }

        val cleanTxHash =
            txHash.trim().lowercase(Locale.ROOT)

        if (cleanTxHash.length < 10) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid transaction hash"
                )
            )
        }

        if (
            !amountReal.isFinite() ||
            amountReal <= 0.0
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Deposit amount must be a finite number greater than zero"
                )
            )
        }

        if (
            amountReal * getRealCoinUsdPrice() <
            MIN_DEPOSIT_USD
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Minimum deposit limit is $25.00 USD"
                )
            )
        }

        return db.withTransaction {
            val user =
                userDao.getUserById(userId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "User not found"
                        )
                    )

            val existing =
                depositDao.getDepositByTxHash(
                    cleanTxHash
                )

            if (existing != null) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Duplicate TxHash: This transaction hash has already been processed"
                    )
                )
            }

            val depositId =
                "DEP_${
                    UUID.randomUUID()
                        .toString()
                        .take(8)
                        .uppercase(Locale.ROOT)
                }"

            val deposit = DepositEntity(
                id = depositId,
                userId = userId,
                txHash = cleanTxHash,
                amountReal = amountReal,
                depositUsd =
                    amountReal * getRealCoinUsdPrice(),
                bonusReal =
                    amountReal * RewardPolicy.BONUS_RATE,
                recipientAddress =
                    BEP20_DEPOSIT_ADDRESS,
                network = "BEP20",
                tokenContract =
                    BuildConfig.USDT_BEP20_CONTRACT,
                confirmations = 0,
                status = "PENDING",
                createdAt =
                    System.currentTimeMillis(),
                confirmedAt = null
            )

            depositDao.insertDeposit(deposit)

            Result.success(deposit)
        }
    }

    // -------------------------------------------------------------
    // 5. WITHDRAWALS
    // -------------------------------------------------------------

    fun getWithdrawalsForUser(
        userId: String
    ): Flow<List<WithdrawalEntity>> {
        return withdrawalDao.getWithdrawalsForUser(userId)
    }

    fun getPendingKyc(): Flow<List<KycEntity>> =
        kycDao.getPendingKyc()

    suspend fun pendingKycCount(): Int =
        kycDao.countPending()

    suspend fun verifiedKycCount(): Int =
        kycDao.countVerified()

    suspend fun processDeposit(
        adminUserId: String,
        depositId: String,
        approve: Boolean,
        reason: String? = null
    ): Result<DepositEntity> =
        db.withTransaction {
            val admin =
                userDao.getUserById(adminUserId)

            if (admin?.role != "ADMIN") {
                return@withTransaction Result.failure(
                    SecurityException(
                        "Admin access required"
                    )
                )
            }

            val deposit =
                depositDao.getDepositById(depositId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Deposit not found"
                        )
                    )

            if (deposit.status != "PENDING") {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Deposit is already processed"
                    )
                )
            }

            if (!approve) {
                val rejected =
                    deposit.copy(
                        status = "REJECTED"
                    )

                depositDao.updateDeposit(rejected)

                return@withTransaction Result.success(
                    rejected
                )
            }

            val wallet =
                walletDao.getWalletSync(deposit.userId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Wallet not found"
                        )
                    )

            val updated =
                deposit.copy(
                    status = "CONFIRMED",
                    confirmations =
                        maxOf(deposit.confirmations, 1),
                    confirmedAt =
                        System.currentTimeMillis()
                )

            depositDao.updateDeposit(updated)

            val after =
                wallet.realBalance + deposit.amountReal

            val bonusAfter =
                wallet.bonusRealBalance +
                    deposit.bonusReal

            walletDao.updateWallet(
                wallet.copy(
                    realBalance = after,
                    bonusRealBalance = bonusAfter,
                    updatedAt =
                        System.currentTimeMillis()
                )
            )

            ledgerDao.insertLedgerEntry(
                LedgerEntryEntity(
                    UUID.randomUUID().toString(),
                    deposit.userId,
                    "DEPOSIT",
                    "REAL",
                    deposit.amountReal,
                    wallet.realBalance,
                    after,
                    depositId,
                    reason
                        ?: "Deposit confirmed by admin",
                    System.currentTimeMillis()
                )
            )

            if (deposit.bonusReal > 0.0) {
                ledgerDao.insertLedgerEntry(
                    LedgerEntryEntity(
                        UUID.randomUUID().toString(),
                        deposit.userId,
                        "DEPOSIT_BONUS_LOCKED",
                        "REAL",
                        deposit.bonusReal,
                        bonusAfter -
                            deposit.bonusReal,
                        bonusAfter,
                        depositId,
                        "10% promotional bonus; not withdrawable or sellable",
                        System.currentTimeMillis()
                    )
                )
            }

            Result.success(updated)
        }

    fun getAllDeposits(): Flow<List<DepositEntity>> =
        depositDao.getAllDeposits()

    fun getAllWithdrawals():
        Flow<List<WithdrawalEntity>> =
        withdrawalDao.getAllWithdrawals()

    fun getDisputedP2POrders():
        Flow<List<P2POrderEntity>> =
        p2pDao.getDisputedOrders()

    fun getPendingHelpRequests():
        Flow<List<HelpRequestEntity>> =
        helpRequestDao.getPending()

    fun getHelpRequestsForUser(
        userId: String
    ): Flow<List<HelpRequestEntity>> =
        helpRequestDao.getForUser(userId)

    suspend fun submitHelpRequest(
        userId: String,
        category: String,
        message: String
    ): Result<HelpRequestEntity> {
        val clean = message.trim()

        if (
            clean.length < 5 ||
            clean.length > 2000
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Help message must be 5-2000 characters"
                )
            )
        }

        if (category.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "Select a help category"
                )
            )
        }

        val user =
            userDao.getUserById(userId)
                ?: return Result.failure(
                    IllegalStateException(
                        "User not found"
                    )
                )

        val request =
            HelpRequestEntity(
                "HELP_${
                    UUID.randomUUID()
                        .toString()
                        .take(8)
                        .uppercase(Locale.ROOT)
                }",
                user.id,
                category.trim(),
                clean
            )

        helpRequestDao.insert(request)

        return Result.success(request)
    }

    suspend fun resolveP2PDispute(
        adminUserId: String,
        orderId: String,
        releaseToBuyer: Boolean
    ): Result<P2POrderEntity> =
        db.withTransaction {
            val admin =
                userDao.getUserById(adminUserId)

            if (admin?.role != "ADMIN") {
                return@withTransaction Result.failure(
                    SecurityException(
                        "Admin access required"
                    )
                )
            }

            val order =
                p2pDao.getOrderById(orderId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Order not found"
                        )
                    )

            if (order.status != "DISPUTED") {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Order is not disputed"
                    )
                )
            }

            if (releaseToBuyer) {
                val sellerWallet =
                    walletDao.getWalletSync(
                        order.sellerId
                    )
                        ?: return@withTransaction Result.failure(
                            IllegalStateException(
                                "Seller wallet not found"
                            )
                        )

                val buyerWallet =
                    walletDao.getWalletSync(
                        order.buyerId
                    )
                        ?: return@withTransaction Result.failure(
                            IllegalStateException(
                                "Buyer wallet not found"
                            )
                        )

                if (
                    sellerWallet.realLockedBalance + 1e-9 <
                    order.cryptoAmount
                ) {
                    return@withTransaction Result.failure(
                        IllegalStateException(
                            "Escrow balance is inconsistent"
                        )
                    )
                }

                walletDao.updateWallet(
                    sellerWallet.copy(
                        realBalance =
                            sellerWallet.realBalance -
                                order.cryptoAmount,
                        realLockedBalance =
                            sellerWallet.realLockedBalance -
                                order.cryptoAmount,
                        updatedAt =
                            System.currentTimeMillis()
                    )
                )

                walletDao.updateWallet(
                    buyerWallet.copy(
                        realBalance =
                            buyerWallet.realBalance +
                                order.cryptoAmount,
                        updatedAt =
                            System.currentTimeMillis()
                    )
                )

                ledgerDao.insertLedgerEntry(
                    LedgerEntryEntity(
                        UUID.randomUUID().toString(),
                        order.sellerId,
                        "P2P_ESCROW_RELEASE",
                        "REAL",
                        -order.cryptoAmount,
                        sellerWallet.realBalance,
                        sellerWallet.realBalance -
                            order.cryptoAmount,
                        orderId,
                        "Dispute resolved: escrow released to buyer",
                        System.currentTimeMillis()
                    )
                )

                ledgerDao.insertLedgerEntry(
                    LedgerEntryEntity(
                        UUID.randomUUID().toString(),
                        order.buyerId,
                        "P2P_ESCROW_RELEASE",
                        "REAL",
                        order.cryptoAmount,
                        buyerWallet.realBalance,
                        buyerWallet.realBalance +
                            order.cryptoAmount,
                        orderId,
                        "Dispute resolved: escrow released to buyer",
                        System.currentTimeMillis()
                    )
                )

                val done =
                    order.copy(
                        status = "COMPLETED",
                        resolvedAt =
                            System.currentTimeMillis(),
                        resolvedByAdminId =
                            adminUserId
                    )

                p2pDao.updateOrder(done)

                Result.success(done)
            } else {
                val sellerWallet =
                    walletDao.getWalletSync(
                        order.sellerId
                    )
                        ?: return@withTransaction Result.failure(
                            IllegalStateException(
                                "Seller wallet not found"
                            )
                        )

                if (
                    sellerWallet.realLockedBalance + 1e-9 <
                    order.cryptoAmount
                ) {
                    return@withTransaction Result.failure(
                        IllegalStateException(
                            "Escrow balance is inconsistent"
                        )
                    )
                }

                walletDao.updateWallet(
                    sellerWallet.copy(
                        realLockedBalance =
                            sellerWallet.realLockedBalance -
                                order.cryptoAmount,
                        updatedAt =
                            System.currentTimeMillis()
                    )
                )

                ledgerDao.insertLedgerEntry(
                    LedgerEntryEntity(
                        UUID.randomUUID().toString(),
                        order.sellerId,
                        "P2P_ESCROW_REFUND",
                        "REAL",
                        order.cryptoAmount,
                        sellerWallet.realBalance,
                        sellerWallet.realBalance,
                        orderId,
                        "Dispute resolved: escrow returned to seller",
                        System.currentTimeMillis()
                    )
                )

                val done =
                    order.copy(
                        status = "CANCELLED",
                        resolvedAt =
                            System.currentTimeMillis(),
                        resolvedByAdminId =
                            adminUserId
                    )

                p2pDao.updateOrder(done)

                Result.success(done)
            }
        }

    suspend fun resolveHelpRequest(
        adminUserId: String,
        requestId: String
    ): Result<HelpRequestEntity> =
        db.withTransaction {
            val admin =
                userDao.getUserById(adminUserId)

            if (admin?.role != "ADMIN") {
                return@withTransaction Result.failure(
                    SecurityException(
                        "Admin access required"
                    )
                )
            }

            val request =
                helpRequestDao.getById(requestId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Help request not found"
                        )
                    )

            if (request.status != "PENDING") {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Help request is already resolved"
                    )
                )
            }

            val updated =
                request.copy(
                    status = "RESOLVED",
                    resolvedAt =
                        System.currentTimeMillis(),
                    resolvedByAdminId =
                        adminUserId
                )

            helpRequestDao.update(updated)

            Result.success(updated)
        }

    suspend fun openP2PDispute(
        userId: String,
        orderId: String,
        reason: String
    ): Result<P2POrderEntity> =
        db.withTransaction {
            val order =
                p2pDao.getOrderById(orderId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Order not found"
                        )
                    )

            if (
                userId != order.sellerId &&
                userId != order.buyerId
            ) {
                return@withTransaction Result.failure(
                    SecurityException(
                        "You are not part of this trade"
                    )
                )
            }

            if (
                order.status != "ESCROW_LOCKED" &&
                order.status != "PAID"
            ) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "This trade cannot be disputed now"
                    )
                )
            }

            val clean = reason.trim()

            if (clean.length < 5) {
                return@withTransaction Result.failure(
                    IllegalArgumentException(
                        "Please provide a dispute reason"
                    )
                )
            }

            val updated =
                order.copy(
                    status = "DISPUTED",
                    disputeReason = clean,
                    disputedAt =
                        System.currentTimeMillis()
                )

            p2pDao.updateOrder(updated)

            Result.success(updated)
        }

    suspend fun submitWithdrawal(
        userId: String,
        bep20Address: String,
        amountReal: Double
    ): Result<WithdrawalEntity> {
        val cleanAddress =
            bep20Address.trim().lowercase(Locale.ROOT)

        if (
            !cleanAddress.matches(
                Regex("^0x[a-f0-9]{40}$")
            )
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid BEP-20 recipient address"
                )
            )
        }

        val usdValue =
            amountReal * getRealCoinUsdPrice()

        if (
            !amountReal.isFinite() ||
            amountReal <= 0.0 ||
            !usdValue.isFinite()
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid withdrawal amount"
                )
            )
        }

        if (usdValue < MIN_WITHDRAWAL_USD) {
            return Result.failure(
                IllegalArgumentException(
                    "Minimum withdrawal limit is $50.00 USD"
                )
            )
        }

        return db.withTransaction {
            val wallet =
                walletDao.getWalletSync(userId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Wallet not found"
                        )
                    )

            if (
                amountReal > wallet.availableRealBalance ||
                amountReal >
                wallet.realBalance -
                wallet.realLockedBalance
            ) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Insufficient available RealCoin balance"
                    )
                )
            }

            val updatedWallet =
                wallet.copy(
                    realLockedBalance =
                        wallet.realLockedBalance +
                            amountReal,
                    updatedAt =
                        System.currentTimeMillis()
                )

            walletDao.updateWallet(updatedWallet)

            val withdrawalId =
                "WTH_${
                    UUID.randomUUID()
                        .toString()
                        .take(8)
                        .uppercase(Locale.ROOT)
                }"

            val withdrawal =
                WithdrawalEntity(
                    id = withdrawalId,
                    userId = userId,
                    recipientAddress = cleanAddress,
                    amountReal = amountReal,
                    usdValue = usdValue,
                    network = "BEP20",
                    status = "PENDING",
                    createdAt =
                        System.currentTimeMillis()
                )

            withdrawalDao.insertWithdrawal(
                withdrawal
            )

            val ledgerEntry =
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = "WITHDRAWAL_LOCK",
                    currency = "REAL",
                    amount = -amountReal,
                    balanceBefore =
                        wallet.realBalance,
                    balanceAfter =
                        wallet.realBalance,
                    referenceId =
                        withdrawalId,
                    notes =
                        "Withdrawal request locked: $amountReal RC to $cleanAddress",
                    timestamp =
                        System.currentTimeMillis()
                )

            ledgerDao.insertLedgerEntry(
                ledgerEntry
            )

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
            val adminUser =
                userDao.getUserById(adminUserId)

            if (
                adminUser == null ||
                adminUser.role != "ADMIN"
            ) {
                return@withTransaction Result.failure(
                    SecurityException(
                        "Unauthorized: Admin access required"
                    )
                )
            }

            val withdrawal =
                withdrawalDao.getWithdrawalById(
                    withdrawalId
                )
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Withdrawal not found"
                        )
                    )

            if (withdrawal.status != "PENDING") {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Withdrawal is already processed (${withdrawal.status})"
                    )
                )
            }

            if (
                withdrawal.amountReal <= 0.0 ||
                !withdrawal.amountReal.isFinite()
            ) {
                return@withTransaction Result.failure(
                    IllegalArgumentException(
                        "Invalid withdrawal amount"
                    )
                )
            }

            val wallet =
                walletDao.getWalletSync(
                    withdrawal.userId
                )
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Wallet not found"
                        )
                    )

            val updatedWithdrawal: WithdrawalEntity

            if (approve) {
                if (
                    wallet.realLockedBalance <
                    withdrawal.amountReal ||
                    wallet.realBalance <
                    withdrawal.amountReal
                ) {
                    return@withTransaction Result.failure(
                        IllegalStateException(
                            "Insufficient locked funds"
                        )
                    )
                }

                val balanceBefore =
                    wallet.realBalance

                val balanceAfter =
                    balanceBefore -
                        withdrawal.amountReal

                val newLocked =
                    (
                        wallet.realLockedBalance -
                            withdrawal.amountReal
                    ).coerceAtLeast(0.0)

                walletDao.updateWallet(
                    wallet.copy(
                        realBalance =
                            balanceAfter,
                        realLockedBalance =
                            newLocked,
                        updatedAt =
                            System.currentTimeMillis()
                    )
                )

                updatedWithdrawal =
                    withdrawal.copy(
                        status = "CONFIRMED",
                        processedAt =
                            System.currentTimeMillis()
                    )

                withdrawalDao.updateWithdrawal(
                    updatedWithdrawal
                )

                ledgerDao.insertLedgerEntry(
                    LedgerEntryEntity(
                        id =
                            UUID.randomUUID().toString(),
                        userId =
                            withdrawal.userId,
                        type =
                            "WITHDRAWAL_CONFIRMED",
                        currency =
                            "REAL",
                        amount =
                            -withdrawal.amountReal,
                        balanceBefore =
                            balanceBefore,
                        balanceAfter =
                            balanceAfter,
                        referenceId =
                            withdrawalId,
                        notes =
                            "Withdrawal confirmed and deducted from locked balance to ${withdrawal.recipientAddress}",
                        timestamp =
                            System.currentTimeMillis()
                    )
                )
            } else {
                val newLocked =
                    (
                        wallet.realLockedBalance -
                            withdrawal.amountReal
                    ).coerceAtLeast(0.0)

                walletDao.updateWallet(
                    wallet.copy(
                        realLockedBalance =
                            newLocked,
                        updatedAt =
                            System.currentTimeMillis()
                    )
                )

                updatedWithdrawal =
                    withdrawal.copy(
                        status = "REJECTED",
                        processedAt =
                            System.currentTimeMillis(),
                        rejectionReason =
                            reason
                                ?: "Compliance review failed"
                    )

                withdrawalDao.updateWithdrawal(
                    updatedWithdrawal
                )

                ledgerDao.insertLedgerEntry(
                    LedgerEntryEntity(
                        id =
                            UUID.randomUUID().toString(),
                        userId =
                            withdrawal.userId,
                        type =
                            "WITHDRAWAL_REFUND",
                        currency =
                            "REAL",
                        amount =
                            withdrawal.amountReal,
                        balanceBefore =
                            wallet.realBalance,
                        balanceAfter =
                            wallet.realBalance,
                        referenceId =
                            withdrawalId,
                        notes =
                            "Withdrawal rejected and refunded: ${
                                reason
                                    ?: "Compliance rejection"
                            }",
                        timestamp =
                            System.currentTimeMillis()
                    )
                )
            }

            Result.success(updatedWithdrawal)
        }
    }

    // -------------------------------------------------------------
    // 6. REWARDS / SPIN WHEEL
    // -------------------------------------------------------------

    companion object {
        const val SPIN_COOLDOWN_MS =
            24L * 60L * 60L * 1000L

        const val PAID_SPIN_COST_RC = 10.0
    }

    data class SpinResult(
        val rewardRc: Double,
        val isFreeSpin: Boolean,
        val nextFreeSpinAt: Long
    )

    private fun getCurrentPeriodKey(): String {
        val sdf =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            )

        sdf.timeZone =
            TimeZone.getTimeZone("UTC")

        return sdf.format(Date())
    }

    suspend fun getSpinState(
        userId: String
    ): Pair<Long?, Long> {
        val last =
            spinStateDao.get(userId)
                ?.lastFreeSpinAt

        val next =
            if (last == null) {
                0L
            } else {
                last + SPIN_COOLDOWN_MS
            }

        return last to next
    }

    suspend fun canClaimRewardToday(
        userId: String
    ): Boolean {
        val (_, nextFreeSpinAt) =
            getSpinState(userId)

        return System.currentTimeMillis() >=
            nextFreeSpinAt
    }

    suspend fun getRewardStatus(
        userId: String
    ): RewardStatus? {
        val user =
            userDao.getUserById(userId)
                ?: return null

        val totalDepositUsd =
            depositDao.getConfirmedDepositTotal(
                userId
            )

        val level =
            RewardPolicy.levelFor(
                totalDepositUsd
            )

        val (next, progress) =
            RewardPolicy.progress(
                totalDepositUsd
            )

        val now =
            System.currentTimeMillis()

        val days =
            (
                now - user.createdAt
            ).coerceAtLeast(0L) /
                (24L * 60L * 60L * 1000L)

        val (sellOk, buyOk) =
            RewardPolicy.qualifyingActivity(
                userId,
                p2pDao.getOrdersForUserSync(userId),
                now,
                user.createdAt
            )

        val activityRequired =
            days >=
                RewardPolicy.ACTIVITY_WINDOW_DAYS

        val active =
            !activityRequired ||
                (sellOk && buyOk)

        return RewardStatus(
            level,
            totalDepositUsd,
            next,
            progress,
            buyOk,
            sellOk,
            !activityRequired ||
                (sellOk && buyOk),
            active,
            days
        )
    }

    private fun randomSpinRewardRc(): Double {
        val roll =
            kotlin.random.Random.nextDouble()

        return when {
            roll < 0.40 -> 0.0
            roll < 0.70 -> 5.0
            roll < 0.80 -> 10.0
            roll < 0.95 -> 20.0
            else -> 50.0
        }
    }

    suspend fun spinWheel(
        userId: String
    ): Result<SpinResult> {
        val now =
            System.currentTimeMillis()

        return db.withTransaction {
            val user =
                userDao.getUserById(userId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "User not found"
                        )
                    )

            val state =
                spinStateDao.get(userId)

            val freeAvailable =
                state?.lastFreeSpinAt == null ||
                    now >=
                    state.lastFreeSpinAt +
                    SPIN_COOLDOWN_MS

            if (freeAvailable) {
                val rewardStatus =
                    getRewardStatus(userId)

                if (rewardStatus?.rewardActive == false) {
                    return@withTransaction Result.failure(
                        IllegalStateException(
                            "Daily reward paused. Complete 1 P2P BUY and 1 P2P SELL with different people within the last 7 days."
                        )
                    )
                }
            }

            val wallet =
                walletDao.getWalletSync(userId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Wallet not found"
                        )
                    )

            if (
                !freeAvailable &&
                wallet.availableRealBalance <
                PAID_SPIN_COST_RC
            ) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Paid spin costs 10 RC. Not enough available RealCoin."
                    )
                )
            }

            val reward =
                randomSpinRewardRc()

            val before =
                wallet.realBalance

            val cost =
                if (freeAvailable) {
                    0.0
                } else {
                    PAID_SPIN_COST_RC
                }

            val afterCost =
                before - cost

            val after =
                afterCost + reward

            if (
                afterCost <
                wallet.realLockedBalance
            ) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Not enough available RealCoin for this spin."
                    )
                )
            }

            if (freeAvailable) {
                spinStateDao.upsert(
                    SpinStateEntity(
                        userId,
                        now
                    )
                )
            }

            walletDao.updateWallet(
                wallet.copy(
                    realBalance = after,
                    updatedAt = now
                )
            )

            if (cost > 0.0) {
                ledgerDao.insertLedgerEntry(
                    LedgerEntryEntity(
                        UUID.randomUUID().toString(),
                        userId,
                        "SPIN_PAID",
                        "REAL",
                        -cost,
                        before,
                        afterCost,
                        "SPIN_${
                            UUID.randomUUID()
                                .toString()
                                .take(8)
                        }",
                        "Paid spin",
                        now
                    )
                )
            }

            ledgerDao.insertLedgerEntry(
                LedgerEntryEntity(
                    UUID.randomUUID().toString(),
                    userId,
                    "SPIN_REWARD",
                    "REAL",
                    reward,
                    afterCost,
                    after,
                    "SPIN_${
                        UUID.randomUUID()
                            .toString()
                            .take(8)
                    }",
                    if (freeAvailable) {
                        "Free daily spin reward"
                    } else {
                        "Paid spin reward"
                    },
                    now
                )
            )

            Result.success(
                SpinResult(
                    reward,
                    freeAvailable,
                    if (freeAvailable) {
                        now + SPIN_COOLDOWN_MS
                    } else {
                        (
                            state?.lastFreeSpinAt
                                ?: 0L
                        ) + SPIN_COOLDOWN_MS
                    }
                )
            )
        }
    }

    suspend fun claimDailyReward(
        userId: String,
        ignoredRewardAmount: Double = 0.0
    ): Result<Double> {
        val now = System.currentTimeMillis()
        val periodKey = getCurrentPeriodKey()

        return db.withTransaction {
            val user =
                userDao.getUserById(userId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "User not found"
                        )
                    )

            val existingClaim =
                rewardDao.getClaimForPeriod(
                    userId,
                    periodKey
                )

            if (existingClaim != null) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Daily reward already claimed"
                    )
                )
            }

            val rewardStatus =
                getRewardStatus(userId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Unable to determine reward level"
                        )
                    )

            if (!rewardStatus.rewardActive) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Daily reward is currently paused"
                    )
                )
            }

            val reward =
                rewardStatus.level.dailyRewardReal

            if (!reward.isFinite() || reward <= 0.0) {
                return@withTransaction Result.failure(
                    IllegalStateException(
                        "Invalid daily reward amount"
                    )
                )
            }

            val wallet =
                walletDao.getWalletSync(userId)
                    ?: return@withTransaction Result.failure(
                        IllegalStateException(
                            "Wallet not found"
                        )
                    )

            val before = wallet.realBalance
            val after = before + reward

            val claim =
                RewardClaimEntity(
                    id =
                        "REWARD_${
                            UUID.randomUUID()
                                .toString()
                                .take(8)
                                .uppercase(Locale.ROOT)
                        }",
                    userId = userId,
                    amountReal = reward,
                    claimTimestamp = now,
                    periodKey = periodKey
                )

            rewardDao.insertRewardClaim(claim)

            walletDao.updateWallet(
                wallet.copy(
                    realBalance = after,
                    updatedAt = now
                )
            )

            ledgerDao.insertLedgerEntry(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = "REWARD_CLAIM",
                    currency = "REAL",
                    amount = reward,
                    balanceBefore = before,
                    balanceAfter = after,
                    referenceId = claim.id,
                    notes = "Daily reward claimed",
                    timestamp = now
                )
            )

            Result.success(reward)
        }
    }

    // -------------------------------------------------------------
    // SAVED PAYMENT ACCOUNTS
    // -------------------------------------------------------------

    fun getSavedPaymentAccounts(
        userId: String
    ): Flow<List<PaymentAccountEntity>> =
        paymentAccountDao.getForUser(userId)

    suspend fun savePaymentAccount(
        userId: String,
        paymentName: String,
        paymentMethod: String,
        accountNumber: String
    ): Result<PaymentAccountEntity> {
        val method =
            paymentMethod.trim()

        val number =
            accountNumber.trim()

        if (
            method !in
            com.example.data.BankOptions.all
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Select a supported bank or payment method"
                )
            )
        }

        if (
            method.isBlank() ||
            number.isBlank()
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Payment method and account number are required"
                )
            )
        }

        if (
            userDao.getUserById(userId) == null
        ) {
            return Result.failure(
                IllegalStateException(
                    "User not found"
                )
            )
        }

        if (
            paymentAccountDao.countForUser(userId) >= 5
        ) {
            return Result.failure(
                IllegalStateException(
                    "You can save up to 5 payment accounts"
                )
            )
        }

        val kyc =
            kycDao.getKycForUserSync(userId)
                ?: return Result.failure(
                    IllegalStateException(
                        "Complete KYC before saving a payment account"
                    )
                )

        if (
            kyc.status != "VERIFIED" ||
            kyc.fullName.isBlank()
        ) {
            return Result.failure(
                IllegalStateException(
                    "Payment account name must come from approved KYC"
                )
            )
        }

        val account =
            PaymentAccountEntity(
                UUID.randomUUID().toString(),
                userId,
                kyc.fullName.trim(),
                method,
                number
            )

        paymentAccountDao.insert(account)

        return Result.success(account)
    }

    suspend fun deleteSavedPaymentAccount(
        userId: String,
        accountId: String
    ): Result<Unit> {
        val deleted =
            paymentAccountDao.deleteOwned(
                accountId,
                userId
            )

        return if (deleted == 1) {
            Result.success(Unit)
        } else {
            Result.failure(
                SecurityException(
                    "Payment account not found or not owned by user"
                )
            )
        }
    }

    // -------------------------------------------------------------
    // 7. P2P TRADING & ESCROW
    // -------------------------------------------------------------

    fun getActiveP2PAds():
        Flow<List<P2PAdEntity>> =
        kotlinx.coroutines.flow.flow {
            refreshP2PFromBackend()
            emitAll(
                p2pDao.getActiveAds()
            )
        }

    fun getActiveP2PAdsForUser(
        userId: String
    ): Flow<List<P2PAdEntity>> =
        kotlinx.coroutines.flow.flow {
            refreshP2PFromBackend()
            emitAll(
                p2pDao.getActiveAdsForUser(
                    userId
                )
            )
        }

    fun getP2POrdersForUser(
        userId: String
    ): Flow<List<P2POrderEntity>> =
        kotlinx.coroutines.flow.flow {
            refreshP2PFromBackend()
            emitAll(
                p2pDao.getOrdersForUser(
                    userId
                )
            )
        }

    suspend fun deleteOwnP2PAd(
        userId: String,
        adId: String
    ): Result<Unit> {
        backendAuth()?.let { auth ->
            return runCatching {
                val response =
                    backendApi.deleteAd(
                        auth,
                        adId
                    )

                if (!response.isSuccessful) {
                    throw backendFailure(
                        response.code(),
                        "Could not delete advertisement"
                    )
                }

                p2pDao.getAdById(adId)?.let {
                    p2pDao.updateAd(
                        it.copy(
                            isActive = false
                        )
                    )
                }
            }
        }

        return Result.failure(
            SecurityException(
                "P2P backend session is required for this action"
            )
        )
    }

    suspend fun createP2PSellAd(
        sellerId: String,
        amountReal: Double,
        fiatPriceEtb: Double,
        paymentMethod: String,
        minOrderReal: Double = 1.0,
        maxOrderReal: Double = amountReal
    ): Result<P2PAdEntity> {
        backendAuth()?.let { auth ->
            if (
                !amountReal.isFinite() ||
                !fiatPriceEtb.isFinite() ||
                amountReal <= 0.0 ||
                fiatPriceEtb <= 0.0 ||
                !minOrderReal.isFinite() ||
                !maxOrderReal.isFinite() ||
                minOrderReal <= 0.0 ||
                maxOrderReal < minOrderReal
            ) {
                return Result.failure(
                    IllegalArgumentException(
                        "Amount, price, and order limits are invalid"
                    )
                )
            }

            return runCatching {
                val response =
                    backendApi.createAd(
                        auth,
                        AdRequest(
                            "SELL",
                            amountReal,
                            fiatPriceEtb,
                            minOrderReal,
                            maxOrderReal,
                            paymentMethod,
                            "",
                            ""
                        )
                    )

                if (!response.isSuccessful) {
                    if (response.code() == 409) {
                        throw IllegalStateException(
                            "Insufficient available REAL balance for this sell advertisement"
                        )
                    }
                    throw backendFailure(
                        response.code(),
                        "Could not create sell advertisement"
                    )
                }

                val ad =
                    response.body()?.ad
                        ?: throw IllegalStateException(
                            "Backend returned no advertisement"
                        )

                mapAd(ad).also {
                    p2pDao.insertAd(it)
                }
            }
        }

        if (
            !amountReal.isFinite() ||
            !fiatPriceEtb.isFinite() ||
            amountReal <= 0.0 ||
            fiatPriceEtb <= 0.0
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Amount and price must be greater than zero"
                )
            )
        }

        if (paymentMethod.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "Select a payment method before creating the advertisement"
                )
            )
        }

        if (
            !minOrderReal.isFinite() ||
            !maxOrderReal.isFinite() ||
            minOrderReal <= 0.0 ||
            maxOrderReal < minOrderReal ||
            maxOrderReal <= 0.0
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Minimum/maximum order limits are invalid"
                )
            )
        }

        return Result.failure(
            SecurityException(
                "P2P backend session is required for this action"
            )
        )
    }

    suspend fun createP2PSellAdUsingPaymentAccount(
        sellerId: String,
        amountReal: Double,
        fiatPriceEtb: Double,
        accountId: String,
        minOrderReal: Double = 1.0,
        maxOrderReal: Double = amountReal
    ): Result<P2PAdEntity> {
        backendAuth()?.let { auth ->
            return runCatching {
                val account =
                    paymentAccountDao.getOwned(
                        accountId,
                        sellerId
                    )
                        ?: throw SecurityException(
                            "Payment account not found or not owned by seller"
                        )

                val response =
                    backendApi.createAd(
                        auth,
                        AdRequest(
                            "SELL",
                            amountReal,
                            fiatPriceEtb,
                            minOrderReal,
                            maxOrderReal,
                            account.paymentMethod,
                            account.paymentName,
                            account.accountNumber
                        )
                    )

                if (!response.isSuccessful) {
                    if (response.code() == 409) {
                        throw IllegalStateException(
                            "Insufficient available REAL balance for this sell advertisement"
                        )
                    }
                    throw backendFailure(
                        response.code(),
                        "Could not create sell advertisement"
                    )
                }

                val ad =
                    response.body()?.ad
                        ?: throw IllegalStateException(
                            "Backend returned no advertisement"
                        )

                mapAd(ad).also {
                    p2pDao.insertAd(it)
                }
            }
        }

        val account =
            paymentAccountDao.getOwned(
                accountId,
                sellerId
            )
                ?: return Result.failure(
                    SecurityException(
                        "Payment account not found or not owned by seller"
                    )
                )

        return createP2PSellAdWithDetails(
            sellerId,
            amountReal,
            fiatPriceEtb,
            account.paymentMethod,
            account.paymentName,
            account.accountNumber,
            minOrderReal,
            maxOrderReal
        )
    }

    private suspend fun createP2PSellAdWithDetails(
        sellerId: String,
        amountReal: Double,
        fiatPriceEtb: Double,
        paymentMethod: String,
        paymentName: String,
        accountNumber: String,
        minOrderReal: Double = 1.0,
        maxOrderReal: Double = amountReal
    ): Result<P2PAdEntity> {
        val result =
            createP2PSellAd(
                sellerId,
                amountReal,
                fiatPriceEtb,
                paymentMethod,
                minOrderReal,
                maxOrderReal
            )

        return result.map {
            it.copy(
                paymentName = paymentName,
                accountNumber = accountNumber
            ).also { updated ->
                p2pDao.updateAd(updated)
            }
        }
    }

    suspend fun createP2PBuyAd(
        buyerId: String,
        amountReal: Double,
        fiatPriceEtb: Double,
        paymentMethod: String,
        minOrderReal: Double = 1.0,
        maxOrderReal: Double = amountReal
    ): Result<P2PAdEntity> {
        backendAuth()?.let { auth ->
            if (
                !amountReal.isFinite() ||
                !fiatPriceEtb.isFinite() ||
                amountReal <= 0.0 ||
                fiatPriceEtb <= 0.0 ||
                !minOrderReal.isFinite() ||
                !maxOrderReal.isFinite() ||
                minOrderReal <= 0.0 ||
                maxOrderReal < minOrderReal
            ) {
                return Result.failure(
                    IllegalArgumentException(
                        "Amount, price, and order limits are invalid"
                    )
                )
            }

            return runCatching {
                val response =
                    backendApi.createAd(
                        auth,
                        AdRequest(
                            "BUY",
                            amountReal,
                            fiatPriceEtb,
                            minOrderReal,
                            maxOrderReal,
                            paymentMethod,
                            "",
                            ""
                        )
                    )

                if (!response.isSuccessful) {
                    throw backendFailure(
                        response.code(),
                        "Could not create buy advertisement"
                    )
                }

                val ad =
                    response.body()?.ad
                        ?: throw IllegalStateException(
                            "Backend returned no advertisement"
                        )

                mapAd(ad).also {
                    p2pDao.insertAd(it)
                }
            }
        }

        if (
            !amountReal.isFinite() ||
            !fiatPriceEtb.isFinite() ||
            amountReal <= 0.0 ||
            fiatPriceEtb <= 0.0
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Amount and price must be greater than zero"
                )
            )
        }

        if (
            !minOrderReal.isFinite() ||
            !maxOrderReal.isFinite() ||
            minOrderReal <= 0.0 ||
            maxOrderReal < minOrderReal ||
            maxOrderReal <= 0.0
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Minimum/maximum order limits are invalid"
                )
            )
        }

        return Result.failure(
            SecurityException(
                "P2P backend session is required for this action"
            )
        )
    }

    suspend fun createP2PBuyAdUsingPaymentAccount(
        buyerId: String,
        amountReal: Double,
        fiatPriceEtb: Double,
        accountId: String,
        minOrderReal: Double = 1.0,
        maxOrderReal: Double = amountReal
    ): Result<P2PAdEntity> {
        val account =
            paymentAccountDao.getOwned(
                accountId,
                buyerId
            )
                ?: return Result.failure(
                    SecurityException(
                        "Payment account not found or not owned by user"
                    )
                )

        val result =
            createP2PBuyAd(
                buyerId,
                amountReal,
                fiatPriceEtb,
                account.paymentMethod,
                minOrderReal,
                maxOrderReal
            )

        return result.map {
            it.copy(
                paymentName = account.paymentName,
                accountNumber = account.accountNumber
            ).also { updated ->
                p2pDao.updateAd(updated)
            }
        }
    }

    suspend fun startP2PTrade(
        buyerId: String,
        adId: String,
        orderEtbAmount: Double
    ): Result<P2POrderEntity> {
        backendAuth()?.let { auth ->
            return runCatching {
                val ad =
                    p2pDao.getAdById(adId)
                        ?: throw IllegalStateException(
                            "Advertisement not found"
                        )

                val cryptoAmount =
                    orderEtbAmount / ad.fiatPrice

                val response =
                    backendApi.createOrder(
                        auth,
                        OrderRequest(
                            adId,
                            cryptoAmount,
                            orderEtbAmount
                        )
                    )

                if (!response.isSuccessful) {
                    throw backendFailure(
                        response.code(),
                        "Could not start P2P trade"
                    )
                }

                val order =
                    response.body()?.order
                        ?: throw IllegalStateException(
                            "Backend returned no order"
                        )

                mapOrder(order).also {
                    p2pDao.insertOrder(it)
                }
            }
        }

        return Result.failure(
            SecurityException(
                "P2P backend session is required for this action"
            )
        )
    }

    fun getP2PChatMessages(
        orderId: String
    ): Flow<List<P2PChatMessageEntity>> =
        kotlinx.coroutines.flow.flow {
            refreshP2PChat(orderId)

            emitAll(
                p2pChatDao.getForOrder(orderId)
            )
        }

    private suspend fun refreshP2PChat(
        orderId: String
    ): Result<Unit> {
        val auth = backendAuth()
            ?: return Result.failure(
                SecurityException(
                    "Backend session is not connected"
                )
            )

        return runCatching {
            val response =
                backendApi.chat(
                    auth,
                    orderId
                )

            if (!response.isSuccessful) {
                throw backendFailure(
                    response.code(),
                    "Could not load trade chat"
                )
            }

            db.withTransaction {
                p2pChatDao.deleteForOrder(
                    orderId
                )

                response.body()
                    ?.messages
                    .orEmpty()
                    .forEach {
                        p2pChatDao.insert(
                            mapMessage(it)
                        )
                    }
            }
        }
    }

    suspend fun sendP2PChatMessage(
        userId: String,
        orderId: String,
        message: String,
        attachmentUri: String? = null
    ): Result<P2PChatMessageEntity> {
        backendAuth()?.let { auth ->
            return runCatching {
                val uploadedAttachment =
                    uploadAttachmentIfNeeded(
                        attachmentUri
                    )

                if (
                    !attachmentUri.isNullOrBlank() &&
                    uploadedAttachment == null
                ) {
                    throw IllegalArgumentException(
                        "Could not upload chat image"
                    )
                }

                val response =
                    backendApi.sendChat(
                        auth,
                        orderId,
                        MessageRequest(
                            message.trim(),
                            uploadedAttachment
                        )
                    )

                if (!response.isSuccessful) {
                    throw backendFailure(
                        response.code(),
                        "Could not send chat message"
                    )
                }

                val item =
                    response.body()?.message
                        ?: throw IllegalStateException(
                            "Backend returned no chat message"
                        )

                mapMessage(item).also {
                    p2pChatDao.insert(it)
                }
            }
        }

        return Result.failure(
            SecurityException(
                "P2P backend session is required for this action"
            )
        )
    }

    suspend fun markP2PPaymentPaid(
        userId: String,
        orderId: String,
        proofUri: String
    ): Result<P2POrderEntity> {
        backendAuth()?.let { auth ->
            return runCatching {
                val uploadedProof =
                    uploadAttachmentIfNeeded(
                        proofUri
                    )
                        ?: throw IllegalArgumentException(
                            "Could not upload payment proof"
                        )

                val response =
                    backendApi.markPaid(
                        auth,
                        orderId,
                        PaymentRequest(
                            uploadedProof
                        )
                    )

                if (!response.isSuccessful) {
                    throw backendFailure(
                        response.code(),
                        "Could not confirm payment"
                    )
                }

                val refreshed =
                    backendApi.orders(auth)

                if (!refreshed.isSuccessful) {
                    throw backendFailure(
                        refreshed.code(),
                        "Could not refresh order"
                    )
                }

                val order =
                    refreshed.body()
                        ?.orders
                        ?.firstOrNull {
                            it.id == orderId
                        }
                        ?: throw IllegalStateException(
                            "Order not found after payment confirmation"
                        )

                mapOrder(order).also {
                    p2pDao.insertOrder(it)
                }
            }
        }

        return Result.failure(
            SecurityException(
                "P2P backend session is required for this action"
            )
        )
    }

    suspend fun expireP2POrderIfUnpaid(
        orderId: String
    ): Result<P2POrderEntity> {
        backendAuth()?.let { auth ->
            return runCatching {
                val response =
                    backendApi.expire(
                        auth,
                        orderId
                    )

                if (!response.isSuccessful) {
                    throw backendFailure(
                        response.code(),
                        "Order has not expired or is no longer active"
                    )
                }

                val refreshed =
                    backendApi.orders(auth)

                val order =
                    refreshed.body()
                        ?.orders
                        ?.firstOrNull {
                            it.id == orderId
                        }
                        ?: throw IllegalStateException(
                            "Order not found after expiry"
                        )

                mapOrder(order).also {
                    p2pDao.insertOrder(it)
                }
            }
        }

        return Result.failure(
            SecurityException(
                "P2P backend session is required for this action"
            )
        )
    }

    suspend fun releaseP2PEscrow(
        callerUserId: String,
        orderId: String
    ): Result<P2POrderEntity> {
        backendAuth()?.let { auth ->
            return runCatching {
                val response =
                    backendApi.release(
                        auth,
                        orderId
                    )

                if (!response.isSuccessful) {
                    throw backendFailure(
                        response.code(),
                        "Could not release escrow"
                    )
                }

                val refreshed =
                    backendApi.orders(auth)

                val order =
                    refreshed.body()
                        ?.orders
                        ?.firstOrNull {
                            it.id == orderId
                        }
                        ?: throw IllegalStateException(
                            "Order not found after escrow release"
                        )

                mapOrder(order).also {
                    p2pDao.insertOrder(it)
                }
            }
        }

        return Result.failure(
            SecurityException(
                "P2P backend session is required for this action"
            )
        )
    }

    // Sample marketplace ads removed.
    // Marketplace starts empty for new installations.
}
