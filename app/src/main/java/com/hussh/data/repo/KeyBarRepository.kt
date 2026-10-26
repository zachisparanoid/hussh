package com.hussh.data.repo

import com.hussh.data.db.KeyBarScope
import com.hussh.data.db.dao.KeyBarDao
import com.hussh.data.db.entities.KeyBarLayoutEntity
import com.hussh.keybar.DefaultKeyBar
import com.hussh.keybar.KeyBarLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores/loads keyboard-bar layouts. The GLOBAL layout applies everywhere; a HOST layout overrides
 * it for a single host. Layouts serialize to JSON in [KeyBarLayoutEntity.orderedButtonsJson].
 */
@Singleton
class KeyBarRepository @Inject constructor(
    private val dao: KeyBarDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun observeGlobal(): Flow<KeyBarLayout> = dao.observeGlobal().map { entity ->
        entity?.let { decode(it.orderedButtonsJson) } ?: DefaultKeyBar.layout
    }

    /** The layout that should apply for a host: its custom layout if set, otherwise the global one. */
    suspend fun effectiveLayout(customKeyBarId: Long?): KeyBarLayout {
        customKeyBarId?.let { id -> dao.getById(id)?.let { return decode(it.orderedButtonsJson) } }
        return dao.getGlobal()?.let { decode(it.orderedButtonsJson) } ?: DefaultKeyBar.layout
    }

    suspend fun getHostLayout(hostId: Long): KeyBarLayout? =
        dao.getForHost(hostId)?.let { decode(it.orderedButtonsJson) }

    suspend fun saveGlobal(layout: KeyBarLayout) {
        val existing = dao.getGlobal()
        dao.upsert(
            (existing ?: KeyBarLayoutEntity(scope = KeyBarScope.GLOBAL, orderedButtonsJson = ""))
                .copy(scope = KeyBarScope.GLOBAL, orderedButtonsJson = encode(layout)),
        )
    }

    /** Save a per-host layout and return its id (store this in [HostEntity.customKeyBarId]). */
    suspend fun saveHostLayout(hostId: Long, layout: KeyBarLayout): Long {
        val existing = dao.getForHost(hostId)
        return dao.upsert(
            (existing ?: KeyBarLayoutEntity(scope = KeyBarScope.HOST, hostId = hostId, orderedButtonsJson = ""))
                .copy(scope = KeyBarScope.HOST, hostId = hostId, orderedButtonsJson = encode(layout)),
        )
    }

    suspend fun resetGlobalToDefault() = saveGlobal(DefaultKeyBar.layout)

    suspend fun deleteHostLayout(id: Long) = dao.deleteById(id)

    suspend fun ensureGlobalSeeded() {
        if (dao.getGlobal() == null) saveGlobal(DefaultKeyBar.layout)
    }

    private fun encode(layout: KeyBarLayout): String = json.encodeToString(KeyBarLayout.serializer(), layout)

    private fun decode(s: String): KeyBarLayout =
        runCatching { json.decodeFromString(KeyBarLayout.serializer(), s) }.getOrDefault(DefaultKeyBar.layout)
}
