package com.termforge.data.repo

import com.termforge.data.crypto.CredentialCrypto
import com.termforge.data.db.KeyType
import com.termforge.data.db.dao.IdentityDao
import com.termforge.data.db.entities.IdentityEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Identity CRUD. Private keys and passphrases are encrypted at rest and decrypted only on use. */
@Singleton
class IdentityRepository @Inject constructor(
    private val identityDao: IdentityDao,
    private val crypto: CredentialCrypto,
) {
    fun observeAll(): Flow<List<IdentityEntity>> = identityDao.observeAll()
    fun observeById(id: Long): Flow<IdentityEntity?> = identityDao.observeById(id)
    suspend fun getById(id: Long): IdentityEntity? = identityDao.getById(id)
    suspend fun hostsUsing(id: Long): Int = identityDao.hostsUsingIdentity(id)

    /**
     * Encrypt and store a new/updated identity. [privateKeyPem] is the textual private key
     * (OpenSSH/PKCS#8). [passphrase] is the key's unlock passphrase to remember, or null.
     */
    suspend fun save(
        id: Long = 0,
        name: String,
        keyType: KeyType,
        privateKeyPem: String,
        publicKey: String,
        fingerprintSha256: String,
        fingerprintMd5: String,
        passphrase: String?,
        createdAt: Long,
    ): Long = identityDao.upsert(
        IdentityEntity(
            id = id,
            name = name,
            keyType = keyType,
            privateKeyEnc = crypto.encryptString(privateKeyPem),
            publicKey = publicKey,
            passphraseEnc = passphrase?.let { crypto.encryptString(it) },
            fingerprintSha256 = fingerprintSha256,
            fingerprintMd5 = fingerprintMd5,
            createdAt = createdAt,
        ),
    )

    suspend fun delete(identity: IdentityEntity) = identityDao.delete(identity)

    fun resolvePrivateKeyPem(identity: IdentityEntity): String =
        crypto.decryptString(identity.privateKeyEnc)

    fun resolvePassphrase(identity: IdentityEntity): String? =
        identity.passphraseEnc?.let { crypto.decryptString(it) }
}
