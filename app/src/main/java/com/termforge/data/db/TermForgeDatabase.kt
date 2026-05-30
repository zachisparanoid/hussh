package com.termforge.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.termforge.data.db.dao.GroupDao
import com.termforge.data.db.dao.HostDao
import com.termforge.data.db.dao.IdentityDao
import com.termforge.data.db.dao.KeyBarDao
import com.termforge.data.db.dao.KnownHostDao
import com.termforge.data.db.dao.PortForwardDao
import com.termforge.data.db.dao.SnippetDao
import com.termforge.data.db.dao.TransferLogDao
import com.termforge.data.db.entities.GroupEntity
import com.termforge.data.db.entities.HostEntity
import com.termforge.data.db.entities.IdentityEntity
import com.termforge.data.db.entities.KeyBarLayoutEntity
import com.termforge.data.db.entities.KnownHostEntity
import com.termforge.data.db.entities.PortForwardEntity
import com.termforge.data.db.entities.SnippetEntity
import com.termforge.data.db.entities.SnippetFolderEntity
import com.termforge.data.db.entities.TransferLogEntity

@Database(
    entities = [
        HostEntity::class,
        IdentityEntity::class,
        GroupEntity::class,
        KeyBarLayoutEntity::class,
        SnippetEntity::class,
        SnippetFolderEntity::class,
        PortForwardEntity::class,
        KnownHostEntity::class,
        TransferLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TermForgeDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun identityDao(): IdentityDao
    abstract fun groupDao(): GroupDao
    abstract fun keyBarDao(): KeyBarDao
    abstract fun snippetDao(): SnippetDao
    abstract fun portForwardDao(): PortForwardDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun transferLogDao(): TransferLogDao

    companion object {
        const val NAME = "termforge.db"
    }
}
