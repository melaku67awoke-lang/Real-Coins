package com.example.data.reward

import com.example.data.db.P2POrderEntity
import com.example.data.db.RewardClaimEntity

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
        RewardLevel("Diamond", 500.0, 100.0),
        RewardLevel("King 👑", 1000.0, 300.0),
        RewardLevel("VVIP", 1500.0, 500.0)
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

    data class RewardCycleState(
        val claimedDays: Int,
        val rewardActive: Boolean,
        val qualifyingBuyCompleted: Boolean,
        val qualifyingSellCompleted: Boolean
    )

    fun rewardCycleState(
        userId: String,
        claims: List<RewardClaimEntity>,
        orders: List<P2POrderEntity>
    ): RewardCycleState {
        val sortedClaims = claims.sortedBy { it.claimTimestamp }
        var startIndex = 0

        while (startIndex + 7 <= sortedClaims.size) {
            val seventhClaim = sortedClaims[startIndex + 6]
            val resetAt = qualifyingResetAt(
                userId,
                orders,
                seventhClaim.claimTimestamp
            ) ?: break

            startIndex = sortedClaims.indexOfFirst {
                it.claimTimestamp > resetAt
            }.let { if (it == -1) sortedClaims.size else it }
        }

        val currentClaims = sortedClaims.drop(startIndex)

        if (currentClaims.size < 7) {
            return RewardCycleState(
                claimedDays = currentClaims.size,
                rewardActive = true,
                qualifyingBuyCompleted = true,
                qualifyingSellCompleted = true
            )
        }

        val seventhClaimAt = currentClaims[6].claimTimestamp
        val buy = completedActivitySince(
            userId,
            orders,
            seventhClaimAt,
            isBuy = true
        )
        val sell = completedActivitySince(
            userId,
            orders,
            seventhClaimAt,
            isBuy = false
        )

        return RewardCycleState(
            claimedDays = 7,
            rewardActive = buy.first &&
                sell.first &&
                buy.second != sell.second,
            qualifyingBuyCompleted = buy.first,
            qualifyingSellCompleted = sell.first
        )
    }

    private fun qualifyingResetAt(
        userId: String,
        orders: List<P2POrderEntity>,
        afterMs: Long
    ): Long? {
        val buys = orders.filter {
            it.status == "COMPLETED" &&
                (it.completedAt ?: 0L) > afterMs &&
                it.buyerId == userId &&
                it.sellerId != userId
        }.mapNotNull {
            val at = it.completedAt ?: return@mapNotNull null
            at to it.sellerId
        }

        val sells = orders.filter {
            it.status == "COMPLETED" &&
                (it.completedAt ?: 0L) > afterMs &&
                it.sellerId == userId &&
                it.buyerId != userId
        }.mapNotNull {
            val at = it.completedAt ?: return@mapNotNull null
            at to it.buyerId
        }

        return buys.flatMap { buy ->
            sells
                .filter { sell -> buy.second != sell.second }
                .map { sell -> maxOf(buy.first, sell.first) }
        }.minOrNull()
    }

    private fun completedActivitySince(
        userId: String,
        orders: List<P2POrderEntity>,
        afterMs: Long,
        isBuy: Boolean
    ): Pair<Boolean, String> {
        val item = orders.filter {
            it.status == "COMPLETED" &&
                (it.completedAt ?: 0L) > afterMs &&
                if (isBuy) {
                    it.buyerId == userId && it.sellerId != userId
                } else {
                    it.sellerId == userId && it.buyerId != userId
                }
        }.minByOrNull { it.completedAt ?: Long.MAX_VALUE }

        return if (item == null) {
            false to ""
        } else {
            true to if (isBuy) item.sellerId else item.buyerId
        }
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
        // A reset requires one completed BUY and one completed SELL with
        // different counterparties. BUY from B + SELL to B does not qualify.
        val differentPeople =
            sellCounterparties.any { it !in buyCounterparties } ||
                buyCounterparties.any { it !in sellCounterparties }
        return sellOk to (buyOk && differentPeople)
    }
}
