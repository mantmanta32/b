package com.flipmate.app.domain.model
import java.math.BigDecimal
data class Ticker(val symbol:String,val lastPrice:BigDecimal,val priceChangePercent:BigDecimal)
data class OpenPosition(val positionId:Long,val symbol:String,val holdVol:BigDecimal,val side:PositionSide,val unrealizedPnl:BigDecimal)
enum class PositionSide { LONG, SHORT; val opposite get()=if(this==LONG) SHORT else LONG }
enum class TradingMode { SIM, REAL }
data class FlipPlan(val symbol:String,val currentSide:PositionSide,val currentVolume:BigDecimal,val targetSide:PositionSide,val targetVolume:BigDecimal,val mode:String,val useReverse:Boolean)
