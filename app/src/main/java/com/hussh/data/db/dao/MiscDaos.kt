package com.hussh.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.hussh.data.db.entities.KeyBarLayoutEntity
import com.hussh.data.db.entities.KnownHostEntity
import com.hussh.data.db.entities.PortForwardEntity
import com.hussh.data.db.entities.SnippetEntity
import com.hussh.data.db.entities.SnippetFolderEntity
import com.hussh.data.db.entities.TransferLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyBarDao {
    @Query("SELECT * FROM keybar_layouts WHERE scope = 'GLOBAL' LIMIT 1")
    fun observeGlobal(): Flow<KeyBarLayoutEntity?>

    @Query("SELECT * FROM keybar_layouts WHERE scope = 'GLOBAL' LIMIT 1")
    suspend fun getGlobal(): KeyBarLayoutEntity?

    @Query("SELECT * FROM keybar_layouts WHERE id = :id")
    suspend fun getById(id: Long): KeyBarLayoutEntity?

    @Query("SELECT * FROM keybar_layouts WHERE hostId = :hostId LIMIT 1")
    suspend fun getForHost(hostId: Long): KeyBarLayoutEntity?

    @Upsert
    suspend fun upsert(layout: KeyBarLayoutEntity): Long

    @Delete
    suspend fun delete(layout: KeyBarLayoutEntity)

    @Query("DELETE FROM keybar_layouts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippet_folders ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeFolders(): Flow<List<SnippetFolderEntity>>

    @Query("SELECT * FROM snippets ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeSnippets(): Flow<List<SnippetEntity>>

    @Upsert suspend fun upsertFolder(folder: SnippetFolderEntity): Long
    @Upsert suspend fun upsertSnippet(snippet: SnippetEntity): Long
    @Delete suspend fun deleteFolder(folder: SnippetFolderEntity)
    @Delete suspend fun deleteSnippet(snippet: SnippetEntity)
}

@Dao
interface PortForwardDao {
    @Query("SELECT * FROM port_forwards WHERE hostId = :hostId")
    fun observeForHost(hostId: Long): Flow<List<PortForwardEntity>>

    @Query("SELECT * FROM port_forwards WHERE hostId = :hostId")
    suspend fun getForHost(hostId: Long): List<PortForwardEntity>

    @Upsert suspend fun upsert(forward: PortForwardEntity): Long
    @Delete suspend fun delete(forward: PortForwardEntity)
    @Query("DELETE FROM port_forwards WHERE hostId = :hostId")
    suspend fun deleteForHost(hostId: Long)
}

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port AND keyType = :keyType LIMIT 1")
    suspend fun find(host: String, port: Int, keyType: String): KnownHostEntity?

    @Query("SELECT * FROM known_hosts ORDER BY host ASC")
    fun observeAll(): Flow<List<KnownHostEntity>>

    @Upsert suspend fun upsert(entry: KnownHostEntity): Long
    @Delete suspend fun delete(entry: KnownHostEntity)
    @Query("DELETE FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun hussht(host: String, port: Int)
}

@Dao
interface TransferLogDao {
    @Query("SELECT * FROM transfer_log ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<TransferLogEntity>>

    @Upsert suspend fun upsert(entry: TransferLogEntity): Long
    @Query("DELETE FROM transfer_log")
    suspend fun clear()
}
