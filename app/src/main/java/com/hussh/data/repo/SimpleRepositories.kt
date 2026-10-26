package com.hussh.data.repo

import com.hussh.data.db.dao.GroupDao
import com.hussh.data.db.dao.KnownHostDao
import com.hussh.data.db.dao.PortForwardDao
import com.hussh.data.db.dao.SnippetDao
import com.hussh.data.db.dao.TransferLogDao
import com.hussh.data.db.entities.GroupEntity
import com.hussh.data.db.entities.KnownHostEntity
import com.hussh.data.db.entities.PortForwardEntity
import com.hussh.data.db.entities.SnippetEntity
import com.hussh.data.db.entities.SnippetFolderEntity
import com.hussh.data.db.entities.TransferLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(private val dao: GroupDao) {
    fun observeAll(): Flow<List<GroupEntity>> = dao.observeAll()
    suspend fun save(group: GroupEntity): Long = dao.upsert(group)
    suspend fun delete(group: GroupEntity) = dao.delete(group)
    suspend fun setCollapsed(id: Long, collapsed: Boolean) = dao.setCollapsed(id, collapsed)
    suspend fun updateSortOrder(id: Long, order: Int) = dao.updateSortOrder(id, order)
}

@Singleton
class SnippetRepository @Inject constructor(private val dao: SnippetDao) {
    fun observeFolders(): Flow<List<SnippetFolderEntity>> = dao.observeFolders()
    fun observeSnippets(): Flow<List<SnippetEntity>> = dao.observeSnippets()
    suspend fun saveFolder(folder: SnippetFolderEntity): Long = dao.upsertFolder(folder)
    suspend fun saveSnippet(snippet: SnippetEntity): Long = dao.upsertSnippet(snippet)
    suspend fun deleteFolder(folder: SnippetFolderEntity) = dao.deleteFolder(folder)
    suspend fun deleteSnippet(snippet: SnippetEntity) = dao.deleteSnippet(snippet)
}

@Singleton
class PortForwardRepository @Inject constructor(private val dao: PortForwardDao) {
    fun observeForHost(hostId: Long): Flow<List<PortForwardEntity>> = dao.observeForHost(hostId)
    suspend fun getForHost(hostId: Long): List<PortForwardEntity> = dao.getForHost(hostId)
    suspend fun save(forward: PortForwardEntity): Long = dao.upsert(forward)
    suspend fun delete(forward: PortForwardEntity) = dao.delete(forward)
}

@Singleton
class KnownHostRepository @Inject constructor(private val dao: KnownHostDao) {
    fun observeAll(): Flow<List<KnownHostEntity>> = dao.observeAll()
    suspend fun find(host: String, port: Int, keyType: String): KnownHostEntity? = dao.find(host, port, keyType)
    suspend fun trust(entry: KnownHostEntity): Long = dao.upsert(entry)
    suspend fun hussht(host: String, port: Int) = dao.hussht(host, port)
}

@Singleton
class TransferLogRepository @Inject constructor(private val dao: TransferLogDao) {
    fun observeRecent(): Flow<List<TransferLogEntity>> = dao.observeRecent()
    suspend fun record(entry: TransferLogEntity): Long = dao.upsert(entry)
    suspend fun clear() = dao.clear()
}
