package com.hussh.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hussh.data.db.dao.GroupDao
import com.hussh.data.db.dao.HostDao
import com.hussh.data.db.dao.IdentityDao
import com.hussh.data.db.dao.KeyBarDao
import com.hussh.data.db.dao.KnownHostDao
import com.hussh.data.db.dao.PortForwardDao
import com.hussh.data.db.dao.SnippetDao
import com.hussh.data.db.dao.TransferLogDao
import com.hussh.data.db.entities.GroupEntity
import com.hussh.data.db.entities.HostEntity
import com.hussh.data.db.entities.IdentityEntity
import com.hussh.data.db.entities.KeyBarLayoutEntity
import com.hussh.data.db.entities.KnownHostEntity
import com.hussh.data.db.entities.PortForwardEntity
import com.hussh.data.db.entities.SnippetEntity
import com.hussh.data.db.entities.SnippetFolderEntity
import com.hussh.data.db.entities.TransferLogEntity

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
abstract class HusshDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun identityDao(): IdentityDao
    abstract fun groupDao(): GroupDao
    abstract fun keyBarDao(): KeyBarDao
    abstract fun snippetDao(): SnippetDao
    abstract fun portForwardDao(): PortForwardDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun transferLogDao(): TransferLogDao

    companion object {
        const val NAME = "hussh.db"
    }
}
