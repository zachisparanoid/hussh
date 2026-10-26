package com.hussh.data.repo

import com.hussh.data.crypto.CredentialCrypto
import com.hussh.data.db.dao.HostDao
import com.hussh.data.db.entities.HostEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hosts CRUD. The inline password is the one sensitive field: it is encrypted on save and decrypted
 * only on demand at connect time via [resolvePassword] — never eagerly for list display.
 */
@Singleton
class HostRepository @Inject constructor(
    private val hostDao: HostDao,
    private val crypto: CredentialCrypto,
) {
    fun observeAll(): Flow<List<HostEntity>> = hostDao.observeAll()
    fun observeById(id: Long): Flow<HostEntity?> = hostDao.observeById(id)
    fun search(query: String): Flow<List<HostEntity>> =
        if (query.isBlank()) hostDao.observeAll() else hostDao.search(query.trim())

    suspend fun getById(id: Long): HostEntity? = hostDao.getById(id)
    suspend fun getAll(): List<HostEntity> = hostDao.getAllOnce()

    /**
     * Persist [host]. If [plaintextPassword] is provided and [HostEntity.savePassword] is set, it is
     * encrypted into `inlinePasswordEnc`; if saving is off, any stored password is cleared.
     */
    suspend fun save(host: HostEntity, plaintextPassword: String? = null): Long {
        val resolved = when {
            host.savePassword && plaintextPassword != null ->
                host.copy(inlinePasswordEnc = crypto.encryptString(plaintextPassword))
            !host.savePassword -> host.copy(inlinePasswordEnc = null)
            else -> host
        }
        return hostDao.upsert(resolved)
    }

    suspend fun delete(id: Long) = hostDao.deleteById(id)
    suspend fun touchLastConnected(id: Long, now: Long) = hostDao.touchLastConnected(id, now)
    suspend fun updateSortOrder(id: Long, order: Int) = hostDao.updateSortOrder(id, order)
    suspend fun moveToGroup(id: Long, groupId: Long?) = hostDao.moveToGroup(id, groupId)

    /** Decrypts the saved inline password, or null if none stored. */
    fun resolvePassword(host: HostEntity): String? =
        host.inlinePasswordEnc?.let { crypto.decryptString(it) }
}
