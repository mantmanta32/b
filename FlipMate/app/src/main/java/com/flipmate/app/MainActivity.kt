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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.flipmate.app.ui.theme.TerminalColors
import java.math.BigDecimal
import java.math.RoundingMode

// ═══════════════════════════════════════════════════════
//  Terminal Design Aliases
// ═══════════════════════════════════════════════════════
private val T = TerminalColors
private val Mono = FontFamily.Monospace
private val Sans = FontFamily.SansSerif
private val CardRadius = 12.dp
private val CardInnerPad = 12.dp

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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    if (state.showConfirmation && state.pendingPlan != null) {
        ConfirmationDialog(
            plan = state.pendingPlan!!,
            symbol = state.symbol,
            leverage = state.leverage,
            onConfirm = { vm.confirmFlip() },
            onCancel = { vm.cancelFlip() }
        )
    }

    val isReal = state.tradingMode == TradingMode.REAL

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = T.Base,
        bottomBar = {
            TerminalBottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(T.Base)
        ) {
            // REAL mode: top accent border
            if (isReal) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Brush.horizontalGradient(listOf(T.ShortRed, T.MartingaleAmber, T.ShortRed)))
                )
            }

            StatusBar(
                tradingMode = state.tradingMode,
                wsConnected = state.wsConnected,
                symbol = state.symbol,
                onToggleMode = {
                    vm.setTradingMode(if (state.tradingMode == TradingMode.SIM) TradingMode.REAL else TradingMode.SIM)
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

            if (selectedTab == 0) {
                FlipBar(state, vm)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  STATUS BAR
// ═══════════════════════════════════════════════════════
@Composable
fun StatusBar(tradingMode: TradingMode, wsConnected: Boolean, symbol: String, onToggleMode: () -> Unit) {
    val isReal = tradingMode == TradingMode.REAL
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = T.Surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
            Text(time, fontFamily = Mono, fontSize = 11.sp, color = T.TextSecondary, fontWeight = FontWeight.Medium)

            // WS Status with icon
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (wsConnected) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = "Bağlı", modifier = Modifier.size(14.dp), tint = T.LongGreen)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = "Bağlanıyor", modifier = Modifier.size(14.dp), tint = T.PendingAmber)
                }
                Text(symbol, fontFamily = Mono, fontSize = 10.sp, color = if (wsConnected) T.LongGreen else T.PendingAmber, fontWeight = FontWeight.Medium)
            }

            // SIM/REAL toggle with icons
            Surface(
                modifier = Modifier
                    .clickable { onToggleMode() }
                    .border(
                        width = 1.dp,
                        color = if (isReal) T.ShortRed.copy(alpha = 0.4f) else T.MartingaleAmber.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(6.dp)
                    ),
                shape = RoundedCornerShape(6.dp),
                color = if (isReal) T.ShortRedDim else T.MartingaleAmberDim
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isReal) Icons.Default.Shield else Icons.Default.Science,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (isReal) T.ShortRed else T.MartingaleAmber
                    )
                    Text(
                        text = if (isReal) "REAL" else "SIM",
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isReal) T.ShortRed else T.MartingaleAmber
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  PANEL TAB
// ═══════════════════════════════════════════════════════
@Composable
fun PanelTab(state: DashboardUiState, vm: DashboardViewModel, symbol: String, onSymbolChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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

// ═══════════════════════════════════════════════════════
//  PRICE CARD
// ═══════════════════════════════════════════════════════
@Composable
fun PriceCard(state: DashboardUiState, symbol: String, onSymbolChange: (String) -> Unit) {
    TerminalCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = symbol,
                onValueChange = onSymbolChange,
                modifier = Modifier.width(130.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = T.BorderStrong,
                    unfocusedBorderColor = T.Border,
                    focusedContainerColor = T.Elevated,
                    unfocusedContainerColor = T.Elevated,
                    focusedTextColor = T.TextPrimary,
                    unfocusedTextColor = T.TextPrimary,
                    cursorColor = T.AccentCyan
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                val price = state.ticker?.lastPrice
                Text(
                    "$ ${price?.let { formatPrice(it) } ?: "0.00"}",
                    fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = T.TextPrimary
                )
                val change = state.ticker?.priceChangePercent
                val isUp = change != null && change >= BigDecimal.ZERO
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isUp) T.LongGreen else T.ShortRed
                    )
                    Text(
                        "${if (isUp) "+" else ""}${change?.let { (it * BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) }?.toPlainString() ?: "0.00"}%",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        fontFamily = Mono,
                        color = if (isUp) T.LongGreen else T.ShortRed
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 24H stats row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val t = state.ticker
            PriceStat("24H HIGH", t?.high24h?.let { formatPrice(it) } ?: "—", T.TextSecondary)
            PriceStat("24H LOW", t?.low24h?.let { formatPrice(it) } ?: "—", T.TextSecondary)
            PriceStat("FAIR", t?.fairPrice?.let { formatPrice(it) } ?: "—", T.AccentCyan)
        }
    }
}

