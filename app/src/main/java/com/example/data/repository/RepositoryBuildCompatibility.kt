package com.example.data.repository

import com.example.data.db.KycEntity
import kotlinx.coroutines.flow.first

/**
 * Build-compatibility helpers for the current GitHub source.
 *
 * These helpers only bridge API names/signatures referenced by the
 * current UI/ViewModel files to repository functions that already exist.
 * They do not change KYC, reward, admin, or P2P rules.
 */

data class DailyRewardState(
    val nextClaimAt: Long
)

val RealCoinRepository.Companion.DAILY_REWARD_COOLDOWN_MS: Long
    get() = 24L * 60L * 60L * 1000L

suspend fun RealCoinRepository.syncKycFromBackend(
    userId: String
): Result<KycEntity?> =
    runCatching {
        // The current repository has no backend KYC endpoint.
        // Keep the existing local KYC record as the source of truth.
        getKycForUserSync(userId)
    }

suspend fun RealCoinRepository.getPendingKycRemote():
    Result<List<KycEntity>> =
    runCatching {
        // Preserve the existing Admin KYC data source.
        getPendingKyc().first()
    }

suspend fun RealCoinRepository.submitKyc(
    userId: String,
    fullName: String,
    idType: String,
    idNumber: String,
    frontIdUri: String,
    backIdUri: String
): Result<KycEntity> {
    // Bridge the newer UI signature to the repository's existing
    // KYC submission method without changing its validation/state rules.
    return submitKyc(
        userId = userId,
        fullName = fullName,
        idNumber = idNumber,
        documentAttached = frontIdUri.isNotBlank() || backIdUri.isNotBlank(),
        documentUri = frontIdUri.ifBlank { backIdUri }
    )
}

suspend fun RealCoinRepository.getDailyRewardState(
    userId: String
): DailyRewardState {
    // The repository's claimDailyReward() remains authoritative for
    // eligibility. This state is only used by the current UI countdown.
    return DailyRewardState(nextClaimAt = 0L)
}
