package com.example.data.database

import androidx.room.*
import com.example.data.model.BillingCycle
import com.example.data.model.ShiftLog
import kotlinx.coroutines.flow.Flow

@Dao
interface PayTrackerDao {

    @Query("SELECT * FROM billing_cycles ORDER BY id DESC")
    fun getAllCyclesFlow(): Flow<List<BillingCycle>>

    @Query("SELECT * FROM shift_logs ORDER BY date ASC, id ASC")
    fun getAllLogsFlow(): Flow<List<ShiftLog>>

    @Query("SELECT * FROM billing_cycles ORDER BY id DESC LIMIT 1")
    suspend fun getLatestCycle(): BillingCycle?

    @Query("SELECT * FROM billing_cycles WHERE id = :id")
    suspend fun getCycleById(id: Long): BillingCycle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: BillingCycle): Long

    @Update
    suspend fun updateCycle(cycle: BillingCycle)

    @Delete
    suspend fun deleteCycle(cycle: BillingCycle)

    @Query("SELECT * FROM shift_logs WHERE cycleId = :cycleId ORDER BY date ASC, id ASC")
    fun getLogsForCycleFlow(cycleId: Long): Flow<List<ShiftLog>>

    @Query("SELECT * FROM shift_logs WHERE cycleId = :cycleId ORDER BY date ASC, id ASC")
    suspend fun getLogsForCycle(cycleId: Long): List<ShiftLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ShiftLog): Long

    @Update
    suspend fun updateLog(log: ShiftLog)

    @Delete
    suspend fun deleteLog(log: ShiftLog)

    @Query("DELETE FROM shift_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM shift_logs WHERE cycleId = :cycleId")
    suspend fun deleteAllLogsForCycle(cycleId: Long)
}
