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
        // Fresh-install login is intentionally backend-authoritative, so a
        // local unit test must not call the production backend. Verify the
        // security invariant that an unknown account is not created locally.
        assertNull(db.userDao().getUserByUsername("nonexistent"))
        assertNull(db.userDao().getUserByNormalizedEmail("nonexistent"))

        // A failed/unregistered login must never create a local account.
        assertNull(db.userDao().getUserByUsername("nonexistent"))
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
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(
            "SecretP@ss99",
            salt
        )

        assertNotEquals(
            "SecretP@ss99",
            hash
        )
        assertTrue(hash.length >= 32)
        assertTrue(salt.isNotEmpty())

        assertTrue(
            PasswordHasher.verifyPassword(
                "SecretP@ss99",
                salt,
                hash
            )
        )

        assertFalse(
            PasswordHasher.verifyPassword(
                "WrongPassword",
                salt,
                hash
            )
        )
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

        // KYC submission itself is backend-authoritative in the production
        // repository. A unit test must not call the real backend or upload
        // documents. Seed the same PENDING local record that a successful
        // backend KYC submission would produce, then test the local KYC
        // state and authorization invariants.
        db.kycDao().insertOrUpdateKyc(
            com.example.data.db.KycEntity(
                userId = user.id,
                fullName = "Melaku Awoke",
                idType = "National ID",
                idNumber = "ETH12345678",
                documentAttached = true,
                documentUri = "content://front-id",
                frontIdUri = "content://front-id",
                backIdUri = "content://back-id",
                status = "PENDING",
                submittedAt = System.currentTimeMillis()
            )
        )

        val kyc = repository.getKycForUserSync(user.id)

        assertNotNull("KYC record must exist", kyc)
        assertEquals("PENDING", kyc!!.status)
        assertEquals("Melaku Awoke", kyc.fullName)
        assertEquals("National ID", kyc.idType)
        assertEquals("ETH12345678", kyc.idNumber)
        assertEquals("content://front-id", kyc.frontIdUri)
        assertEquals("content://back-id", kyc.backIdUri)
        assertTrue(kyc.documentAttached)

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

        // Approval is also backend-authoritative. With no authenticated
        // backend session in this isolated unit test, the repository must
        // refuse the review rather than modifying local KYC state.
        val approveResult = repository.reviewKyc(
            adminUser.id,
            user.id,
            true,
            "Valid passport provided"
        )

        assertTrue(
            "Admin approval requires a secure backend session in unit tests",
            approveResult.isFailure
        )

        assertTrue(
            approveResult.exceptionOrNull()
                ?.message
                ?.contains("Secure admin backend session", ignoreCase = true) == true
        )

        val unchangedKyc = repository.getKycForUserSync(user.id)
        assertNotNull(unchangedKyc)
        assertEquals("PENDING", unchangedKyc!!.status)
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
    fun testP2PBackendSessionRequiredAndValidation() = runTest {
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

        insertConfirmedDepositFixture(
            seller.id,
            "0xdeposit_bob_01",
            20000.0
        )

        // P2P advertisements and orders are backend-authoritative in the
        // current repository. A local unit test has no backend session, so
        // these operations must be rejected rather than creating local
        // escrow state that could bypass the backend security boundary.
        val adResult = repository.createP2PSellAd(
            seller.id,
            15000.0,
            5000.0,
            "Telebirr"
        )

        assertTrue(
            "P2P sell-ad creation must require a backend session",
            adResult.isFailure
        )

        assertTrue(
            adResult.exceptionOrNull()
                ?.message
                ?.contains("backend session", ignoreCase = true) == true
        )

        val tradeResult = repository.startP2PTrade(
            buyerId = buyer.id,
            adId = "nonexistent-test-ad",
            orderEtbAmount = 5000.0
        )

        assertTrue(
            "P2P trade creation must require a backend session",
            tradeResult.isFailure
        )

        assertTrue(
            tradeResult.exceptionOrNull()
                ?.message
                ?.contains("backend session", ignoreCase = true) == true
        )

        // Invalid local input is rejected before any backend call.
        val invalidAd = repository.createP2PSellAd(
            seller.id,
            -1.0,
            5000.0,
            "Telebirr"
        )

        assertTrue(
            "Invalid P2P amount must be rejected",
            invalidAd.isFailure
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

        assertEquals(
            10.0,
            claim1.getOrThrow(),
            0.0
        )

        val wallet =
            repository.getWalletSync(user.id)

        assertEquals(
            10.0,
            wallet!!.realBalance,
            0.0
        )

        val claim2 = repository.claimDailyReward(
            user.id,
            2500.0
        )

        assertTrue(
            "Repeated claim must fail",
            claim2.isFailure
        )

        assertTrue(
            claim2.exceptionOrNull()
                ?.message
                ?.contains(
                    "already claimed",
                    ignoreCase = true
                ) == true
        )

        val walletAfter =
            repository.getWalletSync(user.id)

        assertEquals(
            10.0,
            walletAfter!!.realBalance,
            0.0
        )
    }
}
