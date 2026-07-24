package com.flipmate.app.domain.repository
import com.flipmate.app.domain.model.AccountAsset
interface AccountRepository { suspend fun getAssets(): List<AccountAsset>; suspend fun settlementAsset(symbol: String): AccountAsset? }
