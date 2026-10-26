package com.hussh.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hussh.data.db.BackspaceMode
import com.hussh.data.db.Protocol

/**
 * A saved connection. Columns suffixed `Enc` hold Keystore-AES-GCM ciphertext (base64) — they are
 * never stored or logged in plaintext. The whole database is *also* encrypted at rest by SQLCipher.
 */
@Entity(
    tableName = "hosts",
    indices = [Index("groupId"), Index("identityId"), Index("sortOrder")],
)
data class HostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val address: String,
    val port: Int,
    val protocol: Protocol,
    val groupId: Long? = null,
    val charset: String = "UTF-8",
    val termType: String = "xterm-256color",
    val startupCommand: String? = null,
    val keepAliveSec: Int = 60,
    val autoReconnect: Boolean = false,
    val backspaceMode: BackspaceMode = BackspaceMode.DEL,
    val identityId: Long? = null,
    /** Encrypted inline password (when [savePassword] and not using an identity). */
    val inlinePasswordEnc: String? = null,
    val savePassword: Boolean = false,
    val askEachTime: Boolean = false,
    val useKeyboardInteractive: Boolean = true,
    /** Per-host terminal theme id, or null to use the global default. */
    val terminalThemeId: String? = null,
    /** Per-host key-bar layout id, or null to use the global layout. */
    val customKeyBarId: Long? = null,
    val username: String = "",
    val lastConnected: Long? = null,
    val sortOrder: Int = 0,
)
