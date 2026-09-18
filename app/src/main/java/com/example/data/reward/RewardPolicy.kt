package com.example.data.reward

import com.example.data.db.P2POrderEntity

data class RewardLevel(val name: String, val minimumDepositUsd: Double, val dailyRewardReal: Double)

data class RewardStatus(
    val level: RewardLevel,
    val totalDepositUsd: Double,
    val nextLevel: RewardLevel?,
    val progressToNext: Double,
    val qualifyingBuyCompleted: Boolean,
    val qualifyingSellCompleted: Boolean,
    val activityRequirementMet: Boolean,
    val rewardActive: Boolean,
    val daysSinceRegistration: Long
)

object RewardPolicy {
    const val BONUS_RATE = 0.10
    const val MIN_DEPOSIT_USD = 25.0
    const val MIN_WITHDRAWAL_USD = 50.0
    const val ACTIVITY_WINDOW_DAYS = 7L

    val levels = listOf(
        RewardLevel("Starter", 0.0, 10.0),
        RewardLevel("Silver", 100.0, 30.0),
        RewardLevel("Bronze", 200.0, 50.0),
        RewardLevel("Gold", 300.0, 75.0),
        RewardLevel("Platinum", 400.0, 100.0),
        RewardLevel("Diamond", 500.0, 150.0),
        RewardLevel("KING 👑", 1000.0, 300.0)
    )

    fun levelFor(totalDepositUsd: Double): RewardLevel =
        levels.lastOrNull { totalDepositUsd >= it.minimumDepositUsd } ?: levels.first()

    fun progress(totalDepositUsd: Double): Pair<RewardLevel?, Double> {
        val current = levelFor(totalDepositUsd)
        val next = levels.firstOrNull { it.minimumDepositUsd > current.minimumDepositUsd }
            ?: return null to 1.0
        val span = next.minimumDepositUsd - current.minimumDepositUsd
        return next to ((totalDepositUsd - current.minimumDepositUsd) / span).coerceIn(0.0, 1.0)
    }

    fun qualifyingActivity(
        userId: String,
        orders: List<P2POrderEntity>,
        nowMs: Long,
        registrationMs: Long
    ): Pair<Boolean, Boolean> {
        val windowStart = nowMs - ACTIVITY_WINDOW_DAYS * 24L * 60L * 60L * 1000L
        val effectiveStart = maxOf(windowStart, registrationMs)
        val completed = orders.filter {
            it.status == "COMPLETED" && (it.completedAt ?: 0L) >= effectiveStart
        }
        val sellCounterparties = completed.filter { it.sellerId == userId }
            .map { it.buyerId }.filter { it != userId }.toSet()
        val buyCounterparties = completed.filter { it.buyerId == userId }
            .map { it.sellerId }.filter { it != userId }.toSet()
        val sellOk = sellCounterparties.isNotEmpty()
        val buyOk = buyCounterparties.isNotEmpty()
        val differentPeople = sellCounterparties.any { it !in buyCounterparties } ||
            buyCounterparties.any { it !in sellCounterparties }
        return sellOk to (buyOk && differentPeople)
    }
}
