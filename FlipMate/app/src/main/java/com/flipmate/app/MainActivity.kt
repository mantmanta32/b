package com.flipmate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flipmate.app.core.network.PrivateApiService
import com.flipmate.app.core.network.PublicApiService
import com.flipmate.app.data.repository.TickerRepositoryImpl
import com.flipmate.app.domain.model.*
import com.flipmate.app.ui.dashboard.*
import com.flipmate.app.ui.theme.FlipMateTheme
import java.math.BigDecimal
import java.math.RoundingMode

// Renk paleti (flip.py'den)
val PinkBg = Color(0xFFFDF2F8)
val CardBg = Color(0xFFFFFFFF)
val CardAlt = Color(0xFFF6EEFF)
val TextPrimary = Color(0xFF4A3868)
val TextMuted = Color(0xFF9689B5)
val LongGreen = Color(0xFF10B981)
val ShortRed = Color(0xFFF43F5E)
val AccentPurple = Color(0xFFC026D3)
val AccentViolet = Color(0xFFA855F7)
val AccentPink = Color(0xFFF472B6)
val ResetIndigo = Color(0xFF818CF8)
val Gold = Color(0xFFF59E0B)
val BorderColor = Color(0xFFE8D5F5)
val SurfaceVariant = Color(0xFFFCEEF7)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            FlipMateTheme {
                val app = application as FlipMateApplication
                val vm: DashboardViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>): T {
                        val publicApi = PublicApiService(app.network.publicClient)
                        val privateApi = try { PrivateApiService(app.network.privateClient) } catch (_: Exception) { null }
                        @Suppress("UNCHECKED_CAST")
                        return DashboardViewModel(TickerRepositoryImpl(publicApi), privateApi) as T
                    }
                })
                FlipMateApp(vm)
            }
        }
    }
}

@Composable
fun FlipMateApp(vm: DashboardViewModel) {
    val state by vm.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var symbolInput by remember { mutableStateOf(state.symbol) }
    
    // Sembol debounce
    LaunchedEffect(symbolInput) {
        kotlinx.coroutines.delay(400)
        if (symbolInput.uppercase() != state.symbol) {
            vm.setSymbol(symbolInput.uppercase())
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFDF2F8),
                        Color(0xFFF5EBFF),
                        Color(0xFFF3F0FF)
                    )
                )
            )
    ) {
        // Status Bar
        StatusBar(
            tradingMode = state.tradingMode,
            onToggleMode = {
                vm.setTradingMode(
                    if (state.tradingMode == TradingMode.SIM) TradingMode.REAL
                    else TradingMode.SIM
                )
            }
        )
        
        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> PanelTab(state, vm, symbolInput, onSymbolChange = { symbolInput = it })
                1 -> HistoryTab()
                2 -> OrdersTab()
                3 -> SettingsTab(state, vm, symbolInput, onSymbolChange = { symbolInput = it })
            }
        }
        
        // Bottom bar
        Column {
            if (selectedTab == 0) {
                // FLIP & 2X Button
                FlipBar(state, vm)
            }
            
            // Navigation
            NavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    }
}

@Composable
fun StatusBar(tradingMode: TradingMode, onToggleMode: () -> Unit) {
    val isReal = tradingMode == TradingMode.REAL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clock
        var time by remember { mutableStateOf("--:--:--") }
        LaunchedEffect(Unit) {
            while (true) {
                val now = java.time.LocalTime.now(java.time.ZoneId.of("Europe/Istanbul"))
                time = String.format("%02d:%02d:%02d", now.hour, now.minute, now.second)
                kotlinx.coroutines.delay(1000)
            }
        }
        Text(
            time,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextMuted,
            fontWeight = FontWeight.Medium
        )
        
        // WS Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(LongGreen)
            )
            Text(
                "Bağlı",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextMuted
            )
        }
        
        // SIM/REAL Badge
        val badgeColor = if (isReal) LongGreen else Gold
        val badgeBg = if (isReal) Color(0x1F10B981) else Color(0x1FF59E0B)
        
        Surface(
            modifier = Modifier.clickable { onToggleMode() },
            shape = RoundedCornerShape(4.dp),
            color = badgeBg
        ) {
            Text(
                text = if (isReal) "REAL ⇄" else "SIM ⇄",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
    }
}

