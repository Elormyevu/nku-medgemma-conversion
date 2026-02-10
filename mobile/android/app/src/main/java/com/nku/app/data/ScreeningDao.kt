package com.nku.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * ScreeningDao — data access for screening records.
 */
@Dao
interface ScreeningDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(screening: ScreeningEntity): Long

    @Query("SELECT * FROM screenings ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ScreeningEntity>>

    @Query("SELECT * FROM screenings WHERE id = :id")
    suspend fun getById(id: Long): ScreeningEntity?

    @Query("SELECT COUNT(*) FROM screenings")
    fun getCount(): Flow<Int>

    @Query("DELETE FROM screenings WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
