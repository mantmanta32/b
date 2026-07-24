package com.flipmate.app.domain.model
import java.math.BigDecimal

data class Ticker(
    val symbol: String,
    val lastPrice: BigDecimal,
    val priceChangePercent: BigDecimal,
    val high24h: BigDecimal = BigDecimal.ZERO,
    val low24h: BigDecimal = BigDecimal.ZERO,
    val fairPrice: BigDecimal = BigDecimal.ZERO
)

data class AccountAsset(
    val currency: String,
    val equity: BigDecimal,
    val availableBalance: BigDecimal,
    val availableOpen: BigDecimal,
    val cashBalance: BigDecimal,
    val positionMargin: BigDecimal,
    val frozenBalance: BigDecimal,
    val unrealized: BigDecimal,
    val bonus: BigDecimal
)

data class OpenPosition(
    val positionId: Long,
    val symbol: String,
    val holdVol: BigDecimal,
    val side: PositionSide,
    val entryPrice: BigDecimal,
    val liquidationPrice: BigDecimal,
    val margin: BigDecimal,
    val leverage: Int,
    val unrealizedPnl: BigDecimal
)

enum class PositionSide(val code: Int) {
    LONG(1), SHORT(2);
    val opposite get() = if (this == LONG) SHORT else LONG
}

enum class TradingMode { SIM, REAL }

enum class OrderSide(val code: Int) {
    OPEN_LONG(1), CLOSE_SHORT(2), OPEN_SHORT(3), CLOSE_LONG(4)
}

enum class OpenType(val code: Int) {
    ISOLATED(1), CROSS(2)
}

data class FlipPlan(
    val positionId: Long = 0,
    val symbol: String,
    val currentSide: PositionSide,
    val currentVolume: BigDecimal,
    val targetSide: PositionSide,
    val targetVolume: BigDecimal,
    val mode: String,
    val useReverse: Boolean
)

data class CycleState(
    val cycleNumber: Int = 1,
    val runningPnl: BigDecimal = BigDecimal.ZERO,
    val isProfitable: Boolean = false
)
