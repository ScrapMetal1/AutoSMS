package com.elias.autosms.data

import com.google.gson.annotations.SerializedName

// Request models
data class ChatGptRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<ChatGptMessage>,
    val max_tokens: Int = 150,
    val temperature: Double = 0.7
)

data class ChatGptMessage(
    val role: String,
    val content: String
)

// Response models
data class ChatGptResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<ChatGptChoice>,
    val usage: ChatGptUsage
)

data class ChatGptChoice(
    val index: Int,
    val message: ChatGptMessage,
    @SerializedName("finish_reason")
    val finishReason: String
)

data class ChatGptUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)
