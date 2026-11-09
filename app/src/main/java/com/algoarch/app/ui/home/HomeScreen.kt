package com.algoarch.app.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoarch.app.AlgoArchApp
import com.algoarch.app.data.DesignChallenge
import com.algoarch.app.data.Level
import com.algoarch.app.data.PlayerProgress
import com.algoarch.app.data.Region
import com.algoarch.app.data.Track
import com.algoarch.app.game.Rules
import com.algoarch.app.today
import com.algoarch.app.ui.components.ProgressTrack
import com.algoarch.app.ui.components.StarsRow
import com.algoarch.app.ui.theme.AA
import com.algoarch.app.ui.theme.accent

private enum class NodeState { Locked, Current, Cleared, Open }

@Composable
fun HomeScreen(
    app: AlgoArchApp,
    modifier: Modifier = Modifier,
    onPlay: (String) -> Unit,
    onReview: () -> Unit,
) {
    val stored by app.progress.progress.collectAsStateWithLifecycle(initialValue = null)
    var track by rememberSaveable { mutableStateOf(Track.ALGO) }

    val p0 = stored
    if (p0 == null) {
        Box(modifier.fillMaxSize().background(AA.Ink), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AA.Algo)
        }
        return
    }
    val now = remember(p0) { System.currentTimeMillis() }
    val p = Rules.regenHearts(p0, now)
    val allRegions = app.content.regions
    val regions = allRegions.filter { it.track == track }
    val due = Rules.dueReviewIds(p, now).size
    val accent = track.accent

    // The "current" level is the first unlocked level without stars on this track.
    val currentId = regions.asSequence()
        .flatMap { r -> r.levels.asSequence().map { r to it } }
        .firstOrNull { (r, l) -> Rules.isLevelUnlocked(allRegions, r, l, p) && (p.levelStars[l.id] ?: 0) == 0 }
        ?.second?.id

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(AA.Ink),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item { Header(p) }
        item { TrackSwitcher(track) { track = it } }
        if (due > 0) item { RematchCard(due, onReview) }

        regions.forEach { region ->
            val regionUnlocked = Rules.isRegionUnlocked(allRegions, region, p)
            item(key = region.id) { RegionBanner(region, p, regionUnlocked, accent) }
            itemsIndexed(region.levels, key = { _, l -> l.id }) { i, level ->
                val stars = p.levelStars[level.id] ?: 0
                val state = when {
                    !Rules.isLevelUnlocked(allRegions, region, level, p) -> NodeState.Locked
                    level.id == currentId -> NodeState.Current
                    stars > 0 -> NodeState.Cleared
                    else -> NodeState.Open
                }
                LevelNode(level, i, state, stars, accent) { onPlay(level.id) }
            }
        }
        item {
            Text(
                "More regions coming soon 🛠️",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                color = AA.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Header(p: PlayerProgress) {
    val level = Rules.playerLevel(p.xp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Algo", color = AA.Algo, style = MaterialTheme.typography.headlineMedium)
            Text("Arch", color = AA.Arch, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            StatChip("🔥", "${Rules.displayStreak(p, today())}", AA.Fire)
            Spacer(Modifier.width(8.dp))
            StatChip("❤️", "${p.hearts}", AA.Heart)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lv ${level.level}", color = AA.Xp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            ProgressTrack(level.fraction, AA.Xp, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text("${level.xpIntoLevel}/${level.xpForNext} XP", color = AA.Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StatChip(emoji: String, value: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji)
        Spacer(Modifier.width(4.dp))
        Text(value, color = color, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TrackSwitcher(track: Track, onSelect: (Track) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AA.Panel)
            .padding(4.dp),
    ) {
        listOf(Track.ALGO to "🧩  Algorithms", Track.ARCH to "🏗️  Architecture").forEach { (t, label) ->
            val selected = t == track
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) t.accent else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) AA.Ink else AA.Muted,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun RematchCard(due: Int, onReview: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AA.Xp.copy(alpha = 0.14f))
            .border(1.dp, AA.Xp.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable(onClick = onReview)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🧠", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$due rematch${if (due == 1) "" else "es"} waiting", color = AA.Text, style = MaterialTheme.typography.titleMedium)
            Text("Retry what you missed. Free to play, and it restores a heart.", color = AA.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("▶", color = AA.Xp, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun RegionBanner(region: Region, p: PlayerProgress, unlocked: Boolean, accent: Color) {
    val cleared = region.levels.count { (p.levelStars[it.id] ?: 0) > 0 }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (unlocked) accent.copy(alpha = 0.10f) else AA.Panel)
            .border(1.dp, if (unlocked) accent.copy(alpha = 0.45f) else AA.Line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(region.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(region.title, color = if (unlocked) AA.Text else AA.Muted, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (unlocked) "$cleared/${region.levels.size} levels cleared" else "Clear the previous region to unlock",
                    color = if (unlocked) accent else AA.Muted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (!unlocked) Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = AA.Muted)
        }
        Text(region.description, color = AA.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Levels zig-zag down the screen like a path on a map. */
private val pathOffsets = listOf(0, 56, 84, 56, 0, -56, -84, -56)

@Composable
private fun LevelNode(level: Level, index: Int, state: NodeState, stars: Int, accent: Color, onClick: () -> Unit) {
    val isBoss = level.title.startsWith("Boss")
    val hasDesign = level.challenges.any { it is DesignChallenge }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (state == NodeState.Current) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )
    val color = when (state) {
        NodeState.Locked -> AA.PanelHigh
        NodeState.Cleared -> accent
        NodeState.Current -> accent
        NodeState.Open -> accent.copy(alpha = 0.6f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.offset(x = pathOffsets[index % pathOffsets.size].dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state == NodeState.Current) {
                Text(
                    "START",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AA.Text)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = AA.Ink,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(6.dp))
            }
            Box(
                Modifier
                    .scale(scale)
                    .size(if (isBoss) 92.dp else 76.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (state == NodeState.Locked) 1f else 0.18f))
                    .border(4.dp, color, CircleShape)
                    .clickable(enabled = state != NodeState.Locked, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state == NodeState.Locked -> Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = AA.Muted)
                    isBoss -> Text("👑", style = MaterialTheme.typography.headlineMedium)
                    hasDesign -> Text("🏗️", style = MaterialTheme.typography.headlineSmall)
                    else -> Text("${index + 1}", color = AA.Text, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                level.title,
                color = if (state == NodeState.Locked) AA.Muted else AA.Text,
                style = MaterialTheme.typography.labelLarge,
            )
            if (stars > 0) StarsRow(stars, size = 14)
        }
    }
}
