package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val modelName: String,
    val systemPrompt: String = "You are a helpful, concise AI assistant running locally on a Helio G100-Ultra Octa-core device.",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 1024,
    val threads: Int = 4,
    val gpuLayers: Int = 24,
    val vulkanEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
