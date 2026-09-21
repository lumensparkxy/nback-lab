package com.example.nback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nback.engine.StimulusType
import com.example.nback.engine.SessionScreen
import com.example.nback.engine.SessionState

private val Ink = Color(0xFF173C38)
private val Paper = Color(0xFFF6F5EF)
private val Muted = Color(0xFF465B57)
private val Cell = Color(0xFFE1E6DF)

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
        val time = historyTime(session.formatRevision)
        if (session.historyNavigation.open) HistoryScreen(session, time)
        else SessionContent(state, session::start, { session.match() }, session::home, session.settings,
            session::selectLevel, session::practice, session::nextExample, session::retrySave,
            session.history.state, session.resultId, time, session::openHistory,
            { session.history.retry(session.resultId) }, { session.history.retry() }, session::toggleType, session::match)
    }
}

@Composable
internal fun SessionContent(
    state: SessionState, onStart: () -> Unit, onMatch: () -> Unit, onHome: () -> Unit,
    settings: SettingsState = SettingsState(loading = false), onSelect: (Int) -> Unit = {},
    onPractice: () -> Unit = {}, onNext: (Long) -> Unit = {}, onRetry: () -> Unit = {},
    history: HistoryState = HistoryState(), resultId: String? = null, time: HistoryTime = HistoryTime(),
    onHistory: () -> Unit = {}, onRetryResult: () -> Unit = {}, onRetryAll: () -> Unit = {},
    onToggleType: (StimulusType) -> Unit = {}, onTypeMatch: (StimulusType) -> Unit = { onMatch() },
) {
    Scaffold(containerColor = Paper) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val sidePadding = if (state.screen == SessionScreen.PLAYING && maxWidth < 360.dp) 8.dp else 24.dp
            Box(Modifier.fillMaxSize().padding(horizontal = sidePadding, vertical = 16.dp)) {
                when (state.screen) {
                    SessionScreen.HOME -> Instructions(settings, onSelect, onStart, onPractice, onRetry, history, onHistory, onRetryAll, onToggleType)
                    SessionScreen.PLAYING -> Playing(state, onTypeMatch, onHome)
                    SessionScreen.INTERRUPTED -> Interrupted(state, onStart, onHome)
                    SessionScreen.RESULTS -> Results(state, onStart, onHome, history, resultId, time, onHistory, onRetryResult)
                    SessionScreen.PRACTICE_FEEDBACK, SessionScreen.PRACTICE_COMPLETE -> key(state.feedback?.token) { PracticeExplanation(state, onNext, onPractice, onStart, onHome) }
                }
            }
        }
    }
}

@Composable
internal fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun Instructions(settings: SettingsState, onSelect: (Int) -> Unit, onStart: () -> Unit, onPractice: () -> Unit, onRetry: () -> Unit, history: HistoryState, onHistory: () -> Unit, onRetryAll: () -> Unit, onToggleType: (StimulusType) -> Unit) {
    val level = settings.level
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(modeTitle(settings.modeMask, level), style = MaterialTheme.typography.labelLarge, color = Muted)
        PageTitle(stringResource(R.string.welcome_title))
        TypeSettings(settings, onToggleType)
        DifficultySettings(settings, onSelect, onRetry)
        ActionButton(stringResource(R.string.history), onHistory, tag = "history")
        UnsavedSummary(history, onRetryAll)
        TypeInstructions(settings.modeMask, level)
        Text(pluralStringResource(R.plurals.session_details, level, level, (level + 20) * 3), style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(stringResource(R.string.visual_requirement), style = MaterialTheme.typography.bodyMedium, color = Muted)
        ActionButton(stringResource(R.string.start), onStart, enabled = !settings.loading, tag = "start")
        Text(stringResource(R.string.practice_description), style = MaterialTheme.typography.bodyMedium, color = Muted)
        ActionButton(stringResource(R.string.guided_practice), onPractice, enabled = !settings.loading, tag = "practice")
    }
}

