package com.flipmate.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipmate.app.FlipMateApplication
import com.flipmate.app.core.network.PrivateApiService
import com.flipmate.app.core.network.PublicApiService
import com.flipmate.app.core.security.CredentialManager
import com.flipmate.app.data.local.dao.FlipLogDao
import com.flipmate.app.data.local.entity.FlipLogEntity
import com.flipmate.app.data.repository.TickerRepositoryImpl
import com.flipmate.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicBoolean

data class DashboardUiState(
    val symbol: String = "BTC_USDT",
    val ticker: Ticker? = null,
    val position: OpenPosition? = null,
    val account: AccountAsset? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val tradingMode: TradingMode = TradingMode.SIM,
    val cycleState: CycleState = CycleState(),
    val resetSize: BigDecimal = BigDecimal("2"),
    val maxContracts: BigDecimal = BigDecimal("128"),
    val leverage: Int = 10,
    val resetAction: ResetAction = ResetAction.LONG,
    val isFlipping: Boolean = false,
    val wsConnected: Boolean = false,
    val showConfirmation: Boolean = false,
    val pendingPlan: FlipPlanInfo? = null,
    val flipLogs: List<FlipLogEntity> = emptyList()
)

data class CycleState(
    val cycleNumber: Int = 1,
    val runningPnl: BigDecimal = BigDecimal.ZERO
)

enum class ResetAction { LONG, SHORT, STOP }

data class FlipPlanInfo(
    val currentSide: String,
    val currentVol: String,
    val targetSide: String,
    val targetVol: String,
    val mode: String,
    val estimatedPnl: String,
    val isReal: Boolean
)

