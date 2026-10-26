package com.hussh.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.hussh.data.db.entities.IdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun getById(id: Long): IdentityEntity?

    @Query("SELECT * FROM identities WHERE id = :id")
    fun observeById(id: Long): Flow<IdentityEntity?>

    @Upsert
    suspend fun upsert(identity: IdentityEntity): Long

    @Delete
    suspend fun delete(identity: IdentityEntity)

    @Query("SELECT COUNT(*) FROM hosts WHERE identityId = :id")
    suspend fun hostsUsingIdentity(id: Long): Int
}
