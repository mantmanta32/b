package com.flipmate.app.domain.usecase

import com.flipmate.app.domain.model.*
import com.flipmate.app.domain.repository.PositionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

class ExecuteFlipUseCase(private val positions: PositionRepository) {
    private val mutex = Mutex()
    suspend operator fun invoke(plan: FlipPlan, mode: TradingMode, price: BigDecimal): Result<Unit> = mutex.withLock {
        if (mode == TradingMode.SIM) return@withLock Result.success(Unit)
        runCatching {
            val fresh = positions.getOpenPositions().firstOrNull { it.positionId == plan.positionId }
                ?: error("NO_POSITION")
            require(fresh.side == plan.currentSide && fresh.holdVol == plan.currentVolume) { "POSITION_CHANGED" }
            if (plan.useReverse) {
                check(positions.reverse(plan.symbol, plan.positionId, plan.targetVolume.toPlainString())) { "REVERSE_FAILED" }
            } else {
                positions.createOrder(plan.symbol, if (plan.currentSide == PositionSide.LONG) 4 else 2, plan.currentVolume.toPlainString(), price.toPlainString(), plan.positionId)
                check(positions.getOpenPositions().none { it.positionId == plan.positionId }) { "CLOSE_NOT_CONFIRMED" }
                positions.createOrder(plan.symbol, if (plan.targetSide == PositionSide.LONG) 1 else 3, plan.targetVolume.toPlainString(), price.toPlainString())
            }
        }
    }
}