@Composable
fun PanelTab(state: DashboardUiState, vm: DashboardViewModel, symbol: String, onSymbolChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Price Box
        PriceCard(state, symbol, onSymbolChange)
        
        // Cycle Bar
        CycleBar(state)
        
        // Account Card (REAL mode only)
        if (state.tradingMode == TradingMode.REAL) {
            AccountCard(state)
        }
        
        // Position Card
        PositionCard(state)
        
        // Martingale Ladder
        LadderCard(state)
        
        // Strategy Preview
        ModeHintCard(state)
        
        // Settings Row
        SettingsRow(state, vm)
    }
}

@Composable
fun PriceCard(state: DashboardUiState, symbol: String, onSymbolChange: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Symbol + Price row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = onSymbolChange,
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BorderColor,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = CardAlt,
                        unfocusedContainerColor = CardAlt
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                
                Spacer(Modifier.weight(1f))
                
                Column(horizontalAlignment = Alignment.End) {
                    val price = state.ticker?.lastPrice
                    Text(
                        text = "$ ${price?.let { formatPrice(it) } ?: "0.00"}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    val change = state.ticker?.priceChangePercent
                    val isUp = change != null && change >= BigDecimal.ZERO
                    Text(
                        text = "${if (isUp) "+" else ""}${change?.let { 
                            (it * BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                        }?.toPlainString() ?: "0.00"}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUp) LongGreen else ShortRed
                    )
                }
            }
            
            Spacer(Modifier.height(6.dp))
            
            // High/Low/Fair
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val ticker = state.ticker
                PriceLabel("24H↑", ticker?.high24h?.let { formatPrice(it) } ?: "-")
                PriceLabel("24H↓", ticker?.low24h?.let { formatPrice(it) } ?: "-")
                PriceLabel("Fair", ticker?.fairPrice?.let { formatPrice(it) } ?: "-")
            }
        }
    }
}

@Composable
fun PriceLabel(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        Text(value, fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CycleBar(state: DashboardUiState) {
    val cycle = state.cycleState
    val pnl = cycle.runningPnl
    val isProfitable = pnl > BigDecimal.ZERO
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5EBFF)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cycle number
            Column {
                Text("Döngü", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Text(
                    "# ${cycle.cycleNumber}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentViolet
                )
            }
            
            // Running PnL
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Genel Toplam", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Text(
                    "${if (pnl >= BigDecimal.ZERO) "+" else ""}\$ ${pnl.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isProfitable) LongGreen else if (pnl < BigDecimal.ZERO) ShortRed else TextMuted
                )
            }
            
            // Status
            Column(horizontalAlignment = Alignment.End) {
                Text("Durum", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Text(
                    text = if (isProfitable) "Karda" else if (pnl < BigDecimal.ZERO) "Zararda" else "Başa baş",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isProfitable) LongGreen else if (pnl < BigDecimal.ZERO) ShortRed else TextMuted
                )
            }
        }
    }
}