@Composable
private fun PriceStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        Text(value, fontSize = 11.sp, color = valueColor, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════
//  CYCLE BAR
// ═══════════════════════════════════════════════════════
@Composable
fun CycleBar(state: DashboardUiState) {
    val cycle = state.cycleState
    val pnl = cycle.runningPnl
    val isProfitable = pnl > BigDecimal.ZERO
    val pnlColor = if (isProfitable) T.LongGreen else if (pnl < BigDecimal.ZERO) T.ShortRed else T.TextMuted

    TerminalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isProfitable) T.LongGreenDim else if (pnl < BigDecimal.ZERO) T.ShortRedDim else T.SurfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cycle number
            Column {
                Text("CYCLE", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
                Text("#${cycle.cycleNumber}", fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = T.AccentPurple)
            }
            // Running PnL
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RUNNING PNL", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
                Text(
                    "${if (pnl >= BigDecimal.ZERO) "+" else ""}$${pnl.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                    fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = pnlColor
                )
            }
            // Status badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = pnlColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (isProfitable) "PROFIT" else if (pnl < BigDecimal.ZERO) "LOSS" else "BREAK",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Mono,
                    color = pnlColor,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  ACCOUNT CARD (REAL mode)
// ═══════════════════════════════════════════════════════
@Composable
fun AccountCard(state: DashboardUiState) {
    val asset = state.account ?: return
    TerminalCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.LongGreen)
            Spacer(Modifier.width(6.dp))
            Text("REAL KASA", fontSize = 10.sp, color = T.TextMuted, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(4.dp), color = T.LongGreenDim) {
                Text(asset.currency, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = T.LongGreen)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("EQUITY", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono); Text("$${asset.equity.setScale(2, RoundingMode.HALF_UP)}", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = T.TextPrimary) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("AVAILABLE", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono); Text("$${asset.availableOpen.setScale(2, RoundingMode.HALF_UP)}", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = T.LongGreen) }
            Column(horizontalAlignment = Alignment.End) { Text("POS MARGIN", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono); Text("$${asset.positionMargin.setScale(2, RoundingMode.HALF_UP)}", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = T.TextSecondary) }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("CASH", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono); Text("$${asset.cashBalance.setScale(2, RoundingMode.HALF_UP)}", fontFamily = Mono, fontSize = 11.sp, color = T.TextSecondary) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("UNREALIZED", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono)
                val isUp = asset.unrealized >= BigDecimal.ZERO
                Text("${if (isUp) "+" else ""}$${asset.unrealized.setScale(2, RoundingMode.HALF_UP)}", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isUp) T.LongGreen else T.ShortRed)
            }
            Column(horizontalAlignment = Alignment.End) { Text("FROZEN/BONUS", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono); Text("$${asset.frozenBalance.setScale(2, RoundingMode.HALF_UP)} / $${asset.bonus.setScale(2, RoundingMode.HALF_UP)}", fontFamily = Mono, fontSize = 10.sp, color = T.TextSecondary) }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  POSITION CARD (high priority)
