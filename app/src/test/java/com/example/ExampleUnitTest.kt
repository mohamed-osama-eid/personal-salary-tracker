package com.example

import com.example.data.engine.FinanceCalculations
import com.example.data.model.BillingCycle
import com.example.data.model.ShiftLog
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testNormalAndSixHoursOvertimeCalculations() {
    val cycle = BillingCycle(
        id = 1,
        name = "June cycle",
        baseMonthlyPay = 9000.0 // daily = 300, hourly = 37.5
    )

    // A normal shift (8 hours baseline) but worked 10 hours
    val normalShift = ShiftLog(
        id = 1,
        cycleId = 1,
        date = "22/06/2026",
        scheduledStartTime = "09:00",
        actualStartTime = "09:00",
        actualEndTime = "19:00", // 10 hours worked
        isDoubleShift = false,
        isSixHourShift = false,
        sessionMatrixJson = "[]"
    )

    val normalFinancials = FinanceCalculations.computeShiftFinancials(normalShift, cycle, 0)
    assertEquals(10.0, normalFinancials.hoursWorked, 0.001)
    assertEquals(8.0, normalFinancials.standardHours, 0.001)
    assertEquals(2.0, normalFinancials.overtimeHours, 0.001) // 10 - 8 = 2.0 hrs OT

    // A 6-hour shift (6 hours baseline) and worked 10 hours
    val PlayTrackerShift = ShiftLog(
        id = 2,
        cycleId = 1,
        date = "23/06/2026",
        scheduledStartTime = "09:00",
        actualStartTime = "09:00",
        actualEndTime = "19:00", // 10 hours worked
        isDoubleShift = false,
        isSixHourShift = true,
        sessionMatrixJson = "[]"
    )

    val sixHourFinancials = FinanceCalculations.computeShiftFinancials(PlayTrackerShift, cycle, 1)
    assertEquals(10.0, sixHourFinancials.hoursWorked, 0.001)
    assertEquals(6.0, sixHourFinancials.standardHours, 0.001)
    assertEquals(4.0, sixHourFinancials.overtimeHours, 0.001) // 10 - 6 = 4.0 hrs OT
  }

  @Test
  fun testCampSessionBonusCalculation() {
    val cycle = BillingCycle(
        id = 1,
        name = "July cycle",
        campLowBonus = 80.0,
        campHighBonus = 150.0
    )

    // Test case 1: Kids <= 6 (e.g., 5 kids). Camp Low rate = 80.0.
    // 3 sessions, 2.5 hours each.
    // Expected bonus: (80.0 / 8.0) * 2.5 * 3 = 10.0 * 2.5 * 3 = 75.0
    val bonusLow = FinanceCalculations.calculateSessionBounce(
        sessionType = "Camp",
        kidCount = 5,
        normalLow = 50.0,
        normalHigh = 100.0,
        campLow = cycle.campLowBonus,
        campHigh = cycle.campHighBonus,
        sessionCount = 3,
        hoursPerSession = 2.5
    )
    assertEquals(75.0, bonusLow, 0.001)

    // Test case 2: Kids > 6 (e.g., 8 kids). Camp High rate = 150.0.
    // 2 sessions, 4.0 hours each.
    // Expected bonus: (150.0 / 8.0) * 4.0 * 2 = 18.75 * 4.0 * 2 = 150.0
    val bonusHigh = FinanceCalculations.calculateSessionBounce(
        sessionType = "Camp",
        kidCount = 8,
        normalLow = 50.0,
        normalHigh = 100.0,
        campLow = cycle.campLowBonus,
        campHigh = cycle.campHighBonus,
        sessionCount = 2,
        hoursPerSession = 4.0
    )
    assertEquals(150.0, bonusHigh, 0.001)
  }
}