@Composable
private fun Playing(state: SessionState, onMatch: (StimulusType) -> Unit, onHome: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("playing_area")) {
        val compactResponses = maxWidth > maxHeight || maxHeight < 650.dp
        if (maxWidth > maxHeight) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    StimulusView(state.stimulus, state.config.modeMask, Modifier.weight(1f).fillMaxSize())
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Progress(state, compactResponses)
                        if (state.config.practice) SkipButton(onHome, compact = true)
                    }
                }
                ResponseControls(state, onMatch, compactResponses)
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compactResponses) 8.dp else 12.dp)) {
                if (compactResponses && state.config.practice) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Progress(state, compact = true) }
                        Box(Modifier.weight(1f)) { SkipButton(onHome, compact = true) }
                    }
                } else Progress(state, compactResponses)
                StimulusView(state.stimulus, state.config.modeMask, Modifier.weight(1f).fillMaxWidth())
                ResponseControls(state, onMatch, compactResponses)
                if (state.config.practice && !compactResponses) SkipButton(onHome)
            }
        }
    }
}

@Composable
private fun Progress(state: SessionState, compact: Boolean = false) {
    Text(if (compact) stringResource(R.string.level_option, state.config.level) else modeTitle(state.config.modeMask, state.config.level, state.config.practice), color = Muted, style = MaterialTheme.typography.labelLarge)
    Text(stringResource(if (state.isWarmUp) R.string.warmup_progress else if (state.config.practice) R.string.practice_progress else R.string.scored_progress, state.progress, state.config.level),
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("progress"))
}

@Composable
private fun ResponseControls(state: SessionState, onMatch: (StimulusType) -> Unit, compact: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val gap = if (maxWidth < 300.dp) 4.dp else 8.dp
        val buttonWidth = 112.dp * LocalDensity.current.fontScale
        Row(Modifier.widthIn(max = buttonWidth * state.config.types.size + gap * (state.config.types.size - 1))
            .fillMaxWidth().height(IntrinsicSize.Min).testTag("response_row"),
            horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (type in state.config.types) {
                TypeResponseControl(state, type, onMatch, Modifier.weight(1f).fillMaxHeight(), compact)
            }
        }
    }
}

@Composable
private fun TypeResponseControl(state: SessionState, type: StimulusType, onMatch: (StimulusType) -> Unit, modifier: Modifier, compact: Boolean) {
    val label = stringResource(R.string.type_match, typeLabel(type))
    val recorded = type in state.recordedTypes
    val status = stringResource(if (recorded) R.string.type_recorded else if (state.isWarmUp) R.string.watch_warmup else R.string.match_if_same)
    Button(onClick = { onMatch(type) }, enabled = state.canRespond(type),
        modifier = modifier.heightIn(min = if (compact) 64.dp else 88.dp)
            .testTag(if (type == StimulusType.POSITION) "match" else "match_${type.bit}")
            .semantics { contentDescription = label; stateDescription = status },
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = if (compact) 8.dp else 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White,
            disabledContainerColor = Cell, disabledContentColor = Muted)) {
        Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(typeLabel(type), style = MaterialTheme.typography.labelMedium, letterSpacing = 0.sp, textAlign = TextAlign.Center)
            CompactResponseStatus(recorded)
        }
    }
}

