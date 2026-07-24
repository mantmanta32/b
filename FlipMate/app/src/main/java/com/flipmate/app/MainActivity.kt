package com.flipmate.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flipmate.app.ui.theme.FlipMateTheme
class MainActivity:ComponentActivity(){ override fun onCreate(state:Bundle?){super.onCreate(state);setContent{FlipMateTheme{Dashboard()}}} }
@Composable private fun Dashboard(){ var sim by remember{mutableStateOf(true)}; Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){ Text("FlipMate",style=MaterialTheme.typography.headlineLarge); Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("BTC_USDT");Text("Dashboard hazır",style=MaterialTheme.typography.titleMedium);Text("SIM mode güvenli varsayılandır")}}; Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(sim)"SİMÜLASYON" else "GERÇEK MOD");Switch(sim,onCheckedChange={sim=it})}; Button({ },enabled=false,Modifier.fillMaxWidth()){Text("FLIP & 2X")}; Text("Gerçek işlemler yalnızca kullanıcı onayı ve güncel doğrulama sonrası gönderilir.") } }
