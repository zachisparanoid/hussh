package com.hussh.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hussh.data.db.ForwardType

/** An SSH port-forwarding rule attached to a host. */
@Entity(tableName = "port_forwards", indices = [Index("hostId")])
data class PortForwardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostId: Long,
    val type: ForwardType,
    val bindAddr: String = "127.0.0.1",
    val bindPort: Int,
    /** Unused for DYNAMIC (SOCKS) forwards. */
    val destHost: String = "",
    val destPort: Int = 0,
    val enabled: Boolean = true,
)