class DashboardViewModel(
    private val tickerRepository: TickerRepositoryImpl,
    private val privateApi: PrivateApiService? = null,
    private val app: FlipMateApplication? = null,
    private val flipLogDao: FlipLogDao? = null
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val flipMutex = AtomicBoolean(false)

    init {
        refresh()
        observeLogs()
        startAutoRefresh()
        observeWsPrice()
        
        // SIM modda başlangıç pozisyonu oluştur
        _state.value = _state.value.copy(
            position = OpenPosition(
                positionId = 0,
                symbol = "BTC_USDT",
                holdVol = BigDecimal("2"),
                side = PositionSide.LONG,
                entryPrice = BigDecimal.ZERO,
                liquidationPrice = BigDecimal.ZERO,
                margin = BigDecimal.ZERO,
                leverage = 10,
                unrealizedPnl = BigDecimal.ZERO
            )
        )
    }

    private fun observeWsPrice() {
        tickerRepository.wsPrice?.let { priceFlow ->
            viewModelScope.launch {
                priceFlow.collect { price ->
                    price?.let { p ->
                        val bd = p.toBigDecimalOrNull() ?: return@let
                        val current = _state.value.ticker
                        if (current != null) {
                            _state.value = _state.value.copy(
                                ticker = current.copy(lastPrice = bd)
                            )
                        } else {
                            refresh()
                        }
                        // SIM modda entry price ve unrealizedPnl hesapla
                        updateSimPositionPnl(bd)
                    }
                }
            }
        }
        tickerRepository.wsConnected?.let { connFlow ->
            viewModelScope.launch {
                connFlow.collect { connected ->
                    _state.value = _state.value.copy(wsConnected = connected)
                }
            }
        }
    }

    private fun updateSimPositionPnl(currentPrice: BigDecimal) {
        val state = _state.value
        if (state.tradingMode != TradingMode.SIM) return
        val pos = state.position ?: return
        
        // Entry price 0 ise (yeni pozisyon) currentPrice'ı set et
        val entryPrice = if (pos.entryPrice == BigDecimal.ZERO) currentPrice else pos.entryPrice
        
        // Unrealized PnL hesapla
        val pnl = if (pos.side == PositionSide.LONG) {
            (currentPrice - entryPrice) * pos.holdVol
        } else {
            (entryPrice - currentPrice) * pos.holdVol
        }
        
        // Margin hesapla
        val margin = if (pos.margin == BigDecimal.ZERO && entryPrice > BigDecimal.ZERO) {
            entryPrice * pos.holdVol / BigDecimal(pos.leverage)
        } else pos.margin
        
        // Liq price hesapla (basit)
        val liqPrice = if (pos.liquidationPrice == BigDecimal.ZERO && entryPrice > BigDecimal.ZERO) {
            if (pos.side == PositionSide.LONG) {
                entryPrice * (BigDecimal.ONE - BigDecimal.ONE / BigDecimal(pos.leverage))
            } else {
                entryPrice * (BigDecimal.ONE + BigDecimal.ONE / BigDecimal(pos.leverage))
            }
        } else pos.liquidationPrice
        
        _state.value = _state.value.copy(
            position = pos.copy(
                entryPrice = entryPrice,
                unrealizedPnl = pnl,
                margin = margin,
                liquidationPrice = liqPrice
            )
        )
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                if (_state.value.tradingMode == TradingMode.REAL) {
                    refreshRealData()
                }
            }
        }
    }

    private fun observeLogs() {
        flipLogDao?.let { dao ->
            viewModelScope.launch {
                dao.all().collect { logs ->
                    _state.value = _state.value.copy(flipLogs = logs)
                }
            }
        }
    }

    fun setSymbol(value: String) {
        val v = value.trim().uppercase()
        if (v.isBlank() || v == _state.value.symbol) return
        _state.value = _state.value.copy(symbol = v, position = null, account = null)
        tickerRepository.connectWebSocket(v)
        refresh()
        if (_state.value.tradingMode == TradingMode.REAL) {
            refreshRealData()
        }
    }

    fun setTradingMode(mode: TradingMode) {
        if (mode == TradingMode.REAL) {
            val creds = app?.credentials
            if (creds == null || !creds.has()) {
                _state.value = _state.value.copy(error = "REAL mod için API Key gerekli")
                return
            }
        }
        _state.value = _state.value.copy(tradingMode = mode)
        if (mode == TradingMode.REAL) {
            refreshRealData()
        }
    }

    fun setLeverage(value: Int) {
        _state.value = _state.value.copy(leverage = value)
    }

    fun setResetSize(value: BigDecimal) {
        _state.value = _state.value.copy(resetSize = value)
    }

    fun setResetAction(action: ResetAction) {
        _state.value = _state.value.copy(resetAction = action)
    }

    fun setMaxContracts(value: BigDecimal) {
        _state.value = _state.value.copy(maxContracts = value)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun refresh() {
        val symbol = _state.value.symbol
        if (symbol.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                tickerRepository.getTicker(symbol)
            }.onSuccess {
                tickerRepository.connectWebSocket(symbol)
                _state.value = _state.value.copy(ticker = it, loading = false)
                // SIM modda PnL güncelle
                updateSimPositionPnl(it.lastPrice)
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = "Ticker alınamadı")
            }
        }
    }

    fun testApiConnection(): String? {
        val creds = app?.credentials ?: return "Credential yok"
        if (!creds.has()) return "API Key/Secret girilmemiş"
        var result: String? = null
        viewModelScope.launch {
            result = withContext(Dispatchers.IO) {
                try {
                    val api = PrivateApiService(app.network.privateClient)
                    val assets = api.accountAssets()
                    if (assets.length() > 0) "Bağlantı başarılı! ${assets.length()} varlık bulundu"
                    else "Hesap boş"
                } catch (e: Exception) {
                    "Hata: ${e.message}"
                }
            }
            _state.value = _state.value.copy(error = result)
        }
        return null
    }

    private fun refreshRealData() {
        if (_state.value.tradingMode != TradingMode.REAL || privateApi == null) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val assetsJson: JSONArray = privateApi.accountAssets()
                    val positionsJson: JSONArray = privateApi.openPositions()

                    val symbol = _state.value.symbol
                    val settleCurrency = symbol.substringAfter("_", "USDT")

                    var accountAsset: AccountAsset? = null
                    for (i in 0 until assetsJson.length()) {
                        val asset = assetsJson.getJSONObject(i)
                        if (asset.optString("currency", "").equals(settleCurrency, ignoreCase = true)) {
                            accountAsset = parseAccountAsset(asset)
                            break
                        }
                    }

                    var position: OpenPosition? = null
                    for (i in 0 until positionsJson.length()) {
                        val pos = positionsJson.getJSONObject(i)
                        if (pos.optString("symbol") == symbol &&
                            pos.optBigDecimal("holdVol", BigDecimal.ZERO) > BigDecimal.ZERO
                        ) {
                            position = parsePosition(pos)
                            break
                        }
                    }

                    _state.value = _state.value.copy(
                        account = accountAsset,
                        position = position
                    )
                }
            } catch (_: Exception) {
                // Sessizce ignore, bir sonraki refresh'te tekrar dene
            }
        }
    }

    fun flip() {
        val currentState = _state.value
        if (currentState.isFlipping) return

        val position = currentState.position
        if (position == null) {
            _state.value = currentState.copy(error = "Aktif pozisyon yok!")
            return
        }

        if (currentState.ticker == null) {
            _state.value = currentState.copy(error = "Fiyat bekleniyor...")
            return
        }

        val plan = calculatePlan(position, currentState)
        if (plan == null) {
            _state.value = currentState.copy(error = "İşlem hesaplanamadı")
            return
        }

        if (currentState.tradingMode == TradingMode.REAL) {
            // REAL modda onay sheet göster
            _state.value = currentState.copy(
                showConfirmation = true,
                pendingPlan = plan
            )
        } else {
            // SIM modda direkt uygula
            executeFlip(plan)
        }
    }

    fun confirmFlip() {
        val plan = _state.value.pendingPlan ?: return
        _state.value = _state.value.copy(showConfirmation = false)
        executeFlip(plan)
    }

    fun cancelFlip() {
        _state.value = _state.value.copy(showConfirmation = false, pendingPlan = null)
    }

    private fun calculatePlan(position: OpenPosition, state: DashboardUiState): FlipPlanInfo? {
        val cyclePnl = state.cycleState.runningPnl
        val unrealizedPnl = position.unrealizedPnl
        val projectedPnl = cyclePnl + unrealizedPnl
        val isProfitable = projectedPnl >= BigDecimal("0.01")

        val (targetSide, targetVol, mode) = if (isProfitable) {
            when (state.resetAction) {
                ResetAction.STOP -> Triple("STOP", BigDecimal.ZERO, "RESET")
                ResetAction.LONG -> Triple("LONG", state.resetSize, "RESET")
                ResetAction.SHORT -> Triple("SHORT", state.resetSize, "RESET")
            }
        } else {
            val newVol = position.holdVol * BigDecimal("2")
            if (newVol > state.maxContracts) return null
            Triple(position.side.opposite.name, newVol, "MARTINGALE")
        }

        return FlipPlanInfo(
            currentSide = position.side.name,
            currentVol = position.holdVol.toPlainString(),
            targetSide = targetSide,
            targetVol = targetVol.toPlainString(),
            mode = mode,
            estimatedPnl = unrealizedPnl.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            isReal = state.tradingMode == TradingMode.REAL
        )
    }

    private fun executeFlip(plan: FlipPlanInfo) {
        if (!flipMutex.compareAndSet(false, true)) {
            _state.value = _state.value.copy(error = "İşlem devam ediyor, bekleyin")
            return
        }

        _state.value = _state.value.copy(isFlipping = true, error = null)

        viewModelScope.launch {
            try {
                val state = _state.value
                val position = state.position
                val ticker = state.ticker

                if (position == null || ticker == null) {
                    _state.value = _state.value.copy(isFlipping = false, error = "Pozisyon/fiyat yok")
                    flipMutex.set(false)
                    return@launch
                }

                if (state.tradingMode == TradingMode.REAL && privateApi != null) {
                    // Gerçek API çağrısı
                    executeRealFlip(plan, position, state)
                } else {
                    // SIM mod
                    executeSimFlip(plan, position, ticker)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isFlipping = false,
                    error = "Flip hatası: ${e.message}"
                )
                flipMutex.set(false)
            }
        }
    }

    private suspend fun executeSimFlip(plan: FlipPlanInfo, position: OpenPosition, ticker: Ticker) {
        kotlinx.coroutines.delay(700)

        val newCycleState = when (plan.mode) {
            "RESET" -> {
                // Log kaydet
                logFlip(plan, position, "SUCCESS")
                CycleState(
                    _state.value.cycleState.cycleNumber + 1,
                    BigDecimal.ZERO
                )
            }
            else -> {
                val newPnl = _state.value.cycleState.runningPnl + position.unrealizedPnl
                logFlip(plan, position, "SUCCESS")
                CycleState(_state.value.cycleState.cycleNumber, newPnl)
            }
        }

        val newPosition = if (plan.targetSide == "STOP") {
            null
        } else {
            OpenPosition(
                positionId = 0,
                symbol = _state.value.symbol,
                holdVol = plan.targetVol.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                side = PositionSide.valueOf(plan.targetSide),
                entryPrice = ticker.lastPrice,
                liquidationPrice = BigDecimal.ZERO,
                margin = BigDecimal.ZERO,
                leverage = _state.value.leverage,
                unrealizedPnl = BigDecimal.ZERO
            )
        }

        _state.value = _state.value.copy(
            position = newPosition,
            cycleState = newCycleState,
            isFlipping = false,
            pendingPlan = null,
            error = null
        )
        flipMutex.set(false)
    }

    private suspend fun executeRealFlip(plan: FlipPlanInfo, position: OpenPosition, state: DashboardUiState) {
        if (privateApi == null) {
            _state.value = _state.value.copy(isFlipping = false, error = "API yok")
            flipMutex.set(false)
            return
        }

        try {
            withContext(Dispatchers.IO) {
                if (plan.targetSide == "STOP") {
                    // Sadece kapat
                    val closeSide = if (position.side == PositionSide.LONG) 4 else 2
                    privateApi.createOrder(
                        JSONObject()
                            .put("symbol", state.symbol)
                            .put("price", state.ticker?.lastPrice?.toPlainString() ?: "0")
                            .put("vol", position.holdVol.toPlainString())
                            .put("side", closeSide)
                            .put("type", 5)
                            .put("openType", 1)
                            .put("positionId", position.positionId)
                    )
                } else if (plan.mode == "MARTINGALE" && plan.targetSide != position.side.name) {
                    // Ters yön = atomik reverse
                    privateApi.reversePosition(
                        JSONObject()
                            .put("symbol", state.symbol)
                            .put("positionId", position.positionId)
                            .put("vol", plan.targetVol)
                    )
                } else {
                    // Aynı yön reset: önce kapat, doğrula, sonra aç
                    val closeSide = if (position.side == PositionSide.LONG) 4 else 2
                    privateApi.createOrder(
                        JSONObject()
                            .put("symbol", state.symbol)
                            .put("price", state.ticker?.lastPrice?.toPlainString() ?: "0")
                            .put("vol", position.holdVol.toPlainString())
                            .put("side", closeSide)
                            .put("type", 5)
                            .put("openType", 1)
                            .put("positionId", position.positionId)
                    )

                    // Kapanış doğrulama
                    var confirmed = false
                    for (attempt in 0..7) {
                        kotlinx.coroutines.delay(500)
                        val positions = privateApi.openPositions()
                        val stillOpen = (0 until positions.length()).any { i ->
                            val p = positions.getJSONObject(i)
                            p.optLong("positionId") == position.positionId &&
                                    p.optBigDecimal("holdVol", BigDecimal.ZERO) > BigDecimal.ZERO
                        }
                        if (!stillOpen) {
                            confirmed = true
                            break
                        }
                    }

                    if (!confirmed) {
                        throw Exception("CLOSE_NOT_CONFIRMED")
                    }

                    // Yeni pozisyon aç
                    val openSide = if (plan.targetSide == "LONG") 1 else 3
                    privateApi.createOrder(
                        JSONObject()
                            .put("symbol", state.symbol)
                            .put("price", state.ticker?.lastPrice?.toPlainString() ?: "0")
                            .put("vol", plan.targetVol)
                            .put("side", openSide)
                            .put("type", 5)
                            .put("openType", 1)
                            .put("leverage", state.leverage)
                    )
                }
            }

            // Log kaydet
            logFlip(plan, position, "SUCCESS")

            // Cycle state güncelle
            val newCycleState = when (plan.mode) {
                "RESET" -> CycleState(
                    _state.value.cycleState.cycleNumber + 1,
                    BigDecimal.ZERO
                )
                else -> {
                    val newPnl = _state.value.cycleState.runningPnl + position.unrealizedPnl
                    CycleState(_state.value.cycleState.cycleNumber, newPnl)
                }
            }

            _state.value = _state.value.copy(
                cycleState = newCycleState,
                isFlipping = false,
                pendingPlan = null,
                error = null
            )
            flipMutex.set(false)

            // Pozisyon/hesap verilerini güncelle
            kotlinx.coroutines.delay(1000)
            refreshRealData()

        } catch (e: Exception) {
            val msg = when (e.message) {
                "CLOSE_NOT_CONFIRMED" -> "Kapanış doğrulanamadı! Pozisyonu kontrol edin"
                else -> e.message ?: "Bilinmeyen hata"
            }
            logFlip(plan, position, "FAILED", msg)
            _state.value = _state.value.copy(
                isFlipping = false,
                error = msg,
                pendingPlan = null
            )
            flipMutex.set(false)
        }
    }

    private suspend fun logFlip(
        plan: FlipPlanInfo,
        position: OpenPosition,
        status: String,
        errorMsg: String? = null
    ) {
        flipLogDao?.let { dao ->
            val log = FlipLogEntity(
                createdAt = System.currentTimeMillis(),
                mode = if (plan.isReal) "REAL" else "SIM",
                symbol = _state.value.symbol,
                cycleNumber = _state.value.cycleState.cycleNumber,
                flipMode = plan.mode,
                previousSide = position.side.name,
                previousVolume = position.holdVol.toPlainString(),
                targetSide = plan.targetSide,
                targetVolume = plan.targetVol,
                estimatedPnl = plan.estimatedPnl,
                cyclePnlBefore = _state.value.cycleState.runningPnl.toPlainString(),
                cyclePnlAfter = when (plan.mode) {
                    "RESET" -> BigDecimal.ZERO.toPlainString()
                    else -> (_state.value.cycleState.runningPnl + position.unrealizedPnl).toPlainString()
                },
                status = status,
                orderId = null,
                mexcCode = null,
                errorMessage = errorMsg
            )
            dao.insert(log)
        }
    }

    fun resetCycle() {
        _state.value = _state.value.copy(
            cycleState = CycleState(
                _state.value.cycleState.cycleNumber + 1,
                BigDecimal.ZERO
            )
        )
    }

    fun clearLogs() {
        viewModelScope.launch {
            flipLogDao?.clear()
        }
    }

    fun saveCredentials(apiKey: String, apiSecret: String): Boolean {
        if (apiKey.isBlank() || apiSecret.isBlank()) return false
        app?.credentials?.save(apiKey, apiSecret)
        return true
    }

    fun deleteCredentials() {
        app?.credentials?.clear()
        _state.value = _state.value.copy(tradingMode = TradingMode.SIM, error = "API bilgileri silindi")
    }

    fun hasCredentials(): Boolean = app?.credentials?.has() == true

    private fun parseAccountAsset(asset: JSONObject): AccountAsset {
        fun bd(key: String): BigDecimal = asset.optBigDecimal(key, BigDecimal.ZERO)
        return AccountAsset(
            currency = asset.optString("currency", ""),
            equity = bd("equity"),
            availableBalance = bd("availableBalance"),
            availableOpen = if (asset.has("availableOpen")) bd("availableOpen") else bd("availableBalance"),
            cashBalance = bd("cashBalance"),
            positionMargin = bd("positionMargin"),
            frozenBalance = bd("frozenBalance"),
            unrealized = bd("unrealized"),
            bonus = bd("bonus")
        )
    }

    private fun parsePosition(pos: JSONObject): OpenPosition {
        val positionType = pos.optInt("positionType", 0)
        return OpenPosition(
            positionId = pos.optLong("positionId", 0),
            symbol = pos.optString("symbol", ""),
            holdVol = pos.optBigDecimal("holdVol", BigDecimal.ZERO),
            side = if (positionType == 1) PositionSide.LONG else PositionSide.SHORT,
            entryPrice = pos.optBigDecimal("openAvgPrice", pos.optBigDecimal("holdAvgPrice", BigDecimal.ZERO)),
            liquidationPrice = pos.optBigDecimal("liquidatePrice", BigDecimal.ZERO),
            margin = pos.optBigDecimal("im", BigDecimal.ZERO),
            leverage = pos.optInt("leverage", 10),
            unrealizedPnl = pos.optBigDecimal("unRealizedPnl", BigDecimal.ZERO)
        )
    }
}

private fun JSONObject.optBigDecimal(key: String, defaultValue: BigDecimal): BigDecimal {
    val value = opt(key) ?: return defaultValue
    return when (value) {
        is Number -> try { BigDecimal(value.toString()) } catch (_: Exception) { defaultValue }
        is String -> try { BigDecimal(value) } catch (_: Exception) { defaultValue }
        else -> defaultValue
    }
}
