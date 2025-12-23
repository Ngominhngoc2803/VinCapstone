package com.heartmonitor.app.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAIService {
    @POST("v1/chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

data class ChatCompletionRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ChatRequestMessage>,
    val max_tokens: Int = 1000,
    val temperature: Float = 0.7f
)

data class ChatRequestMessage(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val id: String,
    val choices: List<ChatChoice>
)

data class ChatChoice(
    val index: Int,
    val message: ChatResponseMessage,
    val finish_reason: String?
)

data class ChatResponseMessage(
    val role: String,
    val content: String
)
