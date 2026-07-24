package com.flipmate.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipmate.app.core.network.PublicApiService
import com.flipmate.app.core.network.PrivateApiService
import com.flipmate.app.data.repository.TickerRepositoryImpl
import com.flipmate.app.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

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
    val isFlipping: Boolean = false
)

enum class ResetAction { LONG, SHORT, STOP }

class DashboardViewModel(
    private val tickerRepository: TickerRepositoryImpl,
    private val privateApi: PrivateApiService? = null
) : ViewModel() {
    
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()
    
    init {
        refresh()
        startAutoRefresh()
    }
    
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                refresh()
                if (_state.value.tradingMode == TradingMode.REAL) {
                    refreshRealData()
                }
            }
        }
    }
    
    fun setSymbol(value: String) {
        _state.value = _state.value.copy(symbol = value.trim().uppercase())
        refresh()
    }
    
    fun setTradingMode(mode: TradingMode) {
        if (mode == TradingMode.REAL && privateApi == null) {
            _state.value = _state.value.copy(error = "REAL mod için API gerekli")
            return
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
    
    fun refresh() {
        val symbol = _state.value.symbol
        if (symbol.isBlank()) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                tickerRepository.getTicker(symbol)
            }.onSuccess {
                _state.value = _state.value.copy(ticker = it, loading = false)
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = "Ticker alınamadı")
            }
        }
    }
    
    private fun refreshRealData() {
        if (_state.value.tradingMode != TradingMode.REAL || privateApi == null) return
        
        viewModelScope.launch {
            runCatching {
                val assets = privateApi.accountAssets()
                val positions = privateApi.openPositions()
                
                val symbol = _state.value.symbol
                val settleCurrency = symbol.substringAfter("_", "USDT")
                
                val asset = assets.firstOrNull { 
                    it.optString("currency", "").equals(settleCurrency, ignoreCase = true) 
                }
                
                val accountAsset = if (asset != null) {
                    AccountAsset(
                        currency = asset.optString("currency", ""),
                        equity = asset.optBigDecimal("equity", BigDecimal.ZERO),
                        availableBalance = asset.optBigDecimal("availableBalance", BigDecimal.ZERO),
                        availableOpen = asset.optBigDecimal("availableOpen", asset.optBigDecimal("availableBalance", BigDecimal.ZERO)),
                        cashBalance = asset.optBigDecimal("cashBalance", BigDecimal.ZERO),
                        positionMargin = asset.optBigDecimal("positionMargin", BigDecimal.ZERO),
                        frozenBalance = asset.optBigDecimal("frozenBalance", BigDecimal.ZERO),
                        unrealized = asset.optBigDecimal("unrealized", BigDecimal.ZERO),
                        bonus = asset.optBigDecimal("bonus", BigDecimal.ZERO)
                    )
                } else null
                
                val position = positions.firstOrNull {
                    it.optString("symbol") == symbol && 
                    it.optBigDecimal("holdVol", BigDecimal.ZERO) > BigDecimal.ZERO
                }?.let { pos ->
                    val positionType = pos.optInt("positionType", 0)
                    OpenPosition(
                        positionId = pos.optLong("positionId", 0),
                        symbol = pos.optString("symbol", ""),
                        holdVol = pos.optBigDecimal("holdVol", BigDecimal.ZERO),
                        side = if (positionType == 1) PositionSide.LONG else PositionSide.SHORT,
                        entryPrice = pos.optBigDecimal("openAvgPrice", BigDecimal.ZERO),
                        liquidationPrice = pos.optBigDecimal("liquidatePrice", BigDecimal.ZERO),
                        margin = pos.optBigDecimal("im", BigDecimal.ZERO),
                        leverage = pos.optInt("leverage", 10),
                        unrealizedPnl = pos.optBigDecimal("unRealizedPnl", BigDecimal.ZERO)
                    )
                }
                
                _state.value = _state.value.copy(
                    account = accountAsset,
                    position = position
                )
            }
        }
    }
    
    fun flip() {
        val currentState = _state.value
        
        if (currentState.isFlipping) return
        
        if (currentState.position == null) {
            _state.value = currentState.copy(error = "Aktif pozisyon yok")
            return
        }
        
        if (currentState.ticker == null) {
            _state.value = currentState.copy(error = "Fiyat bekleniyor")
            return
        }
        
        _state.value = currentState.copy(isFlipping = true)
        
        viewModelScope.launch {
            try {
                val position = currentState.position
                val ticker = currentState.ticker
                val cycleState = currentState.cycleState
                
                // Martingale kararı
                val isProfitable = cycleState.runningPnl > BigDecimal.ZERO
                
                val (targetSide, targetVolume, mode) = if (isProfitable) {
                    // RESET
                    when (currentState.resetAction) {
                        ResetAction.STOP -> Triple(position.side, BigDecimal.ZERO, "STOP")
                        ResetAction.LONG -> Triple(PositionSide.LONG, currentState.resetSize, "RESET")
                        ResetAction.SHORT -> Triple(PositionSide.SHORT, currentState.resetSize, "RESET")
                    }
                } else {
                    // MARTINGALE
                    val newVol = position.holdVol * BigDecimal("2")
                    if (newVol > currentState.maxContracts) {
                        _state.value = _state.value.copy(
                            isFlipping = false,
                            error = "Max kontrat (${currentState.maxContracts}) aşımı"
                        )
                        return@launch
                    }
                    Triple(position.side.opposite, newVol, "MARTINGALE")
                }
                
                if (currentState.tradingMode == TradingMode.SIM) {
                    // SIM mod - sadece state güncelle
                    kotlinx.coroutines.delay(700)
                    
                    val newPosition = if (mode == "STOP") {
                        null
                    } else {
                        OpenPosition(
                            positionId = 0,
                            symbol = currentState.symbol,
                            holdVol = targetVolume,
                            side = targetSide,
                            entryPrice = ticker.lastPrice,
                            liquidationPrice = BigDecimal.ZERO,
                            margin = BigDecimal.ZERO,
                            leverage = currentState.leverage,
                            unrealizedPnl = BigDecimal.ZERO
                        )
                    }
                    
                    val newCycleState = if (mode == "RESET") {
                        CycleState(cycleState.cycleNumber + 1, BigDecimal.ZERO, false)
                    } else {
                        val unrealizedPnl = position.unrealizedPnl
                        val newRunningPnl = cycleState.runningPnl + unrealizedPnl
                        CycleState(cycleState.cycleNumber, newRunningPnl, newRunningPnl > BigDecimal.ZERO)
                    }
                    
                    _state.value = _state.value.copy(
                        position = newPosition,
                        cycleState = newCycleState,
                        isFlipping = false,
                        error = null
                    )
                } else {
                    // REAL mod - API çağrısı yap
                    // TODO: Gerçek API çağrısı
                    kotlinx.coroutines.delay(1000)
                    _state.value = _state.value.copy(isFlipping = false)
                    refreshRealData()
                }
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isFlipping = false,
                    error = e.message ?: "Flip hatası"
                )
            }
        }
    }
}

private fun org.json.JSONObject.optBigDecimal(key: String, defaultValue: BigDecimal): BigDecimal {
    val value = opt(key) ?: return defaultValue
    return when (value) {
        is Number -> BigDecimal(value.toString())
        is String -> try {
            BigDecimal(value)
        } catch (e: Exception) {
            defaultValue
        }
        else -> defaultValue
    }
}
