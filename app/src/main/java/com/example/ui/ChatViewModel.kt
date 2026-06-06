package com.example.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository
    private val sharedPrefs = application.getSharedPreferences("pranjal_ai_preferences", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("theme_is_dark", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        sharedPrefs.edit().putBoolean("theme_is_dark", enabled).apply()
    }
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = ChatRepository(database.chatDao())
    }

    // --- User Profile Streams & States ---
    val allUserProfiles: StateFlow<List<UserProfile>> = repository.allUserProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeUserProfile = MutableStateFlow<UserProfile?>(null)
    val activeUserProfile: StateFlow<UserProfile?> = _activeUserProfile.asStateFlow()

    private val _userName = MutableStateFlow("Pranjal Guest")
    val userName: StateFlow<String> = _userName.asStateFlow()

    // Dynamic thread querying: scoped/filtered for the selected User Profile
    val allThreads: StateFlow<List<ChatThread>> = _activeUserProfile
        .flatMapLatest { user ->
            if (user != null) {
                repository.getAllThreadsForUser(user.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeThread = MutableStateFlow<ChatThread?>(null)
    val activeThread: StateFlow<ChatThread?> = _activeThread.asStateFlow()

    // Dynamically query messages for the active thread
    val activeMessages: StateFlow<List<ChatMessage>> = _activeThread
        .flatMapLatest { thread ->
            if (thread != null) {
                repository.getMessagesForThread(thread.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Input field state
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // Loading/generating state
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Settings details
    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    // Keyword Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ChatMessage>>(emptyList())
    val searchResults: StateFlow<List<ChatMessage>> = _searchResults.asStateFlow()

    init {
        // Initialize User Profile first, then setup thread Fallbacks
        viewModelScope.launch {
            val defaultUser = repository.getOrCreateDefaultProfile()
            _activeUserProfile.value = defaultUser
            _userName.value = defaultUser.username

            // Auto-select or create first thread on launch when threads change
            allThreads.collect { threads ->
                if (_activeThread.value == null && threads.isNotEmpty()) {
                    _activeThread.value = threads.first()
                } else if (_activeThread.value == null && threads.isEmpty() && _activeUserProfile.value != null) {
                    createNewThread()
                }
            }
        }
    }

    // --- User Profile Actions ---

    fun selectUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.selectUserProfile(profile)
            _activeUserProfile.value = profile
            _userName.value = profile.username
            _activeThread.value = null // Clear thread, triggering autoSelection/creation for the selected user
            _searchQuery.value = ""
            _searchResults.value = emptyList()
        }
    }

    fun createUserProfile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newProfile = UserProfile(username = name, isActive = false)
            repository.addUserProfile(newProfile)
        }
    }

    fun deleteUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.deleteUserProfile(profile)
            val currentActive = repository.getActiveUserProfile() ?: repository.getOrCreateDefaultProfile()
            _activeUserProfile.value = currentActive
            _userName.value = currentActive.username
            _activeThread.value = null
        }
    }

    fun updateActiveUserProfile(name: String) {
        val current = _activeUserProfile.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val updated = current.copy(username = name)
            repository.updateUserProfile(updated)
            _activeUserProfile.value = updated
            _userName.value = name
        }
    }

    fun saveProfilePicture(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val activeUser = _activeUserProfile.value ?: return@launch
                    val fileName = "avatar_user_${activeUser.id}_${System.currentTimeMillis()}.jpg"
                    val file = File(context.filesDir, fileName)
                    
                    val outputStream = FileOutputStream(file)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    
                    val updated = activeUser.copy(profilePicturePath = file.absolutePath)
                    _activeUserProfile.value = updated
                    repository.updateUserProfile(updated)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Chat Intents / Actions ---

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        val activeUser = _activeUserProfile.value
        if (query.isBlank() || activeUser == null) {
            _searchResults.value = emptyList()
        } else {
            viewModelScope.launch {
                _searchResults.value = repository.searchMessagesForUser(activeUser.id, query)
            }
        }
    }

    fun toggleSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun selectThread(thread: ChatThread) {
        _activeThread.value = thread
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun updateThreadConfig(modelName: String, temperature: Float, systemInstruction: String) {
        val current = _activeThread.value ?: return
        val updated = current.copy(
            modelName = modelName,
            temperature = temperature,
            systemInstruction = systemInstruction
        )
        viewModelScope.launch {
            repository.updateThread(updated)
            _activeThread.value = updated
        }
    }

    fun renameThread(thread: ChatThread, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val updated = thread.copy(title = newTitle)
            repository.updateThread(updated)
            if (_activeThread.value?.id == thread.id) {
                _activeThread.value = updated
            }
        }
    }

    fun deleteThread(thread: ChatThread) {
        viewModelScope.launch {
            repository.deleteThread(thread)
            if (_activeThread.value?.id == thread.id) {
                _activeThread.value = null // Will be auto-fallback selected
            }
        }
    }

    fun clearAllChats() {
        val activeUser = _activeUserProfile.value ?: return
        viewModelScope.launch {
            repository.clearAllThreadsForUser(activeUser.id)
            _activeThread.value = null
            createNewThread()
        }
    }

    fun createNewThread(
        title: String = "New Chat Session",
        personality: String = "Assistant"
    ) {
        val instruction = when (personality) {
            "Coding Expert" -> "You are Pranjal Coding Expert, a world-class senior software engineer. Write clean, optimal, well-commented, production-ready code blocks. Always adopt best practices, explain algorithms elegantly, and optimize for performance."
            "Creative Writer" -> "You are Pranjal Creative Muse, a brilliant storyteller, novelist, lyricist, and essayist. Deliver rich, artistic, descriptive, and imaginative writing that engages all senses and inspires the reader."
            "Language Tutor" -> "You are Pranjal Language Tutor. You assist with translations, explain grammatical issues, highlight vocabulary details, correct texts gracefully, and write in clear conversational tones."
            else -> "You are Pranjal AI, an intelligent, helpful, and highly creative AI assistant. Address the user with respect, deliver helpful insights, and write beautiful, fully functional code blocks when needed."
        }

        viewModelScope.launch {
            val activeUser = _activeUserProfile.value ?: return@launch
            val count = allThreads.value.size + 1
            val finalTitle = if (title == "New Chat Session") "Chat Session #$count" else title
            val newThread = ChatThread(
                title = finalTitle,
                systemInstruction = instruction,
                userId = activeUser.id
            )
            val threadId = repository.createThread(newThread)
            val fullThread = newThread.copy(id = threadId)
            _activeThread.value = fullThread
        }
    }

    fun deleteChatMessage(messageId: Int) {
        viewModelScope.launch {
            repository.deleteMessageById(messageId)
        }
    }

    fun sendMessage() {
        val prompt = _inputText.value.trim()
        val currentThread = _activeThread.value ?: return
        if (prompt.isEmpty() || _isGenerating.value) return

        _inputText.value = ""
        _isGenerating.value = true

        viewModelScope.launch {
            // 1. Insert user message in database
            val userMsg = ChatMessage(
                threadId = currentThread.id,
                role = "user",
                text = prompt
            )
            repository.addMessage(userMsg)

            // 2. Fetch thread's previous messages to construct conversational context
            val activeHistory = activeMessages.value

            // 3. Request Gemini API completion
            val aiResponse = repository.generateAiResponse(
                thread = currentThread,
                conversationHistory = activeHistory,
                userPrompt = prompt
            )

            // 4. Insert AI response into the database
            val modelMsg = ChatMessage(
                threadId = currentThread.id,
                role = "model",
                text = aiResponse
            )
            repository.addMessage(modelMsg)

            // 5. Automatic Descriptive Title Generation
            if (currentThread.title.startsWith("Chat Session #") || currentThread.title == "New Chat Session") {
                generateAutoThreadTitle(currentThread, prompt)
            }

            _isGenerating.value = false
        }
    }

    private suspend fun generateAutoThreadTitle(thread: ChatThread, firstPrompt: String) {
        val titlePrompt = "Extract a highly descriptive, concise 3-5 word title summary for a chat that starts with the prompt: \"$firstPrompt\". Return ONLY the title text as simple string, no quotes."
        try {
            val generatedTitle = repository.generateAiResponse(
                thread = thread.copy(systemInstruction = "You summarize topics into short titles. Do not explain, return ONLY the 3-5 word clean title."),
                conversationHistory = emptyList(),
                userPrompt = titlePrompt
            )
            val cleanTitle = generatedTitle
                .replace("\"", "")
                .replace("Title:", "")
                .trim()
            
            if (cleanTitle.isNotBlank() && !cleanTitle.startsWith("Error")) {
                renameThread(thread, cleanTitle)
            }
        } catch (e: Exception) {
            // Silently swallow title generation errors to prevent main chat disruptions
        }
    }

    // --- File Export Services ---

    fun exportActiveThreadToTxt(uri: android.net.Uri, context: Context) {
        val currentThread = _activeThread.value ?: return
        val messages = activeMessages.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write("=========================\n")
                        writer.write("CHAT EXPORT: ${currentThread.title}\n")
                        writer.write("Model: ${currentThread.modelName}\n")
                        writer.write("Temperature: ${currentThread.temperature}\n")
                        writer.write("System Instruction: ${currentThread.systemInstruction}\n")
                        writer.write("Exported at: ${java.util.Date()}\n")
                        writer.write("=========================\n\n")
                        
                        messages.forEach { message ->
                            val sender = if (message.role == "user") "User" else "Pranjal AI"
                            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(message.timestamp))
                            writer.write("[$time] $sender:\n")
                            writer.write("${message.text}\n")
                            writer.write("-------------------------\n")
                        }
                    }
                }
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Exported successfully as TXT", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun exportActiveThreadToJson(uri: android.net.Uri, context: Context) {
        val currentThread = _activeThread.value ?: return
        val messages = activeMessages.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonObject = org.json.JSONObject().apply {
                    put("thread_title", currentThread.title)
                    put("model_name", currentThread.modelName)
                    put("temperature", currentThread.temperature.toDouble())
                    put("system_instruction", currentThread.systemInstruction)
                    put("exported_at", System.currentTimeMillis())
                    
                    val messagesJsonArray = org.json.JSONArray()
                    messages.forEach { msg ->
                        val msgObj = org.json.JSONObject().apply {
                            put("role", msg.role)
                            put("text", msg.text)
                            put("timestamp", msg.timestamp)
                        }
                        messagesJsonArray.put(msgObj)
                    }
                    put("messages", messagesJsonArray)
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(jsonObject.toString(4)) // indented with 4 spaces for elegant formatting!
                    }
                }
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Exported successfully as JSON", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
