package com.example.data.local

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val db: AppDatabase) {
    val allConversations: Flow<List<ConversationEntity>> = db.conversationDao().getAllConversations()

    fun getMessages(conversationId: Long): Flow<List<MessageEntity>> {
        return db.messageDao().getMessagesForConversation(conversationId)
    }

    suspend fun getConversation(id: Long): ConversationEntity? {
        return db.conversationDao().getConversationById(id)
    }

    suspend fun createConversation(
        title: String,
        modelName: String,
        systemPrompt: String = "You are an expert AI assistant running locally.",
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        threads: Int = 4,
        gpuLayers: Int = 24,
        vulkanEnabled: Boolean = true
    ): Long {
        val conv = ConversationEntity(
            title = title,
            modelName = modelName,
            systemPrompt = systemPrompt,
            temperature = temperature,
            topP = topP,
            threads = threads,
            gpuLayers = gpuLayers,
            vulkanEnabled = vulkanEnabled,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return db.conversationDao().insertConversation(conv)
    }

    suspend fun updateConversation(conversation: ConversationEntity) {
        db.conversationDao().updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(id: Long) {
        db.conversationDao().deleteConversationById(id)
    }

    suspend fun clearAllConversations() {
        db.conversationDao().deleteAllConversations()
    }

    suspend fun insertMessage(
        conversationId: Long,
        role: String,
        content: String,
        tokensCount: Int = 0,
        tps: Float = 0.0f,
        ttftMs: Long = 0L
    ): Long {
        val msg = MessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            tokensCount = tokensCount,
            tokensPerSecond = tps,
            timeToFirstTokenMs = ttftMs
        )
        val msgId = db.messageDao().insertMessage(msg)
        // Update conversation timestamp
        val conv = db.conversationDao().getConversationById(conversationId)
        if (conv != null) {
            db.conversationDao().updateConversation(conv.copy(updatedAt = System.currentTimeMillis()))
        }
        return msgId
    }

    suspend fun deleteMessage(messageId: Long) {
        db.messageDao().deleteMessageById(messageId)
    }
}
