package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_logs")
data class ShiftLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val date: String, // format: "YYYY-MM-DD" or "DD/MM/YYYY"
    val scheduledStartTime: String, // format: HH:MM
    val actualStartTime: String, // format: HH:MM
    val actualEndTime: String, // format: HH:MM
    val isDoubleShift: Boolean,
    val isSixHourShift: Boolean = false,
    val sessionMatrixJson: String, // Serialized List<SessionModel>
    val isWorkFromHome: Boolean = false
)
