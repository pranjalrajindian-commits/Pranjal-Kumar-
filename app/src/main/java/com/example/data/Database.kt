package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val profilePicturePath: String? = null,
    val isActive: Boolean = false
)

@Entity(tableName = "chat_threads")
data class ChatThread(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val systemInstruction: String = "You are Pranjal AI, an intelligent, helpful, and highly creative AI assistant. Address the user with respect, deliver helpful insights, and write beautiful, fully functional code blocks when needed.",
    val modelName: String = "gemini-3.5-flash",
    val temperature: Float = 0.7f,
    val userId: Int = 1 // Linked to UserProfile.id
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThread::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["threadId"])]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val threadId: Int,
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAO Interface ---

@Dao
interface ChatDao {
    // User Profile Queries
    @Query("SELECT * FROM user_profiles ORDER BY id ASC")
    fun getAllUserProfiles(): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveUserProfile(): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getUserProfileById(id: Int): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile): Long

    @Update
    suspend fun updateUserProfile(profile: UserProfile)

    @Query("UPDATE user_profiles SET isActive = 0")
    suspend fun deactivateAllUserProfiles()

    @Delete
    suspend fun deleteUserProfile(profile: UserProfile)

    // Chat Thread Queries
    @Query("SELECT * FROM chat_threads ORDER BY createdAt DESC")
    fun getAllThreads(): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_threads WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllThreadsForUser(userId: Int): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_threads WHERE id = :threadId LIMIT 1")
    suspend fun getThreadById(threadId: Int): ChatThread?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThread): Long

    @Update
    suspend fun updateThread(thread: ChatThread)

    @Delete
    suspend fun deleteThread(thread: ChatThread)

    @Query("DELETE FROM chat_threads")
    suspend fun clearAllThreads()

    @Query("DELETE FROM chat_threads WHERE userId = :userId")
    suspend fun clearAllThreadsForUser(userId: Int)

    // Msg Queries
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Int)

    // Message search feature scoped to searchable query
    @Query("SELECT * FROM chat_messages WHERE text LIKE :query ORDER BY timestamp DESC")
    suspend fun searchMessages(query: String): List<ChatMessage>

    // Message search feature scoped to specific user's threads
    @Query("""
        SELECT msg.* FROM chat_messages msg 
        INNER JOIN chat_threads th ON msg.threadId = th.id 
        WHERE th.userId = :userId AND msg.text LIKE :query 
        ORDER BY msg.timestamp DESC
    """)
    suspend fun searchMessagesForUser(userId: Int, query: String): List<ChatMessage>
}

// --- App Database Class ---

@Database(entities = [UserProfile::class, ChatThread::class, ChatMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pranjal_ai_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
