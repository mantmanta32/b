package com.flipmate.app.data.repository

import com.flipmate.app.core.network.PrivateApiService
import com.flipmate.app.core.util.toDecimalOrZero
import com.flipmate.app.domain.model.AccountAsset
import java.math.BigDecimal

class AccountRepositoryImpl(private val api: PrivateApiService) {
    suspend fun getAssets(): List<AccountAsset> = api.accountAssets().let { array ->
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            AccountAsset(
                currency = item.optString("currency"),
                positionMargin = item.opt("positionMargin").toDecimalOrZero(),
                frozenBalance = item.opt("frozenBalance").toDecimalOrZero(),
                availableBalance = item.opt("availableBalance").toDecimalOrZero(),
                cashBalance = item.opt("cashBalance").toDecimalOrZero(),
                equity = item.opt("equity").toDecimalOrZero(),
                unrealized = item.opt("unrealized").toDecimalOrZero(),
                bonus = item.opt("bonus").toDecimalOrZero(),
                availableCash = item.opt("availableCash").toDecimalOrZero(),
                availableOpen = (if (item.has("availableOpen")) item.opt("availableOpen") else item.opt("availableBalance")).toDecimalOrZero()
            )
        }
    }
    suspend fun settlementAsset(symbol: String): AccountAsset? = getAssets().firstOrNull { it.currency.equals(symbol.substringAfterLast('_'), true) }
}
