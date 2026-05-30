package com.termforge.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.termforge.data.db.KeyBarScope

/**
 * A keyboard helper-bar layout. The ordered list of buttons is serialized to JSON
 * ([orderedButtonsJson]) — see `com.termforge.keybar.KeyBarButtonDef`. Exactly one GLOBAL row
 * exists; HOST rows override it for a single host (referenced by [hostId]).
 */
@Entity(tableName = "keybar_layouts", indices = [Index("hostId")])
data class KeyBarLayoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: KeyBarScope,
    val hostId: Long? = null,
    val orderedButtonsJson: String,
)
