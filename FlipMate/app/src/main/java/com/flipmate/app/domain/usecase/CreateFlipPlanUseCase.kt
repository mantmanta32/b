package com.flipmate.app.domain.usecase
import com.flipmate.app.domain.model.*
import java.math.BigDecimal
class CreateFlipPlanUseCase { operator fun invoke(p:OpenPosition,cycle:BigDecimal,reset:BigDecimal,resetSide:PositionSide,max:BigDecimal):FlipPlan { val mart=cycle+p.unrealizedPnl< BigDecimal.ZERO; val vol=if(mart)p.holdVol*BigDecimal(2) else reset; require(vol<=max){"Contract limit exceeded"}; val side=if(mart)p.side.opposite else resetSide; return FlipPlan(p.positionId,p.symbol,p.side,p.holdVol,side,vol,if(mart)"MARTINGALE" else "RESET",side!=p.side) } }
