package com.algoarch.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoarch.app.data.ChoiceChallenge
import com.algoarch.app.data.OrderChallenge
import com.algoarch.app.data.TraceChallenge
import com.algoarch.app.ui.components.BigButton
import com.algoarch.app.ui.components.CodeBlock
import com.algoarch.app.ui.components.Pill
import com.algoarch.app.ui.components.SectionLabel
import com.algoarch.app.ui.theme.AA
import com.algoarch.app.ui.theme.MonoStyle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Scrollable challenge body with a pinned action area at the bottom. */
@Composable
fun ChallengeScaffold(
    revealed: Boolean,
    bottom: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                content()
                // Leave room to scroll content out from under the feedback panel.
                Spacer(Modifier.height(if (revealed) 300.dp else 8.dp))
            },
        )
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { bottom() }
    }
}

@Composable
fun PromptHeader(tag: String, prompt: String, color: Color) {
    Pill(tag, color)
    Text(prompt, style = MaterialTheme.typography.titleLarge, color = AA.Text)
}

// ---------------------------------------------------------------------------
// Multiple choice
// ---------------------------------------------------------------------------

private enum class OptionState { Normal, Selected, Correct, Wrong }

@Composable
fun ChoiceView(c: ChoiceChallenge, accent: Color, revealed: Boolean, onSubmit: (Boolean) -> Unit) {
    // Shuffle once per challenge so the right answer is not always in the same slot.
    val order = remember(c.id) { c.options.indices.shuffled(Random(c.id.hashCode())) }
    var selected by remember(c.id) { mutableStateOf<Int?>(null) }

    ChallengeScaffold(
        revealed = revealed,
        bottom = {
            BigButton("Check", accent, enabled = selected != null && !revealed) {
                onSubmit(selected == c.answer)
            }
        },
    ) {
        PromptHeader(c.tag, c.prompt, accent)
        c.code?.let { CodeBlock(it) }
        order.forEachIndexed { slot, i ->
            val state = when {
                revealed && i == c.answer -> OptionState.Correct
                revealed && i == selected -> OptionState.Wrong
                i == selected -> OptionState.Selected
                else -> OptionState.Normal
            }
            OptionCard(
                label = ('A' + slot).toString(),
                text = c.options[i],
                state = state,
                accent = accent,
                onClick = { if (!revealed) selected = i },
            )
        }
    }
}

@Composable
private fun OptionCard(label: String, text: String, state: OptionState, accent: Color, onClick: () -> Unit) {
    val border = when (state) {
        OptionState.Normal -> AA.Line
        OptionState.Selected -> accent
        OptionState.Correct -> AA.Good
        OptionState.Wrong -> AA.Bad
    }
    val bg = if (state == OptionState.Normal) AA.Panel else border.copy(alpha = 0.12f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (state == OptionState.Normal) AA.PanelHigh else border),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (state == OptionState.Normal) AA.Muted else AA.Ink,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = AA.Text, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------------------------------------------------------------------------
// Code ordering (Parsons problems)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderView(c: OrderChallenge, accent: Color, revealed: Boolean, onSubmit: (Boolean) -> Unit) {
    val pool = remember(c.id) { (c.lines + c.distractors).shuffled(Random(c.id.hashCode())) }
    var placed by remember(c.id) { mutableStateOf(listOf<Int>()) }
    val built = placed.map { pool[it] }
    val correct = built == c.lines

    ChallengeScaffold(
        revealed = revealed,
        bottom = {
            BigButton("Check", accent, enabled = placed.size == c.lines.size && !revealed) {
                onSubmit(correct)
            }
        },
    ) {
        PromptHeader("Build the code", c.prompt, accent)
        if (c.distractors.isNotEmpty()) {
            Text(
                "Careful: ${c.distractors.size} line${if (c.distractors.size > 1) "s don't" else " doesn't"} belong.",
                color = AA.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Answer area
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF070A16))
                .border(
                    2.dp,
                    when {
                        revealed && correct -> AA.Good
                        revealed -> AA.Bad
                        else -> AA.Line
                    },
                    RoundedCornerShape(14.dp),
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (placed.isEmpty()) {
                Text("Tap lines below to build your solution.", color = AA.Muted, style = MonoStyle)
            }
            placed.forEachIndexed { n, poolIndex ->
                val wrongLine = revealed && c.lines.getOrNull(n) != pool[poolIndex]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (wrongLine) AA.Bad.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable(enabled = !revealed) { placed = placed.filterIndexed { k, _ -> k != n } }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                ) {
                    Text("${n + 1}".padStart(2), color = AA.Line, style = MonoStyle)
                    Spacer(Modifier.width(10.dp))
                    Text(pool[poolIndex], color = AA.Text, style = MonoStyle)
                }
            }
        }

        // Line bank
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pool.indices.filter { it !in placed }.forEach { i ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AA.PanelHigh)
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable(enabled = !revealed && placed.size < c.lines.size) { placed = placed + i }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(pool[i].trim(), color = AA.Text, style = MonoStyle)
                }
            }
        }

        if (revealed && !correct) {
            SectionLabel("Correct solution")
            CodeBlock(c.lines.joinToString("\n"))
        }
    }
}

// ---------------------------------------------------------------------------
// Algorithm tracing on a graph or tree
// ---------------------------------------------------------------------------

