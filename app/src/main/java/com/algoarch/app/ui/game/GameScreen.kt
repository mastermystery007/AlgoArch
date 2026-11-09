package com.algoarch.app.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.algoarch.app.AlgoArchApp
import com.algoarch.app.data.ChoiceChallenge
import com.algoarch.app.data.DesignChallenge
import com.algoarch.app.data.OrderChallenge
import com.algoarch.app.data.TraceChallenge
import com.algoarch.app.ui.components.BigButton
import com.algoarch.app.ui.components.ProgressTrack
import com.algoarch.app.ui.components.StarsRow
import com.algoarch.app.ui.theme.AA
import com.algoarch.app.ui.theme.accent
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    app: AlgoArchApp,
    levelId: String?,
    onExit: () -> Unit,
    onReview: () -> Unit,
) {
    val vm: GameViewModel = viewModel(
        key = levelId ?: "review",
        factory = GameViewModel.factory(app, levelId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val accent = state.track.accent

    Box(
        Modifier
            .fillMaxSize()
            .background(AA.Ink)
            .safeDrawingPadding()
    ) {
        when (val phase = state.phase) {
            Phase.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = accent)
            Phase.NothingToReview -> MessageScreen(
                emoji = "✨",
                title = "No rematches due",
                body = "Questions you miss come back here on a schedule, so they stick. Nothing is due right now.",
                button = "Back to the map",
                color = accent,
                onClick = onExit,
            )
            Phase.OutOfHearts -> OutOfHeartsScreen(state, accent, onExit, onReview)
            is Phase.Finished -> ResultScreen(state, phase, accent, onExit)
            Phase.Playing, is Phase.Feedback -> {
                Column(Modifier.fillMaxSize()) {
                    SessionTopBar(state, accent, onExit)
                    val challenge = state.current
                    val revealed = phase is Phase.Feedback
                    Box(Modifier.weight(1f)) {
                        when (challenge) {
                            is ChoiceChallenge -> ChoiceView(challenge, accent, revealed, vm::submit)
                            is OrderChallenge -> OrderView(challenge, accent, revealed, vm::submit)
                            is TraceChallenge -> TraceView(challenge, accent, revealed, vm::submit)
                            is DesignChallenge -> DesignView(challenge, accent, revealed, vm::submit)
                            null -> Unit
                        }
                        FeedbackOverlay(
                            feedback = phase as? Phase.Feedback,
                            explanation = challenge?.explanation.orEmpty(),
                            isReview = state.isReview,
                            onContinue = vm::next,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTopBar(state: GameUiState, accent: androidx.compose.ui.graphics.Color, onExit: () -> Unit) {
    val progress by animateFloatAsState(state.progress, tween(400), label = "progress")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Filled.Close, contentDescription = "Quit", tint = AA.Muted)
        }
        ProgressTrack(progress, accent, Modifier.weight(1f), height = 14)
        Spacer(Modifier.width(12.dp))
        if (state.isReview) {
            Text("🧠 Practice", color = AA.Xp, style = MaterialTheme.typography.labelLarge)
        } else {
            HeartCounter(state.hearts)
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun HeartCounter(hearts: Int) {
    var bump by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf(hearts) }
    LaunchedEffect(hearts) {
        if (hearts < last) {
            bump = true
            delay(250)
            bump = false
        }
        last = hearts
    }
    val scale by animateFloatAsState(if (bump) 1.5f else 1f, spring(), label = "heart")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("❤️", modifier = Modifier.scale(scale))
        Spacer(Modifier.width(4.dp))
        Text("$hearts", color = AA.Heart, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

/** Slides the feedback panel in; keeps the last feedback visible while it slides out. */
@Composable
private fun FeedbackOverlay(
    feedback: Phase.Feedback?,
    explanation: String,
    isReview: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var last by remember { mutableStateOf<Pair<Phase.Feedback, String>?>(null) }
    if (feedback != null) last = feedback to explanation
    AnimatedVisibility(
        visible = feedback != null,
        modifier = modifier,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        last?.let { (fb, text) ->
            FeedbackPanel(
                correct = fb.correct,
                explanation = text,
                lostHeart = !fb.correct && !isReview,
                onContinue = onContinue,
            )
        }
    }
}

@Composable
private fun FeedbackPanel(correct: Boolean, explanation: String, lostHeart: Boolean, onContinue: () -> Unit) {
    val color = if (correct) AA.Good else AA.Bad
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(AA.PanelHigh)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (correct) "✅" else "❌", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Text(
                if (correct) listOf("Nice!", "Clean.", "Nailed it!", "Ship it!").random() else "Not quite",
                color = color,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.weight(1f))
            if (lostHeart) Text("−1 ❤️", color = AA.Heart, style = MaterialTheme.typography.titleMedium)
            if (correct) Text("+XP", color = AA.Xp, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            explanation,
            color = AA.Text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState()),
        )
        BigButton("Continue", color, onClick = onContinue)
    }
}

@Composable
private fun ResultScreen(state: GameUiState, phase: Phase.Finished, accent: androidx.compose.ui.graphics.Color, onExit: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) 1f else 0.4f, spring(dampingRatio = 0.45f), label = "pop")
    val total = state.challenges.size
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (state.isReview) "🧠" else if (phase.stars == 3) "🏆" else "🎉",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.scale(pop),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (state.isReview) "Rematch complete" else "Level cleared!",
            style = MaterialTheme.typography.headlineMedium,
            color = AA.Text,
        )
        Text(state.title, color = AA.Muted, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        if (!state.isReview) StarsRow(phase.stars, size = 44, modifier = Modifier.scale(pop))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultStat("⚡", "+${phase.xpEarned}", "XP earned", AA.Xp, Modifier.weight(1f))
            ResultStat("🎯", "${state.correctCount}/$total", "Accuracy", accent, Modifier.weight(1f))
        }
        if (phase.heartRestored) {
            Spacer(Modifier.height(12.dp))
            Text("+1 ❤️ restored for practicing", color = AA.Heart, style = MaterialTheme.typography.titleSmall)
        }
        if (!state.isReview && state.mistakes > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Missed questions will come back as rematches.",
                color = AA.Muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        BigButton("Continue", accent, onClick = onExit)
    }
}

@Composable
private fun ResultStat(emoji: String, value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AA.Panel)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Text(value, color = color, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = AA.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OutOfHeartsScreen(
    state: GameUiState,
    accent: androidx.compose.ui.graphics.Color,
    onExit: () -> Unit,
    onReview: () -> Unit,
) {
    val minutes = state.nextHeartInMs?.let { (it / 60_000L).coerceAtLeast(1) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("💔", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text("Out of hearts", style = MaterialTheme.typography.headlineMedium, color = AA.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("Hearts refill over time")
                if (minutes != null) append(" (next one in about $minutes min)")
                append(". Practicing rematches is free and restores a heart.")
            },
            color = AA.Muted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))
        if (state.reviewsDue > 0) {
            BigButton("Practice ${state.reviewsDue} rematches", AA.Xp, onClick = onReview)
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = onExit) { Text("Back to the map", color = accent) }
    }
}

@Composable
private fun MessageScreen(
    emoji: String,
    title: String,
    body: String,
    button: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = AA.Text)
        Spacer(Modifier.height(8.dp))
        Text(body, color = AA.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        BigButton(button, color, onClick = onClick)
    }
}
