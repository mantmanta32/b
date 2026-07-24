package com.flipmate.app.data.repository
import com.flipmate.app.data.local.dao.FlipLogDao
import com.flipmate.app.data.local.entity.FlipLogEntity
import com.flipmate.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
class FlipHistoryRepositoryImpl(private val dao:FlipLogDao){
 fun all():Flow<List<FlipLog>>=dao.all().map{it.map(::domain)}
 suspend fun insert(x:FlipLog)=dao.insert(entity(x))
 suspend fun clear()=dao.clear()
 private fun domain(x:FlipLogEntity)=FlipLog(x.id,x.createdAt,TradingMode.valueOf(x.mode),x.symbol,x.cycleNumber,FlipMode.valueOf(x.flipMode),PositionSide.valueOf(x.previousSide),x.previousVolume,PositionSide.valueOf(x.targetSide),x.targetVolume,x.estimatedPnl,x.cyclePnlBefore,x.cyclePnlAfter,FlipLog.Status.valueOf(x.status),x.orderId,x.mexcCode,x.errorMessage)
 private fun entity(x:FlipLog)=FlipLogEntity(x.id,x.createdAt,x.mode.name,x.symbol,x.cycleNumber,x.flipMode.name,x.previousSide.name,x.previousVolume,x.targetSide.name,x.targetVolume,x.estimatedPnl,x.cyclePnlBefore,x.cyclePnlAfter,x.status.name,x.orderId,x.mexcCode,x.errorMessage)
}
