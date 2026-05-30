package com.termforge.di

import android.content.Context
import androidx.room.Room
import com.termforge.data.crypto.DatabasePassphraseProvider
import com.termforge.data.db.TermForgeDatabase
import com.termforge.data.db.dao.GroupDao
import com.termforge.data.db.dao.HostDao
import com.termforge.data.db.dao.IdentityDao
import com.termforge.data.db.dao.KeyBarDao
import com.termforge.data.db.dao.KnownHostDao
import com.termforge.data.db.dao.PortForwardDao
import com.termforge.data.db.dao.SnippetDao
import com.termforge.data.db.dao.TransferLogDao
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
    ): TermForgeDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(passphraseProvider.passphrase())
        return Room.databaseBuilder(context, TermForgeDatabase::class.java, TermForgeDatabase.NAME)
            .openHelperFactory(factory)
            .build()
    }

    @Provides fun hostDao(db: TermForgeDatabase): HostDao = db.hostDao()
    @Provides fun identityDao(db: TermForgeDatabase): IdentityDao = db.identityDao()
    @Provides fun groupDao(db: TermForgeDatabase): GroupDao = db.groupDao()
    @Provides fun keyBarDao(db: TermForgeDatabase): KeyBarDao = db.keyBarDao()
    @Provides fun snippetDao(db: TermForgeDatabase): SnippetDao = db.snippetDao()
    @Provides fun portForwardDao(db: TermForgeDatabase): PortForwardDao = db.portForwardDao()
    @Provides fun knownHostDao(db: TermForgeDatabase): KnownHostDao = db.knownHostDao()
    @Provides fun transferLogDao(db: TermForgeDatabase): TransferLogDao = db.transferLogDao()
}
