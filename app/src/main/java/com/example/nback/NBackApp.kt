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
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        SessionContent(state, session::start, session::match, session::home, session.settings, session::selectLevel, session::practice, session::nextExample, session::retrySave)
    }
}

@Composable
internal fun SessionContent(
    state: SessionState, onStart: () -> Unit, onMatch: () -> Unit, onHome: () -> Unit,
    settings: SettingsState = SettingsState(loading = false), onSelect: (Int) -> Unit = {},
    onPractice: () -> Unit = {}, onNext: (Long) -> Unit = {}, onRetry: () -> Unit = {},
) {
    Scaffold(containerColor = Paper) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp, vertical = 16.dp)) {
            when (state.screen) {
                SessionScreen.HOME -> Instructions(settings, onSelect, onStart, onPractice, onRetry)
                SessionScreen.PLAYING -> Playing(state, onMatch, onHome)
                SessionScreen.INTERRUPTED -> Interrupted(state, onStart, onHome)
                SessionScreen.RESULTS -> Results(state, onStart, onHome)
                SessionScreen.PRACTICE_FEEDBACK, SessionScreen.PRACTICE_COMPLETE -> key(state.feedback?.token) { PracticeExplanation(state, onNext, onPractice, onStart, onHome) }
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
private fun Instructions(settings: SettingsState, onSelect: (Int) -> Unit, onStart: () -> Unit, onPractice: () -> Unit, onRetry: () -> Unit) {
    val level = settings.level
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.session_eyebrow, level), style = MaterialTheme.typography.labelLarge, color = Muted)
        PageTitle(stringResource(R.string.welcome_title))
        DifficultySettings(settings, onSelect, onRetry)
        Text(stringResource(when (level) { 1 -> R.string.instructions_1; 3 -> R.string.instructions_3; else -> R.string.instructions }), style = MaterialTheme.typography.bodyLarge)
        Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(pluralStringResource(R.plurals.example_heading, level, level), fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((index, cell) in ((0 until level).map { it * 4 } + 0).withIndex()) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PositionGrid(cell, Modifier.fillMaxWidth().height(76.dp), stringResource(R.string.example_grid, index + 1))
                            Text(if (index == level) "A" else ('A' + index).toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(stringResource(when (level) { 1 -> R.string.example_1; 3 -> R.string.example_3; else -> R.string.example_explanation }), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(pluralStringResource(R.plurals.session_details, level, level, (level + 20) * 3), style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(stringResource(R.string.visual_requirement), style = MaterialTheme.typography.bodyMedium, color = Muted)
        ActionButton(stringResource(R.string.start), onStart, enabled = !settings.loading, tag = "start")
        Text(stringResource(R.string.practice_description), style = MaterialTheme.typography.bodyMedium, color = Muted)
        ActionButton(stringResource(R.string.guided_practice), onPractice, enabled = !settings.loading, tag = "practice")
    }
}

@Composable
private fun Playing(state: SessionState, onMatch: () -> Unit, onHome: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                PositionGrid(state.highlightedCell, Modifier.weight(1f).fillMaxSize())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayControls(state, onMatch, onHome)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Progress(state)
                PositionGrid(state.highlightedCell, Modifier.weight(1f).fillMaxWidth())
                ResponseControls(state, onMatch)
                if (state.config.practice) SkipButton(onHome)
            }
        }
    }
}

@Composable
private fun PlayControls(state: SessionState, onMatch: () -> Unit, onHome: () -> Unit) {
    Progress(state)
    ResponseControls(state, onMatch)
    if (state.config.practice) SkipButton(onHome)
}

@Composable
private fun Progress(state: SessionState) {
    Text(stringResource(if (state.config.practice) R.string.practice_eyebrow else R.string.session_eyebrow, state.config.level), color = Muted, style = MaterialTheme.typography.labelLarge)
    Text(stringResource(if (state.isWarmUp) R.string.warmup_progress else if (state.config.practice) R.string.practice_progress else R.string.scored_progress, state.progress, state.config.level),
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("progress"))
}

