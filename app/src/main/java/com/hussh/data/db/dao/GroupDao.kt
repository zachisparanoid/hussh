package com.hussh.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.hussh.data.db.entities.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Upsert
    suspend fun upsert(group: GroupEntity): Long

    @Delete
    suspend fun delete(group: GroupEntity)

    @Query("UPDATE groups SET collapsed = :collapsed WHERE id = :id")
    suspend fun setCollapsed(id: Long, collapsed: Boolean)

    @Query("UPDATE groups SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
