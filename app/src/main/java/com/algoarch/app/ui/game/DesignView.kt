package com.algoarch.app.ui.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algoarch.app.data.DesignChallenge
import com.algoarch.app.data.Scenario
import com.algoarch.app.game.Architecture
import com.algoarch.app.game.ComponentType
import com.algoarch.app.game.DesignSimulator
import com.algoarch.app.game.SimulationResult
import com.algoarch.app.game.TierStatus
import com.algoarch.app.ui.components.BigButton
import com.algoarch.app.ui.components.ProgressTrack
import com.algoarch.app.ui.components.SectionLabel
import com.algoarch.app.ui.components.StarsRow
import com.algoarch.app.ui.theme.AA
import kotlin.math.roundToInt

@Composable
fun DesignView(c: DesignChallenge, accent: Color, revealed: Boolean, onSubmit: (Boolean) -> Unit) {
    val scenario = c.scenario
    var arch by remember(c.id) { mutableStateOf(Architecture.fromStart(scenario.start)) }
    var result by remember(c.id) { mutableStateOf<SimulationResult?>(null) }
    var simulatedArch by remember(c.id) { mutableStateOf<Architecture?>(null) }
    val stale = result != null && simulatedArch != arch

    ChallengeScaffold(
        revealed = revealed,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        result = DesignSimulator.run(scenario, arch)
                        simulatedArch = arch
                    },
                    enabled = !revealed,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                ) {
                    Text("▶  Simulate", fontWeight = FontWeight.ExtraBold)
                }
                BigButton(
                    "Ship it 🚀",
                    accent,
                    enabled = result != null && !stale && !revealed,
                    modifier = Modifier.weight(1f),
                ) {
                    onSubmit(result?.passed == true)
                }
            }
        },
    ) {
        PromptHeader("System design", c.prompt, accent)
        ScenarioCard(scenario, accent)

        SectionLabel("Your architecture")
        ComponentType.entries.forEach { type ->
            ComponentRow(
                type = type,
                count = arch.count(type),
                accent = accent,
                enabled = !revealed,
                onChange = { arch = arch.with(type, it) },
            )
        }

        val cost = arch.cost
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Monthly cost", color = AA.Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "$$cost / $${scenario.budget}",
                color = if (cost > scenario.budget) AA.Bad else AA.Text,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        ProgressTrack(cost.toFloat() / scenario.budget, if (cost > scenario.budget) AA.Bad else accent)

        result?.let { r ->
            SectionLabel(if (stale) "Last simulation (changed since, run it again)" else "Traffic simulation")
            SimulationPanel(r, scenario, dimmed = stale)
        } ?: Text(
            "Add components, then tap Simulate to send traffic through your design. Simulating is free; shipping a broken design costs a heart.",
            color = AA.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ScenarioCard(s: Scenario, accent: Color) {
    val peak = (s.baseRps * s.peakMultiplier).roundToInt()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("📋 ${s.name}", color = accent, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spec("Peak traffic", "%,d/s".format(peak), Modifier.weight(1f))
            Spec("Reads", "${(s.readRatio * 100).roundToInt()}%", Modifier.weight(1f))
            Spec(
                "Static",
                if (s.staticRatio > 0) "${(s.staticRatio * 100).roundToInt()}%" else "—",
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spec("Budget", "$${s.budget}/mo", Modifier.weight(1f))
            Spec("Latency", "≤ ${s.maxLatencyMs} ms", Modifier.weight(1f))
            Spec("Spike", if (s.peakMultiplier > 1.0) "${s.peakMultiplier.formatX()}×" else "none", Modifier.weight(1f))
        }
    }
}

private fun Double.formatX(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

@Composable
private fun Spec(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = AA.Muted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = AA.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ComponentRow(
    type: ComponentType,
    count: Int,
    accent: Color,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val active = count > 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) AA.PanelHigh else AA.Panel)
            .border(1.dp, if (active) accent.copy(alpha = 0.5f) else AA.Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(type.emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(type.label, color = AA.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("$${type.cost}", color = AA.Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text(type.blurb, color = AA.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Stepper(
            count = count,
            min = type.min,
            max = type.max,
            accent = accent,
            enabled = enabled,
            onChange = onChange,
        )
    }
}

@Composable
private fun Stepper(count: Int, min: Int, max: Int, accent: Color, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled && count > min, accent) { onChange(count - 1) }
        Text(
            "$count",
            modifier = Modifier.widthIn(min = 28.dp),
            color = if (count > 0) AA.Text else AA.Muted,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton("+", enabled && count < max, accent) { onChange(count + 1) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) accent.copy(alpha = 0.18f) else AA.Panel)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = if (enabled) accent else AA.Line, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SimulationPanel(r: SimulationResult, scenario: Scenario, dimmed: Boolean) {
    val transition = rememberInfiniteTransition(label = "traffic")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val alpha = if (dimmed) 0.45f else 1f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF070A16))
            .border(1.dp, AA.Line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("👥  Users", color = AA.Muted.copy(alpha = alpha), style = MaterialTheme.typography.labelLarge)
        r.tiers.forEach { tier ->
            TierRow(tier, phase, alpha)
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(
                "Latency",
                if (r.latencyMs >= 999) "timeouts" else "${r.latencyMs} ms",
                ok = r.latencyMs <= scenario.maxLatencyMs,
                modifier = Modifier.weight(1f),
            )
            Metric("Cost", "$${r.cost}/mo", ok = r.cost <= scenario.budget, modifier = Modifier.weight(1f))
        }

        r.issues.forEach { issue ->
            Text("⚠️  $issue", color = AA.Bad.copy(alpha = alpha), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (r.passed) "✅ Holds up under load" else "❌ Not ready to ship",
                color = (if (r.passed) AA.Good else AA.Bad).copy(alpha = alpha),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (r.passed) StarsRow(r.stars, size = 20)
        }
        if (r.passed && r.stars < 3) {
            Text(
                "For 3★, keep every tier under 80% load and spend at most 90% of the budget.",
                color = AA.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TierRow(tier: TierStatus, phase: Float, alpha: Float) {
    val u = tier.utilization
    val color = when {
        tier.unlimited -> AA.Algo
        u > 1.0 -> AA.Bad
        u > 0.8 -> AA.Warn
        else -> AA.Good
    }.copy(alpha = alpha)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${tier.emoji}  ${tier.name}", color = AA.Text.copy(alpha = alpha), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                if (tier.unlimited) "%,d/s".format(tier.load.roundToInt())
                else "%,d / %,d  (%d%%)".format(tier.load.roundToInt(), tier.capacity.roundToInt(), (u * 100).roundToInt()),
                color = color,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(AA.PanelHigh, cornerRadius = radius)
            val fill = if (tier.unlimited) 1f else u.coerceAtMost(1.0).toFloat()
            drawRoundRect(color.copy(alpha = 0.35f * alpha), size = Size(size.width * fill, size.height), cornerRadius = radius)
            // Moving dots: more traffic, more dots. Overloaded tiers pile up red at the end.
            val dots = (tier.load / 600.0).roundToInt().coerceIn(2, 16)
            val travel = if (tier.unlimited) 1f else fill
            for (k in 0 until dots) {
                val t = ((k.toFloat() / dots) + phase) % 1f
                val x = t * size.width * travel
                drawCircle(color, radius = size.height / 4, center = Offset(x, size.height / 2))
            }
            if (u > 1.0) {
                drawRoundRect(
                    AA.Bad.copy(alpha = (0.25f + 0.25f * phase) * alpha),
                    topLeft = Offset(size.width * 0.85f, 0f),
                    size = Size(size.width * 0.15f, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, ok: Boolean, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AA.Panel)
            .padding(10.dp),
    ) {
        Text(label, color = AA.Muted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = if (ok) AA.Good else AA.Bad, style = MaterialTheme.typography.titleMedium)
    }
}