@Composable
private fun ResponseControls(state: SessionState, onMatch: () -> Unit) {
    val messages = listOf(stringResource(R.string.watch_positions),
        stringResource(R.string.response_recorded), pluralStringResource(R.plurals.match_prompt, state.config.level, state.config.level))
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
private fun Interrupted(state: SessionState, onStart: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle(stringResource(if (state.config.practice) R.string.practice_interrupted else R.string.interrupted))
        Text(stringResource(R.string.session_eyebrow, state.config.level), color = Muted)
        Text(stringResource(R.string.interrupted_detail), style = MaterialTheme.typography.bodyLarge)
        ActionButton(stringResource(if (state.config.practice) R.string.restart_practice else R.string.restart), onStart, tag = "restart")
        HomeButton(onHome)
    }
}

@Composable
private fun Results(state: SessionState, onStart: () -> Unit, onHome: () -> Unit) {
    val result = requireNotNull(state.result)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.session_complete), color = Muted, style = MaterialTheme.typography.labelLarge)
        PageTitle(stringResource(R.string.results))
        Text(stringResource(R.string.session_eyebrow, state.config.level), color = Muted)
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

@Composable
private fun DifficultySettings(settings: SettingsState, onSelect: (Int) -> Unit, onRetry: () -> Unit) {
    val label = stringResource(R.string.difficulty)
    Column(Modifier.selectableGroup().semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.difficulty), fontWeight = FontWeight.SemiBold)
        for (level in 1..3) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("level_$level")
                .selectable(selected = level == settings.level, enabled = !settings.loading, role = Role.RadioButton, onClick = { onSelect(level) }),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = level == settings.level, onClick = null, enabled = !settings.loading)
                Text(stringResource(R.string.level_option, level), Modifier.padding(start = 12.dp))
            }
        }
        if (settings.loading) Text(stringResource(R.string.settings_loading), Modifier.testTag("settings_loading"))
        if (settings.saving) Text(stringResource(R.string.settings_saving), Modifier.testTag("settings_saving"))
        settings.notice?.let { notice ->
            Text(stringResource(when (notice) {
                SettingsNotice.RESET -> R.string.settings_reset
                SettingsNotice.LOAD_FAILED -> R.string.settings_load_failed
                SettingsNotice.SAVE_FAILED -> R.string.settings_save_failed
            }), Modifier.testTag("settings_notice").semantics { liveRegion = LiveRegionMode.Polite })
            if (notice == SettingsNotice.SAVE_FAILED) ActionButton(stringResource(R.string.retry), onRetry, tag = "retry")
        }
    }
}

@Composable
private fun SkipButton(onHome: () -> Unit) {
    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("skip")) {
        Text(stringResource(R.string.skip_practice))
    }
}

@Composable
private fun PracticeExplanation(state: SessionState, onNext: (Long) -> Unit, onPractice: () -> Unit, onStart: () -> Unit, onHome: () -> Unit) {
    val feedback = requireNotNull(state.feedback)
    val complete = state.screen == SessionScreen.PRACTICE_COMPLETE
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.practice_eyebrow, state.config.level), color = Muted)
        PageTitle(stringResource(if (complete) R.string.practice_complete else R.string.practice_feedback))
        Text(stringResource(R.string.practice_progress, state.progress))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.this_turn))
                PositionGrid(feedback.currentCell, Modifier.fillMaxWidth().height(120.dp), stringResource(R.string.this_turn))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.reference_turn, state.config.level, state.config.level))
                PositionGrid(feedback.referenceCell, Modifier.fillMaxWidth().height(120.dp), pluralStringResource(R.plurals.reference_turn, state.config.level, state.config.level))
            }
        }
        Text(stringResource(if (feedback.matches) R.string.positions_match else R.string.positions_differ),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("feedback_answer").semantics { liveRegion = LiveRegionMode.Polite })
        Text(stringResource(if (feedback.responded) R.string.you_tapped else R.string.you_waited))
        Text(stringResource(if (feedback.matches) R.string.expected_match else R.string.expected_wait))
        if (complete) {
            ActionButton(stringResource(R.string.practice_again), onPractice, tag = "practice_again")
            ActionButton(stringResource(R.string.start), onStart, tag = "start")
            HomeButton(onHome)
        } else {
            ActionButton(stringResource(R.string.next_example), { onNext(feedback.token) }, tag = "next")
            SkipButton(onHome)
        }
    }
}
