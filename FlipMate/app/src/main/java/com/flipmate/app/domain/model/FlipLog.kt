package com.flipmate.app.domain.model

data class FlipLog(val id:Long=0,val createdAt:Long,val mode:TradingMode,val symbol:String,val cycleNumber:Int,val flipMode:FlipMode,val previousSide:PositionSide,val previousVolume:String,val targetSide:PositionSide,val targetVolume:String,val estimatedPnl:String,val cyclePnlBefore:String,val cyclePnlAfter:String,val status:Status,val orderId:String?,val mexcCode:Int?,val errorMessage:String?){enum class Status{PLANNED,CANCELLED,SUCCESS,FAILED,AMBIGUOUS}}
