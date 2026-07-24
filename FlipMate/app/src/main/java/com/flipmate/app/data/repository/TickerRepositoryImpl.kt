package com.flipmate.app.data.repository

import com.flipmate.app.core.network.PublicApiService
import com.flipmate.app.core.network.WebSocketManager
import com.flipmate.app.domain.model.Ticker
import com.flipmate.app.domain.repository.TickerRepository
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal

class TickerRepositoryImpl(
    private val api: PublicApiService,
    private val wsManager: WebSocketManager? = null
) : TickerRepository {
    
    override suspend fun getTicker(symbol: String): Ticker {
        val data = api.getTicker(symbol)
        fun decimal(key: String) = data.optString(key, "0").toBigDecimalOrNull() ?: BigDecimal.ZERO
        return Ticker(
            symbol,
            decimal("lastPrice"),
            decimal("riseFallRate"),
            decimal("high24Price"),
            decimal("lower24Price"),
            decimal("fairPrice")
        )
    }
    
    fun connectWebSocket(symbol: String) {
        wsManager?.connect(symbol)
    }
    
    fun disconnectWebSocket() {
        wsManager?.disconnect()
    }
    
    val wsPrice: StateFlow<String?>? = wsManager?.price
    val wsConnected: StateFlow<Boolean>? = wsManager?.connected
}
