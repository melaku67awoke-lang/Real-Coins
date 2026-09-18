package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordResetSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: PasswordResetSessionEntity)

    @Query("SELECT * FROM password_reset_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PasswordResetSessionEntity?

    @Query("UPDATE password_reset_sessions SET usedAt = :usedAt WHERE id = :id AND usedAt IS NULL")
    suspend fun markUsed(id: String, usedAt: Long): Int

    @Query("DELETE FROM password_reset_sessions WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long): Int
}

@Dao
interface PaymentAccountDao {

    @Query("SELECT * FROM payment_accounts WHERE userId = :userId ORDER BY createdAt DESC")
    fun getForUser(userId: String): Flow<List<PaymentAccountEntity>>

    @Query("SELECT * FROM payment_accounts WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getOwned(id: String, userId: String): PaymentAccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: PaymentAccountEntity)

    @Query("SELECT COUNT(*) FROM payment_accounts WHERE userId = :userId")
    suspend fun countForUser(userId: String): Int

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

    @Query("UPDATE users SET passwordHash = :passwordHash, passwordSalt = :passwordSalt WHERE id = :userId")
    suspend fun updatePassword(
        userId: String,
        passwordHash: String,
        passwordSalt: String
    ): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = 'USER'")
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

    @Query("SELECT COUNT(*) FROM kyc_records WHERE status = 'PENDING'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM kyc_records WHERE status = 'VERIFIED'")
    suspend fun countVerified(): Int

    @Query("SELECT * FROM kyc_records WHERE status = 'PENDING' ORDER BY submittedAt ASC")
    fun getPendingKyc(): Flow<List<KycEntity>>

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

    @Query("SELECT * FROM deposits ORDER BY createdAt DESC")
    fun getAllDeposits(): Flow<List<DepositEntity>>

    @Query("SELECT COALESCE(SUM(depositUsd), 0) FROM deposits WHERE userId = :userId AND status = 'CONFIRMED'")
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

    @Query("SELECT * FROM withdrawals ORDER BY createdAt DESC")
    fun getAllWithdrawals(): Flow<List<WithdrawalEntity>>
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
    fun getActiveAds():
