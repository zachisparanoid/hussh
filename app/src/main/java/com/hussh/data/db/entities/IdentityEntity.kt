package com.hussh.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hussh.data.db.KeyType

/**
 * An SSH identity (key pair). [privateKeyEnc] and [passphraseEnc] are Keystore-AES-GCM ciphertext;
 * [publicKey] is plaintext OpenSSH format (`ssh-ed25519 AAAA... comment`).
 */
@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val keyType: KeyType,
    /** Encrypted private key bytes (OpenSSH/PKCS#8 PEM). */
    val privateKeyEnc: String,
    val publicKey: String,
    /** Encrypted key passphrase, or null if the key is unencrypted or the passphrase isn't stored. */
    val passphraseEnc: String? = null,
    val fingerprintSha256: String,
    val fingerprintMd5: String = "",
    val createdAt: Long,
)
