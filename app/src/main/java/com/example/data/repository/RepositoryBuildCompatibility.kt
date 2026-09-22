package com.example.data.repository

import com.example.data.db.KycEntity
import kotlinx.coroutines.flow.first

/**
 * Compatibility helpers used by the current UI/ViewModel source.
 *
 * KYC is now synchronized through the authenticated Cloudflare backend.
 * The existing local Room KYC record is still maintained as the Android-side
 * cache and for the existing unit-test/local behavior.
 */

data class DailyRewardState(
    val nextClaimAt: Long
)

val RealCoinRepository.Companion.DAILY_REWARD_COOLDOWN_MS: Long
    get() = 24L * 60L * 60L * 1000L

suspend fun RealCoinRepository.syncKycFromBackend(
    userId: String
): Result<KycEntity?> =
    syncKycFromBackend(userId)

/** Name used by the current ViewModel. */
suspend fun RealCoinRepository.syncKycFromBackendCompat(
    userId: String
): Result<KycEntity?> =
    syncKycFromBackend(userId)

suspend fun RealCoinRepository.getPendingKycRemote():
    Result<List<KycEntity>> =
    runCatching {
        getPendingKyc().first()
    }

suspend fun RealCoinRepository.submitKyc(
    userId: String,
    fullName: String,
    idType: String,
    idNumber: String,
    frontIdUri: String,
    backIdUri: String
): Result<KycEntity> =
    submitKyc(
        userId,
        fullName,
        idType,
        idNumber,
        frontIdUri,
        backIdUri
    )

/** Name used by the current ViewModel. */
suspend fun RealCoinRepository.submitKycCompat(
    userId: String,
    fullName: String,
    idType: String,
    idNumber: String,
    frontIdUri: String,
    backIdUri: String
): Result<KycEntity> =
    submitKyc(
        userId,
        fullName,
        idType,
        idNumber,
        frontIdUri,
        backIdUri
    )

suspend fun RealCoinRepository.getDailyRewardState(
    userId: String
): DailyRewardState {
    return DailyRewardState(nextClaimAt = 0L)
}
