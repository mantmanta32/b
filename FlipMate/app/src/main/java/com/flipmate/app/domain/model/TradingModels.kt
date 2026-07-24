package com.flipmate.app.domain.model
import java.math.BigDecimal
data class AccountAsset(val currency:String,val positionMargin:BigDecimal,val frozenBalance:BigDecimal,val availableBalance:BigDecimal,val cashBalance:BigDecimal,val equity:BigDecimal,val unrealized:BigDecimal,val bonus:BigDecimal,val availableCash:BigDecimal,val availableOpen:BigDecimal)
enum class FlipMode { RESET, MARTINGALE }
data class CycleState(val cycleNumber:Int,val runningPnl:BigDecimal,val resetSize:BigDecimal,val resetSide:PositionSide,val maxContracts:BigDecimal)
