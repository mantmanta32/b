package com.flipmate.app.domain.usecase
import com.flipmate.app.domain.model.*
import java.math.BigDecimal
class ValidateFlipPlanUseCase { operator fun invoke(plan:FlipPlan, max:BigDecimal):Result<Unit> = runCatching { require(plan.targetVolume>BigDecimal.ZERO); require(plan.targetVolume<=max); require(plan.symbol.matches(Regex("[A-Z0-9]+_[A-Z0-9]+"))) } }
