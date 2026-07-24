package com.flipmate.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flipmate.app.core.network.PrivateApiService
import com.flipmate.app.core.network.PublicApiService
import com.flipmate.app.data.local.AppDatabase
import com.flipmate.app.data.repository.TickerRepositoryImpl
import com.flipmate.app.domain.model.*
import com.flipmate.app.ui.dashboard.*
import com.flipmate.app.ui.theme.FlipMateTheme
import java.math.BigDecimal
import java.math.RoundingMode

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

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            FlipMateTheme {
                val app = application as FlipMateApplication
                val db = remember { AppDatabase.get(this) }
                val dao = remember { db.flipLogDao() }
                val vm: DashboardViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>): T {
                        val publicApi = PublicApiService(app.network.publicClient)
                        val privateApi = try { PrivateApiService(app.network.privateClient) } catch (_: Exception) { null }
                        val tickerRepo = TickerRepositoryImpl(publicApi, app.wsManager)
                        @Suppress("UNCHECKED_CAST")
                        return DashboardViewModel(tickerRepo, privateApi, app, dao) as T
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

    LaunchedEffect(symbolInput) {
        kotlinx.coroutines.delay(400)
        if (symbolInput.uppercase() != state.symbol) {
            vm.setSymbol(symbolInput.uppercase())
        }
    }

    // Error Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    // Confirmation Dialog
    if (state.showConfirmation && state.pendingPlan != null) {
        ConfirmationDialog(
            plan = state.pendingPlan!!,
            symbol = state.symbol,
            leverage = state.leverage,
            onConfirm = { vm.confirmFlip() },
            onCancel = { vm.cancelFlip() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFDF2F8), Color(0xFFF5EBFF), Color(0xFFF3F0FF))
                    )
                )
        ) {
            StatusBar(
                tradingMode = state.tradingMode,
                wsConnected = state.wsConnected,
                symbol = state.symbol,
                onToggleMode = {
                    vm.setTradingMode(
                        if (state.tradingMode == TradingMode.SIM) TradingMode.REAL
                        else TradingMode.SIM
                    )
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> PanelTab(state, vm, symbolInput, onSymbolChange = { symbolInput = it })
                    1 -> HistoryTab(state, vm)
                    2 -> OrdersTab()
                    3 -> SettingsTab(state, vm)
                }
            }

            Column {
                if (selectedTab == 0) {
                    FlipBar(state, vm)
                }
                BottomNavBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    plan: FlipPlanInfo,
    symbol: String,
    leverage: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                if (plan.isReal) "⚠️ GERÇEK EMİR" else "SIM EMİR",
                fontWeight = FontWeight.Bold,
                color = if (plan.isReal) ShortRed else Gold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${plan.currentVol} ${plan.currentSide} → ${plan.targetVol} ${plan.targetSide}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Market / Isolated / ${leverage}x",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Mod: ${plan.mode}",
                    fontSize = 12.sp,
                    color = if (plan.mode == "MARTINGALE") ShortRed else ResetIndigo,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tahmini PnL: ${plan.estimatedPnl} USDT",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (plan.estimatedPnl.startsWith("-")) ShortRed else LongGreen
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = if (plan.isReal) ShortRed else AccentPurple)
            ) {
                Text(if (plan.isReal) "GERÇEK FLIP" else "SİMÜLE ET")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("İptal") }
        }
    )
}

@Composable
fun StatusBar(tradingMode: TradingMode, wsConnected: Boolean, symbol: String, onToggleMode: () -> Unit) {
    val isReal = tradingMode == TradingMode.REAL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var time by remember { mutableStateOf("--:--:--") }
        LaunchedEffect(Unit) {
            while (true) {
                val now = java.time.LocalTime.now(java.time.ZoneId.of("Europe/Istanbul"))
                time = String.format("%02d:%02d:%02d", now.hour, now.minute, now.second)
                kotlinx.coroutines.delay(1000)
            }
        }
        Text(time, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (wsConnected) LongGreen else Gold)
            )
            Text(
                if (wsConnected) symbol else "Bağlanıyor...",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextMuted
            )
        }

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
        PriceCard(state, symbol, onSymbolChange)
        CycleBar(state)
        if (state.tradingMode == TradingMode.REAL) AccountCard(state)
        PositionCard(state)
        LadderCard(state)
        ModeHintCard(state)
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = onSymbolChange,
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderColor, unfocusedBorderColor = BorderColor, focusedContainerColor = CardAlt, unfocusedContainerColor = CardAlt),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    val price = state.ticker?.lastPrice
                    Text("$ ${price?.let { formatPrice(it) } ?: "0.00"}", fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    val change = state.ticker?.priceChangePercent
                    val isUp = change != null && change >= BigDecimal.ZERO
                    Text(
                        "${if (isUp) "+" else ""}${change?.let { (it * BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) }?.toPlainString() ?: "0.00"}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUp) LongGreen else ShortRed
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val t = state.ticker
                PriceLabel("24H↑", t?.high24h?.let { formatPrice(it) } ?: "-")
                PriceLabel("24H↓", t?.low24h?.let { formatPrice(it) } ?: "-")
                PriceLabel("Fair", t?.fairPrice?.let { formatPrice(it) } ?: "-")
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5EBFF))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Döngü", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Text("# ${cycle.cycleNumber}", fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentViolet)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Genel Toplam", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Text(
                    "${if (pnl >= BigDecimal.ZERO) "+" else ""}$ ${pnl.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                    fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (isProfitable) LongGreen else if (pnl < BigDecimal.ZERO) ShortRed else TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Durum", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Text(
                    text = if (isProfitable) "Karda" else if (pnl < BigDecimal.ZERO) "Zararda" else "Başa baş",
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isProfitable) LongGreen else if (pnl < BigDecimal.ZERO) ShortRed else TextMuted
                )
            }
        }
    }
}

