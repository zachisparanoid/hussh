package com.termforge.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.termforge.data.db.entities.HostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY sortOrder ASC, nickname COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    fun observeById(id: Long): Flow<HostEntity?>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getById(id: Long): HostEntity?

    @Query(
        "SELECT * FROM hosts WHERE nickname LIKE '%' || :q || '%' " +
            "OR address LIKE '%' || :q || '%' OR username LIKE '%' || :q || '%' " +
            "ORDER BY sortOrder ASC, nickname COLLATE NOCASE ASC"
    )
    fun search(q: String): Flow<List<HostEntity>>

    @Upsert
    suspend fun upsert(host: HostEntity): Long

    @Delete
    suspend fun delete(host: HostEntity)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE hosts SET lastConnected = :ts WHERE id = :id")
    suspend fun touchLastConnected(id: Long, ts: Long)

    @Query("UPDATE hosts SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("UPDATE hosts SET groupId = :groupId WHERE id = :id")
    suspend fun moveToGroup(id: Long, groupId: Long?)

    @Query("SELECT * FROM hosts")
    suspend fun getAllOnce(): List<HostEntity>
}
