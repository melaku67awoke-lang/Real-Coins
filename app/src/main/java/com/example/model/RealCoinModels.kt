package com.example.model

data class UserProfile(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val realCoinBalance: Double = 0.0,
    val usdtBalance: Double = 0.0,
    val realLockedBalance: Double = 0.0,
    val isKycVerified: Boolean = false,
    val kycStatus: String = "NOT_SUBMITTED",
    val role: String = "USER"
)

data class TransactionRecord(
    val id: String,
    val type: TransactionType,
    val amountRealCoin: Double,
    val usdValue: Double,
    val txHashOrAddress: String,
    val network: String = "BEP20",
    val status: String = "Completed",
    val timestamp: Long = System.currentTimeMillis()
)

enum class TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    P2P_BUY,
    P2P_SELL,
    SPIN_REWARD
}

data class P2POrder(
    val id: String,
    val traderName: String,
    val type: String, // "BUY" or "SELL"
    val cryptoAmount: Double,
    val fiatPrice: Double, // USDT or ETB
    val fiatCurrency: String = "ETB",
    val paymentMethod: String = "Commercial Bank of Ethiopia (CBE)"
)
