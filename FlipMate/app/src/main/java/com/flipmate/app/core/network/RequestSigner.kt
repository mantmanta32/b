package com.flipmate.app.core.network

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object RequestSigner {
    fun query(params: Map<String, Any?>): String = params.filterValues { it != null }.toSortedMap().entries.joinToString("&") { (k,v) -> encode(k)+"="+encode(decimal(v!!)) }
    fun signature(secret:String, accessKey:String, requestTime:String, param:String):String {
        val mac=Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8),"HmacSHA256"))
        return mac.doFinal((accessKey+requestTime+param).toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    private fun encode(value:String)=URLEncoder.encode(value,"UTF-8").replace("+","%20")
    private fun decimal(value:Any)=when(value){is Double->java.math.BigDecimal.valueOf(value).toPlainString();is Float->value.toBigDecimal().toPlainString();else->value.toString()}
}
