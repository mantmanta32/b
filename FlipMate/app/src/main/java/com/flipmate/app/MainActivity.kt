package com.flipmate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flipmate.app.core.network.PrivateApiService
import com.flipmate.app.core.network.PublicApiService
import com.flipmate.app.data.local.AppDatabase
import com.flipmate.app.data.repository.TickerRepositoryImpl
import com.flipmate.app.ui.dashboard.*
import com.flipmate.app.ui.theme.*
import com.flipmate.app.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlipMateTheme {
                val app = application as FlipMateApplication
                val db = remember { AppDatabase.get(this) }
                val dao = remember { db.flipLogDao() }
                val viewModel: DashboardViewModel = viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>): T {
                            val publicApi = PublicApiService(app.network.publicClient)
                            val privateApi = try { PrivateApiService(app.network.privateClient) } catch (_: Exception) { null }
                            val tickerRepo = TickerRepositoryImpl(publicApi, app.wsManager)
                            @Suppress("UNCHECKED_CAST")
                            return DashboardViewModel(tickerRepo, privateApi, app, dao) as T
                        }
                    }
                )
                FlipMateTerminalScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlipMateTerminalScreen(viewModel: DashboardViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLeverageSheet by remember { mutableStateOf(false) }
    var symbolInput by remember { mutableStateOf(state.symbol) }
    var resetSizeInput by remember { mutableStateOf(state.resetSize.toPlainString()) }
    var maxContractsInput by remember { mutableStateOf(state.maxContracts.toPlainString()) }

    // Sembol değişimini debounce et
    LaunchedEffect(symbolInput) {
        kotlinx.coroutines.delay(400)
        if (symbolInput.uppercase() != state.symbol) {
            viewModel.setSymbol(symbolInput.uppercase())
        }
    }

    Scaffold(
        containerColor = PastelBg,
        bottomBar = {
            FlipMateBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        snackbarHost = {
            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(state.error) {
                state.error?.let {
                    snackbarHostState.showSnackbar(it)
                    viewModel.clearError()
                }
            }
            SnackbarHost(snackbarHostState)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Üst Bar: Logo, Mod, WS Bağlantısı
            item {
                TopAppBarHeader(
                    isWsConnected = state.wsConnected,
                    tradingMode = state.tradingMode,
                    onToggleMode = {
                        viewModel.setTradingMode(
                            if (state.tradingMode == TradingMode.SIM) TradingMode.REAL
                            else TradingMode.SIM
                        )
                    }
                )
            }

            // Hero Canlı Pozisyon ve Fiyat Motoru
            item {
                HeroPositionEngine(
                    symbol = state.symbol,
                    ticker = state.ticker,
                    position = state.position,
                    cycleState = state.cycleState
                )
            }

            // Martingale Merdiveni Görsel
            item {
                val steps = buildMartingaleSteps(state.resetSize, state.maxContracts)
                val currentIdx = state.position?.holdVol?.let { holdVol ->
                    steps.indexOfFirst { it >= holdVol }.coerceAtLeast(0)
                } ?: 0
                MartingaleStaircaseVisualizer(
                    currentStep = currentIdx + 1,
                    maxSteps = steps.size,
                    multiplierSequence = steps.map { it.toPlainString() }
                )
            }

            // FLIP Butonu
            item {
                FlipMicroInteractionButton(
                    isEnabled = state.position != null && !state.isFlipping,
                    isFlipping = state.isFlipping,
                    onClick = { viewModel.flip() }
                )
            }

            // Segmented Tabs
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = PastelPrimary,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Parametreler", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Açık Emirler", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Flip Geçmişi", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            // Tab İçeriği
            when (selectedTab) {
                0 -> {
                    item {
                        StrategyParametersPanel(
                            leverage = state.leverage,
                            resetSize = resetSizeInput,
                            maxContracts = maxContractsInput,
                            onOpenLeverageSheet = { showLeverageSheet = true },
                            onResetSizeChange = {
                                resetSizeInput = it
                                it.toIntOrNull()?.let { v -> viewModel.setResetSize(BigDecimal(v)) }
                            },
                            onMaxContractsChange = {
                                maxContractsInput = it
                                it.toIntOrNull()?.let { v -> viewModel.setMaxContracts(BigDecimal(v)) }
                            }
                        )
                    }
                }
                1 -> {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.ReceiptLong,
                            title = "Açık emir yok",
                            description = "Martingale motoru tetiklendiğinde aktif emirler burada listelenir."
                        )
                    }
                }
                2 -> {
                    if (state.flipLogs.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Rounded.History,
                                title = "Henüz flip yok",
                                description = "Bu oturumda gerçekleşen flip işlemleri burada görünecek."
                            )
                        }
                    } else {
                        items(state.flipLogs) { log ->
                            FlipLogItem(log = log)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Leverage Modal Bottom Sheet
    if (showLeverageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLeverageSheet = false },
            containerColor = PastelSurface
        ) {
            LeverageSelectorContent(
                currentLeverage = state.leverage,
                onSelected = {
                    viewModel.setLeverage(it)
                    showLeverageSheet = false
                }
            )
        }
    }

    // Confirmation Dialog (REAL modda)
    if (state.showConfirmation && state.pendingPlan != null) {
        val plan = state.pendingPlan!!
        AlertDialog(
            onDismissRequest = { viewModel.cancelFlip() },
            containerColor = PastelSurface,
            title = {
                Text(
                    if (plan.isReal) "⚠️ Gerçek Emir Onayı" else "Simülasyon Onayı",
                    color = PastelTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${plan.targetSide} ${plan.targetVol} kontrat",
                        fontWeight = FontWeight.Bold,
                        color = PastelTextPrimary
                    )
                    Text(
                        "Mod: ${plan.mode}",
                        color = PastelTextSecondary
                    )
                    Text(
                        "Tahmini PnL: ${plan.estimatedPnl} USDT",
                        color = if (plan.estimatedPnl.startsWith("-")) SoftShortRed else SoftLongGreen
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmFlip() },
                    colors = ButtonDefaults.buttonColors(containerColor = PastelPrimary)
                ) {
                    Text(if (plan.isReal) "Gerçek Flip" else "Simüle Et")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelFlip() }) {
                    Text("İptal", color = PastelTextSecondary)
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════
// Üst Header & Bağlantı Göstergesi
// ═══════════════════════════════════════════════════════
@Composable
fun TopAppBarHeader(
    isWsConnected: Boolean,
    tradingMode: TradingMode,
    onToggleMode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PastelPrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Autorenew,
                    contentDescription = "FlipMate Logo",
                    tint = PastelPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "FlipMate",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PastelTextPrimary
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // WS Rozeti
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isWsConnected) SoftLongGreenBg else SoftShortRedBg,
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isWsConnected) SoftLongGreen else SoftShortRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isWsConnected) "WS Canlı" else "Koptu",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isWsConnected) SoftLongGreen else SoftShortRed
                    )
                }
            }

            // SIM / REAL Rozeti
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (tradingMode == TradingMode.SIM) SoftWarningYellow.copy(alpha = 0.15f) else PastelPrimaryContainer,
                modifier = Modifier
                    .height(28.dp)
                    .clickable { onToggleMode() }
            ) {
                Text(
                    text = tradingMode.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (tradingMode == TradingMode.SIM) SoftWarningYellow else PastelPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Hero Canlı Pozisyon ve Fiyat Motoru
// ═══════════════════════════════════════════════════════
@Composable
fun HeroPositionEngine(
    symbol: String,
    ticker: Ticker?,
    position: OpenPosition?,
    cycleState: CycleState
) {
    val isLong = position?.side == PositionSide.LONG
    val badgeBg = if (position == null) PastelSurfaceVariant else if (isLong) SoftLongGreenBg else SoftShortRedBg
    val badgeColor = if (position == null) PastelTextSecondary else if (isLong) SoftLongGreen else SoftShortRed
    val markPrice = ticker?.lastPrice?.toDouble()?.let { String.format("%.2f", it) } ?: "0.00"
    val pnlValue = position?.unrealizedPnl ?: BigDecimal.ZERO
    val pnlText = formatPnl(pnlValue)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PastelSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = symbol,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PastelTextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = if (position == null) "BOŞ" else position.side.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "$$markPrice",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PastelTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CANLI PnL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PastelTextSecondary,
                letterSpacing = 1.sp
            )
            Text(
                text = pnlText,
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (pnlValue >= BigDecimal.ZERO) SoftLongGreen else SoftShortRed
            )

            if (cycleState.runningPnl != BigDecimal.ZERO) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Döngü #${cycleState.cycleNumber}",
                        fontSize = 12.sp,
                        color = PastelTextSecondary
                    )
                    Text(
                        text = "Genel: ${formatPnl(cycleState.runningPnl)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (cycleState.runningPnl >= BigDecimal.ZERO) SoftLongGreen else SoftShortRed
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// İmza Öğesi: Martingale Merdiveni Görsel
// ═══════════════════════════════════════════════════════
@Composable
fun MartingaleStaircaseVisualizer(
    currentStep: Int,
    maxSteps: Int,
    multiplierSequence: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PastelSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MARTINGALE MERDİVENİ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PastelTextSecondary,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Adım $currentStep / $maxSteps",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PastelPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                multiplierSequence.forEachIndexed { index, mult ->
                    val isActive = index + 1 == currentStep
                    val isPassed = index + 1 < currentStep

                    val stepHeight = (18 + (index * 6)).dp
                    val stepColor = when {
                        isActive -> PastelPrimary
                        isPassed -> PastelSecondary.copy(alpha = 0.5f)
                        else -> PastelTextSecondary.copy(alpha = 0.2f)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = mult,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) PastelPrimary else PastelTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(stepHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(stepColor)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Mikro-etkileşimli FLIP Butonu
// ═══════════════════════════════════════════════════════
@Composable
fun FlipMicroInteractionButton(
    isEnabled: Boolean,
    isFlipping: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ButtonScale"
    )

    Button(
        onClick = {
            isPressed = true
            onClick()
            isPressed = false
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PastelPrimary,
            disabledContainerColor = PastelPrimary.copy(alpha = 0.4f)
        ),
        enabled = isEnabled,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isFlipping) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "Flip Action",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (isFlipping) "İŞLENİYOR..." else "ŞİMDİ FLIP ET",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Strateji Parametreleri Paneli
// ═══════════════════════════════════════════════════════
@Composable
fun StrategyParametersPanel(
    leverage: Int,
    resetSize: String,
    maxContracts: String,
    onOpenLeverageSheet: () -> Unit,
    onResetSizeChange: (String) -> Unit,
    onMaxContractsChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Kaldıraç Seçici Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenLeverageSheet() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PastelSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = "Kaldıraç",
                        tint = PastelPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Kaldıraç Değeri",
                        fontWeight = FontWeight.Medium,
                        color = PastelTextPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${leverage}x",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = PastelPrimary
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Aç",
                        tint = PastelTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Reset Size Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PastelSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset Size",
                        tint = PastelPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Reset Büyüklüğü",
                        fontWeight = FontWeight.Medium,
                        color = PastelTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = resetSize,
                    onValueChange = onResetSizeChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PastelPrimary,
                        unfocusedBorderColor = PastelBorder,
                        focusedContainerColor = PastelSurface,
                        unfocusedContainerColor = PastelSurface
                    )
                )
            }
        }

        // Max Contracts Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PastelSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Max Contracts",
                        tint = PastelPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Maksimum Kontrat",
                        fontWeight = FontWeight.Medium,
                        color = PastelTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = maxContracts,
                    onValueChange = onMaxContractsChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PastelPrimary,
                        unfocusedBorderColor = PastelBorder,
                        focusedContainerColor = PastelSurface,
                        unfocusedContainerColor = PastelSurface
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Leverage Selector Bottom Sheet
// ═══════════════════════════════════════════════════════
@Composable
fun LeverageSelectorContent(
    currentLeverage: Int,
    onSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Kaldıraç Seçin",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PastelTextPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        listOf(5, 10, 20, 50, 75, 100).forEach { lev ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(lev) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${lev}x Multiplier",
                    fontSize = 16.sp,
                    color = PastelTextPrimary
                )
                if (lev == currentLeverage) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Seçili",
                        tint = PastelPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (lev != 100) {
                HorizontalDivider(color = PastelSurfaceVariant)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Alt Navigasyon
// ═══════════════════════════════════════════════════════
@Composable
fun FlipMateBottomNavigation(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = PastelSurface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Rounded.ShowChart, contentDescription = "Terminal") },
            label = { Text("Terminal") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PastelPrimary,
                indicatorColor = PastelPrimaryContainer
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Rounded.Tune, contentDescription = "Ayarlar") },
            label = { Text("Ayarlar") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PastelPrimary,
                indicatorColor = PastelPrimaryContainer
            )
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Rounded.History, contentDescription = "Geçmiş") },
            label = { Text("Geçmiş") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PastelPrimary,
                indicatorColor = PastelPrimaryContainer
            )
        )
    }
}

