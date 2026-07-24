package com.flipmate.app.core.util
import java.math.BigDecimal
fun Any?.toDecimalOrZero(): BigDecimal = when (this) {
 is BigDecimal -> this
 null -> BigDecimal.ZERO
 else -> toString().toBigDecimalOrNull() ?: BigDecimal.ZERO
}
