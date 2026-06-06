package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async

class ChatRepository(private val chatDao: ChatDao) {

    // --- User Profile Operations ---
    val allUserProfiles: Flow<List<UserProfile>> = chatDao.getAllUserProfiles()
    
    suspend fun getActiveUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        chatDao.getActiveUserProfile()
    }

    suspend fun getOrCreateDefaultProfile(): UserProfile = withContext(Dispatchers.IO) {
        val active = chatDao.getActiveUserProfile()
        if (active != null) {
            active
        } else {
            // Check if there's any profile at all, else create default
            val defaultProfile = UserProfile(username = "Pranjal Guest", isActive = true)
            chatDao.insertUserProfile(defaultProfile)
            chatDao.getActiveUserProfile() ?: defaultProfile.copy(id = 1)
        }
    }

    suspend fun addUserProfile(profile: UserProfile): Int = withContext(Dispatchers.IO) {
        chatDao.insertUserProfile(profile).toInt()
    }

    suspend fun updateUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        chatDao.updateUserProfile(profile)
    }

    suspend fun selectUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        chatDao.deactivateAllUserProfiles()
        chatDao.updateUserProfile(profile.copy(isActive = true))
    }

    suspend fun deleteUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        chatDao.deleteUserProfile(profile)
        // If we deleted the active profile, reactivate the first available one or let default recreate
        val active = chatDao.getActiveUserProfile()
        if (active == null) {
            getOrCreateDefaultProfile()
        }
    }

    // --- Thread Database Operations ---
    val allThreads: Flow<List<ChatThread>> = chatDao.getAllThreads()

    fun getAllThreadsForUser(userId: Int): Flow<List<ChatThread>> {
        return chatDao.getAllThreadsForUser(userId)
    }

    suspend fun getThreadById(threadId: Int): ChatThread? = withContext(Dispatchers.IO) {
        chatDao.getThreadById(threadId)
    }

    suspend fun createThread(thread: ChatThread): Int = withContext(Dispatchers.IO) {
        chatDao.insertThread(thread).toInt()
    }

    suspend fun updateThread(thread: ChatThread) = withContext(Dispatchers.IO) {
        chatDao.updateThread(thread)
    }

    suspend fun deleteThread(thread: ChatThread) = withContext(Dispatchers.IO) {
        chatDao.deleteThread(thread)
    }

    suspend fun clearAllThreads() = withContext(Dispatchers.IO) {
        chatDao.clearAllThreads()
    }

    suspend fun clearAllThreadsForUser(userId: Int) = withContext(Dispatchers.IO) {
        chatDao.clearAllThreadsForUser(userId)
    }

    // --- Message Database Operations ---
    fun getMessagesForThread(threadId: Int): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForThread(threadId)
    }

    suspend fun addMessage(message: ChatMessage): Int = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message).toInt()
    }

    suspend fun deleteMessageById(messageId: Int) = withContext(Dispatchers.IO) {
        chatDao.deleteMessageById(messageId)
    }

    suspend fun searchMessages(keyword: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) emptyList() else chatDao.searchMessages("%$keyword%")
    }

    suspend fun searchMessagesForUser(userId: Int, keyword: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) emptyList() else chatDao.searchMessagesForUser(userId, "%$keyword%")
    }

    // --- Remote AI Synthesis (Gemini Integration) ---
    suspend fun generateAiResponse(
        thread: ChatThread,
        conversationHistory: List<ChatMessage>,
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {
        // Retrieve API key
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Key is missing! Please configure it in the Secrets panel in Google AI Studio to unlock Pranjal AI's intelligence."
        }

        if (thread.modelName == "combined-dual") {
            try {
                // Query factual recall model (Pro) and creative writer model (Flash) in parallel
                val deferredPro = async(Dispatchers.IO) {
                    generateSingleModelContent(
                        model = "gemini-3.1-pro-preview",
                        apiKey = apiKey,
                        thread = thread,
                        conversationHistory = conversationHistory,
                        userPrompt = userPrompt
                    )
                }
                val deferredFlash = async(Dispatchers.IO) {
                    generateSingleModelContent(
                        model = "gemini-3.5-flash",
                        apiKey = apiKey,
                        thread = thread,
                        conversationHistory = conversationHistory,
                        userPrompt = userPrompt
                    )
                }

                val proResponse = deferredPro.await()
                val flashResponse = deferredFlash.await()

                // Generate combined/synthesized response from both responses
                val synthesisPrompt = """
                    You are the Pranjal AI Assistant. Blend the following two responses into one highly versatile, clean response that inherits the factuality and precision of Model A, and the creative flair and aesthetic language of Model B.
                    
                    Provide a single cohesive unified response. Keep it clear, friendly, and structured.
                    
                    Model A (Factual / Pro):
                    $proResponse
                    
                    Model B (Creative / Flash):
                    $flashResponse
                """.trimIndent()

                val synthesisResponse = try {
                    generateSingleModelContent(
                        model = "gemini-3.5-flash",
                        apiKey = apiKey,
                        thread = thread.copy(systemInstruction = "You are the Pranjal AI Synthesis Engine. You combine factual precision with creative elegance into a unified, clean, highly helpful result."),
                        conversationHistory = emptyList(),
                        userPrompt = synthesisPrompt
                    )
                } catch (e: Exception) {
                    ""
                }

                buildString {
                    append("### 🔮 Combined Fused Synthesis\n")
                    if (synthesisResponse.isNotBlank() && !synthesisResponse.startsWith("Error")) {
                        append(synthesisResponse)
                    } else {
                        append("Here is the blended output of the factual and creative engines:\n\n$proResponse\n\n$flashResponse")
                    }
                    append("\n\n---\n")
                    append("### 🧠 Model A: Factual Recall (Pro)\n")
                    append(proResponse)
                    append("\n\n---\n")
                    append("### 🎨 Model B: Creative Writing (Flash)\n")
                    append(flashResponse)
                }
            } catch (e: Exception) {
                "Error during model synthesis: ${e.localizedMessage}"
            }
        } else {
            generateSingleModelContent(
                model = thread.modelName,
                apiKey = apiKey,
                thread = thread,
                conversationHistory = conversationHistory,
                userPrompt = userPrompt
            )
        }
    }

    private suspend fun generateSingleModelContent(
        model: String,
        apiKey: String,
        thread: ChatThread,
        conversationHistory: List<ChatMessage>,
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {
        // Build System instruction content (roles are omitted in system instructions)
        val systemInstructionContent = if (thread.systemInstruction.isNotBlank()) {
            Content(parts = listOf(Part(text = thread.systemInstruction)))
        } else {
            null
        }

        // Map ChatMessage models to Gemini Content payloads
        // Gemini expects multi-turn dialogue to alternate or flow with 'user' and 'model' roles
        val contents = mutableListOf<Content>()
        
        // Add past messages to contents list
        for (msg in conversationHistory) {
            val role = if (msg.role == "user") "user" else "model"
            contents.add(Content(parts = listOf(Part(text = msg.text)), role = role))
        }

        // Add current user query to contents list if not present
        if (conversationHistory.isEmpty() || conversationHistory.last().text != userPrompt) {
            contents.add(Content(parts = listOf(Part(text = userPrompt)), role = "user"))
        }

        // Limit active context window to the last 20 messages
        val trimmedContents = if (contents.size > 20) {
            contents.takeLast(20)
        } else {
            contents
        }

        val request = GenerateContentRequest(
            contents = trimmedContents,
            generationConfig = GenerationConfig(
                temperature = thread.temperature,
                maxOutputTokens = 2048
            ),
            systemInstruction = systemInstructionContent
        )

        try {
            val response = RetrofitClient.service.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )

            val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (textResult.isNullOrBlank()) {
                Log.e("ChatRepository", "Empty or incomplete candidate response received.")
                "Error: No response generated from model $model. It might have triggered safety policies."
            } else {
                textResult
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Gemini call failed for $model with exception: ${e.message}", e)
            "Error: ${e.localizedMessage ?: "Failed to reach Pranjal AI servers."}"
        }
    }
}
