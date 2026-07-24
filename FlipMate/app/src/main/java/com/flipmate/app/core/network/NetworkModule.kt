package com.flipmate.app.core.network

import com.flipmate.app.core.security.CredentialManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NetworkModule(credentials: CredentialManager) {
    val publicClient: OkHttpClient = base().build()
    val privateClient: OkHttpClient = base().addInterceptor(AuthInterceptor(credentials)).build()
    private fun base() = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
}
