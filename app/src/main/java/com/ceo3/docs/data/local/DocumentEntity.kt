package com.ceo3.docs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val type: String,
    val lastModified: Long,
    val isPinned: Boolean,
    val tags: String, // Comma separated for simplicity
    val accentTheme: String = "classic", // classic, sepia, mint, charcoal
    val accentColor: String = "blue"     // red, blue, green, orange, purple
)
