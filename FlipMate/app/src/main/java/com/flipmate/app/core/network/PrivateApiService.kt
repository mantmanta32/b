package com.flipmate.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class PrivateApiService(private val client: OkHttpClient, private val baseUrl: String = "https://api.mexc.com") {
    suspend fun accountAssets(): JSONArray = getList("/api/v1/private/account/assets")
    suspend fun openPositions(): JSONArray = getList("/api/v1/private/position/open_positions")
    suspend fun createOrder(payload: JSONObject): JSONObject = post("/api/v1/private/order/create", payload)
    suspend fun reversePosition(payload: JSONObject): JSONObject = post("/api/v1/private/position/reverse", payload)

    private suspend fun getList(path: String): JSONArray = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(baseUrl + path).get().build()).optJSONArray("data") ?: JSONArray()
    }
    private suspend fun post(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        execute(Request.Builder().url(baseUrl + path).post(body).build()).optJSONObject("data") ?: JSONObject()
    }
    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw IOException("Empty response")
            if (!response.isSuccessful) throw ApiException(response.code, message = "HTTP ${response.code}")
            val root = JSONObject(text)
            if (!root.optBoolean("success", false)) throw ApiException(exchangeCode = root.optInt("code", -1), message = root.optString("message", "Exchange error"))
            return root
        }
    }
}
