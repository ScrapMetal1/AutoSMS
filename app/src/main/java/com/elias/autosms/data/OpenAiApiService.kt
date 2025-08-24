package com.elias.autosms.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAiApiService {
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun generateChatCompletion(
        @Body request: ChatGptRequest
    ): Response<ChatGptResponse>
}
