package com.flipmate.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class PublicApiService(private val client: OkHttpClient, private val baseUrl: String = "https://api.mexc.com") {
    suspend fun getTicker(symbol: String): JSONObject = withContext(Dispatchers.IO) {
        require(symbol.matches(Regex("[A-Za-z0-9_]{3,30}"))) { "Invalid symbol" }
        val url = "$baseUrl/api/v1/contract/ticker".toHttpUrl().newBuilder().addQueryParameter("symbol", symbol).build()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Empty response")
            if (!response.isSuccessful) throw ApiException(response.code, message = "HTTP ${response.code}")
            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) throw ApiException(exchangeCode = root.optInt("code", -1), message = root.optString("message", "Exchange error"))
            return@withContext root.optJSONObject("data") ?: throw IOException("Missing data")
        }
    }
}
