package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AppDatabase
import com.example.data.engine.CycleReport
import com.example.data.engine.FinanceCalculations
import com.example.data.engine.ShiftFinancials
import com.example.data.model.BillingCycle
import com.example.data.model.SessionModel
import com.example.data.model.ShiftLog
import com.example.data.repository.PayTrackerRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.DeepCharcoal
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.NeonMint
import com.example.ui.theme.SoftCrimson
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.viewmodel.PayTrackerViewModel
import com.example.viewmodel.PayTrackerViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Setup database & repository
        val database = AppDatabase.getDatabase(this)
        val repository = PayTrackerRepository(database.dao())
        val factory = PayTrackerViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                val viewModel: PayTrackerViewModel = viewModel(factory = factory)
                val context = LocalContext.current
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // List for alerts
                LaunchedEffect(Unit) {
                    viewModel.alertMessage.collectLatest { msg ->
                        // Standard interactive message
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = msg,
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepCharcoal
                ) {
                    MainAppScreen(viewModel = viewModel, snackbarHostState = snackbarHostState)
                }
            }
        }
    }
}

// Share PDF Intent Helper
fun sharePdfReport(context: Context, pdfFile: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.paytracker.mzkvqy.fileprovider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Salary Report PDF"))
    } catch (e: Exception) {
         Toast.makeText(context, "Error sharing report: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun MainAppScreen(
    viewModel: PayTrackerViewModel,
    snackbarHostState: SnackbarHostState
) {
    var activeTab by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    val currentCycle by viewModel.currentCycle.collectAsStateWithLifecycle()
    val currentReport by viewModel.currentReport.collectAsStateWithLifecycle()
    val currentLogs by viewModel.currentLogs.collectAsStateWithLifecycle()
    val cyclesList by viewModel.cycles.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = ElevatedSlate,
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("HOME", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemColors(
                        selectedIconColor = NeonMint,
                        selectedTextColor = NeonMint,
                        selectedIndicatorColor = DeepCharcoal,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        disabledIconColor = Color.DarkGray,
                        disabledTextColor = Color.DarkGray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Logs") },
                    label = { Text("LOGS", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemColors(
                        selectedIconColor = NeonMint,
                        selectedTextColor = NeonMint,
                        selectedIndicatorColor = DeepCharcoal,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        disabledIconColor = Color.DarkGray,
                        disabledTextColor = Color.DarkGray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "HR Setup") },
                    label = { Text("HR SETUP", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemColors(
                        selectedIconColor = NeonMint,
                        selectedTextColor = NeonMint,
                        selectedIndicatorColor = DeepCharcoal,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        disabledIconColor = Color.DarkGray,
                        disabledTextColor = Color.DarkGray
                    )
                )
            }
        },
        containerColor = DeepCharcoal
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Section
            HeaderView()

            if (currentCycle == null) {
                // Empty state warning
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonMint)
                }
            } else {
                when (activeTab) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        currentCycle = currentCycle!!,
                        currentReport = currentReport,
                        recentLogs = currentLogs.takeLast(3),
                        onLogShiftClick = { showAddDialog = true }
                    )
                    1 -> LogsScreen(
                        logs = currentLogs,
                        report = currentReport,
                        onDeleteLog = { viewModel.deleteShiftLog(it) },
                        onDeleteAllLogs = { viewModel.deleteAllShiftLogsForCurrentCycle() }
                    )
                    2 -> SettingsScreen(
                        viewModel = viewModel,
                        currentCycle = currentCycle!!,
                        cyclesList = cyclesList
                    )
                }
            }
        }
    }

    if (showAddDialog && currentCycle != null) {
        AddShiftLogDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { date, supStart, actStart, actEnd, isDouble, isSixHour, sessions, isWfh ->
                val success = viewModel.addShiftLog(
                    date = date,
                    scheduledStartTime = supStart,
                    actualStartTime = actStart,
                    actualEndTime = actEnd,
                    isDoubleShift = isDouble,
                    isSixHourShift = isSixHour,
                    sessions = sessions,
                    isWorkFromHome = isWfh
                )
                if (success) {
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun HeaderView() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "FINTECH ARCHITECT",
                fontSize = 11.sp,
                color = Color.Gray,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Payment Tracker",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        
        // JD visual avatar circle from specs
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ElevatedSlate)
                .border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "JD",
                color = NeonMint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: PayTrackerViewModel,
    currentCycle: BillingCycle,
    currentReport: CycleReport?,
    recentLogs: List<ShiftLog>,
    onLogShiftClick: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning Banner if Cycle complete/locked
        if (currentReport != null && currentReport.isCycleComplete) {
            item {
                Card(
                     colors = CardDefaults.cardColors(containerColor = SoftCrimson.copy(alpha = 0.15f)),
                     border = BorderStroke(1.dp, SoftCrimson),
                     shape = RoundedCornerShape(16.dp),
                     modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked Notification",
                            tint = SoftCrimson,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "30-Day Cycle Complete!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoftCrimson
                            )
                            Text(
                                text = "Entries are locked. Export HR Salary Report now to process payment.",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                        Button(
                            onClick = { viewModel.unlockCycle() },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftCrimson),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("UNLOCK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Active cycle card with target day markers and 2 progress bars
        item {
            CycleProgressCard(currentReport, currentCycle)
        }

        // Financial summary 2-grid
        item {
            FinancialsGrid(currentReport)
        }

        // Sessions Analysis summary
        item {
            SessionsSummaryCard(currentReport)
        }

        // Calendar matrix shift representations
        item {
            CalendarMatrixView(currentReport)
        }

        // Recent shift log sessions list
        item {
            RecentLogsList(currentReport)
        }

        // Quick action buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onLogShiftClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("log_shift_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentCycle.isLocked) Color.Gray else NeonMint,
                        contentColor = DeepCharcoal
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !currentCycle.isLocked
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LOG SHIFT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val file = viewModel.exportHrPdf(context)
                        if (file != null) {
                            sharePdfReport(context, file)
                        } else {
                            Toast.makeText(context, "Export error", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("export_pdf_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedSlate,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Export")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SHARE HR PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CycleProgressCard(
    report: CycleReport?,
    cycle: BillingCycle
) {
    val totalMilestones = report?.totalMilestones ?: 0
    val daysLeft = maxOf(0, 30 - totalMilestones)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Active Cycle Name: ${cycle.name}",
                        color = NeonMint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Day $totalMilestones ",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CYCLE TARGET",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$daysLeft Days Left",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dual Progress Split Engine
            // 1. Payout block (1 to 22)
            val payoutProgress = minOf(1.0f, totalMilestones.toFloat() / 22.0f)
            val payoutDays = minOf(22, totalMilestones)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current Payout Block (Days 1 -> 22)",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$payoutDays / 22 Days",
                        color = NeonMint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { payoutProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NeonMint,
                    trackColor = DeepCharcoal
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Reserve / Retained Pending Wallet block (23 to 30)
            val reserveMilestones = maxOf(0, totalMilestones - 22)
            val reserveProgress = minOf(1.0f, reserveMilestones.toFloat() / 8.0f)
            val isReserveActive = totalMilestones > 22
            val reserveLabelColor = if (isReserveActive) Color.LightGray else Color.Gray
            val reserveValueColor = if (isReserveActive) ElectricPurple else Color.DarkGray

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pending Wallet Reserve (Days 23 -> 30)",
                        color = reserveLabelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$reserveMilestones / 8 Days",
                        color = reserveValueColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { reserveProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = ElectricPurple,
                    trackColor = DeepCharcoal
                )
            }
        }
    }
}

@Composable
fun SessionsSummaryCard(report: CycleReport?) {
    val reportLogs = (report?.payoutLogs ?: emptyList()) + (report?.reserveLogs ?: emptyList())
    val allSessions = reportLogs.flatMap { it.sessions }

    val normalSessions = allSessions.filter { it.sessionType.equals("Normal", ignoreCase = true) }
    val campSessions = allSessions.filter { it.sessionType.equals("Camp", ignoreCase = true) }

    val normalLowSessions = normalSessions.filter { it.kidCount <= 3 }
    val normalHighSessions = normalSessions.filter { it.kidCount > 3 }
    val campLowSessions = campSessions.filter { it.kidCount <= 6 }
    val campHighSessions = campSessions.filter { it.kidCount > 6 }

    val normalLowCount = normalLowSessions.size
    val normalHighCount = normalHighSessions.size
    val campLowCount = campLowSessions.size
    val campHighCount = campHighSessions.size

    val normalLowHours = normalLowCount * 2.0
    val normalHighHours = normalHighCount * 2.0
    val campLowHours = campLowSessions.sumOf { it.sessionCount * it.hoursPerSession }
    val campHighHours = campHighSessions.sumOf { it.sessionCount * it.hoursPerSession }

    val totalCount = allSessions.size
    val totalHours = normalLowHours + normalHighHours + campLowHours + campHighHours

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sessions_summary_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row with stats icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Sessions Stats",
                        tint = NeonMint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SESSION SIZES & HOURS ANALYSIS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = "$totalCount Sessions | ${String.format(Locale.US, "%.2f", totalHours)} Hrs",
                    fontSize = 10.sp,
                    color = NeonMint,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Normal Sessions breakdown card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(DeepCharcoal, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Normal Session",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonMint
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Low size: <= 3 kids
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Low (≤3 kids)", fontSize = 8.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(text = "$normalLowCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(text = "ses", fontSize = 8.sp, color = Color.Gray)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Hours", fontSize = 8.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f", normalLowHours),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.06f)
                        )

                        // High size: > 3 kids
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("High (≥4 kids)", fontSize = 8.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(text = "$normalHighCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(text = "ses", fontSize = 8.sp, color = Color.Gray)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Hours", fontSize = 8.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f", normalHighHours),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Camp Sessions breakdown card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(DeepCharcoal, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Camp Session",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricPurple
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Low size: <= 5 kids
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Low (≤6 kids)", fontSize = 8.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(text = "$campLowCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(text = "ses", fontSize = 8.sp, color = Color.Gray)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Hours", fontSize = 8.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = String.format(Locale.US, "%.2f", campLowHours),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.06f)
                        )

                        // High size: > 5 kids
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("High (≥7 kids)", fontSize = 8.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(text = "$campHighCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(text = "ses", fontSize = 8.sp, color = Color.Gray)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Hours", fontSize = 8.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = String.format(Locale.US, "%.2f", campHighHours),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialsGrid(report: CycleReport?) {
    val payoutNet = report?.payoutNetSalary ?: 0.0
    val payoutOvertime = report?.payoutOvertime ?: 0.0
    val reservePending = report?.reservePendingWalletValue ?: 0.0
    val isReserveActive = (report?.totalMilestones ?: 0) > 22

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Net payout
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Net Payout (LE)",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "%,.2f", payoutNet),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "+%.2f Overtime", payoutOvertime),
                    fontSize = 9.sp,
                    color = NeonMint,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Retained pending wallet
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Pending Wallet",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "%,.2f", reservePending),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isReserveActive) ElectricPurple else Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isReserveActive) "Rolling balance active" else "Isolating after Day 22",
                    fontSize = 9.sp,
                    color = if (isReserveActive) ElectricPurple else Color.DarkGray,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun CalendarMatrixView(report: CycleReport?) {
    val allLogs = (report?.payoutLogs ?: emptyList()) + (report?.reserveLogs ?: emptyList())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SHIFT MATRIX MATRIX INDICATORS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (allLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No shift logs entered yet for this cycle.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Render matrix items (up to 30 markers)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(allLogs) { fin ->
                        val hasDeduction = fin.deduction > 0
                        val markerColor = when {
                            hasDeduction -> SoftCrimson // Red automatically
                            fin.log.isDoubleShift -> ElectricPurple // Purple
                            fin.log.isSixHourShift -> Color(0xFF00B0FF) // Light Blue
                            else -> NeonMint // Green
                        }

                        // Determine index/number based on startMilestoneIndex + 1
                        val milestoneNumber = fin.startMilestoneIndex + 1
                        
                        // Extract short date format (dd/MM) from dd/MM/yyyy
                        val displayDate = if (fin.date.contains("/")) {
                            val parts = fin.date.split("/")
                            if (parts.size >= 2) "${parts[0]}/${parts[1]}" else fin.date
                        } else {
                            fin.date
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.0f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DeepCharcoal)
                                .border(1.dp, markerColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Day $milestoneNumber",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = displayDate,
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                // Indicator Dot
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(markerColor)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Calendar Legends
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LegendItem(color = NeonMint, label = "Normal Shift")
                    LegendItem(color = ElectricPurple, label = "Double Shift")
                    LegendItem(color = SoftCrimson, label = "Deductions")
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RecentLogsList(report: CycleReport?) {
    val reportLogs = (report?.payoutLogs ?: emptyList()) + (report?.reserveLogs ?: emptyList())

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "SHIFT LOGS MATRIX (LAST 3 ENTRIES)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        if (reportLogs.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedSlate)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Once you log shifts, they will appear here.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val last3 = reportLogs.takeLast(3)
                last3.forEach { financials ->
                    RecentLogItem(financials = financials)
                }
            }
        }
    }
}

@Composable
fun RecentLogItem(financials: ShiftFinancials) {
    val log = financials.log
    val borderAccent = when {
        log.isWorkFromHome -> Color(0xFFFF9100) // Neon Orange
        financials.deduction > 0 -> SoftCrimson
        log.isDoubleShift -> ElectricPurple
        log.isSixHourShift -> Color(0xFF00B0FF)
        else -> NeonMint
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(borderAccent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = financials.date,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            log.isWorkFromHome -> "Work From Home • ${String.format(Locale.US, "%.1f", financials.hoursWorked)} Hrs"
                            log.isDoubleShift -> "Double Shift • 12 Hrs"
                            log.isSixHourShift -> "6 Hrs Shift • 6 Hrs"
                            else -> "Normal Shift • 8 Hrs"
                        },
                        fontSize = 9.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "+%,.1f LE", financials.totalDailyValue),
                    fontSize = 12.sp,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (financials.deduction > 0) {
                    Text(
                        text = String.format(Locale.US, "Penalty: -%.1f LE", financials.deduction),
                        fontSize = 8.sp,
                        color = SoftCrimson,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun LogsScreen(
    logs: List<ShiftLog>,
    report: CycleReport?,
    onDeleteLog: (ShiftLog) -> Unit,
    onDeleteAllLogs: () -> Unit
) {
    val reportLogs = (report?.payoutLogs ?: emptyList()) + (report?.reserveLogs ?: emptyList())
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = {
                Text(
                    text = "🚨 Delete All Shifts?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete ALL shift logs for this month/billing cycle? This action cannot be undone.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            containerColor = ElevatedSlate,
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllLogs()
                        showDeleteAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftCrimson)
                ) {
                    Text("Delete All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("Cancel", fontSize = 11.sp, color = Color.Gray)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ALL ACTIVE CYCLES SHIFT RECORDS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            if (reportLogs.isNotEmpty()) {
                TextButton(
                    onClick = { showDeleteAllConfirm = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete All",
                            tint = SoftCrimson,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "DELETE ALL",
                            color = SoftCrimson,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }


        if (reportLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty list",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No shifts logged in this billing cycle.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reportLogs.reversed()) { fin ->
                    DetailedLogCard(financials = fin, onDelete = { onDeleteLog(fin.log) })
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun DetailedLogCard(financials: ShiftFinancials, onDelete: () -> Unit) {
    val log = financials.log
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    log.isWorkFromHome -> Color(0xFFFF9100)
                                    log.isDoubleShift -> ElectricPurple
                                    log.isSixHourShift -> Color(0xFF00B0FF)
                                    else -> NeonMint
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = financials.date,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (financials.isCarriedOver) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Carried Over",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepCharcoal,
                                    modifier = Modifier
                                        .background(Color(0xFFBB86FC), shape = RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = when {
                                log.isWorkFromHome -> "Work From Home (${String.format(Locale.US, "%.1f", financials.hoursWorked)} hrs)"
                                log.isDoubleShift -> "Double Shift (12 hrs)"
                                log.isSixHourShift -> "6 hrs Shift (6 hrs)"
                                else -> "Normal Shift (8 hrs)"
                            },
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.US, "+%,.2f LE", financials.totalDailyValue),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonMint,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand info",
                        tint = Color.LightGray
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Lateness logic
                    if (!log.isWorkFromHome) {
                        DetailRow(label = "Supposed Start", value = log.scheduledStartTime)
                        DetailRow(label = "Actual Arrival", value = log.actualStartTime)
                        DetailRow(label = "Actual Departure", value = log.actualEndTime)
                    }
                    DetailRow(label = "Hours Worked", value = String.format(Locale.US, "%.2f hrs", financials.hoursWorked))
                    DetailRow(label = "Overtime Accrued", value = String.format(Locale.US, "%.2f hrs", financials.overtimeHours))
                    DetailRow(label = "Overtime Earnings", value = String.format(Locale.US, "%.2f LE", financials.overtimePay))
                    if (!log.isWorkFromHome) {
                        DetailRow(label = "Lateness Period", value = "${financials.minutesLate} Mins")
                    }

                    if (financials.deduction > 0) {
                        DetailRow(
                            label = "Lateness Deductions",
                            value = String.format(Locale.US, "-%.2f LE", financials.deduction),
                            valueColor = SoftCrimson
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Session Blocks Matrix",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (financials.sessions.isEmpty()) {
                        Text(
                            text = "No private sessions recorded on this log.",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic
                        )
                    } else {
                        financials.sessions.forEachIndexed { i, session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• Session #${i + 1}: ${session.sessionType}",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${session.kidCount} Kids",
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                            }
                        }
                        DetailRow(
                            label = "Cumulative Session Bonuses",
                            value = String.format(Locale.US, "+%.2f LE", financials.totalSessionBonus)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (financials.isCarriedOver) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), shape = RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔒 Carried Over from previous cycle (read-only)",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    } else {
                        // Safe Delete button
                        Button(
                            onClick = onDelete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SoftCrimson.copy(alpha = 0.9f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DELETE SHIFT RECORD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
        Text(text = value, fontSize = 11.sp, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsScreen(
    viewModel: PayTrackerViewModel,
    currentCycle: BillingCycle,
    cyclesList: List<BillingCycle>
) {
    var activeCycleName by remember { mutableStateOf(currentCycle.name) }
    var baseMonthly by remember { mutableStateOf(currentCycle.baseMonthlyPay.toString()) }
    var mediaBonusValue by remember { mutableStateOf(currentCycle.mediaBonus.toString()) }
    var mediaBonusEnabled by remember { mutableStateOf(currentCycle.isMediaBonusEnabled) }

    var normalLow by remember { mutableStateOf(currentCycle.normalLowBonus.toString()) }
    var normalHigh by remember { mutableStateOf(currentCycle.normalHighBonus.toString()) }
    var campLow by remember { mutableStateOf(currentCycle.campLowBonus.toString()) }
    var campHigh by remember { mutableStateOf(currentCycle.campHighBonus.toString()) }

    var newCycleName by remember { mutableStateOf("") }
    
    // Quick triggers to restore settings values when selected cycle updates
    LaunchedEffect(currentCycle) {
        activeCycleName = currentCycle.name
        baseMonthly = currentCycle.baseMonthlyPay.toString()
        mediaBonusValue = currentCycle.mediaBonus.toString()
        mediaBonusEnabled = currentCycle.isMediaBonusEnabled
        normalLow = currentCycle.normalLowBonus.toString()
        normalHigh = currentCycle.normalHighBonus.toString()
        campLow = currentCycle.campLowBonus.toString()
        campHigh = currentCycle.campHighBonus.toString()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Selection of Active Cycle Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SELECT REGISTERED BILLING CYCLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonMint
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    cyclesList.forEach { c ->
                        val isSel = c.id == currentCycle.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) DeepCharcoal else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSel) NeonMint.copy(alpha = 0.4f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.selectCycle(c.id) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = c.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Base Pay: ${c.baseMonthlyPay} LE • ${if (c.isLocked) "Locked" else "Active"}",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            if (isSel) {
                                Icon(Icons.Default.Check, contentDescription = "Active", tint = NeonMint)
                            }
                        }
                    }
                }
            }
        }

        // Add New Cycle Drawer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NEW RECURRING FISCAL CYCLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newCycleName,
                        onValueChange = { newCycleName = it },
                        label = { Text("Cycle Reference Name", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonMint,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (newCycleName.isNotBlank()) {
                                viewModel.createCycle(
                                    name = newCycleName,
                                    baseMonthlyPay = baseMonthly.toDoubleOrNull() ?: 8000.0,
                                    mediaBonus = mediaBonusValue.toDoubleOrNull() ?: 500.0,
                                    isMediaBonusEnabled = mediaBonusEnabled,
                                    normalLow = normalLow.toDoubleOrNull() ?: 50.0,
                                    normalHigh = normalHigh.toDoubleOrNull() ?: 100.0,
                                    campLow = campLow.toDoubleOrNull() ?: 80.0,
                                    campHigh = campHigh.toDoubleOrNull() ?: 150.0
                                )
                                newCycleName = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = DeepCharcoal)
                    ) {
                        Text("PROVISION NEW CONTRACT CYCLE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Update financial parameters config card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CORE CONTRACT VALUATION SETTINGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonMint,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Payment Cycle Name input
                    OutlinedTextField(
                        value = activeCycleName,
                        onValueChange = { activeCycleName = it },
                        label = { Text("Payment Cycle Name", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().testTag("active_cycle_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonMint,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Base Monthly Pay input
                    OutlinedTextField(
                        value = baseMonthly,
                        onValueChange = { baseMonthly = it },
                        label = { Text("Base Monthly Pay (LE)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonMint,
                            unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Media Flat Bonus input
                    OutlinedTextField(
                        value = mediaBonusValue,
                        onValueChange = { mediaBonusValue = it },
                        label = { Text("Media Flat Bonus (LE)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonMint,
                            unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Media Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Apply Media Flat Bonus?",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "When checked, added to Day 22's Liquid net payout",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = mediaBonusEnabled,
                            onCheckedChange = { mediaBonusEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonMint,
                                checkedTrackColor = NeonMint.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "TIERED SESSION MATRIX REWARDS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = normalLow,
                            onValueChange = { normalLow = it },
                            label = { Text("Normal Low (<=3 kids)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonMint
                            )
                        )
                        OutlinedTextField(
                            value = normalHigh,
                            onValueChange = { normalHigh = it },
                            label = { Text("Normal High (>=4 kids)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonMint
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = campLow,
                            onValueChange = { campLow = it },
                            label = { Text("Camp Low (<=6 kids)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonMint
                            )
                        )
                        OutlinedTextField(
                            value = campHigh,
                            onValueChange = { campHigh = it },
                            label = { Text("Camp High (>=7 kids)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonMint
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val baseNum = baseMonthly.toDoubleOrNull() ?: 8000.0
                            val mediaNum = mediaBonusValue.toDoubleOrNull() ?: 500.0
                            val normL = normalLow.toDoubleOrNull() ?: 50.0
                            val normH = normalHigh.toDoubleOrNull() ?: 100.0
                            val campL = campLow.toDoubleOrNull() ?: 80.0
                            val campH = campHigh.toDoubleOrNull() ?: 150.0

                            viewModel.updateActiveCycleSettings(
                                name = if (activeCycleName.isNotBlank()) activeCycleName else currentCycle.name,
                                basePay = baseNum,
                                mediaBonus = mediaNum,
                                isMediaBonusEnabled = mediaBonusEnabled,
                                normalLow = normL,
                                normalHigh = normH,
                                campLow = campL,
                                campHigh = campH
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_settings_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = DeepCharcoal)
                    ) {
                        Text("SAVE RE-CALCULATED RULES", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Dialog for adding single shift entry
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShiftLogDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        date: String,
        scheduledStart: String,
        actualStart: String,
        actualEnd: String,
        isDouble: Boolean,
        isSixHour: Boolean,
        sessions: List<SessionModel>,
        isWorkFromHome: Boolean
    ) -> Unit
) {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val defaultDate = sdf.format(Date())

    var date by remember { mutableStateOf(defaultDate) }
    var scheduledStartTime by remember { mutableStateOf("09:00") }
    var actualStartTime by remember { mutableStateOf("09:00") }
    var actualEndTime by remember { mutableStateOf("17:00") }
    var isDoubleShift by remember { mutableStateOf(false) }
    var isSixHourShift by remember { mutableStateOf(false) }
    var isWorkFromHome by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var activeTimeField by remember { mutableStateOf<String?>(null) } // "scheduled", "actualStart", "actualEnd"

    // Dynamic session builder
    val sessionsList = remember { mutableStateListOf<SessionModel>() }
    var selectedSessionType by remember { mutableStateOf("Normal") }
    var kidCountText by remember { mutableStateOf("3") }
    var sessionCountText by remember { mutableStateOf("1") }
    var hoursPerSessionText by remember { mutableStateOf("2.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Ingest Daily Shift Log",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        containerColor = ElevatedSlate,
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info block
                item {
                    Text(
                        text = if (isWorkFromHome) "Log work from home sessions. Hours are calculated as 2.0 hrs/session." else "Enter raw timestamps for the algorithmic financial matrix.",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontStyle = FontStyle.Italic
                    )
                }

                // Type segment selector
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DeepCharcoal)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { isWorkFromHome = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isWorkFromHome) ElectricPurple else Color.Transparent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Onsite Shift", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { isWorkFromHome = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWorkFromHome) ElectricPurple else Color.Transparent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1.3f).height(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Work From Home", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Date Picker trigger field
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                    ) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = {},
                            label = { Text("Date (DD/MM/YYYY)", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = Color.White,
                                disabledBorderColor = NeonMint,
                                disabledLabelColor = Color.LightGray
                            )
                        )
                    }
                }

                if (!isWorkFromHome) {
                    // Time slots trigger fields row
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeTimeField = "scheduled" }
                            ) {
                                OutlinedTextField(
                                    value = scheduledStartTime,
                                    onValueChange = {},
                                    label = { Text("Scheduled Start", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    readOnly = true,
                                    enabled = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = Color.White,
                                        disabledBorderColor = NeonMint,
                                        disabledLabelColor = Color.LightGray
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeTimeField = "actualStart" }
                            ) {
                                OutlinedTextField(
                                    value = actualStartTime,
                                    onValueChange = {},
                                    label = { Text("Actual Arrival", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    readOnly = true,
                                    enabled = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = Color.White,
                                        disabledBorderColor = NeonMint,
                                        disabledLabelColor = Color.LightGray
                                    )
                                )
                            }
                        }
                    }

                    // Finish Time slot trigger field
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeTimeField = "actualEnd" }
                        ) {
                            OutlinedTextField(
                                value = actualEndTime,
                                onValueChange = {},
                                label = { Text("Actual Finish Time", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color.White,
                                    disabledBorderColor = NeonMint,
                                    disabledLabelColor = Color.LightGray
                                )
                            )
                        }
                    }

                    // Double shift options
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Double Shift?",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Standard shift changes to 12 Hrs. Target increments by +2 days.",
                                    fontSize = 9.sp,
                                    color = TextSecondary
                                )
                            }
                            Checkbox(
                                checked = isDoubleShift,
                                onCheckedChange = { isChecked ->
                                    isDoubleShift = isChecked
                                    if (isChecked) {
                                        isSixHourShift = false
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = ElectricPurple)
                            )
                        }
                    }

                    // 6 hours shift options
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "6 Hours Shift?",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Standard shift changes to 6 Hrs. Overtime is calculated for hours exceeding 6 hrs.",
                                    fontSize = 9.sp,
                                    color = TextSecondary
                                )
                            }
                            Checkbox(
                                checked = isSixHourShift,
                                onCheckedChange = { isChecked ->
                                    isSixHourShift = isChecked
                                    if (isChecked) {
                                        isDoubleShift = false
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00B0FF))
                            )
                        }
                    }
                }

                // Dynamic Session matrices builder
                item {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isWorkFromHome) "Work From Home Session Blocks" else "Session Blocks Builder Matrix",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonMint
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DeepCharcoal)
                            .padding(10.dp)
                    ) {
                        // Header label
                        Text(
                            text = "SESSION TYPE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Type selection: Normal vs Camp buttons side-by-side filling the width
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { selectedSessionType = "Normal" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedSessionType == "Normal") NeonMint else ElevatedSlate,
                                    contentColor = if (selectedSessionType == "Normal") DeepCharcoal else Color.White
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Normal Session", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { selectedSessionType = "Camp" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedSessionType == "Camp") NeonMint else ElevatedSlate,
                                    contentColor = if (selectedSessionType == "Camp") DeepCharcoal else Color.White
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Camp Session", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedSessionType == "Camp") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = sessionCountText,
                                    onValueChange = { sessionCountText = it },
                                    label = { Text("Num of Sessions", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = NeonMint,
                                        unfocusedBorderColor = Color.Gray
                                    )
                                )
                                OutlinedTextField(
                                    value = hoursPerSessionText,
                                    onValueChange = { hoursPerSessionText = it },
                                    label = { Text("Hours per Session", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = NeonMint,
                                        unfocusedBorderColor = Color.Gray
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Kids input & Add Button row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Kids Input with full label support and proper height
                            OutlinedTextField(
                                value = kidCountText,
                                onValueChange = { kidCountText = it },
                                label = { Text("Kids Count", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = NeonMint,
                                    unfocusedBorderColor = Color.Gray
                                )
                            )

                            // Add action button
                            Button(
                                onClick = {
                                    val count = kidCountText.toIntOrNull() ?: 1
                                    val sCount = if (selectedSessionType == "Camp") (sessionCountText.toIntOrNull() ?: 1) else 1
                                    val hrs = if (selectedSessionType == "Camp") (hoursPerSessionText.toDoubleOrNull() ?: 0.0) else 0.0
                                    sessionsList.add(
                                        SessionModel(
                                            sessionType = selectedSessionType,
                                            kidCount = count,
                                            sessionCount = sCount,
                                            hoursPerSession = hrs
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonMint,
                                    contentColor = DeepCharcoal
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("add_session_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add session", modifier = Modifier.size(16.dp))
                                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Added sessions elements scroll list
                        if (sessionsList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            sessionsList.forEachIndexed { idx, s ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val desc = if (s.sessionType.equals("Camp", ignoreCase = true)) {
                                        "Camp: ${s.sessionCount} sessions × ${s.hoursPerSession} hrs (${s.kidCount} Kids)"
                                    } else {
                                        "Normal Session (${s.kidCount} Kids)"
                                    }
                                    Text(
                                        text = "${idx+1}. $desc",
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { sessionsList.removeAt(idx) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = SoftCrimson, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        date,
                        scheduledStartTime,
                        actualStartTime,
                        actualEndTime,
                        isDoubleShift,
                        isSixHourShift,
                        sessionsList.toList(),
                        isWorkFromHome
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonMint, contentColor = DeepCharcoal),
                modifier = Modifier.testTag("submit_button")
            ) {
                Text("SUBMIT LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            val format = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            date = format.format(Date(ms))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = NeonMint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (activeTimeField != null) {
        val currentVal = when (activeTimeField) {
            "scheduled" -> scheduledStartTime
            "actualStart" -> actualStartTime
            else -> actualEndTime
        }
        val parts = currentVal.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { activeTimeField = null },
            title = {
                Text(
                    text = when (activeTimeField) {
                        "scheduled" -> "Select Scheduled Start"
                        "actualStart" -> "Select Actual Arrival"
                        else -> "Select Actual Finish"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            containerColor = ElevatedSlate,
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formattedTime = String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                        when (activeTimeField) {
                            "scheduled" -> scheduledStartTime = formattedTime
                            "actualStart" -> actualStartTime = formattedTime
                            "actualEnd" -> actualEndTime = formattedTime
                        }
                        activeTimeField = null
                    }
                ) {
                    Text("OK", color = NeonMint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTimeField = null }) {
                    Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
