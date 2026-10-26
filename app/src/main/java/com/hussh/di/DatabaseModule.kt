package com.hussh.di

import android.content.Context
import androidx.room.Room
import com.hussh.data.crypto.DatabasePassphraseProvider
import com.hussh.data.db.HusshDatabase
import com.hussh.data.db.dao.GroupDao
import com.hussh.data.db.dao.HostDao
import com.hussh.data.db.dao.IdentityDao
import com.hussh.data.db.dao.KeyBarDao
import com.hussh.data.db.dao.KnownHostDao
import com.hussh.data.db.dao.PortForwardDao
import com.hussh.data.db.dao.SnippetDao
import com.hussh.data.db.dao.TransferLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): HusshDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(passphraseProvider.passphrase())
        return Room.databaseBuilder(context, HusshDatabase::class.java, HusshDatabase.NAME)
            .openHelperFactory(factory)
            .build()
    }

    @Provides fun hostDao(db: HusshDatabase): HostDao = db.hostDao()
    @Provides fun identityDao(db: HusshDatabase): IdentityDao = db.identityDao()
    @Provides fun groupDao(db: HusshDatabase): GroupDao = db.groupDao()
    @Provides fun keyBarDao(db: HusshDatabase): KeyBarDao = db.keyBarDao()
    @Provides fun snippetDao(db: HusshDatabase): SnippetDao = db.snippetDao()
    @Provides fun portForwardDao(db: HusshDatabase): PortForwardDao = db.portForwardDao()
    @Provides fun knownHostDao(db: HusshDatabase): KnownHostDao = db.knownHostDao()
    @Provides fun transferLogDao(db: HusshDatabase): TransferLogDao = db.transferLogDao()
}
