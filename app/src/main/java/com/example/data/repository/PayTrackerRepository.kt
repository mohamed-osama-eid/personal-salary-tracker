package com.example.data.repository

import com.example.data.database.PayTrackerDao
import com.example.data.model.BillingCycle
import com.example.data.model.ShiftLog
import kotlinx.coroutines.flow.Flow

class PayTrackerRepository(private val dao: PayTrackerDao) {

    val allCycles: Flow<List<BillingCycle>> = dao.getAllCyclesFlow()

    val allLogs: Flow<List<ShiftLog>> = dao.getAllLogsFlow()

    fun getLogsForCycleFlow(cycleId: Long): Flow<List<ShiftLog>> = dao.getLogsForCycleFlow(cycleId)

    suspend fun getLatestCycle(): BillingCycle? = dao.getLatestCycle()

    suspend fun getCycleById(id: Long): BillingCycle? = dao.getCycleById(id)

    suspend fun insertCycle(cycle: BillingCycle): Long = dao.insertCycle(cycle)

    suspend fun updateCycle(cycle: BillingCycle) = dao.updateCycle(cycle)

    suspend fun deleteCycle(cycle: BillingCycle) = dao.deleteCycle(cycle)

    suspend fun insertLog(log: ShiftLog): Long = dao.insertLog(log)

    suspend fun updateLog(log: ShiftLog) = dao.updateLog(log)

    suspend fun deleteLog(log: ShiftLog) = dao.deleteLog(log)

    suspend fun deleteLogById(id: Long) = dao.deleteLogById(id)

    suspend fun deleteAllLogsForCycle(cycleId: Long) = dao.deleteAllLogsForCycle(cycleId)


    suspend fun getLogsForCycle(cycleId: Long): List<ShiftLog> = dao.getLogsForCycle(cycleId)
}