// ═══════════════════════════════════════════════════════
// Boş Durum Görünümü
// ═══════════════════════════════════════════════════════
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(PastelSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PastelTextSecondary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PastelTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            fontSize = 12.sp,
            color = PastelTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════
// Flip Log Item
// ═══════════════════════════════════════════════════════
@Composable
fun FlipLogItem(log: com.flipmate.app.data.local.entity.FlipLogEntity) {
    val isLong = log.targetSide == "LONG"
    val sideColor = if (isLong) SoftLongGreen else SoftShortRed
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val time = timeFormat.format(Date(log.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PastelSurface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#${log.cycleNumber} • $time",
                    fontSize = 11.sp,
                    color = PastelTextSecondary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (log.flipMode == "MARTINGALE") SoftShortRedBg else SoftLongGreenBg
                ) {
                    Text(
                        text = log.flipMode,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (log.flipMode == "MARTINGALE") SoftShortRed else SoftLongGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isLong) SoftLongGreenBg else SoftShortRedBg
                ) {
                    Text(
                        text = log.targetSide,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = sideColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "${log.targetVolume} kontrat",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PastelTextPrimary
                )
                Text(
                    text = "${log.estimatedPnl} USDT",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (log.estimatedPnl.startsWith("-")) SoftShortRed else SoftLongGreen
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Yardımcı Fonksiyonlar
// ═══════════════════════════════════════════════════════
fun buildMartingaleSteps(resetSize: BigDecimal, maxContracts: BigDecimal): List<BigDecimal> {
    val steps = mutableListOf<BigDecimal>()
    var current = resetSize
    while (current <= maxContracts && steps.size < 6) {
        steps.add(current)
        current = current * BigDecimal(2)
    }
    return steps
}

fun formatPnl(value: BigDecimal): String {
    val scaled = value.setScale(2, RoundingMode.HALF_UP)
    val prefix = if (scaled >= BigDecimal.ZERO) "+" else ""
    return "$prefix$scaled USDT"
}
