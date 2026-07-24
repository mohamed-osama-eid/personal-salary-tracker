package com.example.data.engine

import com.example.data.model.BillingCycle
import com.example.data.model.SessionModel
import com.example.data.model.SessionJson
import com.example.data.model.ShiftLog
import kotlin.math.max

data class ShiftFinancials(
    val log: ShiftLog,
    val date: String,
    val milestoneWeight: Int,
    val hoursWorked: Double,
    val standardHours: Double,
    val overtimeHours: Double,
    val minutesLate: Int,
    
    val baseRateDaily: Double,
    val baseRateHourly: Double,
    
    val basePayCalculated: Double, // rate * milestone weight
    val overtimePay: Double,
    val deduction: Double,
    val sessions: List<SessionModel>,
    val totalSessionBonus: Double,
    val totalDailyValue: Double, // base + bonus + overtime - deduction
    
    val startMilestoneIndex: Int,
    val endMilestoneIndex: Int,
    val isReserveBlock: Boolean,
    val isCarriedOver: Boolean = false
)

data class CycleReport(
    val cycle: BillingCycle,
    val totalMilestones: Int,
    val isCycleComplete: Boolean,
    
    // Core constants
    val dailyBaseRate: Double,
    val hourlyBaseRate: Double,
    
    // Logs split
    val payoutLogs: List<ShiftFinancials>,
    val reserveLogs: List<ShiftFinancials>,
    
    // Financial sums for Payout block (Days 1 to 22)
    val payoutBasePay: Double, // fixed dailyBaseRate * 22
    val payoutOvertime: Double,
    val payoutSessionBonus: Double,
    val payoutDeductions: Double,
    val payoutMediaBonus: Double,
    val payoutNetSalary: Double,
    
    // Financial sums for Reserve block (Days 23 to 30) - Pending Wallet
    val reserveBasePay: Double, // fixed dailyBaseRate * 8
    val reserveOvertime: Double,
    val reserveSessionBonus: Double,
    val reserveDeductions: Double,
    val reservePendingWalletValue: Double // total accumulated Base + Overtime + Session - Deductions
)

object FinanceCalculations {

