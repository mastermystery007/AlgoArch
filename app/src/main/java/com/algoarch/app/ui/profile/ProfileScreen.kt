package com.algoarch.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoarch.app.AlgoArchApp
import com.algoarch.app.game.Achievement
import com.algoarch.app.game.Rules
import com.algoarch.app.today
import com.algoarch.app.ui.components.ProgressTrack
import com.algoarch.app.ui.components.SectionLabel
import com.algoarch.app.ui.components.StatTile
import com.algoarch.app.ui.theme.AA
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(app: AlgoArchApp, modifier: Modifier = Modifier) {
    val stored by app.progress.progress.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }

    val p = stored
    if (p == null) {
        Box(modifier.fillMaxSize().background(AA.Ink), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AA.Algo)
        }
        return
    }
    val level = Rules.playerLevel(p.xp)
    val totalLevels = app.content.allLevels.size
    val cleared = p.levelStars.count { it.value > 0 }
    val perfect = p.levelStars.count { it.value >= 3 }
    val accuracy = if (p.answered == 0) "—" else "${(100.0 * p.correct / p.answered).roundToInt()}%"

    Column(
        modifier
            .fillMaxSize()
            .background(AA.Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AA.Xp.copy(alpha = 0.18f))
                    .border(3.dp, AA.Xp, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("🧑‍💻", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Level ${level.level} Engineer", color = AA.Text, style = MaterialTheme.typography.titleLarge)
                Text("${p.xp} XP total", color = AA.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(6.dp))
                ProgressTrack(level.fraction, AA.Xp)
            }
        }

        SectionLabel("Stats")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("🔥", "${Rules.displayStreak(p, today())}", "Day streak", Modifier.weight(1f))
            StatTile("📅", "${p.bestStreak}", "Best streak", Modifier.weight(1f))
            StatTile("🎯", accuracy, "Accuracy", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("🗺️", "$cleared/$totalLevels", "Levels", Modifier.weight(1f))
            StatTile("💎", "$perfect", "Perfect", Modifier.weight(1f))
            StatTile("🏗️", "${p.designsShipped}", "Designs", Modifier.weight(1f))
        }

        SectionLabel("Achievements")
        Rules.achievements(p).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { AchievementCard(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        TextButton(onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Reset progress", color = AA.Muted)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("XP, stars, streaks and rematches will be erased. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { app.progress.reset() }
                }) { Text("Reset", color = AA.Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
            containerColor = AA.PanelHigh,
        )
    }
}

@Composable
private fun AchievementCard(a: Achievement, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (a.unlocked) AA.Arch.copy(alpha = 0.12f) else AA.Panel)
            .border(1.dp, if (a.unlocked) AA.Arch.copy(alpha = 0.5f) else AA.Line, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .alpha(if (a.unlocked) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (a.unlocked) a.emoji else "🔒", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(a.title, color = AA.Text, style = MaterialTheme.typography.titleSmall)
            Text(a.description, color = AA.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
