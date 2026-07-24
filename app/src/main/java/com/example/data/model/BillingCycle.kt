package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "billing_cycles")
data class BillingCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isLocked: Boolean = false,
    val isExported: Boolean = false,
    val baseMonthlyPay: Double = 8000.0,
    val mediaBonus: Double = 500.0,
    val isMediaBonusEnabled: Boolean = true,
    val normalLowBonus: Double = 50.0,
    val normalHighBonus: Double = 100.0,
    val campLowBonus: Double = 80.0,
    val campHighBonus: Double = 150.0
)
