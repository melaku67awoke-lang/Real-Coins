package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow


@Dao
interface PaymentAccountDao {
    @Query("SELECT * FROM payment_accounts WHERE userId = :userId ORDER BY createdAt DESC")
    fun getForUser(userId: String): Flow<List<PaymentAccountEntity>>
    @Query("SELECT * FROM payment_accounts WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getOwned(id: String, userId: String): PaymentAccountEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: PaymentAccountEntity)
    @Query("DELETE FROM payment_accounts WHERE id = :id AND userId = :userId")
    suspend fun deleteOwned(id: String, userId: String): Int
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE normalizedEmail = :normalizedEmail LIMIT 1")
    suspend fun getUserByNormalizedEmail(normalizedEmail: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets WHERE userId = :userId LIMIT 1")
    fun getWallet(userId: String): Flow<WalletEntity?>

    @Query("SELECT * FROM wallets WHERE userId = :userId LIMIT 1")
    suspend fun getWalletSync(userId: String): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: WalletEntity)

    @Update
    suspend fun updateWallet(wallet: WalletEntity)
}

@Dao
interface KycDao {
    @Query("SELECT * FROM kyc_records WHERE userId = :userId LIMIT 1")
    fun getKycForUser(userId: String): Flow<KycEntity?>

    @Query("SELECT * FROM kyc_records WHERE userId = :userId LIMIT 1")
    suspend fun getKycForUserSync(userId: String): KycEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateKyc(kyc: KycEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(log: KycAuditLogEntity)

    @Query("SELECT * FROM kyc_audit_logs WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAuditLogsForUser(userId: String): List<KycAuditLogEntity>
}

@Dao
interface DepositDao {
    @Query("SELECT * FROM deposits WHERE txHash = :txHash LIMIT 1")
    suspend fun getDepositByTxHash(txHash: String): DepositEntity?

    @Query("SELECT * FROM deposits WHERE id = :id LIMIT 1")
    suspend fun getDepositById(id: String): DepositEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDeposit(deposit: DepositEntity)

    @Update
    suspend fun updateDeposit(deposit: DepositEntity)

    @Query("SELECT * FROM deposits WHERE userId = :userId ORDER BY createdAt DESC")
    fun getDepositsForUser(userId: String): Flow<List<DepositEntity>>

    @Query("SELECT * FROM deposits WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getDepositsForUserSync(userId: String): List<DepositEntity>

    @Query("SELECT COALESCE(SUM(amountReal), 0) FROM deposits WHERE userId = :userId AND status = 'CONFIRMED'")
    suspend fun getConfirmedDepositTotal(userId: String): Double
}

@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM withdrawals WHERE id = :id LIMIT 1")
    suspend fun getWithdrawalById(id: String): WithdrawalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWithdrawal(withdrawal: WithdrawalEntity)

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity)

    @Query("SELECT * FROM withdrawals WHERE userId = :userId ORDER BY createdAt DESC")
    fun getWithdrawalsForUser(userId: String): Flow<List<WithdrawalEntity>>

    @Query("SELECT * FROM withdrawals WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getWithdrawalsForUserSync(userId: String): List<WithdrawalEntity>
}

@Dao
interface LedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity)

    @Query("SELECT * FROM ledger_entries WHERE userId = :userId ORDER BY timestamp DESC")
    fun getLedgerForUser(userId: String): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getLedgerForUserSync(userId: String): List<LedgerEntryEntity>
}

@Dao
interface P2PDao {
    @Query("SELECT * FROM p2p_ads WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveAds(): Flow<List<P2PAdEntity>>

    @Query("SELECT * FROM p2p_ads WHERE isActive = 1 ORDER BY createdAt DESC")
    suspend fun getActiveAdsSync(): List<P2PAdEntity>

    @Query("SELECT * FROM p2p_ads WHERE id = :id LIMIT 1")
    suspend fun getAdById(id: String): P2PAdEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAd(ad: P2PAdEntity)

    @Update
    suspend fun updateAd(ad: P2PAdEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(order: P2POrderEntity)

    @Update
    suspend fun updateOrder(order: P2POrderEntity)

    @Query("SELECT * FROM p2p_orders WHERE id = :id LIMIT 1")
    suspend fun getOrderById(id: String): P2POrderEntity?

    @Query("SELECT * FROM p2p_orders WHERE sellerId = :userId OR buyerId = :userId ORDER BY createdAt DESC")
    fun getOrdersForUser(userId: String): Flow<List<P2POrderEntity>>

    @Query("SELECT * FROM p2p_orders WHERE sellerId = :userId OR buyerId = :userId ORDER BY createdAt DESC")
    suspend fun getOrdersForUserSync(userId: String): List<P2POrderEntity>
}

@Dao
interface RewardDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRewardClaim(claim: RewardClaimEntity)

    @Query("SELECT * FROM reward_claims WHERE userId = :userId AND periodKey = :periodKey LIMIT 1")
    suspend fun getClaimForPeriod(userId: String, periodKey: String): RewardClaimEntity?

    @Query("SELECT * FROM reward_claims WHERE userId = :userId ORDER BY claimTimestamp DESC")
    suspend fun getClaimsForUser(userId: String): List<RewardClaimEntity>
}
