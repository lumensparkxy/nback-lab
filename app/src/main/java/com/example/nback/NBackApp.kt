package com.example.nback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nback.engine.SessionResult
import com.example.nback.engine.SessionScreen
import com.example.nback.engine.SessionState

private val Ink = Color(0xFF173C38)
private val Paper = Color(0xFFF6F5EF)
private val Muted = Color(0xFF465B57)
private val Cell = Color(0xFFE1E6DF)
private val Active = Color(0xFF18675A)

@Composable
internal fun NBackApp(session: SessionViewModel) {
    val state = session.state
    val activity = LocalActivity.current as? MainActivity
    SideEffect { activity?.keepScreenAwake(state.screen == SessionScreen.PLAYING) }
    DisposableEffect(activity) { onDispose { activity?.keepScreenAwake(false) } }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Ink, onPrimary = Color.White, background = Paper, onBackground = Ink,
        surface = Paper, onSurface = Ink, onSurfaceVariant = Muted,
    )) {
        SessionContent(state, session::start, session::match, session::home)
    }
}

@Composable
internal fun SessionContent(state: SessionState, onStart: () -> Unit, onMatch: () -> Unit, onHome: () -> Unit) {
    Scaffold(containerColor = Paper) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp, vertical = 16.dp)) {
            when (state.screen) {
                SessionScreen.HOME -> Instructions(onStart)
                SessionScreen.PLAYING -> Playing(state, onMatch)
                SessionScreen.INTERRUPTED -> Interrupted(onStart, onHome)
                SessionScreen.RESULTS -> Results(requireNotNull(state.result), onStart, onHome)
            }
        }
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun Instructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.session_eyebrow), style = MaterialTheme.typography.labelLarge, color = Muted)
        PageTitle(stringResource(R.string.welcome_title))
        Text(stringResource(R.string.instructions), style = MaterialTheme.typography.bodyLarge)
        Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.example_heading), fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((index, cell) in listOf(0, 4, 0).withIndex()) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PositionGrid(cell, Modifier.fillMaxWidth().height(76.dp), stringResource(R.string.example_grid, index + 1))
                            Text(listOf("A", "B", "A")[index], fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(stringResource(R.string.example_explanation), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.session_details), style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(stringResource(R.string.visual_requirement), style = MaterialTheme.typography.bodyMedium, color = Muted)
        ActionButton(stringResource(R.string.start), onStart, tag = "start")
    }
}

@Composable
private fun Playing(state: SessionState, onMatch: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                PositionGrid(state.highlightedCell, Modifier.weight(1f).fillMaxSize())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayControls(state, onMatch)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Progress(state)
                PositionGrid(state.highlightedCell, Modifier.weight(1f).fillMaxWidth())
                ResponseControls(state, onMatch)
            }
        }
    }
}

@Composable
private fun PlayControls(state: SessionState, onMatch: () -> Unit) {
    Progress(state)
    ResponseControls(state, onMatch)
}

@Composable
private fun Progress(state: SessionState) {
    Text(stringResource(R.string.session_eyebrow), color = Muted, style = MaterialTheme.typography.labelLarge)
    Text(stringResource(if (state.isWarmUp) R.string.warmup_progress else R.string.scored_progress, state.progress),
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("progress"))
}

@Composable
private fun ResponseControls(state: SessionState, onMatch: () -> Unit) {
    val messages = listOf(stringResource(R.string.watch_positions),
        stringResource(R.string.response_recorded), stringResource(R.string.match_prompt))
    // Reserve the largest feedback layout at this width/font size. Changing the
    // message must not resize or recenter the spatial stimulus grid.
    Box {
        messages.forEach { message ->
            Text(message, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.alpha(0f).clearAndSetSemantics {})
        }
        Text(messages[when { state.isWarmUp -> 0; state.responseRecorded -> 1; else -> 2 }],
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("response_status"))
    }
    ActionButton(stringResource(R.string.match), onMatch, state.canRespond, "match")
}

@Composable
internal fun PositionGrid(highlighted: Int?, modifier: Modifier = Modifier, description: String = stringResource(R.string.visual_grid)) {
    // One stable semantic node. Cell changes are intentionally not spoken aloud.
    BoxWithConstraints(modifier.semantics { contentDescription = description }.testTag("grid"), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 420.dp)
        Canvas(Modifier.size(side)) {
            val gap = size.width * 0.035f
            val cellSide = (size.width - gap * 2) / 3
            val radius = CornerRadius(cellSide * 0.14f)
            repeat(9) { index ->
                val offset = Offset((index % 3) * (cellSide + gap), (index / 3) * (cellSide + gap))
                drawRoundRect(if (index == highlighted) Active else Cell, offset, Size(cellSide, cellSide), radius)
                if (index == highlighted) {
                    val inset = 4.dp.toPx()
                    drawRoundRect(Color.White, offset + Offset(inset, inset),
                        Size(cellSide - inset * 2, cellSide - inset * 2), radius, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun Interrupted(onStart: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle(stringResource(R.string.interrupted))
        Text(stringResource(R.string.interrupted_detail), style = MaterialTheme.typography.bodyLarge)
        ActionButton(stringResource(R.string.restart), onStart, tag = "restart")
        HomeButton(onHome)
    }
}

@Composable
private fun Results(result: SessionResult, onStart: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.session_complete), color = Muted, style = MaterialTheme.typography.labelLarge)
        PageTitle(stringResource(R.string.results))
        Text(stringResource(R.string.accuracy, result.accuracy), style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("accuracy"))
        Text(stringResource(R.string.correct, result.correct), style = MaterialTheme.typography.titleLarge)
        Outcome(stringResource(R.string.hits), result.hits, stringResource(R.string.hits_detail))
        Outcome(stringResource(R.string.misses), result.misses, stringResource(R.string.misses_detail))
        Outcome(stringResource(R.string.false_alarms), result.falseAlarms, stringResource(R.string.false_alarms_detail))
        Outcome(stringResource(R.string.correct_rejections), result.correctRejections, stringResource(R.string.correct_rejections_detail))
        Text(stringResource(R.string.accuracy_note), style = MaterialTheme.typography.bodyMedium, color = Muted)
        ActionButton(stringResource(R.string.play_again), onStart, tag = "play_again")
        HomeButton(onHome)
    }
}

@Composable
private fun Outcome(label: String, count: Int, detail: String) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp)
        .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.outcome_count, label, count), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
private fun ActionButton(label: String, action: () -> Unit, enabled: Boolean = true, tag: String) {
    Button(onClick = action, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White,
            disabledContainerColor = Cell, disabledContentColor = Muted)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun HomeButton(onHome: () -> Unit) {
    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home")) {
        Text(stringResource(R.string.home), modifier = Modifier.padding(vertical = 6.dp))
    }
}