    fun timeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return 0
        val hour = parts[0].toIntOrNull() ?: 0
        val min = parts[1].toIntOrNull() ?: 0
        return hour * 60 + min
    }

    fun calculateDeduction(minutesLate: Int, hourlyBaseRate: Double, dailyBaseRate: Double): Double {
        if (minutesLate <= 10) return 0.0
        if (minutesLate <= 25) return 1.0 * hourlyBaseRate
        if (minutesLate <= 60) return 2.0 * hourlyBaseRate
        return 2.0 * hourlyBaseRate
    }

    fun calculateSessionBounce(
        sessionType: String,
        kidCount: Int,
        normalLow: Double,
        normalHigh: Double,
        campLow: Double,
        campHigh: Double,
        sessionCount: Int = 1,
        hoursPerSession: Double = 0.0
    ): Double {
        return if (sessionType.equals("Camp", ignoreCase = true)) {
            val baseBonus = if (kidCount <= 6) campLow else campHigh
            (baseBonus / 8.0) * hoursPerSession * sessionCount
        } else {
            val baseBonus = if (kidCount <= 3) normalLow else normalHigh
            (baseBonus / 8.0) * 2.0
        }
    }

    fun computeShiftFinancials(
        log: ShiftLog,
        cycle: BillingCycle,
        startMilestoneIdx: Int,
        accumulatedWfhHoursBefore: Double = 0.0
    ): ShiftFinancials {
        val baseMonthly = cycle.baseMonthlyPay
        val dailyBaseRate = baseMonthly / 30.0
        val hourlyBaseRate = dailyBaseRate / 8.0
        
        val sessions = SessionJson.fromJson(log.sessionMatrixJson)
        var totalSessionBonus = 0.0
        for (session in sessions) {
            totalSessionBonus += calculateSessionBounce(
                sessionType = session.sessionType,
                kidCount = session.kidCount,
                normalLow = cycle.normalLowBonus,
                normalHigh = cycle.normalHighBonus,
                campLow = cycle.campLowBonus,
                campHigh = cycle.campHighBonus,
                sessionCount = session.sessionCount,
                hoursPerSession = session.hoursPerSession
            )
        }

        val weight: Int
        val workedHours: Double
        val standardHours: Double
        val overtimeHours: Double
        val overtimePay: Double
        val deduction: Double
        val minutesLate: Int

        if (log.isWorkFromHome) {
            minutesLate = 0
            deduction = 0.0
            
            // Working hours = total sessions * 2.0
            workedHours = sessions.sumOf { it.sessionCount } * 2.0
            
            val totalWfhHoursAfter = accumulatedWfhHoursBefore + workedHours
            val oldMilestones = accumulatedWfhHoursBefore.toInt() / 8
            val newMilestones = totalWfhHoursAfter.toInt() / 8
            weight = newMilestones - oldMilestones
            
            standardHours = workedHours
            overtimeHours = 0.0
            overtimePay = 0.0
        } else {
            weight = if (log.isDoubleShift) 2 else 1
            
            val scheduledStart = timeToMinutes(log.scheduledStartTime)
            val actualStart = timeToMinutes(log.actualStartTime)
            minutesLate = max(0, actualStart - scheduledStart)
            deduction = calculateDeduction(minutesLate, hourlyBaseRate, dailyBaseRate)

            val actualStartMin = timeToMinutes(log.actualStartTime)
            var actualEndMin = timeToMinutes(log.actualEndTime)
            if (actualEndMin < actualStartMin) {
                actualEndMin += 24 * 60 // crossed midnight
            }
            workedHours = (actualEndMin - actualStartMin) / 60.0
            standardHours = if (log.isDoubleShift) {
                12.0
            } else if (log.isSixHourShift) {
                6.0
            } else {
                8.0
            }
            overtimeHours = max(0.0, workedHours - standardHours)
            overtimePay = overtimeHours * 2.0 * hourlyBaseRate
        }

        val endMilestoneIdx = startMilestoneIdx + weight
        val isReserveBlock = startMilestoneIdx >= 22

        val basePayCalculated = if (log.isWorkFromHome) {
            workedHours * hourlyBaseRate
        } else {
            dailyBaseRate * weight
        }
        val totalDailyValue = basePayCalculated + totalSessionBonus + overtimePay - deduction

        return ShiftFinancials(
            log = log,
            date = log.date,
            milestoneWeight = weight,
            hoursWorked = workedHours,
            standardHours = standardHours,
            overtimeHours = overtimeHours,
            minutesLate = minutesLate,
            baseRateDaily = dailyBaseRate,
            baseRateHourly = hourlyBaseRate,
            basePayCalculated = basePayCalculated,
            overtimePay = overtimePay,
            deduction = deduction,
            sessions = sessions,
            totalSessionBonus = totalSessionBonus,
            totalDailyValue = totalDailyValue,
            startMilestoneIndex = startMilestoneIdx,
            endMilestoneIndex = endMilestoneIdx,
            isReserveBlock = isReserveBlock
        )
    }

    private fun parseLogDate(dateStr: String): java.util.Date {
        return try {
            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).parse(dateStr) ?: java.util.Date(0)
        } catch (e: Exception) {
            java.util.Date(0)
        }
    }

    fun generateCycleReport(
        cycle: BillingCycle,
        rawLogs: List<ShiftLog>,
        previousReserveLogs: List<ShiftLog> = emptyList()
    ): CycleReport {
        val dailyBaseRate = cycle.baseMonthlyPay / 30.0
        val hourlyBaseRate = dailyBaseRate / 8.0

        val financials = mutableListOf<ShiftFinancials>()
        var runningMilestones = 0
        var runningWfhHours = 0.0

        // 1. Process previous reserve logs first as carried over shifts
        val sortedPrevLogs = previousReserveLogs.sortedWith(
            compareBy<ShiftLog> { parseLogDate(it.date) }.thenBy { it.id }
        )
        for (log in sortedPrevLogs) {
            val sFin = computeShiftFinancials(log, cycle, runningMilestones, runningWfhHours).copy(isCarriedOver = true)
            financials.add(sFin)
            runningMilestones += sFin.milestoneWeight
            if (log.isWorkFromHome) {
                runningWfhHours += sFin.hoursWorked
            }
        }

        // 2. Sort current logs chronologically and process
        val sortedLogs = rawLogs.sortedWith(
            compareBy<ShiftLog> { parseLogDate(it.date) }.thenBy { it.id }
        )
        for (log in sortedLogs) {
            val sFin = computeShiftFinancials(log, cycle, runningMilestones, runningWfhHours)
            financials.add(sFin)
            runningMilestones += sFin.milestoneWeight
            if (log.isWorkFromHome) {
                runningWfhHours += sFin.hoursWorked
            }
        }


        val totalMilestones = runningMilestones
        val isCycleComplete = totalMilestones >= 30

        // Bifurcation
        val payoutLogs = financials.filter { !it.isReserveBlock }
        val reserveLogs = financials.filter { it.isReserveBlock }

        // Payout block counts (Days 1 to 22) - includes the 8 paid holidays as requested
        val payoutBasePay = if (payoutLogs.isEmpty()) 0.0 else (dailyBaseRate * 22.0) + (dailyBaseRate * 8.0)
        val payoutOvertime = payoutLogs.sumOf { it.overtimePay }
        val payoutSessionBonus = payoutLogs.sumOf { it.totalSessionBonus }
        val payoutDeductions = payoutLogs.sumOf { it.deduction }
        val payoutMediaBonus = if (payoutLogs.isEmpty()) 0.0 else (if (cycle.isMediaBonusEnabled) cycle.mediaBonus else 0.0)
        val payoutNetSalary = if (payoutLogs.isEmpty()) 0.0 else (payoutBasePay + payoutOvertime + payoutSessionBonus + payoutMediaBonus - payoutDeductions)

        // Reserve block counts (Days 23 to 30) - Pending Wallet
        val reserveBasePay = if (reserveLogs.isEmpty()) 0.0 else dailyBaseRate * 8.0
        val reserveOvertime = reserveLogs.sumOf { it.overtimePay }
        val reserveSessionBonus = reserveLogs.sumOf { it.totalSessionBonus }
        val reserveDeductions = reserveLogs.sumOf { it.deduction }
        val reservePendingWalletValue = if (reserveLogs.isEmpty()) 0.0 else (reserveBasePay + reserveOvertime + reserveSessionBonus - reserveDeductions)

        return CycleReport(
            cycle = cycle,
            totalMilestones = totalMilestones,
            isCycleComplete = isCycleComplete,
            dailyBaseRate = dailyBaseRate,
            hourlyBaseRate = hourlyBaseRate,
            payoutLogs = payoutLogs,
            reserveLogs = reserveLogs,
            payoutBasePay = payoutBasePay,
            payoutOvertime = payoutOvertime,
            payoutSessionBonus = payoutSessionBonus,
            payoutDeductions = payoutDeductions,
            payoutMediaBonus = payoutMediaBonus,
            payoutNetSalary = payoutNetSalary,
            reserveBasePay = reserveBasePay,
            reserveOvertime = reserveOvertime,
            reserveSessionBonus = reserveSessionBonus,
            reserveDeductions = reserveDeductions,
            reservePendingWalletValue = reservePendingWalletValue
        )
    }
}
