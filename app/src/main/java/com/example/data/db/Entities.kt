package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["normalizedEmail"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val normalizedEmail: String,
    val passwordHash: String,
    val passwordSalt: String,
    val role: String = "USER", // "USER" or "ADMIN"
    val referralCode: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val userId: String,
    val realBalance: Double = 0.0,
    val usdtBalance: Double = 0.0,
    val bonusRealBalance: Double = 0.0,
    val realLockedBalance: Double = 0.0,
    val usdtLockedBalance: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val availableRealBalance: Double
        get() = (realBalance - realLockedBalance).coerceAtLeast(0.0)

    val availableUsdtBalance: Double
        get() = (usdtBalance - usdtLockedBalance).coerceAtLeast(0.0)
}

@Entity(tableName = "kyc_records")
data class KycEntity(
    @PrimaryKey val userId: String,
    val fullName: String,
    val idNumber: String,
    val documentAttached: Boolean,
    val documentUri: String? = null,
    val status: String = "NOT_SUBMITTED", // "NOT_SUBMITTED", "PENDING", "VERIFIED", "REJECTED"
    val submittedAt: Long? = null,
    val reviewedAt: Long? = null,
    val reviewedByAdminId: String? = null,
    val rejectionReason: String? = null
)

@Entity(tableName = "kyc_audit_logs")
data class KycAuditLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val adminId: String,
    val action: String, // "APPROVED", "REJECTED"
    val notes: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "deposits",
    indices = [Index(value = ["txHash"], unique = true)]
)
data class DepositEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val txHash: String,
    val amountReal: Double,
    val depositUsd: Double = 0.0,
    val bonusReal: Double = 0.0,
    val recipientAddress: String,
    val network: String = "BEP20",
    val tokenContract: String = "",
    val confirmations: Int = 0,
    val status: String = "PENDING", // "PENDING", "CONFIRMED", "REJECTED", "FAILED"
    val createdAt: Long = System.currentTimeMillis(),
    val confirmedAt: Long? = null
)

@Entity(tableName = "withdrawals")
data class WithdrawalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val recipientAddress: String,
    val amountReal: Double,
    val usdValue: Double,
    val network: String = "BEP20",
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED", "CONFIRMED", "FAILED"
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null,
    val rejectionReason: String? = null
)

@Entity(
    tableName = "ledger_entries",
    indices = [Index(value = ["userId"])]
)
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String, // "DEPOSIT", "WITHDRAWAL_LOCK", "WITHDRAWAL_CONFIRMED", "WITHDRAWAL_REFUND", "P2P_ESCROW_LOCK", "P2P_ESCROW_RELEASE", "P2P_ESCROW_REFUND", "REWARD_CLAIM"
    val currency: String = "REAL",
    val amount: Double,
    val balanceBefore: Double,
    val balanceAfter: Double,
    val referenceId: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)


@Entity(tableName = "payment_accounts", indices = [Index(value = ["userId"])])
data class PaymentAccountEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val paymentName: String,
    val paymentMethod: String,
    val accountNumber: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "p2p_ads")
data class P2PAdEntity(
    @PrimaryKey val id: String,
    val sellerId: String,
    val sellerName: String,
    val type: String, // "BUY" or "SELL"
    val cryptoAmount: Double,
    val fiatPrice: Double, // ETB per 1 REAL
    val fiatOrderAmount: Double = 0.0, // ETB actually traded
    val fiatCurrency: String = "ETB", // ETB only
    val paymentMethod: String = "Commercial Bank of Ethiopia (CBE)",
    val paymentName: String = "",
    val accountNumber: String = "",
    val isActive: Boolean = true,
    val minOrderReal: Double = 1.0,
    val maxOrderReal: Double = cryptoAmount,
    val minOrderEtb: Double = 0.0,
    val maxOrderEtb: Double = 0.0,
    val originalMaxOrderEtb: Double = maxOrderEtb,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "p2p_orders")
data class P2POrderEntity(
    @PrimaryKey val id: String,
    val adId: String,
    val sellerId: String,
    val sellerName: String,
    val buyerId: String,
    val buyerName: String,
    val cryptoAmount: Double,
    val fiatPrice: Double,
    val fiatCurrency: String = "ETB",
    val paymentMethod: String,
    val paymentName: String = "",
    val accountNumber: String = "",
    val paymentProofUri: String? = null,
    val paidAt: Long? = null,
    val status: String = "ESCROW_LOCKED", // "ESCROW_LOCKED", "PAID", "COMPLETED", "CANCELLED", "DISPUTED"
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val expiresAt: Long = createdAt + 20L * 60L * 1000L,
    val disputeReason: String? = null,
    val disputedAt: Long? = null,
    val resolvedAt: Long? = null,
    val resolvedByAdminId: String? = null
)


@Entity(tableName = "spin_states")
data class SpinStateEntity(
    @PrimaryKey val userId: String,
    val lastFreeSpinAt: Long? = null
)

@Entity(
    tableName = "reward_claims",
    indices = [
        Index(value = ["userId", "periodKey"], unique = true)
    ]
)
data class RewardClaimEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val amountReal: Double,
    val claimTimestamp: Long = System.currentTimeMillis(),
    val periodKey: String // Date key like "2026-09-16" or epoch day
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "password_reset_sessions",
    indices = [
        Index(value = ["normalizedEmail"]),
        Index(value = ["expiresAt"])
    ]
)
data class PasswordResetSessionEntity(
    @PrimaryKey val id: String,
    val normalizedEmail: String,
    val authorizationTokenHash: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val usedAt: Long? = null
)


@Entity(tableName = "help_requests")
data class HelpRequestEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val category: String,
    val message: String,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val resolvedByAdminId: String? = null
)


@Entity(
    tableName = "p2p_chat_messages",
    indices = [Index(value = ["orderId", "createdAt"])]
)
data class P2PChatMessageEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val senderId: String,
    val senderName: String,
    val message: String = "",
    val attachmentUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