// ═══════════════════════════════════════════════════════
@Composable
fun PositionCard(state: DashboardUiState) {
    val pos = state.position
    val isLong = pos?.side == PositionSide.LONG

    TerminalCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (pos != null) (if (isLong) T.LongGreen else T.ShortRed).copy(alpha = 0.3f) else T.Border,
        containerColor = T.Elevated
    ) {
        // Header row
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("POSITION", fontSize = 10.sp, color = T.TextMuted, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
                Spacer(Modifier.width(8.dp))
                if (pos != null) {
                    Surface(shape = RoundedCornerShape(4.dp), color = if (isLong) T.LongGreenDim else T.ShortRedDim) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (isLong) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (isLong) T.LongGreen else T.ShortRed
                            )
                            Text(pos.side.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = if (isLong) T.LongGreen else T.ShortRed)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${pos.holdVol}c", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = T.TextPrimary)
                    Text("${pos.leverage}x", fontFamily = Mono, fontSize = 10.sp, color = T.TextMuted, modifier = Modifier.padding(start = 4.dp))
                } else {
                    Surface(shape = RoundedCornerShape(4.dp), color = T.SurfaceVariant) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(12.dp), tint = T.TextMuted)
                            Text("NO POSITION", fontSize = 11.sp, color = T.TextMuted, fontFamily = Mono)
                        }
                    }
                }
            }

            if (pos != null) {
                val isUp = pos.unrealizedPnl >= BigDecimal.ZERO
                val pnlPct = if (pos.margin > BigDecimal.ZERO) (pos.unrealizedPnl / pos.margin * BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) else BigDecimal.ZERO
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${if (isUp) "+" else ""}$${pos.unrealizedPnl.setScale(2, RoundingMode.HALF_UP)}",
                        fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (isUp) T.LongGreen else T.ShortRed
                    )
                    Text(
                        "${if (isUp) "+" else ""}${pnlPct}%",
                        fontFamily = Mono, fontSize = 10.sp,
                        color = if (isUp) T.LongGreen else T.ShortRed
                    )
                }
            }
        }

        if (pos != null) {
            Spacer(Modifier.height(8.dp))
            // PnL Bar
            val pnlPct = if (pos.margin > BigDecimal.ZERO) ((pos.unrealizedPnl / pos.margin) * BigDecimal("100")).toFloat().coerceIn(-100f, 100f) else 0f
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(T.Base)) {
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).align(Alignment.Center).background(T.Border))
                if (pnlPct != 0f) {
                    val halfWidth = (kotlin.math.abs(pnlPct) / 200f).coerceIn(0.005f, 0.5f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(halfWidth)
                            .align(if (pnlPct >= 0) Alignment.CenterStart else Alignment.CenterEnd)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (pnlPct >= 0) T.LongGreen else T.ShortRed)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Data row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("ENTRY", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono, letterSpacing = 0.5.sp); Text("$${formatPrice(pos.entryPrice)}", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = T.TextPrimary) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("LIQ", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono, letterSpacing = 0.5.sp); Text("$${formatPrice(pos.liquidationPrice)}", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = T.ShortRed) }
                Column(horizontalAlignment = Alignment.End) { Text("MARGIN", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono, letterSpacing = 0.5.sp); Text("$${pos.margin.setScale(2, RoundingMode.HALF_UP)}", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = T.TextSecondary) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  LADDER CARD (Martingale)
// ═══════════════════════════════════════════════════════
@Composable
fun LadderCard(state: DashboardUiState) {
    val steps = mutableListOf<BigDecimal>()
    var s = state.resetSize
    while (s <= state.maxContracts && steps.size < 10) { steps.add(s); s = s * BigDecimal("2") }
    val currentSize = state.position?.holdVol ?: state.resetSize
    val currentIdx = steps.indexOf(currentSize)

    TerminalCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MARTINGALE LADDER", fontSize = 10.sp, color = T.TextMuted, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
            Text(if (currentIdx >= 0) "${currentIdx + 1}/${steps.size}" else "~${currentSize}c", fontFamily = Mono, fontSize = 10.sp, color = T.TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            steps.forEachIndexed { idx, step ->
                if (idx > 0) {
                    // Connector line
                    val isPast = currentIdx >= 0 && idx <= currentIdx
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(2.dp)
                            .background(if (isPast) T.MartingaleAmber else T.Border, RoundedCornerShape(1.dp))
                    )
                }
                val isActive = idx == currentIdx
                val isPast = currentIdx >= 0 && idx < currentIdx

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                isActive -> T.MartingaleAmberDim
                                isPast -> T.SurfaceVariant
                                else -> T.SurfaceVariant
                            }
                        )
                        .then(
                            if (isActive) Modifier.border(1.dp, T.MartingaleAmber, RoundedCornerShape(6.dp))
                            else Modifier
                        )
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPast) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = T.MartingaleAmber.copy(alpha = 0.7f))
                    } else if (isActive) {
                        Icon(Icons.Default.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(12.dp), tint = T.MartingaleAmber)
                    } else {
                        Text(step.toPlainString(), fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = T.TextMuted)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  MODE HINT (KARAR) CARD
// ═══════════════════════════════════════════════════════
@Composable
fun ModeHintCard(state: DashboardUiState) {
    val pos = state.position
    val cycle = state.cycleState
    val isProfitable = cycle.runningPnl > BigDecimal.ZERO

    val borderColor: Color
    val bgColor: Color
    val textColor: Color
    val icon: ImageVector
    val message: String

    when {
        isProfitable && state.resetAction == ResetAction.STOP -> {
            borderColor = T.ShortRed.copy(alpha = 0.4f)
            bgColor = T.ShortRedDim
            textColor = T.ShortRed
            icon = Icons.Default.StopCircle
            message = "Equity kârda → KAPAT & DURDUR"
        }
        isProfitable -> {
            borderColor = T.LongGreen.copy(alpha = 0.3f)
            bgColor = T.LongGreenDim
            textColor = T.LongGreen
            icon = Icons.Default.CheckCircleOutline
            message = "Equity kârda → SIFIRLA ${state.resetSize} ${state.resetAction.name}"
        }
        else -> {
            val newSide = pos?.side?.opposite?.name ?: "SHORT"
            val newSize = pos?.holdVol?.times(BigDecimal("2")) ?: BigDecimal.ZERO
            borderColor = T.MartingaleAmber.copy(alpha = 0.4f)
            bgColor = T.MartingaleAmberDim
            textColor = T.MartingaleAmber
            icon = Icons.Default.WarningAmber
            message = "Zararda → MARTINGALE → $newSize $newSide"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = textColor)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("SIGNAL", fontSize = 9.sp, color = textColor.copy(alpha = 0.6f), fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
                Text(message, fontSize = 13.sp, color = textColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  SETTINGS ROW (Leverage, Count, Max)
// ═══════════════════════════════════════════════════════
@Composable
fun SettingsRow(state: DashboardUiState, vm: DashboardViewModel) {
    var selectedLev by remember { mutableIntStateOf(state.leverage) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Leverage
        Column(modifier = Modifier.weight(1f)) {
            Text("LEVERAGE", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
            var expanded by remember { mutableStateOf(false) }
            Box {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = T.Elevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, T.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${selectedLev}x", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T.TextPrimary)
                            // Risk indicator
                            val riskColor = when {
                                selectedLev <= 10 -> T.LongGreen
                                selectedLev <= 20 -> T.MartingaleAmber
                                else -> T.ShortRed
                            }
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(riskColor))
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.TextMuted)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = T.Elevated) {
                    listOf(5, 10, 20, 50, 100).forEach { lev ->
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val rc = when {
                                        lev <= 10 -> T.LongGreen
                                        lev <= 20 -> T.MartingaleAmber
                                        else -> T.ShortRed
                                    }
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(rc))
                                    Text("${lev}x", fontFamily = Mono, fontWeight = FontWeight.SemiBold, color = T.TextPrimary)
                                    Text(
                                        when { lev <= 10 -> "LOW"; lev <= 20 -> "MED"; else -> "HIGH" },
                                        fontFamily = Mono, fontSize = 10.sp, color = rc
                                    )
                                }
                            },
                            onClick = { selectedLev = lev; vm.setLeverage(lev); expanded = false }
                        )
                    }
                }
            }
        }
        // Count
        Column(modifier = Modifier.weight(1f)) {
            Text("COUNT", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
            var sizeText by remember { mutableStateOf(state.resetSize.toPlainString()) }
            OutlinedTextField(
                value = sizeText,
                onValueChange = { sizeText = it; it.toIntOrNull()?.let { v -> vm.setResetSize(BigDecimal(v)) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T.TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = T.BorderStrong, unfocusedBorderColor = T.Border, focusedContainerColor = T.Elevated, unfocusedContainerColor = T.Elevated, cursorColor = T.AccentCyan),
                shape = RoundedCornerShape(8.dp)
            )
        }
        // Max
        Column(modifier = Modifier.weight(1f)) {
            Text("MAX", fontSize = 9.sp, color = T.TextMuted, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
            var maxText by remember { mutableStateOf(state.maxContracts.toPlainString()) }
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it; it.toIntOrNull()?.let { v -> vm.setMaxContracts(BigDecimal(v)) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T.TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = T.BorderStrong, unfocusedBorderColor = T.Border, focusedContainerColor = T.Elevated, unfocusedContainerColor = T.Elevated, cursorColor = T.AccentCyan),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
//  FLIP CTA BUTTON
// ═══════════════════════════════════════════════════════
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

    val isReal = state.tradingMode == TradingMode.REAL
    val btnBg = when {
        state.isFlipping -> T.TextMuted
        isReal && !state.isFlipping -> T.ShortRed
        else -> T.AccentPurple
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = T.Surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Button(
                onClick = { vm.flip() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !state.isFlipping && pos != null,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnBg,
                    disabledContainerColor = btnBg.copy(alpha = 0.5f),
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                if (state.isFlipping) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("İŞLENİYOR", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                } else {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("FLIP & 2X", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                }
            }
            Text(
                text = preview,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = T.TextMuted,
                fontFamily = Mono
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
//  BOTTOM NAVIGATION BAR
// ═══════════════════════════════════════════════════════
@Composable
fun TerminalBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = T.Surface,
        contentColor = T.TextPrimary,
        tonalElevation = 2.dp
    ) {
        data class NavItem(val label: String, val activeIcon: ImageVector, val inactiveIcon: ImageVector)
        val items = listOf(
            NavItem("Panel", Icons.Default.Dashboard, Icons.Default.Dashboard),
            NavItem("Log", Icons.Default.ReceiptLong, Icons.Default.ReceiptLong),
            NavItem("Emirler", Icons.Default.FactCheck, Icons.Default.FactCheck),
            NavItem("Ayarlar", Icons.Default.Settings, Icons.Default.Settings)
        )

        items.forEachIndexed { idx, item ->
            val selected = idx == selectedTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(idx) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.activeIcon else item.inactiveIcon,
                        contentDescription = item.label,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) T.AccentPurple else T.TextMuted
                    )
                },
                label = {
                    Text(
                        item.label,
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = T.AccentPurple,
                    selectedTextColor = T.AccentPurple,
                    unselectedIconColor = T.TextMuted,
                    unselectedTextColor = T.TextMuted,
                    indicatorColor = T.AccentPurple.copy(alpha = 0.08f)
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
//  CONFIRMATION DIALOG
// ═══════════════════════════════════════════════════════
@Composable
fun ConfirmationDialog(plan: FlipPlanInfo, symbol: String, leverage: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val isReal = plan.isReal
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = T.Elevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (isReal) Icons.Default.Warning else Icons.Default.Science,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (isReal) T.ShortRed else T.MartingaleAmber
                )
                Text(
                    if (isReal) "GERÇEK EMİR" else "SİMÜLASYON",
                    fontWeight = FontWeight.Bold,
                    fontFamily = Mono,
                    fontSize = 14.sp,
                    color = if (isReal) T.ShortRed else T.MartingaleAmber,
                    letterSpacing = 0.5.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(symbol, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = T.TextPrimary)
                // Trade details
                Surface(shape = RoundedCornerShape(8.dp), color = T.Surface) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Text("→ ${plan.currentVol} ${plan.currentSide}  ▸  ${plan.targetVol} ${plan.targetSide}", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = T.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("Market / Isolated / ${leverage}x", fontSize = 11.sp, color = T.TextSecondary)
                    }
                }
                // Mode badge
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (plan.mode == "MARTINGALE") Icons.Default.Warning else Icons.Default.Refresh,
                        contentDescription = null, modifier = Modifier.size(14.dp),
                        tint = if (plan.mode == "MARTINGALE") T.MartingaleAmber else T.LongGreen
                    )
                    Text(
                        plan.mode,
                        fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (plan.mode == "MARTINGALE") T.MartingaleAmber else T.LongGreen
                    )
                }
                // Est PnL
                val isNeg = plan.estimatedPnl.startsWith("-")
                Text(
                    "Est. PnL: ${plan.estimatedPnl} USDT",
                    fontFamily = Mono, fontSize = 12.sp,
                    color = if (isNeg) T.ShortRed else T.LongGreen
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = if (isReal) T.ShortRed else T.AccentPurple),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(if (isReal) Icons.Default.Bolt else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isReal) "GERÇEK FLIP" else "SİMÜLE ET", fontFamily = Mono, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("İptal", color = T.TextMuted) }
        }
    )
}

// ═══════════════════════════════════════════════════════
//  HISTORY TAB
// ═══════════════════════════════════════════════════════
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.TextSecondary)
                Text("FLIP GEÇMİŞİ", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = T.TextPrimary, letterSpacing = 0.5.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.resetCycle() }) { Text("Sıfırla", fontFamily = Mono, fontSize = 11.sp, color = T.TextSecondary) }
                TextButton(onClick = { vm.clearLogs() }) { Text("Temizle", fontFamily = Mono, fontSize = 11.sp, color = T.ShortRed) }
            }
        }

        if (state.flipLogs.isEmpty()) {
            TerminalEmptyState(
                icon = Icons.Default.ReceiptLong,
                title = "Henüz flip yok",
                subtitle = "FLIP & 2X butonuna basarak ilk işlemini yap"
            )
        } else {
            state.flipLogs.forEach { log ->
                val isLong = log.targetSide == "LONG"
                val isStop = log.targetSide == "STOP"
                val isSuccess = log.status == "SUCCESS"
                val sideColor = when {
                    isStop -> T.TextMuted
                    isLong -> T.LongGreen
                    else -> T.ShortRed
                }
                val sideBg = when {
                    isStop -> T.SurfaceVariant
                    isLong -> T.LongGreenDim
                    else -> T.ShortRedDim
                }

                TerminalCard(modifier = Modifier.fillMaxWidth(), containerColor = T.Surface) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.createdAt))
                        Text("#${log.cycleNumber} · $timeStr", fontFamily = Mono, fontSize = 10.sp, color = T.TextMuted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                if (log.flipMode == "MARTINGALE") Icons.Default.Warning else Icons.Default.Refresh,
                                contentDescription = null, modifier = Modifier.size(10.dp),
                                tint = if (log.flipMode == "MARTINGALE") T.MartingaleAmber else T.AccentCyan
                            )
                            Text(log.flipMode, fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (log.flipMode == "MARTINGALE") T.MartingaleAmber else T.AccentCyan)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(4.dp), color = sideBg) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    if (isLong) Icons.Default.TrendingUp else if (isStop) Icons.Default.RemoveCircleOutline else Icons.Default.TrendingDown,
                                    contentDescription = null, modifier = Modifier.size(10.dp), tint = sideColor
                                )
                                Text(log.targetSide, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = sideColor)
                            }
                        }
                        Text("${log.targetVolume}c", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = T.TextPrimary)
                        Text("$${log.estimatedPnl}", fontFamily = Mono, fontSize = 10.sp, color = if (log.estimatedPnl.startsWith("-")) T.ShortRed else T.LongGreen)
                        Text("Σ${log.cyclePnlAfter}", fontFamily = Mono, fontSize = 10.sp, color = if (log.cyclePnlAfter?.startsWith("-") == true) T.ShortRed else T.LongGreen)
                    }
                    if (log.errorMessage != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(10.dp), tint = T.ShortRed)
                            Text(log.errorMessage!!, fontSize = 9.sp, color = T.ShortRed, fontFamily = Mono)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  ORDERS TAB
