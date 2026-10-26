package com.hussh.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A folder for organizing snippets. */
@Entity(tableName = "snippet_folders")
data class SnippetFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

/** A reusable command string, insertable into any terminal. */
@Entity(tableName = "snippets", indices = [Index("folderId")])
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val name: String,
    val content: String,
    val appendNewline: Boolean = true,
    val sortOrder: Int = 0,
)