@Composable
fun AccountCard(state: DashboardUiState) {
    val asset = state.account ?: return
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("REAL Kasa", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0x1F10B981)
                    ) {
                        Text(
                            asset.currency,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LongGreen
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(6.dp))
            
            // Account details grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Toplam Equity", fontSize = 9.sp, color = TextMuted)
                    Text("$${asset.equity.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Kullanılabilir", fontSize = 9.sp, color = TextMuted)
                    Text("$${asset.availableOpen.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LongGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Poz. Margin", fontSize = 9.sp, color = TextMuted)
                    Text("$${asset.positionMargin.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Cash", fontSize = 9.sp, color = TextMuted)
                    Text("$${asset.cashBalance.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Unrealized", fontSize = 9.sp, color = TextMuted)
                    val isUp = asset.unrealized >= BigDecimal.ZERO
                    Text(
                        "${if (isUp) "+" else ""}$${asset.unrealized.setScale(2, RoundingMode.HALF_UP)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (isUp) LongGreen else ShortRed
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Frozen/Bonus", fontSize = 9.sp, color = TextMuted)
                    Text("$${asset.frozenBalance.setScale(2, RoundingMode.HALF_UP)} / $${asset.bonus.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun PositionCard(state: DashboardUiState) {
    val pos = state.position
    val isLong = pos?.side == PositionSide.LONG
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pozisyon", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    
                    if (pos != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isLong) Color(0x1F10B981) else Color(0x1FF43F5E)
                        ) {
                            Text(
                                pos.side.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLong) LongGreen else ShortRed
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("${pos.holdVol}c", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("${state.leverage}x", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
                    } else {
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0x1F64748B)) {
                            Text("YOK", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
                
                if (pos != null) {
                    val isUp = pos.unrealizedPnl >= BigDecimal.ZERO
                    val pnlPct = if (pos.margin > BigDecimal.ZERO) {
                        (pos.unrealizedPnl / pos.margin * BigDecimal("100")).setScale(1, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO
                    
                    Text(
                        "${if (isUp) "+" else ""}$${pos.unrealizedPnl.setScale(2, RoundingMode.HALF_UP)} (${if (isUp) "+" else ""}${pnlPct}%)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUp) LongGreen else ShortRed
                    )
                } else {
                    Text("$0.00 (+0.0%)", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted)
                }
            }
            
            Spacer(Modifier.height(6.dp))
            
            // PnL Bar
            val pnlPct = if (pos != null && pos.margin > BigDecimal.ZERO) {
                ((pos.unrealizedPnl / pos.margin) * BigDecimal("100")).toFloat().coerceIn(-100f, 100f)
            } else 0f
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CardAlt)
            ) {
                // Center line
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .align(Alignment.Center)
                        .background(BorderColor)
                )
                
                // PnL bar
                val halfWidth = (kotlin.math.abs(pnlPct) / 2f).coerceIn(0f, 50f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (pnlPct >= 0) 0.5f else 0.5f - halfWidth / 100f)
                        .align(if (pnlPct >= 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .width(
                            (halfWidth * 2).percent()
                        )
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (pnlPct >= 0) LongGreen else ShortRed)
                )
            }
            
            Spacer(Modifier.height(6.dp))
            
            // Details grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Entry", fontSize = 9.sp, color = TextMuted)
                    Text(
                        pos?.let { "$${formatPrice(it.entryPrice)}" } ?: "-",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Liq", fontSize = 9.sp, color = TextMuted)
                    Text(
                        pos?.let { "$${formatPrice(it.liquidationPrice)}" } ?: "-",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Margin", fontSize = 9.sp, color = TextMuted)
                    Text(
                        pos?.let { "$${it.margin.setScale(2, RoundingMode.HALF_UP)}" } ?: "$0.00",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun Float.percent(): androidx.compose.ui.unit.Dp = (this * 2).dp

@Composable
fun LadderCard(state: DashboardUiState) {
    val steps = mutableListOf<BigDecimal>()
    var s = state.resetSize
    while (s <= state.maxContracts && steps.size < 10) {
        steps.add(s)
        s = s * BigDecimal("2")
    }
    
    val currentSize = state.position?.holdVol ?: state.resetSize
    val currentIdx = steps.indexOf(currentSize)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Martingale Serisi (kontrat)", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Text(
                    if (currentIdx >= 0) "${currentIdx + 1}/${steps.size}" else "~${currentSize}c",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
            
            Spacer(Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                steps.forEachIndexed { idx, step ->
                    val isActive = idx == currentIdx
                    val isPast = currentIdx >= 0 && idx < currentIdx
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                when {
                                    isActive -> Color(0x24C026D3)
                                    isPast -> Color(0x24818CF8)
                                    else -> CardAlt
                                }
                            )
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            step.toPlainString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isActive -> AccentPurple
                                isPast -> ResetIndigo
                                else -> TextMuted
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModeHintCard(state: DashboardUiState) {
    val pos = state.position
    val cycle = state.cycleState
    val isProfitable = cycle.runningPnl > BigDecimal.ZERO
    
    val bgColor: Color
    val borderColorVal: Color
    val textColor: Color
    val message: String
    
    when {
        isProfitable && state.resetAction == ResetAction.STOP -> {
            bgColor = Color(0x1FF43F5E)
            borderColorVal = Color(0x40F43F5E)
            textColor = ShortRed
            message = "Equity kârda → KAPAT & DURDUR"
        }
        isProfitable -> {
            val rs = when (state.resetAction) {
                ResetAction.LONG -> "LONG"
                ResetAction.SHORT -> "SHORT"
                ResetAction.STOP -> "STOP"
            }
            bgColor = Color(0x24818CF8)
            borderColorVal = Color(0x4D818CF8)
            textColor = Color(0xFF6366F1)
            message = "Equity kârda → SIFIRLA ${state.resetSize} $rs"
        }
        else -> {
            val newSide = pos?.side?.opposite?.name ?: "SHORT"
            val newSize = pos?.holdVol?.times(BigDecimal("2")) ?: BigDecimal.ZERO
            bgColor = if (cycle.runningPnl < BigDecimal.ZERO) Color(0x1FF43F5E) else Color(0x1F64748B)
            borderColorVal = if (cycle.runningPnl < BigDecimal.ZERO) Color(0x40F43F5E) else Color(0x3864748B)
            textColor = if (cycle.runningPnl < BigDecimal.ZERO) ShortRed else Color(0xFF475569)
            message = "Zararda → MARTINGALE → $newSize $newSide"
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColorVal, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "KARAR:",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                message,
                fontSize = 11.sp,
                color = textColor
            )
        }
    }
}

@Composable
fun SettingsRow(state: DashboardUiState, vm: DashboardViewModel) {
    var selectedLev by remember { mutableIntStateOf(state.leverage) }
    var selectedMargin by remember { mutableIntStateOf(1) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Leverage
        Column(modifier = Modifier.weight(1f)) {
            Text("Leverage", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            var expanded by remember { mutableStateOf(false) }
            Box {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
                    color = CardAlt
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedLev}x", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("▼", fontSize = 8.sp, color = TextMuted)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(5, 10, 20, 50, 100).forEach { lev ->
                        DropdownMenuItem(
                            text = { Text("${lev}x", fontFamily = FontFamily.Monospace) },
                            onClick = { selectedLev = lev; vm.setLeverage(lev); expanded = false }
                        )
                    }
                }
            }
        }
        
        // Margin
        Column(modifier = Modifier.weight(1f)) {
            Text("Margin", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            var expanded by remember { mutableStateOf(false) }
            Box {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
                    color = CardAlt
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (selectedMargin == 1) "ISO" else "CROSS", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("▼", fontSize = 8.sp, color = TextMuted)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("ISO") }, onClick = { selectedMargin = 1; expanded = false })
                    DropdownMenuItem(text = { Text("CROSS") }, onClick = { selectedMargin = 2; expanded = false })
                }
            }
        }
        
        // Reset Size
        Column(modifier = Modifier.weight(1f)) {
            Text("Count", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            var sizeText by remember { mutableStateOf(state.resetSize.toPlainString()) }
            OutlinedTextField(
                value = sizeText,
                onValueChange = {
                    sizeText = it
                    it.toIntOrNull()?.let { v ->
                        vm.setResetSize(BigDecimal(v))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BorderColor,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = CardAlt,
                    unfocusedContainerColor = CardAlt
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
fun FlipBar(state: DashboardUiState, vm: DashboardViewModel) {
    val pos = state.position
    val cycle = state.cycleState
    val isProfitable = cycle.runningPnl > BigDecimal.ZERO
    
    // Next action preview
    val preview = when {
        state.isFlipping -> "İşleniyor..."
        pos == null -> "Pozisyon yok"
        isProfitable -> when (state.resetAction) {
            ResetAction.STOP -> "KAPAT & DURDUR"
            ResetAction.LONG -> "${state.resetSize} LONG | döngü biter"
            ResetAction.SHORT -> "${state.resetSize} SHORT | döngü biter"
        }
        else -> {
            val newSide = pos.side.opposite.name
            val newSize = pos.holdVol * BigDecimal("2")
            "$newSize $newSide (seri devam)"
        }
    }
    
    val gradient = if (state.isFlipping) {
        Brush.linearGradient(listOf(Color(0xFF64748B), Color(0xFF64748B)))
    } else {
        Brush.linearGradient(listOf(AccentPurple, AccentViolet, AccentPink))
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAF3FF))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Button(
            onClick = { vm.flip() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isFlipping && pos != null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gradient, RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.isFlipping) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("İşleniyor...", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "🔄 FLIP & 2X",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
        
        Text(
            text = preview,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
            color = TextMuted,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun NavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        "Panel" to "panel",
        "Log" to "log",
        "Emirler" to "orders",
        "Ayarlar" to "settings"
    )
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFAF4FF),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { idx, (label, _) ->
                val isSelected = idx == selectedTab
                Column(
                    modifier = Modifier
                        .clickable { onTabSelected(idx) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        color = if (isSelected) AccentPurple else TextMuted,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .background(AccentPurple, RoundedCornerShape(1.dp))
                        )
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Flip Geçmişi", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            Text("Henüz flip yok", color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun OrdersTab() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Açık emir yok", color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun SettingsTab(state: DashboardUiState, vm: DashboardViewModel, symbol: String, onSymbolChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Kâr Sonrası Davranış
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Kâr Sonrası Davranış", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                
                var expanded by remember { mutableStateOf(false) }
                val currentAction = state.resetAction
                
                Box {
                    OutlinedTextField(
                        value = when (currentAction) {
                            ResetAction.LONG -> "Yeni LONG pozisyon aç"
                            ResetAction.SHORT -> "Yeni SHORT pozisyon aç"
                            ResetAction.STOP -> "Pozisyonu kapat ve DURDUR"
                        },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderColor,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = CardAlt,
                            unfocusedContainerColor = CardAlt
                        ),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            Text("▼", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(end = 8.dp))
                        }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Yeni LONG pozisyon aç") }, onClick = { vm.setResetAction(ResetAction.LONG); expanded = false })
                        DropdownMenuItem(text = { Text("Yeni SHORT pozisyon aç") }, onClick = { vm.setResetAction(ResetAction.SHORT); expanded = false })
                        DropdownMenuItem(text = { Text("Pozisyonu kapat ve DURDUR") }, onClick = { vm.setResetAction(ResetAction.STOP); expanded = false })
                    }
                }
            }
        }
        
        // Güvenlik
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Güvenlik", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Maksimum kontrat", fontSize = 13.sp)
                    OutlinedTextField(
                        value = state.maxContracts.toPlainString(),
                        onValueChange = {},
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Right
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderColor,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = CardAlt,
                            unfocusedContainerColor = CardAlt
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }
    }
}

// Utility functions
private fun formatPrice(price: BigDecimal): String {
    val abs = price.abs()
    val scale = when {
        abs >= BigDecimal("1000") -> 2
        abs >= BigDecimal("1") -> 4
        abs >= BigDecimal("0.01") -> 5
        abs >= BigDecimal("0.0001") -> 7
        else -> 9
    }
    return price.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
