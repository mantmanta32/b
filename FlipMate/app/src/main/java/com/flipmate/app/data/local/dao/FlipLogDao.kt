package com.flipmate.app.data.local.dao
import androidx.room.*
import com.flipmate.app.data.local.entity.FlipLogEntity
import kotlinx.coroutines.flow.Flow
@Dao interface FlipLogDao { @Query("SELECT * FROM flip_logs ORDER BY createdAt DESC") fun all():Flow<List<FlipLogEntity>>; @Query("SELECT * FROM flip_logs WHERE mode=:mode ORDER BY createdAt DESC") fun byMode(mode:String):Flow<List<FlipLogEntity>>; @Insert suspend fun insert(log:FlipLogEntity):Long; @Query("DELETE FROM flip_logs") suspend fun clear() }
