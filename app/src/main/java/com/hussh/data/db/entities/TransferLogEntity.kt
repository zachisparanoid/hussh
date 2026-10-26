package com.hussh.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hussh.data.db.TransferDirection
import com.hussh.data.db.TransferStatus

/** History entry for an SFTP/SCP transfer, shown in the transfer log. */
@Entity(tableName = "transfer_log")
data class TransferLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostId: Long? = null,
    val direction: TransferDirection,
    val remotePath: String,
    val localName: String,
    val bytes: Long,
    val status: TransferStatus,
    val timestamp: Long,
)
