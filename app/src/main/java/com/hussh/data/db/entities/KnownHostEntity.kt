package com.hussh.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A trusted host key (Trust-On-First-Use). Uniqueness is (host, port, keyType): a server may offer
 * several key types. A fingerprint mismatch for an existing row triggers the loud mismatch warning.
 */
@Entity(
    tableName = "known_hosts",
    indices = [Index(value = ["host", "port", "keyType"], unique = true)],
)
data class KnownHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val keyType: String,
    /** SHA-256 base64 fingerprint, e.g. `SHA256:abc...`. */
    val fingerprint: String,
    /** Full base64 of the server public key, for exact comparison. */
    val publicKeyBase64: String = "",
    val addedAt: Long = 0,
)
