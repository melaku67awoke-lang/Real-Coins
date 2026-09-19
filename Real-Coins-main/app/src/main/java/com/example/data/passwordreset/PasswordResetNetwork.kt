package com.example.data.passwordreset

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Network client for the server-side admin-verification password recovery flow.
 *
 * The backend URL is configuration, not a secret. Gmail credentials and reset
 * signing keys must never be placed in the Android application.
 */
object PasswordResetNetwork {
    private fun baseUrl(): String {
        val configured = BuildConfig.PASSWORD_RESET_BACKEND_URL.trim()
        require(configured.startsWith("https://")) {
            "Password reset backend must use HTTPS"
        }
        return if (configured.endsWith('/')) configured else "$configured/"
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .build()
    }

    val api: PasswordResetApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl())
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(PasswordResetApi::class.java)
    }
}
