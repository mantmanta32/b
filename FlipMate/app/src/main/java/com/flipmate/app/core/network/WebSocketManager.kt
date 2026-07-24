package com.flipmate.app.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager {
    private val client=OkHttpClient.Builder().readTimeout(0,TimeUnit.MILLISECONDS).build()
    private var socket:WebSocket?=null
    private var symbol:String?=null
    private var reconnectAttempt=0
    private val _connected=MutableStateFlow(false); val connected:StateFlow<Boolean> = _connected
    private val _price=MutableStateFlow<String?>(null); val price:StateFlow<String?> = _price
    fun connect(newSymbol:String){ if(symbol==newSymbol&&socket!=null)return; disconnect(); symbol=newSymbol; open(newSymbol) }
    private fun open(s:String){ socket=client.newWebSocket(Request.Builder().url("wss://contract.mexc.com/edge").build(),object:WebSocketListener(){
        override fun onOpen(ws:WebSocket,response:Response){reconnectAttempt=0;_connected.value=true;ws.send(JSONObject().put("method","sub.ticker").put("param",JSONObject().put("symbol",s)).toString())}
        override fun onMessage(ws:WebSocket,text:String){runCatching{val j=JSONObject(text);if(j.optString("channel")=="push.ticker")_price.value=j.optJSONObject("data")?.optString("lastPrice")}}
        override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){_connected.value=false;retry(s)}
        override fun onClosed(ws:WebSocket,code:Int,reason:String){_connected.value=false}
    }) }
    private fun retry(s:String){if(symbol!=s)return; val delay=(1000L shl reconnectAttempt.coerceAtMost(4)); reconnectAttempt++; Thread{Thread.sleep(delay);if(symbol==s&&socket==null)open(s)}.start()}
    fun disconnect(){symbol=null;socket?.close(1000,"client close");socket=null;_connected.value=false}
}
