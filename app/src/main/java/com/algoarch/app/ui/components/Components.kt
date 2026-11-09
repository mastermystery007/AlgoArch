package com.algoarch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoarch.app.ui.theme.AA
import com.algoarch.app.ui.theme.MonoStyle

/** A small rounded pill: "🔥 4", "Pattern spotting", etc. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            color = if (filled) AA.Ink else color,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun CodeBlock(code: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF070A16))
            .border(1.dp, AA.Line, RoundedCornerShape(14.dp))
            .horizontalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text(code, style = MonoStyle, color = AA.Text)
    }
}

/** The big chunky action button used at the bottom of every challenge. */
@Composable
fun BigButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = AA.Ink,
            disabledContainerColor = AA.PanelHigh,
            disabledContentColor = AA.Muted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AA.Panel)
            .border(1.dp, AA.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(value, style = MaterialTheme.typography.titleLarge, color = AA.Text)
        Text(label, style = MaterialTheme.typography.bodySmall, color = AA.Muted)
    }
}

@Composable
fun ProgressTrack(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 10) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(AA.PanelHigh)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = AA.Muted,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun StarsRow(stars: Int, max: Int = 3, size: Int = 16, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(max) { i ->
            Text(
                "★",
                color = if (i < stars) AA.Arch else AA.Line,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = size.sp),
            )
            if (i < max - 1) Spacer(Modifier.width(2.dp))
        }
    }
}
