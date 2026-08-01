package com.example.weatherlocalapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_table ORDER BY timeMinutes ASC")
    fun getAllTimelines(): Flow<List<TimelineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeline(entity: TimelineEntity)

    @Update
    suspend fun updateTimeline(entity: TimelineEntity)

    @Delete
    suspend fun deleteTimeline(entity: TimelineEntity)
}