// ═══════════════════════════════════════════════════════
@Composable
fun OrdersTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.TextSecondary)
            Text("AÇIK EMİRLER", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = T.TextPrimary, letterSpacing = 0.5.sp)
        }
        TerminalEmptyState(
            icon = Icons.Default.Inbox,
            title = "Açık emir yok",
            subtitle = "Bir flip işlemi başlattığında emirler burada görünecek"
        )
    }
}

// ═══════════════════════════════════════════════════════
//  SETTINGS TAB
// ═══════════════════════════════════════════════════════
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
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.AccentPurple)
                Text("MEXC API", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = T.TextPrimary, letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp), tint = T.ShortRed)
                Text("Withdrawal izni KESİNLİKLE kapalı olmalı!", fontSize = 10.sp, color = T.ShortRed, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API Key", color = T.TextMuted) }, singleLine = true, leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.TextMuted) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = T.BorderStrong, unfocusedBorderColor = T.Border, focusedContainerColor = T.Surface, unfocusedContainerColor = T.Surface, focusedTextColor = T.TextPrimary, unfocusedTextColor = T.TextPrimary, focusedLabelColor = T.TextSecondary, unfocusedLabelColor = T.TextMuted, cursorColor = T.AccentCyan), shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = apiSecret, onValueChange = { apiSecret = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API Secret", color = T.TextMuted) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.TextMuted) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = T.BorderStrong, unfocusedBorderColor = T.Border, focusedContainerColor = T.Surface, unfocusedContainerColor = T.Surface, focusedTextColor = T.TextPrimary, unfocusedTextColor = T.TextPrimary, focusedLabelColor = T.TextSecondary, unfocusedLabelColor = T.TextMuted, cursorColor = T.AccentCyan), shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (vm.saveCredentials(apiKey, apiSecret)) vm.setTradingMode(TradingMode.REAL) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = T.AccentPurple)) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kaydet & Bağlan", fontFamily = Mono, fontSize = 12.sp)
                }
                Button(onClick = { vm.testApiConnection() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = T.Elevated)) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Test", fontFamily = Mono, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { vm.deleteCredentials(); apiKey = ""; apiSecret = "" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = T.ShortRed)
                Spacer(Modifier.width(4.dp))
                Text("API bilgilerini tamamen sil", fontFamily = Mono, fontSize = 11.sp, color = T.ShortRed)
            }
        }

        // Reset Action
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.ToggleOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.AccentCyan)
                Text("KÂR SONRASI DAVRANIŞ", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = T.TextPrimary, letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.height(8.dp))
            var expanded by remember { mutableStateOf(false) }
            Box {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = T.Surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().border(1.dp, T.Border, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val icon = when (state.resetAction) {
                                ResetAction.LONG -> Icons.Default.TrendingUp
                                ResetAction.SHORT -> Icons.Default.TrendingDown
                                ResetAction.STOP -> Icons.Default.StopCircle
                            }
                            val iconColor = when (state.resetAction) {
                                ResetAction.LONG -> T.LongGreen
                                ResetAction.SHORT -> T.ShortRed
                                ResetAction.STOP -> T.TextMuted
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = iconColor)
                            val label = when (state.resetAction) {
                                ResetAction.LONG -> "Yeni LONG pozisyon aç"
                                ResetAction.SHORT -> "Yeni SHORT pozisyon aç"
                                ResetAction.STOP -> "Kapat ve DURDUR"
                            }
                            Text(label, fontSize = 12.sp, color = T.TextPrimary, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = T.TextMuted)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = T.Elevated) {
                    DropdownMenuItem(
                        text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.LongGreen); Text("Yeni LONG pozisyon aç", fontFamily = Mono, fontSize = 12.sp, color = T.TextPrimary) } },
                        onClick = { vm.setResetAction(ResetAction.LONG); expanded = false },
                        leadingIcon = { if (state.resetAction == ResetAction.LONG) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = T.LongGreen) }
                    )
                    DropdownMenuItem(
                        text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.TrendingDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.ShortRed); Text("Yeni SHORT pozisyon aç", fontFamily = Mono, fontSize = 12.sp, color = T.TextPrimary) } },
                        onClick = { vm.setResetAction(ResetAction.SHORT); expanded = false },
                        leadingIcon = { if (state.resetAction == ResetAction.SHORT) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = T.LongGreen) }
                    )
                    DropdownMenuItem(
                        text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.TextMuted); Text("Kapat ve DURDUR", fontFamily = Mono, fontSize = 12.sp, color = T.TextPrimary) } },
                        onClick = { vm.setResetAction(ResetAction.STOP); expanded = false },
                        leadingIcon = { if (state.resetAction == ResetAction.STOP) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = T.LongGreen) }
                    )
                }
            }
        }

        // Security card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp), tint = T.LongGreen)
                Text("GÜVENLİK", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = T.TextPrimary, letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("Uygulama sürümü: 1.0.0", fontSize = 11.sp, color = T.TextSecondary, fontFamily = Mono)
            Text("Şifreli credential storage: AES-256-GCM", fontSize = 10.sp, color = T.TextMuted, fontFamily = Mono)
        }
    }
}

// ═══════════════════════════════════════════════════════
//  REUSABLE COMPONENTS
// ═══════════════════════════════════════════════════════

@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = T.Surface,
    borderColor: Color = T.Border,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.border(0.5.dp, borderColor, RoundedCornerShape(CardRadius)),
        shape = RoundedCornerShape(CardRadius),
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(CardInnerPad)) {
            content()
        }
    }
}

@Composable
fun TerminalEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    TerminalCard(modifier = Modifier.fillMaxWidth(), containerColor = T.Surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = T.TextMuted)
            Text(title, fontSize = 13.sp, color = T.TextSecondary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = T.TextMuted, textAlign = TextAlign.Center)
        }
    }
}

// ═══════════════════════════════════════════════════════
//  UTILITIES
// ═══════════════════════════════════════════════════════

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
