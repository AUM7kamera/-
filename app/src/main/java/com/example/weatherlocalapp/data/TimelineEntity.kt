package com.example.weatherlocalapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timeline_table")
data class TimelineEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val timeMinutes: Int, // e.g. -30 for 30 minutes before, +60 for 60 minutes after warning
    val contactName: String,
    val contactPhone: String
)
