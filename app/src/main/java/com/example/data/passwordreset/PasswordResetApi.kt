package com.example.data.passwordreset

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PasswordResetApi {

    @POST("v1/password-recovery/request")
    suspend fun requestRecovery(
        @Body request: RecoveryRequest
    ): Response<RecoveryResponse>

    @POST("v1/password-recovery/status")
    suspend fun checkRecoveryStatus(
        @Body request: RecoveryStatusRequest
    ): Response<RecoveryStatusResponse>
}

@JsonClass(generateAdapter = true)
data class RecoveryRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class RecoveryResponse(
    val ok: Boolean,
    val recoveryRequestId: String?,
    val status: String?
)

@JsonClass(generateAdapter = true)
data class RecoveryStatusRequest(
    val recoveryRequestId: String
)

@JsonClass(generateAdapter = true)
data class RecoveryStatusResponse(
    val ok: Boolean,
    val status: String?,
    val email: String?
)