@Composable
fun TraceView(c: TraceChallenge, accent: Color, revealed: Boolean, onSubmit: (Boolean) -> Unit) {
    var taps by remember(c.id) { mutableStateOf(listOf<String>()) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val radiusPx = with(density) { 22.dp.toPx() }
    val padPx = with(density) { 30.dp.toPx() }
    val correct = taps == c.answer

    fun center(id: String, w: Float, h: Float): Offset {
        val n = c.nodes.first { it.id == id }
        return Offset(padPx + n.x * (w - 2 * padPx), padPx + n.y * (h - 2 * padPx))
    }

    ChallengeScaffold(
        revealed = revealed,
        bottom = {
            BigButton("Check", accent, enabled = taps.size == c.answer.size && !revealed) {
                onSubmit(correct)
            }
        },
    ) {
        PromptHeader("Trace it", c.prompt, accent)

        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .clip(RoundedCornerShape(18.dp))
                .background(AA.Panel)
                .border(1.dp, AA.Line, RoundedCornerShape(18.dp))
                .pointerInput(c.id, revealed) {
                    detectTapGestures { pos ->
                        if (revealed || taps.size >= c.answer.size) return@detectTapGestures
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val hit = c.nodes.minByOrNull {
                            val o = center(it.id, w, h)
                            hypot(o.x - pos.x, o.y - pos.y)
                        } ?: return@detectTapGestures
                        val o = center(hit.id, w, h)
                        if (hypot(o.x - pos.x, o.y - pos.y) <= radiusPx * 1.6f && hit.id !in taps) {
                            taps = taps + hit.id
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // Edges
            c.edges.forEach { e ->
                val a = center(e.from, w, h)
                val b = center(e.to, w, h)
                drawLine(AA.Line, a, b, strokeWidth = 4f, cap = StrokeCap.Round)
                if (c.directed) drawArrowHead(a, b, radiusPx, AA.Muted)
                e.weight?.let { weight ->
                    val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                    drawCircle(AA.Ink, 13.dp.toPx(), mid)
                    drawCircle(AA.Arch, 13.dp.toPx(), mid, style = Stroke(2f))
                    val layout = measurer.measure("$weight", TextStyle(color = AA.Arch, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    drawText(layout, topLeft = mid - Offset(layout.size.width / 2f, layout.size.height / 2f))
                }
            }

            // Nodes
            c.nodes.forEach { node ->
                val o = center(node.id, w, h)
                val tapIndex = taps.indexOf(node.id)
                val answerIndex = c.answer.indexOf(node.id)
                val fill = when {
                    revealed && tapIndex >= 0 && tapIndex == answerIndex -> AA.Good
                    revealed && tapIndex >= 0 -> AA.Bad
                    tapIndex >= 0 -> accent
                    else -> AA.PanelHigh
                }
                drawCircle(fill, radiusPx, o)
                drawCircle(if (tapIndex >= 0) fill else AA.Muted, radiusPx, o, style = Stroke(3f))
                val label = measurer.measure(
                    node.id,
                    TextStyle(color = if (tapIndex >= 0) AA.Ink else AA.Text, fontSize = 15.sp, fontWeight = FontWeight.Black),
                )
                drawText(label, topLeft = o - Offset(label.size.width / 2f, label.size.height / 2f))

                // Order badge: the player's tap order, or the correct order once revealed.
                val badge = when {
                    revealed && answerIndex >= 0 -> answerIndex + 1
                    tapIndex >= 0 -> tapIndex + 1
                    else -> null
                }
                if (badge != null) {
                    val bo = o + Offset(radiusPx * 0.8f, -radiusPx * 0.8f)
                    drawCircle(AA.Xp, 10.dp.toPx(), bo)
                    val b = measurer.measure("$badge", TextStyle(color = AA.Ink, fontSize = 11.sp, fontWeight = FontWeight.Black))
                    drawText(b, topLeft = bo - Offset(b.size.width / 2f, b.size.height / 2f))
                }
            }
        }

        // The sequence so far
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (taps.isEmpty()) "Tap the first node…" else taps.joinToString("  →  "),
                color = if (taps.isEmpty()) AA.Muted else AA.Text,
                style = MonoStyle.copy(fontSize = 15.sp),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { taps = taps.dropLast(1) }, enabled = taps.isNotEmpty() && !revealed) {
                Text("Undo", color = accent)
            }
            TextButton(onClick = { taps = emptyList() }, enabled = taps.isNotEmpty() && !revealed) {
                Text("Reset", color = AA.Muted)
            }
        }
        if (revealed && !correct) {
            SectionLabel("Correct order")
            Text(c.answer.joinToString("  →  "), color = AA.Good, style = MonoStyle.copy(fontSize = 15.sp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(AA.Xp))
            Spacer(Modifier.width(6.dp))
            Text("Badges show the visit order", color = AA.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun DrawScope.drawArrowHead(from: Offset, to: Offset, radius: Float, color: Color) {
    val angle = atan2(to.y - from.y, to.x - from.x)
    val tip = Offset(to.x - cos(angle) * (radius + 2f), to.y - sin(angle) * (radius + 2f))
    val len = 16f
    val spread = 0.45f
    val left = Offset(tip.x - cos(angle - spread) * len, tip.y - sin(angle - spread) * len)
    val right = Offset(tip.x - cos(angle + spread) * len, tip.y - sin(angle + spread) * len)
    drawLine(color, tip, left, strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(color, tip, right, strokeWidth = 4f, cap = StrokeCap.Round)
}
