package com.example.viewmodel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.engine.CycleReport
import com.example.data.engine.FinanceCalculations
import com.example.data.engine.ShiftFinancials
import com.example.data.model.BillingCycle
import com.example.data.model.SessionModel
import com.example.data.model.SessionJson
import com.example.data.model.ShiftLog
import com.example.data.repository.PayTrackerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PayTrackerViewModel(private val repository: PayTrackerRepository) : ViewModel() {

    // Cycles
    val cycles: StateFlow<List<BillingCycle>> = repository.allCycles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active/Selected Cycle
    private val _selectedCycleId = MutableStateFlow<Long?>(null)
    val selectedCycleId: StateFlow<Long?> = _selectedCycleId.asStateFlow()

    val currentCycle: StateFlow<BillingCycle?> = _selectedCycleId
        .flatMapLatest { id ->
            if (id == null) {
                // flow of last element
                repository.allCycles.map { list -> list.firstOrNull() }
            } else {
                repository.allCycles.map { list -> list.find { it.id == id } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current shift logs flow
    val currentLogs: StateFlow<List<ShiftLog>> = currentCycle
        .flatMapLatest { cycle ->
            if (cycle == null) {
                flowOf(emptyList())
            } else {
                repository.getLogsForCycleFlow(cycle.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Logs Flow across all cycles
    val allLogs: StateFlow<List<ShiftLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Direct Chronological Calculation Map of all Cycle Reports
    val reportsMap: StateFlow<Map<Long, CycleReport>> = combine(cycles, allLogs) { allCycles, logsList ->
        val sortedCycles = allCycles.sortedBy { it.id } // Oldest to newest
        val logsByCycle = logsList.groupBy { it.cycleId }
        val resultMap = mutableMapOf<Long, CycleReport>()
        
        var previousReserveLogs = emptyList<ShiftLog>()
        for (cycle in sortedCycles) {
            val cycleLogs = logsByCycle[cycle.id] ?: emptyList()
            val report = FinanceCalculations.generateCycleReport(cycle, cycleLogs, previousReserveLogs)
            resultMap[cycle.id] = report
            previousReserveLogs = report.reserveLogs.map { it.log }
        }
        resultMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Previous Cycle and Report Flows for Retained Wallet rollover
    val previousCycle: StateFlow<BillingCycle?> = combine(cycles, currentCycle) { allCycles, active ->
        if (active == null || allCycles.isEmpty()) null
        else {
            val index = allCycles.indexOfFirst { it.id == active.id }
            if (index != -1 && index + 1 < allCycles.size) {
                allCycles[index + 1]
            } else {
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val previousReport: StateFlow<CycleReport?> = combine(previousCycle, reportsMap) { pCycle, map ->
        if (pCycle == null) null else map[pCycle.id]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Live Report Calculation
    val currentReport: StateFlow<CycleReport?> = combine(currentCycle, reportsMap) { cycle, map ->
        if (cycle == null) null else map[cycle.id]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Notification State
    private val _alertMessage = MutableSharedFlow<String>(replay = 0)
    val alertMessage: SharedFlow<String> = _alertMessage.asSharedFlow()

    init {
        // Initialize logic: Create a default cycle if none exists
        viewModelScope.launch {
            val list = repository.allCycles.first()
            if (list.isEmpty()) {
                val defaultCycle = BillingCycle(
                    name = "June 2026 Shift Cycle",
                    baseMonthlyPay = 8000.0,
                    mediaBonus = 500.0,
                    isMediaBonusEnabled = true,
                    normalLowBonus = 50.0,
                    normalHighBonus = 100.0,
                    campLowBonus = 80.0,
                    campHighBonus = 150.0
                )
                val id = repository.insertCycle(defaultCycle)
                _selectedCycleId.value = id
            } else {
                _selectedCycleId.value = list.first().id
            }
        }
    }

    fun selectCycle(id: Long) {
        _selectedCycleId.value = id
    }

    fun createCycle(
        name: String,
        baseMonthlyPay: Double,
        mediaBonus: Double,
        isMediaBonusEnabled: Boolean,
        normalLow: Double,
        normalHigh: Double,
        campLow: Double,
        campHigh: Double
    ) {
        viewModelScope.launch {
            val cycle = BillingCycle(
                name = name,
                baseMonthlyPay = baseMonthlyPay,
                mediaBonus = mediaBonus,
                isMediaBonusEnabled = isMediaBonusEnabled,
                normalLowBonus = normalLow,
                normalHighBonus = normalHigh,
                campLowBonus = campLow,
                campHighBonus = campHigh
            )
            val newId = repository.insertCycle(cycle)
            _selectedCycleId.value = newId
        }
    }

    fun updateActiveCycleSettings(
        name: String,
        basePay: Double,
        mediaBonus: Double,
        isMediaBonusEnabled: Boolean,
        normalLow: Double,
        normalHigh: Double,
        campLow: Double,
        campHigh: Double
    ) {
        val cycle = currentCycle.value ?: return
        viewModelScope.launch {
            repository.updateCycle(
                cycle.copy(
                    name = name,
                    baseMonthlyPay = basePay,
                    mediaBonus = mediaBonus,
                    isMediaBonusEnabled = isMediaBonusEnabled,
                    normalLowBonus = normalLow,
                    normalHighBonus = normalHigh,
                    campLowBonus = campLow,
                    campHighBonus = campHigh
                )
            )
        }
    }

    fun toggleMediaBonus() {
        val cycle = currentCycle.value ?: return
        viewModelScope.launch {
            repository.updateCycle(
                cycle.copy(isMediaBonusEnabled = !cycle.isMediaBonusEnabled)
            )
        }
    }

    fun addShiftLog(
        date: String,
        scheduledStartTime: String,
        actualStartTime: String,
        actualEndTime: String,
        isDoubleShift: Boolean,
        isSixHourShift: Boolean = false,
        sessions: List<SessionModel>,
        isWorkFromHome: Boolean = false
    ): Boolean {
        val cycle = currentCycle.value ?: return false
        val report = currentReport.value ?: return false

        // Rule: Block additions if cycle is complete (locked)
        if (report.isCycleComplete || cycle.isLocked) {
            viewModelScope.launch {
                _alertMessage.emit("⚠️ Cycle is Locked! Please export the HR Salary Report or unlock the cycle first.")
            }
            return false
        }

        val weight = if (isWorkFromHome) {
            val existingWfhHours = (report.payoutLogs + report.reserveLogs).map { it.log }
                .filter { it.isWorkFromHome }
                .sumOf { log -> SessionJson.fromJson(log.sessionMatrixJson).sumOf { it.sessionCount } * 2.0 }
            val newHours = sessions.sumOf { it.sessionCount } * 2.0
            val oldMilestones = existingWfhHours.toInt() / 8
            val newMilestones = (existingWfhHours + newHours).toInt() / 8
            newMilestones - oldMilestones
        } else {
            if (isDoubleShift) 2 else 1
        }
        val wouldCapExceeded = report.totalMilestones + weight > 30

        if (wouldCapExceeded) {
            viewModelScope.launch {
                _alertMessage.emit("⚠️ Exceeds the 30-day cycle limit! This shift (+${weight} days) would exceed the maximum target.")
            }
            return false
        }

        viewModelScope.launch {
            val log = ShiftLog(
                cycleId = cycle.id,
                date = date,
                scheduledStartTime = if (isWorkFromHome) "" else scheduledStartTime,
                actualStartTime = if (isWorkFromHome) "" else actualStartTime,
                actualEndTime = if (isWorkFromHome) "" else actualEndTime,
                isDoubleShift = if (isWorkFromHome) false else isDoubleShift,
                isSixHourShift = if (isWorkFromHome) false else isSixHourShift,
                sessionMatrixJson = SessionJson.toJson(sessions),
                isWorkFromHome = isWorkFromHome
            )
            repository.insertLog(log)

            // Trigger complete check
            val logsPlusOne = repository.getLogsForCycle(cycle.id)
            val pReserveLogs = previousReport.value?.reserveLogs?.map { it.log } ?: emptyList()
            val newReport = FinanceCalculations.generateCycleReport(cycle, logsPlusOne, pReserveLogs)
            if (newReport.totalMilestones >= 30) {
                // Auto lock
                repository.updateCycle(cycle.copy(isLocked = true))
                _alertMessage.emit("🔔 30-Day Cycle Complete! Ready to export HR Salary Report.")
            }
        }
        return true
    }

    fun deleteShiftLog(log: ShiftLog) {
        viewModelScope.launch {
            repository.deleteLog(log)
            
            // Check if deleted shift unlocked the cycle
            val cycle = currentCycle.value ?: return@launch
            val logsAfter = repository.getLogsForCycle(cycle.id)
            val pReserveLogs = previousReport.value?.reserveLogs?.map { it.log } ?: emptyList()
            val newReport = FinanceCalculations.generateCycleReport(cycle, logsAfter, pReserveLogs)
            if (newReport.totalMilestones < 30) {
                repository.updateCycle(cycle.copy(isLocked = false))
            }
        }
    }

    fun deleteAllShiftLogsForCurrentCycle() {
        val cycle = currentCycle.value ?: return
        viewModelScope.launch {
            repository.deleteAllLogsForCycle(cycle.id)
            repository.updateCycle(cycle.copy(isLocked = false))
            _alertMessage.emit("🗑️ All shifts for ${cycle.name} have been deleted.")
        }
    }


    fun unlockCycle() {
        val cycle = currentCycle.value ?: return
        viewModelScope.launch {
            repository.updateCycle(cycle.copy(isLocked = false))
            _alertMessage.emit("🔓 Cycle unlocked. You can now modify logs.")
        }
    }

    fun exportHrPdf(context: Context): File? {
        val report = currentReport.value ?: return null
        val cycle = currentCycle.value ?: return null
        
        try {
            // Naming convention: Month_Year_Salary_Report.pdf
            // We can resolve safe folder and file name
            val safeCycleName = cycle.name.replace(" ", "_").replace("/", "_")
            val fileName = "${safeCycleName}_Salary_Report.pdf"
            val pdfFile = File(context.cacheDir, fileName)
            
            val document = PdfDocument()
            
            // We will create custom paints
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            
            val headerPaint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            
            val titlePaint = Paint().apply {
                color = Color.argb(255, 3, 218, 198) // #03DAC6
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            val subtitlePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }

            val tableHeaderPaint = Paint().apply {
                color = Color.BLACK
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            val crimsonPaint = Paint().apply {
                color = Color.argb(255, 207, 102, 121) // #CF6679 crimson color for deductions
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            // Standard page dimensions (A4: 595 x 842 points)
            val pageInfo = PdfDocument.PageInfo.Builder(1000, 1400, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // Make sure the PDF background is clean white for printing
            canvas.drawColor(Color.WHITE)

            var y = 50f
            
            // Draw Main Header
            canvas.drawText("FINTECH ARCHITECT PAY SYSTEM", 50f, y, headerPaint)
            y += 20f
            canvas.drawText("HR Salary & Operational Performance Report", 50f, y, titlePaint)
            y += 18f
            canvas.drawText("Cycle: ${cycle.name} | Created on: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", 50f, y, subtitlePaint)
            y += 30f

            // Settings/Parameters Card inside PDF
            canvas.drawRect(50f, y, 950f, y + 45f, Paint().apply {
                color = Color.argb(25, 3, 218, 198)
                style = Paint.Style.FILL
            })
            canvas.drawRect(50f, y, 950f, y + 45f, Paint().apply {
                color = Color.argb(100, 3, 218, 198)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            
            val valPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val keyPaint = Paint().apply {
                color = Color.GRAY
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            
            canvas.drawText("BASE MONTHLY PAY", 65f, y + 18f, keyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f LE", cycle.baseMonthlyPay), 65f, y + 34f, valPaint)

            canvas.drawText("DAILY BASE RATE", 220f, y + 18f, keyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f LE", report.dailyBaseRate), 220f, y + 34f, valPaint)

            canvas.drawText("HOURLY BASE RATE", 380f, y + 18f, keyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f LE", report.hourlyBaseRate), 380f, y + 34f, valPaint)

            canvas.drawText("MEDIA BONUS", 540f, y + 18f, keyPaint)
            canvas.drawText(if (cycle.isMediaBonusEnabled) String.format(Locale.US, "%.2f LE (Enabled)", cycle.mediaBonus) else "0.00 LE (Disabled)", 540f, y + 34f, valPaint)

            canvas.drawText("MILESTONES", 740f, y + 18f, keyPaint)
            canvas.drawText("${report.totalMilestones} / 30 Days", 740f, y + 34f, valPaint)

            y += 70f

            // Start Tabular Grid
            canvas.drawText("ALL CYCLE LOGS - DETAILED TABULAR MATRIX (22 DAYS PAYOUT & RESERVES)", 50f, y, headerPaint)
            y += 15f

            // Grid Headers
            val colWidths = floatArrayOf(
                70f,   // Date
                70f,   // Supposed Start
                70f,   // Actual Start
                70f,   // Finish Time
                70f,   // Double Shift?
                70f,   // Overtime (Hrs)
                80f,   // Session Count
                120f,  // Kids/Session
                100f,  // Session Bounce
                110f,  // Total Daily Bounce
                100f   // Deduction
            )

            val startX = 50f
            val headers = arrayOf(
                "Date",
                "Supposed",
                "Actual Start",
                "Finish Time",
                "Double?",
                "Overtime",
                "Sessions",
                "Kids List",
                "Sess Bounce",
                "Daily Bounce",
                "Deduction"
            )

            // Draw header background
            canvas.drawRect(startX, y - 12f, 950f, y + 8f, Paint().apply {
                color = Color.argb(45, 120, 120, 120)
                style = Paint.Style.FILL
            })

            var currX = startX
            for (i in headers.indices) {
                canvas.drawText(headers[i], currX + 4f, y, tableHeaderPaint)
                currX += colWidths[i]
            }
            y += 14f

            // Combine all logs (payout + reserve) to present them clearly
            val allFinancials = report.payoutLogs + report.reserveLogs

            for (fin in allFinancials) {
                // Draw a light separator line
                canvas.drawLine(startX, y - 10f, 950f, y - 10f, Paint().apply {
                    color = Color.argb(40, 150, 150, 150)
                    strokeWidth = 1f
                })

                currX = startX
                
                // 1. Date (DD/MM/YYYY)
                canvas.drawText(fin.date, currX + 4f, y, textPaint)
                currX += colWidths[0]

                // 2. Supposed Start (HH:MM)
                canvas.drawText(fin.log.scheduledStartTime, currX + 4f, y, textPaint)
                currX += colWidths[1]

                // 3. Actual Start (HH:MM)
                canvas.drawText(fin.log.actualStartTime, currX + 4f, y, textPaint)
                currX += colWidths[2]

                // 4. Finish Time (HH:MM)
                canvas.drawText(fin.log.actualEndTime, currX + 4f, y, textPaint)
                currX += colWidths[3]

                // 5. Shift Type / Double Shift?
                val shiftTypeStr = when {
                    fin.log.isDoubleShift -> "Double (+2d)"
                    fin.log.isSixHourShift -> "6 Hours (+1d)"
                    else -> "Normal (+1d)"
                }
                canvas.drawText(shiftTypeStr, currX + 4f, y, textPaint)
                currX += colWidths[4]

                // 6. Overtime (Hrs)
                canvas.drawText(String.format(Locale.US, "%.1f hrs", fin.overtimeHours), currX + 4f, y, textPaint)
                currX += colWidths[5]

                // 7. Session Count
                canvas.drawText("${fin.sessions.size}", currX + 4f, y, textPaint)
                currX += colWidths[6]

                // 8. Kids/Session
                val kidsStr = fin.sessions.joinToString(", ") { it.kidCount.toString() }
                val truncatedKids = if (kidsStr.length > 20) kidsStr.substring(0, 17) + "..." else kidsStr
                canvas.drawText(if (truncatedKids.isEmpty()) "-" else truncatedKids, currX + 4f, y, textPaint)
                currX += colWidths[7]

                // 9. Session Bounce
                canvas.drawText(String.format(Locale.US, "%.1f LE", fin.totalSessionBonus), currX + 4f, y, textPaint)
                currX += colWidths[8]

                // 10. Total Daily Bounce (Session Bounce + Overtime Pay + Daily Base Rate)
                canvas.drawText(String.format(Locale.US, "%.1f LE", fin.totalDailyValue), currX + 4f, y, textPaint)
                currX += colWidths[9]

                // 11. Deduction
                if (fin.deduction > 0) {
                    canvas.drawText(String.format(Locale.US, "-%.1f LE", fin.deduction), currX + 4f, y, crimsonPaint)
                } else {
                    canvas.drawText("0.0 LE", currX + 4f, y, textPaint)
                }
                currX += colWidths[10]

                y += 18f

                // Prevent PDF overflow
                if (y > 1150f) {
                    // Start a new path (For simplicity, we just keep going inside a large landscape style canvas we created 1000x1400)
                }
            }

            // Draw final table bottom line
            canvas.drawLine(startX, y - 5f, 950f, y - 5f, Paint().apply {
                color = Color.BLACK
                strokeWidth = 1.5f
            })
            y += 20f

            // Add information regarding 22/8 split
            canvas.drawText("THE 22/8 FISCAL SPLIT VERDICT", 50f, y, headerPaint)
            y += 15f

            // Draw 22-Day & 8-Day Block Side-By-Side Information Panels
            val panelHeight = 85f
            
            // 22-Day Liquid Payout Panel
            canvas.drawRect(50f, y, 485f, y + panelHeight, Paint().apply {
                color = Color.argb(12, 3, 218, 198)
                style = Paint.Style.FILL
            })
            canvas.drawRect(50f, y, 485f, y + panelHeight, Paint().apply {
                color = Color.argb(50, 3, 218, 198)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            canvas.drawText("LIQUID MONTHLY PAYOUT BLOCK (DAYS 1 TO 22)", 65f, y + 18f, keyPaint)
            canvas.drawText(String.format(Locale.US, "Base Pay (22 Days Fixed): %.2f LE", report.payoutBasePay), 65f, y + 36f, textPaint)
            canvas.drawText(String.format(Locale.US, "Overtime Accrued: %.2f LE", report.payoutOvertime), 65f, y + 50f, textPaint)
            canvas.drawText(String.format(Locale.US, "Session Matrix Bonuses: %.2f LE", report.payoutSessionBonus), 65f, y + 64f, textPaint)
            canvas.drawText(String.format(Locale.US, "Deductions Penalty (Crimson): -%.2f LE", report.payoutDeductions), 65f, y + 78f, crimsonPaint)

            // 8-Day Reserve Panel
            canvas.drawRect(515f, y, 950f, y + panelHeight, Paint().apply {
                color = Color.argb(12, 120, 120, 120)
                style = Paint.Style.FILL
            })
            canvas.drawRect(515f, y, 950f, y + panelHeight, Paint().apply {
                color = Color.argb(50, 120, 120, 120)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            canvas.drawText("PENDING RETAINED RESERVE WALLET (DAYS 23 TO 30)", 530f, y + 18f, Paint().apply {
                color = Color.DKGRAY
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            })
            canvas.drawText(String.format(Locale.US, "Base Pay Retained (8 Days): %.2f LE", report.reserveBasePay), 530f, y + 36f, textPaint)
            canvas.drawText(String.format(Locale.US, "Overtime Retained: %.2f LE", report.reserveOvertime), 530f, y + 50f, textPaint)
            canvas.drawText(String.format(Locale.US, "Session Bonuses Retained: %.2f LE", report.reserveSessionBonus), 530f, y + 64f, textPaint)
            canvas.drawText(String.format(Locale.US, "Retained Deductions: -%.2f LE", report.reserveDeductions), 530f, y + 78f, Paint().apply {
                color = Color.GRAY
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            })

            y += panelHeight + 35f

            // Session Analyzer Section inside PDF
            canvas.drawText("SESSION SIZES & HOURS ANALYSIS", 50f, y, headerPaint)
            
            val allSessions = allFinancials.flatMap { it.sessions }
            val normalSessions = allSessions.filter { it.sessionType.equals("Normal", ignoreCase = true) }
            val campSessions = allSessions.filter { it.sessionType.equals("Camp", ignoreCase = true) }

            val normalLowSessions = normalSessions.filter { it.kidCount <= 3 }
            val normalHighSessions = normalSessions.filter { it.kidCount > 3 }
            val campLowSessions = campSessions.filter { it.kidCount <= 5 }
            val campHighSessions = campSessions.filter { it.kidCount > 5 }

            val normalLowCount = normalLowSessions.size
            val normalHighCount = normalHighSessions.size
            val campLowCount = campLowSessions.size
            val campHighCount = campHighSessions.size

            val normalLowHours = normalLowCount * 2.0
            val normalHighHours = normalHighCount * 2.0
            val campLowHours = campLowCount * 0.75
            val campHighHours = campHighCount * 0.75

            val totalSessionCount = allSessions.size
            val totalSessionHours = normalLowHours + normalHighHours + campLowHours + campHighHours

            canvas.drawText("Total: $totalSessionCount Sessions  |  ${String.format(Locale.US, "%.2f", totalSessionHours)} Cumulative Hrs", 650f, y, Paint().apply {
                color = Color.GRAY
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            })

            y += 15f

            val analysisPanelHeight = 90f

            // Normal Sessions Panel
            canvas.drawRect(50f, y, 485f, y + analysisPanelHeight, Paint().apply {
                color = Color.argb(12, 3, 218, 198) // Light neon mint overlay
                style = Paint.Style.FILL
            })
            canvas.drawRect(50f, y, 485f, y + analysisPanelHeight, Paint().apply {
                color = Color.argb(50, 3, 218, 198)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            
            canvas.drawText("NORMAL SESSIONS (RATE: 2.0 HRS / SESSION)", 65f, y + 18f, Paint().apply {
                color = Color.argb(255, 0, 150, 136)
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            })
            canvas.drawText("Low Size (<= 3 kids):  $normalLowCount Sessions  |  ${String.format(Locale.US, "%.1f", normalLowHours)} Hours", 65f, y + 42f, textPaint)
            canvas.drawText("High Size (>= 4 kids):  $normalHighCount Sessions  |  ${String.format(Locale.US, "%.1f", normalHighHours)} Hours", 65f, y + 66f, textPaint)

            // Camp Sessions Panel
            canvas.drawRect(515f, y, 950f, y + analysisPanelHeight, Paint().apply {
                color = Color.argb(12, 187, 134, 252) // Light ElectricPurple overlay
                style = Paint.Style.FILL
            })
            canvas.drawRect(515f, y, 950f, y + analysisPanelHeight, Paint().apply {
                color = Color.argb(50, 187, 134, 252)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })

            canvas.drawText("CAMP SESSIONS (RATE: 0.75 HRS / SESSION)", 530f, y + 18f, Paint().apply {
                color = Color.argb(255, 103, 58, 183)
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            })
            canvas.drawText("Low Size (<= 5 kids):  $campLowCount Sessions  |  ${String.format(Locale.US, "%.2f", campLowHours)} Hours", 530f, y + 42f, textPaint)
            canvas.drawText("High Size (>= 6 kids):  $campHighCount Sessions  |  ${String.format(Locale.US, "%.2f", campHighHours)} Hours", 530f, y + 66f, textPaint)

            y += analysisPanelHeight + 35f

            // Specialized Summary Footer Card
            val cardY = y
            canvas.drawRect(50f, cardY, 950f, cardY + 110f, Paint().apply {
                color = Color.argb(255, 18, 18, 18) // High contrast dark charcoal from theme guidelines
                style = Paint.Style.FILL
            })
            
            val goldPaint = Paint().apply {
                color = Color.argb(255, 3, 218, 198) // Neon mint `#03DAC6` for highlighting net salary
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val darkCardTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val darkCardHeaderPaint = Paint().apply {
                color = Color.argb(255, 3, 218, 198)
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            canvas.drawText("OFFICIAL HR SALARY DISTRIBUTION SUMS (APPROVED FOR DISBURSEMENT)", 70f, cardY + 25f, darkCardHeaderPaint)
            canvas.drawText("Total Active Days Logged: ${allFinancials.size} Days (22 Days Payout, ${allFinancials.size - report.payoutLogs.size} Days Reserve)", 70f, cardY + 45f, darkCardTextPaint)
            canvas.drawText(String.format(Locale.US, "Media Flat Bonus: %.2f LE", report.payoutMediaBonus), 70f, cardY + 65f, darkCardTextPaint)
            canvas.drawText(String.format(Locale.US, "Total Deductions Accrued: %.2f LE", report.payoutDeductions), 70f, cardY + 85f, darkCardTextPaint)

            // Redraw border around net payout
            canvas.drawRect(520f, cardY + 35f, 930f, cardY + 95f, Paint().apply {
                color = Color.argb(255, 3, 218, 198)
                strokeWidth = 2f
                style = Paint.Style.STROKE
            })

            canvas.drawText("NET DISBURSED SALARY:", 540f, cardY + 56f, Paint().apply {
                color = Color.WHITE
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            })
            canvas.drawText(String.format(Locale.US, "%.2f LE", report.payoutNetSalary), 540f, cardY + 83f, goldPaint)

            // Reserve/Wallet Footer inside bottom right of dark card
            val pendingWalletPaint = Paint().apply {
                color = Color.argb(180, 255, 255, 255)
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText(String.format(Locale.US, "RETAINED WALLET: %.2f LE", report.reservePendingWalletValue), 740f, cardY + 56f, pendingWalletPaint)
            canvas.drawText("(Transferred to Base Wallet)", 740f, cardY + 74f, Paint().apply {
                color = Color.argb(120, 255, 255, 255)
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            })

            document.finishPage(page)
            
            val out = FileOutputStream(pdfFile)
            document.writeTo(out)
            out.flush()
            out.close()
            document.close()
            
            // Mark as exported in cycle
            viewModelScope.launch {
                repository.updateCycle(cycle.copy(isExported = true))
            }
            
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

class PayTrackerViewModelFactory(private val repository: PayTrackerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PayTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PayTrackerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
