package com.example.data.passwordreset

import java.util.Locale

class PasswordResetService(
    private val api: PasswordResetApi = PasswordResetNetwork.api
) {
    suspend fun requestRecovery(email: String): Result<RecoveryResponse> {
        val normalized = email.trim().lowercase(Locale.ROOT)

        if (!isValidEmail(normalized)) {
            return Result.failure(
                IllegalArgumentException("Enter a valid email address")
            )
        }

        return runCatching {
            val response = api.requestRecovery(
                RecoveryRequest(normalized)
            )

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Password recovery request failed (${response.code()})"
                )
            }

            response.body()?.takeIf {
                it.ok && !it.recoveryRequestId.isNullOrBlank()
            } ?: throw IllegalStateException(
                "Invalid password recovery response"
            )
        }
    }

    suspend fun checkRecoveryStatus(
        recoveryRequestId: String
    ): Result<RecoveryStatusResponse> {
        if (recoveryRequestId.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Recovery request ID is missing")
            )
        }

        return runCatching {
            val response = api.checkRecoveryStatus(
                RecoveryStatusRequest(recoveryRequestId.trim())
            )

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Could not check recovery request (${response.code()})"
                )
            }

            response.body()?.takeIf { it.ok }
                ?: throw IllegalStateException(
                    "Invalid recovery status response"
                )
        }
    }

    suspend fun completeRecovery(
        recoveryRequestId: String,
        newPassword: String
    ): Result<RecoveryCompleteResponse> {
        if (recoveryRequestId.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Recovery request ID is missing")
            )
        }

        return runCatching {
            val response = api.completeRecovery(
                RecoveryCompleteRequest(
                    recoveryRequestId.trim(),
                    newPassword
                )
            )

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Could not complete recovery request (${response.code()})"
                )
            }

            response.body()?.takeIf { it.ok }
                ?: throw IllegalStateException(
                    "Invalid recovery completion response"
                )
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.length in 3..254 &&
            Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(email)
    }
}