@Composable
private fun CompactResponseStatus(recorded: Boolean) {
    val messages = listOf(stringResource(R.string.match_action), stringResource(R.string.type_recorded))
    Box(contentAlignment = Alignment.Center) {
        messages.forEach { Text(it, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.sp, textAlign = TextAlign.Center, modifier = Modifier.alpha(0f).clearAndSetSemantics {}) }
        Text(messages[if (recorded) 1 else 0], style = MaterialTheme.typography.labelSmall, letterSpacing = 0.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Interrupted(state: SessionState, onStart: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle(stringResource(if (state.config.practice) R.string.practice_interrupted else R.string.interrupted))
        Text(modeTitle(state.config.modeMask, state.config.level), color = Muted)
        Text(stringResource(R.string.interrupted_detail), style = MaterialTheme.typography.bodyLarge)
        ActionButton(stringResource(if (state.config.practice) R.string.restart_practice else R.string.restart), onStart, tag = "restart")
        HomeButton(onHome)
    }
}

@Composable
private fun Results(state: SessionState, onStart: () -> Unit, onHome: () -> Unit,
    history: HistoryState, resultId: String?, time: HistoryTime, onHistory: () -> Unit, onRetry: () -> Unit) {
    val entry = history.entries[resultId]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.session_complete), color = Muted, style = MaterialTheme.typography.labelLarge)
        PageTitle(stringResource(R.string.results))
        MultiResultSummary(state.results, state.config.level, entry?.record?.completedAt, time)
        entry?.let { SaveNotice(it.status, history.clearing, onRetry) }
        ActionButton(stringResource(R.string.history), onHistory, tag = "history")
        ActionButton(stringResource(R.string.play_again), onStart, tag = "play_again")
        HomeButton(onHome)
    }
}

@Composable
internal fun ActionButton(label: String, action: () -> Unit, enabled: Boolean = true, tag: String) {
    Button(onClick = action, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White,
            disabledContainerColor = Cell, disabledContentColor = Muted)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
internal fun HomeButton(onHome: () -> Unit) {
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
                SettingsNotice.TYPES_RESET -> R.string.types_reset
                SettingsNotice.BOTH_RESET -> R.string.settings_both_reset
                SettingsNotice.LOAD_FAILED -> R.string.settings_load_failed
                SettingsNotice.SAVE_FAILED -> R.string.settings_save_failed
            }), Modifier.testTag("settings_notice").semantics { liveRegion = LiveRegionMode.Polite })
            if (notice == SettingsNotice.SAVE_FAILED) ActionButton(stringResource(R.string.retry), onRetry, tag = "retry")
        }
    }
}

@Composable
private fun SkipButton(onHome: () -> Unit, compact: Boolean = false) {
    val label = stringResource(R.string.skip_practice)
    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("skip")
        .then(if (compact) Modifier.semantics { contentDescription = label } else Modifier)) {
        Text(if (compact) stringResource(R.string.skip_short) else label,
            modifier = if (compact) Modifier.clearAndSetSemantics {} else Modifier)
    }
}

@Composable
private fun PracticeExplanation(state: SessionState, onNext: (Long) -> Unit, onPractice: () -> Unit, onStart: () -> Unit, onHome: () -> Unit) {
    val feedback = requireNotNull(state.feedback)
    val complete = state.screen == SessionScreen.PRACTICE_COMPLETE
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(modeTitle(state.config.modeMask, state.config.level, true), color = Muted)
        PageTitle(stringResource(if (complete) R.string.practice_complete else R.string.practice_feedback))
        Text(stringResource(R.string.practice_progress, state.progress))
        val current = feedback.types.mapValues { it.value.current }
        val reference = feedback.types.mapValues { it.value.reference }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.this_turn))
                StimulusView(current, state.config.modeMask, Modifier.fillMaxWidth().height(120.dp), "practice_current")
            }
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.reference_turn, state.config.level, state.config.level))
                StimulusView(reference, state.config.modeMask, Modifier.fillMaxWidth().height(120.dp), "practice_reference")
            }
        }
        for ((type, response) in feedback.types) {
            Text(typeLabel(type), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.type_comparison, valueLabel(type, response.current), valueLabel(type, response.reference)))
            Text(stringResource(if (response.matches) R.string.type_matched else R.string.type_differed, typeLabel(type)),
                Modifier.testTag(if (type == StimulusType.POSITION) "feedback_answer" else "feedback_${type.bit}").semantics { liveRegion = LiveRegionMode.Polite })
            Text(if (response.responded) stringResource(R.string.tapped_type, typeLabel(type)) else stringResource(R.string.you_waited))
            Text(if (response.matches) stringResource(R.string.expected_type, typeLabel(type)) else stringResource(R.string.expected_type_wait, typeLabel(type)))
        }
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
