package com.flipmate.app.core.network

import com.flipmate.app.core.security.CredentialManager
import okhttp3.Interceptor
import okio.Buffer

class AuthInterceptor(private val credentials:CredentialManager):Interceptor {
    override fun intercept(chain:Interceptor.Chain):okhttp3.Response {
        val key=credentials.apiKey()?.takeIf { it.isNotBlank() } ?: throw MissingCredentialsException()
        val secret=credentials.apiSecret()?.takeIf { it.isNotBlank() } ?: throw MissingCredentialsException()
        val request=chain.request(); val param=if(request.method=="GET"||request.method=="DELETE") request.url.query ?: "" else request.body?.let { b->Buffer().also { b.writeTo(it) }.readUtf8() } ?: ""
        val time=System.currentTimeMillis().toString()
        return chain.proceed(request.newBuilder().header("ApiKey",key).header("Request-Time",time).header("Signature",RequestSigner.signature(secret,key,time,param)).header("Recv-Window","30").header("Content-Type","application/json").build())
    }
}
class MissingCredentialsException:Exception("API credentials are missing")
