package com.flipmate.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipmate.app.core.network.PublicApiService
import com.flipmate.app.data.repository.TickerRepositoryImpl
import com.flipmate.app.domain.model.Ticker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(val symbol:String="BTC_USDT",val ticker:Ticker?=null,val loading:Boolean=false,val error:String?=null)
class DashboardViewModel(private val tickerRepository:TickerRepositoryImpl):ViewModel(){
 private val _state=MutableStateFlow(DashboardUiState()); val state:StateFlow<DashboardUiState> = _state.asStateFlow()
 init{ refresh() }
 fun setSymbol(value:String){_state.value=_state.value.copy(symbol=value.trim().uppercase())}
 fun refresh(){val symbol=_state.value.symbol;if(symbol.isBlank())return;viewModelScope.launch{_state.value=_state.value.copy(loading=true,error=null);runCatching{tickerRepository.getTicker(symbol)}.onSuccess{_state.value=_state.value.copy(ticker=it,loading=false)}.onFailure{_state.value=_state.value.copy(loading=false,error="Ticker alınamadı")}}}
}
