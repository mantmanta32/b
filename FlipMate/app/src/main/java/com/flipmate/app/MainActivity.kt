package com.flipmate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flipmate.app.core.network.PublicApiService
import com.flipmate.app.data.repository.TickerRepositoryImpl
import com.flipmate.app.ui.dashboard.DashboardViewModel
import com.flipmate.app.ui.theme.FlipMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            FlipMateTheme {
                val vm: DashboardViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>): T {
                        val publicApi = PublicApiService((application as FlipMateApplication).network.publicClient)
                        @Suppress("UNCHECKED_CAST")
                        return DashboardViewModel(TickerRepositoryImpl(publicApi)) as T
                    }
                })
                Dashboard(vm)
            }
        }
    }
}

@Composable
private fun Dashboard(vm: DashboardViewModel) {
    val s by vm.state.collectAsState()
    var symbol by remember { mutableStateOf(s.symbol) }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("FlipMate", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase() },
            label = { Text("Sembol") },
            modifier = Modifier.fillMaxWidth()
        )
        Button({ vm.setSymbol(symbol); vm.refresh() }, Modifier.fillMaxWidth()) {
            Text("Yenile")
        }
        if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        s.ticker?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(it.symbol)
                    Text(it.lastPrice.toPlainString(), style = MaterialTheme.typography.headlineLarge)
                    Text("Değişim: ${it.priceChangePercent}%")
                }
            }
        }
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("SIM modu: Gerçek emir gönderilmez.")
    }
}
