package com.flipmate.app.data.repository

import com.flipmate.app.core.network.PublicApiService
import com.flipmate.app.domain.model.Ticker
import com.flipmate.app.domain.repository.TickerRepository
import java.math.BigDecimal

class TickerRepositoryImpl(private val api: PublicApiService): TickerRepository {
    override suspend fun getTicker(symbol: String): Ticker {
        val data = api.getTicker(symbol)
        fun decimal(key: String) = data.optString(key, "0").toBigDecimalOrNull() ?: BigDecimal.ZERO
        return Ticker(symbol, decimal("lastPrice"), decimal("priceChangePercent"))
    }
}
