package com.termforge.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-defined folder/group for organizing hosts, with a colored dot in the list. */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val sortOrder: Int = 0,
    val collapsed: Boolean = false,
)