@Composable
fun AccountCard(state: DashboardUiState) {
    val asset = state.account ?: return
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("REAL Kasa", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = Color(0x1F10B981)) {
                    Text(asset.currency, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LongGreen)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Toplam Equity", fontSize = 9.sp, color = TextMuted); Text("$${asset.equity.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Kullanılabilir", fontSize = 9.sp, color = TextMuted); Text("$${asset.availableOpen.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LongGreen) }
                Column(horizontalAlignment = Alignment.End) { Text("Poz. Margin", fontSize = 9.sp, color = TextMuted); Text("$${asset.positionMargin.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Cash", fontSize = 9.sp, color = TextMuted); Text("$${asset.cashBalance.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Unrealized", fontSize = 9.sp, color = TextMuted)
                    val isUp = asset.unrealized >= BigDecimal.ZERO
                    Text("${if (isUp) "+" else ""}$${asset.unrealized.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (isUp) LongGreen else ShortRed)
                }
                Column(horizontalAlignment = Alignment.End) { Text("Frozen/Bonus", fontSize = 9.sp, color = TextMuted); Text("$${asset.frozenBalance.setScale(2, RoundingMode.HALF_UP)} / $${asset.bonus.setScale(2, RoundingMode.HALF_UP)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
fun PositionCard(state: DashboardUiState) {
    val pos = state.position
    val isLong = pos?.side == PositionSide.LONG
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pozisyon", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    if (pos != null) {
                        Surface(shape = RoundedCornerShape(4.dp), color = if (isLong) Color(0x1F10B981) else Color(0x1FF43F5E)) {
                            Text(pos.side.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isLong) LongGreen else ShortRed)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("${pos.holdVol}c", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("${pos.leverage}x", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
                    } else {
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0x1F64748B)) {
                            Text("YOK", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
                if (pos != null) {
                    val isUp = pos.unrealizedPnl >= BigDecimal.ZERO
                    val pnlPct = if (pos.margin > BigDecimal.ZERO) (pos.unrealizedPnl / pos.margin * BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) else BigDecimal.ZERO
                    Text("${if (isUp) "+" else ""}$${pos.unrealizedPnl.setScale(2, RoundingMode.HALF_UP)} (${if (isUp) "+" else ""}${pnlPct}%)", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isUp) LongGreen else ShortRed)
                } else {
                    Text("$0.00 (+0.0%)", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted)
                }
            }
            Spacer(Modifier.height(6.dp))
            // PnL Bar
            Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(CardAlt)) {
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).align(Alignment.Center).background(BorderColor))
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Entry", fontSize = 9.sp, color = TextMuted); Text(pos?.let { "$${formatPrice(it.entryPrice)}" } ?: "-", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Liq", fontSize = 9.sp, color = TextMuted); Text(pos?.let { "$${formatPrice(it.liquidationPrice)}" } ?: "-", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                Column(horizontalAlignment = Alignment.End) { Text("Margin", fontSize = 9.sp, color = TextMuted); Text(pos?.let { "$${it.margin.setScale(2, RoundingMode.HALF_UP)}" } ?: "$0.00", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun LadderCard(state: DashboardUiState) {
    val steps = mutableListOf<BigDecimal>()
    var s = state.resetSize
    while (s <= state.maxContracts && steps.size < 10) { steps.add(s); s = s * BigDecimal("2") }
    val currentSize = state.position?.holdVol ?: state.resetSize
    val currentIdx = steps.indexOf(currentSize)

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Martingale Serisi (kontrat)", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Text(if (currentIdx >= 0) "${currentIdx + 1}/${steps.size}" else "~${currentSize}c", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.forEachIndexed { idx, step ->
                    val isActive = idx == currentIdx
                    val isPast = currentIdx >= 0 && idx < currentIdx
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (isActive) Color(0x24C026D3) else if (isPast) Color(0x24818CF8) else CardAlt).padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(step.toPlainString(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (isActive) AccentPurple else if (isPast) ResetIndigo else TextMuted)
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
    val bgColor: Color; val borderColorVal: Color; val textColor: Color; val message: String
    when {
        isProfitable && state.resetAction == ResetAction.STOP -> { bgColor = Color(0x1FF43F5E); borderColorVal = Color(0x40F43F5E); textColor = ShortRed; message = "Equity kârda → KAPAT & DURDUR" }
        isProfitable -> { val rs = state.resetAction.name; bgColor = Color(0x24818CF8); borderColorVal = Color(0x4D818CF8); textColor = Color(0xFF6366F1); message = "Equity kârda → SIFIRLA ${state.resetSize} $rs" }
        else -> { val newSide = pos?.side?.opposite?.name ?: "SHORT"; val newSize = pos?.holdVol?.times(BigDecimal("2")) ?: BigDecimal.ZERO; bgColor = if (cycle.runningPnl < BigDecimal.ZERO) Color(0x1FF43F5E) else Color(0x1F64748B); borderColorVal = if (cycle.runningPnl < BigDecimal.ZERO) Color(0x40F43F5E) else Color(0x3864748B); textColor = if (cycle.runningPnl < BigDecimal.ZERO) ShortRed else Color(0xFF475569); message = "Zararda → MARTINGALE → $newSize $newSide" }
    }
    Surface(modifier = Modifier.fillMaxWidth().border(1.dp, borderColorVal, RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp), color = bgColor) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("KARAR:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(Modifier.width(6.dp))
            Text(message, fontSize = 11.sp, color = textColor)
        }
    }
}

@Composable
fun SettingsRow(state: DashboardUiState, vm: DashboardViewModel) {
    var selectedLev by remember { mutableIntStateOf(state.leverage) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Leverage
        Column(modifier = Modifier.weight(1f)) {
            Text("Leverage", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            var expanded by remember { mutableStateOf(false) }
            Box {
                Surface(modifier = Modifier.fillMaxWidth().clickable { expanded = true }.clip(RoundedCornerShape(10.dp)).border(1.dp, BorderColor, RoundedCornerShape(10.dp)), color = CardAlt) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedLev}x", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("▼", fontSize = 8.sp, color = TextMuted)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(5, 10, 20, 50, 100).forEach { lev ->
                        DropdownMenuItem(text = { Text("${lev}x", fontFamily = FontFamily.Monospace) }, onClick = { selectedLev = lev; vm.setLeverage(lev); expanded = false })
                    }
                }
            }
        }
        // Count
        Column(modifier = Modifier.weight(1f)) {
            Text("Count", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            var sizeText by remember { mutableStateOf(state.resetSize.toPlainString()) }
            OutlinedTextField(value = sizeText, onValueChange = { sizeText = it; it.toIntOrNull()?.let { v -> vm.setResetSize(BigDecimal(v)) } }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderColor, unfocusedBorderColor = BorderColor, focusedContainerColor = CardAlt, unfocusedContainerColor = CardAlt), shape = RoundedCornerShape(10.dp))
        }
        // Max
        Column(modifier = Modifier.weight(1f)) {
            Text("Max", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            var maxText by remember { mutableStateOf(state.maxContracts.toPlainString()) }
            OutlinedTextField(value = maxText, onValueChange = { maxText = it; it.toIntOrNull()?.let { v -> vm.setMaxContracts(BigDecimal(v)) } }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderColor, unfocusedBorderColor = BorderColor, focusedContainerColor = CardAlt, unfocusedContainerColor = CardAlt), shape = RoundedCornerShape(10.dp))
        }
    }
}

@Composable
fun FlipBar(state: DashboardUiState, vm: DashboardViewModel) {
    val pos = state.position
    val cycle = state.cycleState
    val isProfitable = cycle.runningPnl > BigDecimal.ZERO
    val preview = when {
        state.isFlipping -> "İşleniyor..."
        pos == null -> "Pozisyon yok"
        isProfitable -> when (state.resetAction) {
            ResetAction.STOP -> "KAPAT & DURDUR"
            ResetAction.LONG -> "${state.resetSize} LONG | döngü biter"
            ResetAction.SHORT -> "${state.resetSize} SHORT | döngü biter"
        }
        else -> { val ns = pos.side.opposite.name; val nv = pos.holdVol * BigDecimal("2"); "$nv $ns (seri devam)" }
    }
    val gradient = if (state.isFlipping) Brush.linearGradient(listOf(Color(0xFF64748B), Color(0xFF64748B))) else Brush.linearGradient(listOf(AccentPurple, AccentViolet, AccentPink))

    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFFAF3FF)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Button(
            onClick = { vm.flip() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isFlipping && pos != null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().background(gradient, RoundedCornerShape(12.dp)).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                if (state.isFlipping) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("İşleniyor...", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("🔄 FLIP & 2X", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        Text(text = preview, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Panel", "Log", "Emirler", "Ayarlar")
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFFAF4FF)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEachIndexed { idx, label ->
                val isSelected = idx == selectedTab
                Column(modifier = Modifier.clickable { onTabSelected(idx) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 12.sp, color = if (isSelected) AccentPurple else TextMuted, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                    if (isSelected) {
                        Box(modifier = Modifier.width(20.dp).height(2.dp).background(AccentPurple, RoundedCornerShape(1.dp)))
                    } else { Spacer(Modifier.height(2.dp)) }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(state: DashboardUiState, vm: DashboardViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Flip Geçmişi", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.resetCycle() }) { Text("Sıfırla", fontSize = 11.sp) }
                TextButton(onClick = { vm.clearLogs() }) { Text("Temizle", fontSize = 11.sp, color = ShortRed) }
            }
        }

        if (state.flipLogs.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Text("Henüz flip yok", modifier = Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center, color = TextMuted)
            }
        } else {
            state.flipLogs.forEach { log ->
                val isSuccess = log.status == "SUCCESS"
                val isLong = log.targetSide == "LONG"
                val badgeColor = if (log.targetSide == "STOP") TextMuted else if (isLong) LongGreen else ShortRed
                val badgeBg = if (log.targetSide == "STOP") Color(0x1F64748B) else if (isLong) Color(0x1F10B981) else Color(0x1FF43F5E)

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.createdAt))
                            Text("#${log.cycleNumber} | $timeStr", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
                            Text(
                                log.flipMode,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (log.flipMode == "MARTINGALE") ShortRed else ResetIndigo
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(4.dp), color = badgeBg) {
                                Text(log.targetSide, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                            }
                            Text("${log.targetVolume}c", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("$${log.estimatedPnl}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (log.estimatedPnl.startsWith("-")) ShortRed else LongGreen)
                            Text("Σ${log.cyclePnlAfter}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (log.cyclePnlAfter?.startsWith("-") == true) ShortRed else LongGreen)
                        }
                        if (log.errorMessage != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(log.errorMessage!!, fontSize = 9.sp, color = ShortRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrdersTab() {
    Card(modifier = Modifier.fillMaxWidth().padding(10.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Text("Açık emir yok", modifier = Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center, color = TextMuted)
    }
}

@Composable
fun SettingsTab(state: DashboardUiState, vm: DashboardViewModel) {
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // API Credentials
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("MEXC API (Futures)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("⚠️ Withdrawal izni KESİNLİKLE kapalı olmalı!", fontSize = 10.sp, color = ShortRed, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderColor, unfocusedBorderColor = BorderColor, focusedContainerColor = CardAlt, unfocusedContainerColor = CardAlt), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = apiSecret, onValueChange = { apiSecret = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API Secret") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderColor, unfocusedBorderColor = BorderColor, focusedContainerColor = CardAlt, unfocusedContainerColor = CardAlt), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (vm.saveCredentials(apiKey, apiSecret)) {
                                vm.setTradingMode(TradingMode.REAL)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                    ) { Text("Kaydet & Bağlan") }
                    Button(
                        onClick = { vm.testApiConnection() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ResetIndigo)
                    ) { Text("Test Et") }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { vm.deleteCredentials(); apiKey = ""; apiSecret = "" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("API bilgilerini tamamen sil", color = ShortRed) }
            }
        }

        // Reset Action
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Kâr Sonrası Davranış", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = when (state.resetAction) {
                            ResetAction.LONG -> "Yeni LONG pozisyon aç"
                            ResetAction.SHORT -> "Yeni SHORT pozisyon aç"
                            ResetAction.STOP -> "Pozisyonu kapat ve DURDUR"
                        },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderColor, unfocusedBorderColor = BorderColor, focusedContainerColor = CardAlt, unfocusedContainerColor = CardAlt),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = { Text("▼", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(end = 8.dp)) }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Yeni LONG pozisyon aç") }, onClick = { vm.setResetAction(ResetAction.LONG); expanded = false })
                        DropdownMenuItem(text = { Text("Yeni SHORT pozisyon aç") }, onClick = { vm.setResetAction(ResetAction.SHORT); expanded = false })
                        DropdownMenuItem(text = { Text("Pozisyonu kapat ve DURDUR") }, onClick = { vm.setResetAction(ResetAction.STOP); expanded = false })
                    }
                }
            }
        }

        // Screenshot
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Güvenlik", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("Uygulama sürümü: 1.0.0", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

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
