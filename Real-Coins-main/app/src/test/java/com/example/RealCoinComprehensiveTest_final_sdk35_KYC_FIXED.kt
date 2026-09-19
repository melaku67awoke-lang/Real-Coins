package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.DepositEntity
import com.example.data.db.LedgerEntryEntity
import com.example.data.repository.RealCoinRepository
import com.example.security.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Locale
import java.util.UUID
import androidx.room.withTransaction

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RealCoinComprehensiveTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RealCoinRepository

    /**
     * Trusted test-only funding fixture.
     *
     * This deliberately bypasses submitDeposit(), because user-submitted
     * deposits must remain PENDING until an external trusted blockchain
     * verifier confirms them.
     */
    private suspend fun insertConfirmedDepositFixture(
        userId: String,
        txHash: String,
        amountReal: Double
    ) {
        db.withTransaction {
            val wallet = db.walletDao().getWalletSync(userId)
                ?: error("Wallet not found for test fixture")

            val cleanTxHash = txHash.trim().lowercase(Locale.ROOT)

            db.depositDao().insertDeposit(
                DepositEntity(
                    id = "TEST_DEP_${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}",
                    userId = userId,
                    txHash = cleanTxHash,
                    amountReal = amountReal,
                    recipientAddress = com.example.ui.screens.BEP20_DEPOSIT_ADDRESS,
                    network = "BEP20",
                    tokenContract = BuildConfig.USDT_BEP20_CONTRACT,
                    confirmations = 1,
                    status = "CONFIRMED",
                    createdAt = System.currentTimeMillis(),
                    confirmedAt = System.currentTimeMillis()
                )
            )

            db.walletDao().updateWallet(
                wallet.copy(
                    realBalance = wallet.realBalance + amountReal,
                    updatedAt = System.currentTimeMillis()
                )
            )

            db.ledgerDao().insertLedgerEntry(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = "DEPOSIT",
                    currency = "REAL",
                    amount = amountReal,
                    balanceBefore = wallet.realBalance,
                    balanceAfter = wallet.realBalance + amountReal,
                    referenceId = cleanTxHash,
                    notes = "Test fixture: trusted confirmed deposit",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        repository = RealCoinRepository(
            db = db,
            context = context
        )
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    // -------------------------------------------------------------
    // 1. NEW USERS TEST
    // -------------------------------------------------------------

    @Test
    fun testNewUserStartsEmptyAndUnverified() = runTest {
        val regResult = repository.register(
            "newbie",
            "newbie@realcoin.io",
            "Pass1234!"
        )

        assertTrue("Registration must succeed", regResult.isSuccess)

        val user = regResult.getOrThrow()

        assertEquals("USER", user.role)

        val wallet = repository.getWalletSync(user.id)

        assertNotNull("Wallet must exist", wallet)
        assertEquals(0.0, wallet!!.realBalance, 0.0)
        assertEquals(0.0, wallet.usdtBalance, 0.0)
        assertEquals(0.0, wallet.realLockedBalance, 0.0)
        assertEquals(0.0, wallet.usdtLockedBalance, 0.0)
        assertEquals(0.0, wallet.availableRealBalance, 0.0)

        val kyc = repository.getKycForUserSync(user.id)

        assertNotNull("KYC record must exist", kyc)
        assertEquals("NOT_SUBMITTED", kyc!!.status)
        assertFalse(kyc.documentAttached)
    }

    // -------------------------------------------------------------
    // 2. AUTHENTICATION AND REGISTRATION TESTS
    // -------------------------------------------------------------

    @Test
    fun testLoginBeforeRegistrationPrevented() = runTest {
        val loginResult = repository.login(
            "nonexistent",
            "Pass1234!"
        )

        assertTrue(
            "Login before registration must fail",
            loginResult.isFailure
        )

        assertTrue(
            loginResult.exceptionOrNull()
                ?.message
                ?.contains("register", ignoreCase = true) == true
        )
    }

    @Test
    fun testDuplicateUsernameRejected() = runTest {
        val user1 = repository.register(
            "cryptotrader",
            "trader1@realcoin.io",
            "Pass1234!"
        )

        assertTrue(user1.isSuccess)

        val user2 = repository.register(
            "cryptotrader",
            "trader2@realcoin.io",
            "Pass1234!"
        )

        assertTrue(
            "Duplicate username must be rejected",
            user2.isFailure
        )

        assertTrue(
            user2.exceptionOrNull()
                ?.message
                ?.contains("already taken", ignoreCase = true) == true
        )
    }

    @Test
    fun testDuplicateEmailCaseInsensitiveRejected() = runTest {
        val user1 = repository.register(
            "traderA",
            "USER@realcoin.io",
            "Pass1234!"
        )

        assertTrue(user1.isSuccess)

        val user2 = repository.register(
            "traderB",
            "user@realcoin.io",
            "Pass1234!"
        )

        assertTrue(
            "Duplicate lowercase email must be rejected",
            user2.isFailure
        )

        assertTrue(
            user2.exceptionOrNull()
                ?.message
                ?.contains("already registered", ignoreCase = true) == true
        )
    }

    @Test
    fun testPasswordHashingAndVerification() = runTest {
        val regResult = repository.register(
            "secureUser",
            "secure@realcoin.io",
            "SecretP@ss99"
        )

        val user = regResult.getOrThrow()

        assertNotEquals(
            "SecretP@ss99",
            user.passwordHash
        )

        assertTrue(user.passwordHash.length >= 32)
        assertTrue(user.passwordSalt.isNotEmpty())

        assertTrue(
            PasswordHasher.verifyPassword(
                "SecretP@ss99",
                user.passwordSalt,
                user.passwordHash
            )
        )

        assertFalse(
            PasswordHasher.verifyPassword(
                "WrongPassword",
                user.passwordSalt,
                user.passwordHash
            )
        )

        val loginSuccess = repository.login(
            "secureUser",
            "SecretP@ss99"
        )

        assertTrue(loginSuccess.isSuccess)

        val loginFail = repository.login(
            "secureUser",
            "WrongPassword"
        )

        assertTrue(loginFail.isFailure)
    }

    // -------------------------------------------------------------
    // 3. KYC TESTS
    // -------------------------------------------------------------

    @Test
    fun testKycSubmissionAndAdminAuthorization() = runTest {
        val userResult = repository.register(
            "kycUser",
            "kyc@realcoin.io",
            "Pass1234!"
        )

        val user = userResult.getOrThrow()

        val kycResult = repository.submitKyc(
            user.id,
            "Melaku Awoke",
            "Passport",
            "ETH12345678",
            "test://kyc/front",
            "test://kyc/back"
        )

        assertTrue(kycResult.isSuccess)

        val kyc = kycResult.getOrThrow()

        assertEquals("PENDING", kyc.status)
        assertEquals("Melaku Awoke", kyc.fullName)
        assertTrue(kyc.documentAttached)

        val dupKyc = repository.submitKyc(
            user.id,
            "Melaku Awoke",
            "Passport",
            "ETH12345678",
            "test://kyc/front",
            "test://kyc/back"
        )

        assertTrue(
            "Duplicate submission must fail",
            dupKyc.isFailure
        )

        val regularUserAttempt = repository.reviewKyc(
            user.id,
            user.id,
            true
        )

        assertTrue(
            "Regular user cannot approve KYC",
            regularUserAttempt.isFailure
        )

        val adminSalt = PasswordHasher.generateSalt()

        val adminUser = com.example.data.db.UserEntity(
            id = "ADMIN_001",
            username = "ComplianceAdmin",
            normalizedEmail = "admin@compliance.org",
            passwordHash = PasswordHasher.hashPassword(
                "AdminPass123!",
                adminSalt
            ),
            passwordSalt = adminSalt,
            role = "ADMIN"
        )

        db.userDao().insertUser(adminUser)

        val adminSelfAttempt = repository.reviewKyc(
            adminUser.id,
            adminUser.id,
            true
        )

        assertTrue(
            "Admin cannot review own KYC",
            adminSelfAttempt.isFailure
        )

        val approveResult = repository.reviewKyc(
            adminUser.id,
            user.id,
            true,
            "Valid passport provided"
        )

        assertTrue(
            "Admin approval must succeed",
            approveResult.isSuccess
        )

        val verifiedKyc = approveResult.getOrThrow()

        assertEquals("VERIFIED", verifiedKyc.status)
        assertEquals(
            adminUser.id,
            verifiedKyc.reviewedByAdminId
        )

        val auditLogs = db.kycDao().getAuditLogsForUser(user.id)

        assertEquals(1, auditLogs.size)
        assertEquals("APPROVED", auditLogs[0].action)
        assertEquals(adminUser.id, auditLogs[0].adminId)
    }

    // -------------------------------------------------------------
    // 4. DEPOSIT AND DUPLICATE TXHASH PROTECTION TESTS
    // -------------------------------------------------------------

    @Test
    fun testDepositWithDuplicateTxHashProtection() = runTest {
        val user = repository.register(
            "depositUser",
            "dep@realcoin.io",
            "Pass1234!"
        ).getOrThrow()

        val txHash =
            "0x8e54105bed3243e1ca44a0cccc6b62cf2bff9df497955"

        val depResult = repository.submitDeposit(
            user.id,
            txHash,
            50000.0
        )

        assertTrue(
            "First deposit must succeed",
            depResult.isSuccess
        )

        val wallet = repository.getWalletSync(user.id)

        assertEquals(
            0.0,
            wallet!!.realBalance,
            0.0
        )

        val ledger = repository
            .getLedgerForUser(user.id)
            .first()

        assertTrue(ledger.isEmpty())

        val dupDeposit = repository.submitDeposit(
            user.id,
            txHash,
            50000.0
        )

        assertTrue(
            "Duplicate TxHash deposit must fail",
            dupDeposit.isFailure
        )

        assertTrue(
            dupDeposit.exceptionOrNull()
                ?.message
                ?.contains("duplicate", ignoreCase = true) == true
        )

        val walletAfter = repository.getWalletSync(user.id)

        assertEquals(
            0.0,
            walletAfter!!.realBalance,
            0.0
        )
    }

    @Test
    fun testPendingDepositDoesNotIncreaseSpendableBalance() = runTest {
        val user = repository.register(
            "pendingUser",
            "pending@realcoin.io",
            "Pass1234!"
        ).getOrThrow()

        val result = repository.submitDeposit(
            user.id,
            "0xpending_tx_01",
            25000.0
        )

        assertTrue(
            "Deposit submission must succeed",
            result.isSuccess
        )

        assertEquals(
            "PENDING",
            result.getOrThrow().status
        )

        val wallet = repository.getWalletSync(user.id)

        assertEquals(
            0.0,
            wallet!!.realBalance,
            0.0
        )

        assertEquals(
            0.0,
            wallet.availableRealBalance,
            0.0
        )

        val deposits = repository
            .getDepositsForUser(user.id)
            .first()

        assertEquals(1, deposits.size)
        assertEquals("PENDING", deposits[0].status)
    }

    // -------------------------------------------------------------
    // 5. WITHDRAWAL TESTS
    // -------------------------------------------------------------

    @Test
    fun testWithdrawalValidationAndFundLocking() = runTest {
        val user = repository.register(
            "withdrawUser",
            "wth@realcoin.io",
            "Pass1234!"
        ).getOrThrow()

        val validBep20 =
            "0x8e54105bed3243e1ca44a0cccc6b62cf2bff9df4"

        val zeroBalResult = repository.submitWithdrawal(
            user.id,
            validBep20,
            20000.0
        )

        assertTrue(
            "Insufficient balance withdrawal must fail",
            zeroBalResult.isFailure
        )

        insertConfirmedDepositFixture(
            user.id,
            "0xtx_fund_01",
            30000.0
        )

        val belowMinResult = repository.submitWithdrawal(
            user.id,
            validBep20,
            1000.0
        )

        assertTrue(
            "Below minimum USD withdrawal must fail",
            belowMinResult.isFailure
        )

        val invalidAddrResult = repository.submitWithdrawal(
            user.id,
            "invalid_address",
            20000.0
        )

        assertTrue(
            "Invalid address withdrawal must fail",
            invalidAddrResult.isFailure
        )

        val wthResult = repository.submitWithdrawal(
            user.id,
            validBep20,
            20000.0
        )

        assertTrue(
            "Valid withdrawal must succeed",
            wthResult.isSuccess
        )

        val wth = wthResult.getOrThrow()

        assertEquals("PENDING", wth.status)

        val wallet = repository.getWalletSync(user.id)

        assertEquals(
            30000.0,
            wallet!!.realBalance,
            0.0
        )

        assertEquals(
            20000.0,
            wallet.realLockedBalance,
            0.0
        )

        assertEquals(
            10000.0,
            wallet.availableRealBalance,
            0.0
        )

        val secondWth = repository.submitWithdrawal(
            user.id,
            validBep20,
            15000.0
        )

        assertTrue(
            "Exceeding available balance must fail",
            secondWth.isFailure
        )

        val adminSalt = PasswordHasher.generateSalt()

        val adminUser = com.example.data.db.UserEntity(
            id = "ADMIN_002",
            username = "WithdrawalAdmin",
            normalizedEmail = "wadmin@compliance.org",
            passwordHash = PasswordHasher.hashPassword(
                "AdminPass123!",
                adminSalt
            ),
            passwordSalt = adminSalt,
            role = "ADMIN"
        )

        db.userDao().insertUser(adminUser)

        val rejectResult = repository.processWithdrawal(
            adminUser.id,
            wth.id,
            false,
            "Suspicious activity"
        )

        assertTrue(rejectResult.isSuccess)

        val walletRefunded =
            repository.getWalletSync(user.id)

        assertEquals(
            30000.0,
            walletRefunded!!.realBalance,
            0.0
        )

        assertEquals(
            0.0,
            walletRefunded.realLockedBalance,
            0.0
        )

        assertEquals(
            30000.0,
            walletRefunded.availableRealBalance,
            0.0
        )
    }

    // -------------------------------------------------------------
    // 6. P2P ESCROW AND CONCURRENCY TESTS
    // -------------------------------------------------------------

    @Test
    fun testP2PRequiresBackendSession() = runTest {
        val seller = repository.register(
            "sellerBob",
            "bob@realcoin.io",
            "Pass1234!"
        ).getOrThrow()

        val buyer = repository.register(
            "buyerAlice",
            "alice@realcoin.io",
            "Pass1234!"
        ).getOrThrow()

        // P2P advertisement creation is backend-session protected.
        // This Robolectric test intentionally has no authenticated backend
        // session, so creation must be rejected rather than using a fake
        // local escrow implementation.
        val adResult = repository.createP2PSellAd(
            seller.id,
            15000.0,
            5000.0,
            "Telebirr"
        )

        assertTrue(
            "Sell ad creation without a backend session must fail",
            adResult.isFailure
        )

        assertTrue(
            "Sell ad failure must explain the backend session requirement",
            adResult.exceptionOrNull()
                ?.message
                ?.contains(
                    "backend session",
                    ignoreCase = true
                ) == true
        )

        // P2P order creation has the same backend-session requirement.
        val tradeResult = repository.startP2PTrade(
            buyerId = buyer.id,
            adId = "missing-ad",
            orderEtbAmount = 5000.0
        )

        assertTrue(
            "P2P trade without a backend session must fail",
            tradeResult.isFailure
        )

        assertTrue(
            "P2P trade failure must explain the backend session requirement",
            tradeResult.exceptionOrNull()
                ?.message
                ?.contains(
                    "backend session",
                    ignoreCase = true
                ) == true
        )
    }

    // -------------------------------------------------------------
    // 7. CROSS-USER PRIVACY TESTS
    // -------------------------------------------------------------

    @Test
    fun testCrossUserPrivacyIsolation() = runTest {
        val user1 = repository.register(
            "userOne",
            "user1@realcoin.io",
            "Pass1234!"
        ).getOrThrow()

        val user2 = repository.register(
            "userTwo",
            "user2@realcoin.io",
            "Pass1234!"
        ).getOrThrow()

        insertConfirmedDepositFixture(
            user1.id,
            "0xdeposit_u1",
            40000.0
        )

        val u2Wallet =
            repository.getWalletSync(user2.id)

        assertEquals(
            0.0,
            u2Wallet!!.realBalance,
            0.0
        )

        val u2Ledger =
            repository.getLedgerForUser(user2.id).first()

        assertTrue(
            "User 2 must have zero transactions",
            u2Ledger.isEmpty()
        )

        val u1Deposits =
            repository.getDepositsForUser(user1.id).first()

        assertEquals(
            1,
            u1Deposits.size
        )

        assertEquals(
            user1.id,
            u1Deposits[0].userId
        )

        assertEquals(
            "CONFIRMED",
            u1Deposits[0].status
        )
    }

    // -------------------------------------------------------------
    // 8. REWARDS & EXPLOIT PROTECTION TESTS
    // -------------------------------------------------------------

    @Test
    fun testDailyRewardClaimAndRepeatExploitPrevention() = runTest {
        val user = repository.register(
            "luckySpinner",
            "spin@realcoin.io",
            "Pass1234!"
        ).getOrThrow()

        val claim1 = repository.claimDailyReward(
            user.id,
            1500.0
        )

        assertTrue(
            "First claim must succeed",
            claim1.isSuccess
        )

        val reward = claim1.getOrThrow()

        // The repository uses a randomized reward table.
        assertTrue(
            "Reward must be one of the configured daily-spin values",
            reward == 0.0 ||
                reward == 5.0 ||
                reward == 10.0 ||
                reward == 20.0 ||
                reward == 50.0
        )

        val wallet =
            repository.getWalletSync(user.id)

        assertEquals(
            reward,
            wallet!!.realBalance,
            0.0
        )

        // claimDailyReward represents the free daily claim. After the first
        // claim the 24-hour free-spin cooldown must be active.
        assertFalse(
            "A second free daily claim must be blocked during the cooldown",
            repository.canClaimRewardToday(user.id)
        )

        val walletAfter =
            repository.getWalletSync(user.id)

        assertEquals(
            reward,
            walletAfter!!.realBalance,
            0.0
        )
    }

}
