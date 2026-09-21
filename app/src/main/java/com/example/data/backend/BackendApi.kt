package com.example.data.backend

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class AuthRequest(val username: String? = null, val email: String? = null, val password: String, val referralCode: String? = null)
@JsonClass(generateAdapter = true)
data class AuthAccount(val id: String, val username: String, val email: String, val role: String, val referralCode: String? = null)
@JsonClass(generateAdapter = true)
data class AuthResponse(val ok: Boolean, val account: AuthAccount?, val sessionToken: String?)

@JsonClass(generateAdapter = true)
data class AdRequest(val type: String, val cryptoAmount: Double, val fiatPrice: Double, val minOrderEtb: Double, val maxOrderEtb: Double, val paymentMethod: String, val paymentName: String, val accountNumber: String)
@JsonClass(generateAdapter = true)
data class AdResponse(val ok: Boolean, val ad: BackendAd?)
@JsonClass(generateAdapter = true)
data class AdsResponse(val ok: Boolean, val ads: List<BackendAd> = emptyList())
@JsonClass(generateAdapter = true)
data class BackendAd(val id: String, val sellerId: String, val sellerName: String, val type: String, val cryptoAmount: Double, val fiatPrice: Double, val fiatCurrency: String, val paymentMethod: String, val paymentName: String, val accountNumber: String, val isActive: Int, val minOrderEtb: Double, val maxOrderEtb: Double, val originalMaxOrderEtb: Double, val createdAt: Long)

@JsonClass(generateAdapter = true)
data class OrderRequest(val adId: String, val cryptoAmount: Double, val fiatAmount: Double)
@JsonClass(generateAdapter = true)
data class OrderResponse(val ok: Boolean, val order: BackendOrder?)
@JsonClass(generateAdapter = true)
data class OrdersResponse(val ok: Boolean, val orders: List<BackendOrder> = emptyList())
@JsonClass(generateAdapter = true)
data class BackendOrder(val id: String, val adId: String, val sellerId: String, val sellerName: String, val buyerId: String, val buyerName: String, val cryptoAmount: Double, val fiatPrice: Double, val fiatOrderAmount: Double, val fiatCurrency: String, val paymentMethod: String, val paymentName: String, val accountNumber: String, val paymentProofUrl: String?, val paidAt: Long?, val status: String, val createdAt: Long, val expiresAt: Long, val completedAt: Long?, val disputeReason: String?, val disputedAt: Long?, val resolvedAt: Long?, val resolvedByAdminId: String?)
@JsonClass(generateAdapter = true)
data class AttachmentUploadRequest(val contentType: String, val dataBase64: String)
@JsonClass(generateAdapter = true)
data class AttachmentUploadResponse(val ok: Boolean, val url: String?)

@JsonClass(generateAdapter = true)
data class PaymentRequest(val proofUrl: String)
@JsonClass(generateAdapter = true)
data class SimpleResponse(val ok: Boolean, val status: String? = null)

@JsonClass(generateAdapter = true)
data class MessageRequest(val message: String = "", val attachmentUrl: String? = null)
@JsonClass(generateAdapter = true)
data class ChatResponse(val ok: Boolean, val messages: List<BackendMessage> = emptyList())
@JsonClass(generateAdapter = true)
data class MessageResponse(val ok: Boolean, val message: BackendMessage?)
@JsonClass(generateAdapter = true)
data class BackendMessage(val id: String, val orderId: String, val senderId: String, val senderName: String, val message: String, val attachmentUrl: String?, val createdAt: Long)

@JsonClass(generateAdapter = true)
data class PricingResponse(val ok: Boolean, val realCoinUsdPrice: Double, val usdToEtbRate: Double = 186.0)

@JsonClass(generateAdapter = true)
data class ReferralResponse(
    val ok: Boolean,
    val referralCode: String? = null,
    val referredCount: Int = 0,
    val rewardClaimed: Boolean = false
)

interface BackendApi {
    @POST("v1/auth/register") suspend fun register(@Body request: AuthRequest): Response<AuthResponse>
    @POST("v1/auth/login") suspend fun login(@Body request: AuthRequest): Response<AuthResponse>
    @POST("v1/auth/logout") suspend fun logout(@Header("Authorization") auth: String): Response<Unit>
    @GET("v1/auth/me") suspend fun me(@Header("Authorization") auth: String): Response<AuthResponse>
    @GET("v1/config/pricing") suspend fun pricing(): Response<PricingResponse>
    @POST("v1/admin/pricing") suspend fun updatePricing(@Header("Authorization") auth: String, @Body request: PricingResponse): Response<PricingResponse>
    @GET("v1/referral") suspend fun referral(@Header("Authorization") auth: String): Response<ReferralResponse>
    @POST("v1/p2p/attachments") suspend fun uploadAttachment(@Header("Authorization") auth: String, @Body request: AttachmentUploadRequest): Response<AttachmentUploadResponse>
    @GET("v1/p2p/ads") suspend fun ads(@Header("Authorization") auth: String, @Query("type") type: String? = null): Response<AdsResponse>
    @POST("v1/p2p/ads") suspend fun createAd(@Header("Authorization") auth: String, @Body request: AdRequest): Response<AdResponse>
    @POST("v1/p2p/ads/{id}/delete") suspend fun deleteAd(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
    @GET("v1/p2p/orders") suspend fun orders(@Header("Authorization") auth: String): Response<OrdersResponse>
    @POST("v1/p2p/orders") suspend fun createOrder(@Header("Authorization") auth: String, @Body request: OrderRequest): Response<OrderResponse>
    @POST("v1/p2p/orders/{id}/paid") suspend fun markPaid(@Header("Authorization") auth: String, @Path("id") id: String, @Body request: PaymentRequest): Response<SimpleResponse>
    @POST("v1/p2p/orders/{id}/expire") suspend fun expire(@Header("Authorization") auth: String, @Path("id") id: String): Response<SimpleResponse>
    @POST("v1/p2p/orders/{id}/complete") suspend fun release(@Header("Authorization") auth: String, @Path("id") id: String): Response<SimpleResponse>
    @POST("v1/p2p/orders/{id}/dispute") suspend fun dispute(@Header("Authorization") auth: String, @Path("id") id: String, @Body request: Map<String, String>): Response<SimpleResponse>
    @POST("v1/p2p/orders/{id}/resolve") suspend fun resolve(@Header("Authorization") auth: String, @Path("id") id: String, @Body request: Map<String, String>): Response<SimpleResponse>
    @GET("v1/p2p/orders/{id}/chat") suspend fun chat(@Header("Authorization") auth: String, @Path("id") id: String): Response<ChatResponse>
    @POST("v1/p2p/orders/{id}/chat") suspend fun sendChat(@Header("Authorization") auth: String, @Path("id") id: String, @Body request: MessageRequest): Response<MessageResponse>
}

object BackendNetwork {
    private fun baseUrl(): String {
        val configured = BuildConfig.PASSWORD_RESET_BACKEND_URL.trim()
        require(configured.startsWith("https://")) { "Backend must use HTTPS" }
        return if (configured.endsWith('/')) configured else "$configured/"
    }
    val api: BackendApi by lazy {
        Retrofit.Builder().baseUrl(baseUrl()).client(OkHttpClient.Builder().build()).addConverterFactory(MoshiConverterFactory.create()).build().create(BackendApi::class.java)
    }
}
