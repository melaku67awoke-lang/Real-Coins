package com.example.data.passwordreset

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PasswordResetApi {
    @POST("v1/password-reset/request")
    suspend fun requestOtp(@Body request: RequestOtpRequest): Response<RequestOtpResponse>

    @POST("v1/password-reset/verify")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<VerifyOtpResponse>

    @POST("v1/password-reset/consume")
    suspend fun consumeResetAuthorization(@Body request: ConsumeResetAuthorizationRequest): Response<ConsumeResetAuthorizationResponse>
}

@JsonClass(generateAdapter = true)
data class RequestOtpRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class RequestOtpResponse(
    val ok: Boolean,
    val resetSessionId: String?,
    val expiresAtMs: Long?
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val resetSessionId: String,
    val otp: String
)

@JsonClass(generateAdapter = true)
data class VerifyOtpResponse(
    val ok: Boolean,
    val resetAuthorization: String?,
    val expiresInSeconds: Long?
)

@JsonClass(generateAdapter = true)
data class ConsumeResetAuthorizationRequest(
    val resetAuthorization: String
)

@JsonClass(generateAdapter = true)
data class ConsumeResetAuthorizationResponse(
    val ok: Boolean,
    val email: String?
)
