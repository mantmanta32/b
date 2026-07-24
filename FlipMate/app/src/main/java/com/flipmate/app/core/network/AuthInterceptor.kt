package com.flipmate.app.core.network
import com.flipmate.app.core.security.CredentialManager
import okhttp3.Interceptor
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
class AuthInterceptor(private val credentials:CredentialManager):Interceptor { override fun intercept(chain:Interceptor.Chain):okhttp3.Response { val r=chain.request(); if(!credentials.has()) throw MissingCredentialsException(); return chain.proceed(r) } }
class MissingCredentialsException:Exception("API credentials are missing")
