package com.algoarch.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.algoarch.app.AlgoArchApp
import com.algoarch.app.data.Challenge
import com.algoarch.app.data.DesignChallenge
import com.algoarch.app.data.Track
import com.algoarch.app.game.Rules
import com.algoarch.app.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Phase {
    data object Loading : Phase
    data object Playing : Phase
    data class Feedback(val correct: Boolean) : Phase
    data class Finished(val stars: Int, val xpEarned: Int, val heartRestored: Boolean) : Phase
    data object OutOfHearts : Phase
    data object NothingToReview : Phase
}

data class GameUiState(
    val title: String = "",
    val track: Track = Track.ALGO,
    val challenges: List<Challenge> = emptyList(),
    val index: Int = 0,
    val phase: Phase = Phase.Loading,
    val hearts: Int = Rules.MAX_HEARTS,
    val mistakes: Int = 0,
    val correctCount: Int = 0,
    val xpEarned: Int = 0,
    val isReview: Boolean = false,
    val nextHeartInMs: Long? = null,
    val reviewsDue: Int = 0,
) {
    val current: Challenge? get() = challenges.getOrNull(index)
    val progress: Float
        get() = if (challenges.isEmpty()) 0f else {
            val done = index + if (phase is Phase.Feedback || phase is Phase.Finished) 1 else 0
            done.toFloat() / challenges.size
        }
}

/**
 * Runs one play session: a level, or a review ("rematch") session when [levelId] is null.
 * Rematches never cost hearts, and finishing one restores a heart.
 */
class GameViewModel(private val app: AlgoArchApp, private val levelId: String?) : ViewModel() {

    private val _state = MutableStateFlow(GameUiState(isReview = levelId == null))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val now = System.currentTimeMillis()
        val p = app.progress.update { Rules.regenHearts(it, now) }
        val due = Rules.dueReviewIds(p, now)

        if (levelId == null) {
            val challenges = due.take(Rules.REVIEW_SESSION_SIZE).mapNotNull { app.content.challenge(it) }
            val track = challenges.firstOrNull()?.let { app.content.trackOf(it.id) } ?: Track.ALGO
            _state.value = GameUiState(
                title = "Rematch",
                track = track,
                challenges = challenges,
                hearts = p.hearts,
                isReview = true,
                phase = if (challenges.isEmpty()) Phase.NothingToReview else Phase.Playing,
            )
            return
        }

        val (region, level) = app.content.level(levelId) ?: run {
            _state.update { it.copy(phase = Phase.NothingToReview) }
            return
        }
        _state.value = GameUiState(
            title = level.title,
            track = region.track,
            challenges = level.challenges,
            hearts = p.hearts,
            isReview = false,
            nextHeartInMs = Rules.nextHeartIn(p, now),
            reviewsDue = due.size,
            phase = if (p.hearts <= 0) Phase.OutOfHearts else Phase.Playing,
        )
    }

    fun submit(correct: Boolean) {
        val s = _state.value
        val challenge = s.current ?: return
        if (s.phase != Phase.Playing) return

        val xpGain = when {
            !correct -> 0
            s.isReview -> Rules.XP_REVIEW_CORRECT
            else -> Rules.XP_PER_CORRECT
        }
        _state.update {
            it.copy(
                phase = Phase.Feedback(correct),
                mistakes = it.mistakes + if (correct) 0 else 1,
                correctCount = it.correctCount + if (correct) 1 else 0,
                xpEarned = it.xpEarned + xpGain,
                hearts = if (!correct && !it.isReview) (it.hearts - 1).coerceAtLeast(0) else it.hearts,
            )
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = app.progress.update { p ->
                var next = Rules.recordAnswer(p, challenge.id, correct, now)
                next = next.copy(xp = next.xp + xpGain)
                if (!correct && !s.isReview) next = Rules.loseHeart(next, now)
                if (correct && challenge is DesignChallenge) next = next.copy(designsShipped = next.designsShipped + 1)
                next
            }
            _state.update {
                it.copy(
                    hearts = updated.hearts,
                    nextHeartInMs = Rules.nextHeartIn(updated, now),
                    reviewsDue = Rules.dueReviewIds(updated, now).size,
                )
            }
        }
    }

    fun next() {
        val s = _state.value
        if (s.phase !is Phase.Feedback) return
        when {
            !s.isReview && s.hearts <= 0 -> _state.update { it.copy(phase = Phase.OutOfHearts) }
            s.index + 1 < s.challenges.size -> _state.update { it.copy(index = it.index + 1, phase = Phase.Playing) }
            else -> finish()
        }
    }

    private fun finish() {
        val s = _state.value
        val stars = Rules.stars(s.mistakes)
        val bonus = if (s.isReview) 0 else Rules.XP_LEVEL_CLEAR + if (s.mistakes == 0) Rules.XP_PERFECT_BONUS else 0
        _state.update {
            it.copy(phase = Phase.Finished(stars, it.xpEarned + bonus, heartRestored = it.isReview), xpEarned = it.xpEarned + bonus)
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            app.progress.update { p ->
                var next = Rules.touchStreak(p, today()).copy(xp = p.xp + bonus)
                if (levelId != null) {
                    val best = maxOf(p.levelStars[levelId] ?: 0, stars)
                    next = next.copy(levelStars = p.levelStars + (levelId to best))
                } else {
                    next = Rules.gainHeart(next, now).copy(reviewsCleared = p.reviewsCleared + 1)
                }
                next
            }
        }
    }

    companion object {
        fun factory(app: AlgoArchApp, levelId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(app, levelId) }
        }
    }
}
