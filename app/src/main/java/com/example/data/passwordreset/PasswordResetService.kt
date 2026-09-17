package com.example.data.passwordreset

import java.util.Locale

class PasswordResetService(
    private val api: PasswordResetApi = PasswordResetNetwork.api
) {
    suspend fun requestOtp(email: String): Result<RequestOtpResponse> {
        val normalized = email.trim().lowercase(Locale.ROOT)
        if (!isValidEmail(normalized)) {
            return Result.failure(IllegalArgumentException("Enter a valid email address"))
        }

        return runCatching {
            val response = api.requestOtp(RequestOtpRequest(normalized))
            if (!response.isSuccessful) {
                throw IllegalStateException("Password reset request failed (${response.code()})")
            }
            response.body()?.takeIf { it.ok && !it.resetSessionId.isNullOrBlank() }
                ?: throw IllegalStateException("Invalid password reset response")
        }
    }

    suspend fun verifyOtp(resetSessionId: String, otp: String): Result<VerifyOtpResponse> {
        if (resetSessionId.isBlank() || !otp.matches(Regex("^\\d{6}$"))) {
            return Result.failure(IllegalArgumentException("Enter the 6-digit confirmation code"))
        }

        return runCatching {
            val response = api.verifyOtp(VerifyOtpRequest(resetSessionId.trim(), otp))
            if (!response.isSuccessful) {
                throw IllegalStateException("Confirmation code is invalid or expired")
            }
            response.body()?.takeIf { it.ok && !it.resetAuthorization.isNullOrBlank() }
                ?: throw IllegalStateException("Invalid password reset response")
        }
    }

    suspend fun consumeResetAuthorization(resetAuthorization: String): Result<String> {
        if (resetAuthorization.isBlank()) {
            return Result.failure(IllegalArgumentException("Password reset authorization is missing"))
        }
        return runCatching {
            val response = api.consumeResetAuthorization(ConsumeResetAuthorizationRequest(resetAuthorization.trim()))
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true || body.email.isNullOrBlank()) {
                throw IllegalStateException("Password reset authorization is expired or already used")
            }
            body.email.trim().lowercase(Locale.ROOT)
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.length in 3..254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)
    }
}
