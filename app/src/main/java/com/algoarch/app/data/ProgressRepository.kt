package com.algoarch.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.algoarch.app.game.Rules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReviewItem(
    /** Leitner box: 0 = just missed, higher = remembered for longer. */
    val box: Int = 0,
    val dueAt: Long = 0L,
)

@Serializable
data class PlayerProgress(
    val xp: Int = 0,
    val hearts: Int = Rules.MAX_HEARTS,
    val heartsUpdatedAt: Long = 0L,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Day number (days since epoch, local time) of the last finished session. */
    val lastActiveDay: Long = -1L,
    val levelStars: Map<String, Int> = emptyMap(),
    val review: Map<String, ReviewItem> = emptyMap(),
    val answered: Int = 0,
    val correct: Int = 0,
    val designsShipped: Int = 0,
    val reviewsCleared: Int = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "algoarch")

/** Stores the whole player profile as one JSON document. Small, simple, easy to migrate. */
class ProgressRepository(private val context: Context) {
    private val key = stringPreferencesKey("progress")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val progress: Flow<PlayerProgress> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString(PlayerProgress.serializer(), it) }.getOrNull() }
            ?: PlayerProgress()
    }

    suspend fun current(): PlayerProgress = progress.first()

    suspend fun update(transform: (PlayerProgress) -> PlayerProgress): PlayerProgress {
        var result = PlayerProgress()
        context.dataStore.edit { prefs ->
            val current = prefs[key]
                ?.let { runCatching { json.decodeFromString(PlayerProgress.serializer(), it) }.getOrNull() }
                ?: PlayerProgress()
            result = transform(current)
            prefs[key] = json.encodeToString(PlayerProgress.serializer(), result)
        }
        return result
    }

    suspend fun reset() {
        context.dataStore.edit { it.remove(key) }
    }
}
