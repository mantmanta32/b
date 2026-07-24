package com.flipmate.app.domain.repository

import com.flipmate.app.domain.model.OpenPosition
import com.flipmate.app.domain.model.Ticker

interface TickerRepository { suspend fun getTicker(symbol: String): Ticker }
interface PositionRepository {
    suspend fun getOpenPositions(): List<OpenPosition>
    suspend fun createOrder(symbol: String, side: Int, volume: String, price: String, positionId: Long? = null): String
    suspend fun reverse(symbol: String, positionId: Long, volume: String): Boolean
}
interface CredentialRepository { fun hasCredentials(): Boolean; fun clearCredentials() }
