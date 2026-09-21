package com.example.nback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import com.example.nback.engine.SessionConfig
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.nback.engine.StimulusType

/** Retained by the session ViewModel; deliberately absent from saved instance state. */
internal class HomeUiState { var helpExpanded by mutableStateOf(false) }

@Composable internal fun GuidedHome(
    settings: SettingsState, onSelect: (Int) -> Unit, onStart: () -> Unit,
    onPractice: () -> Unit, onRetry: () -> Unit, history: HistoryState,
    onHistory: () -> Unit, onRetryAll: () -> Unit, onToggleType: (StimulusType) -> Unit,
    uiState: HomeUiState, onInterval: (Int) -> Unit,
) {
    var interval by remember(settings.intervalSeconds) { mutableIntStateOf(settings.intervalSeconds) }
    val config = SessionConfig(settings.level, modeMask = settings.modeMask, intervalSeconds = interval)
    val decreaseLabel = stringResource(R.string.decrease_interval)
    val increaseLabel = stringResource(R.string.increase_interval)
    val intervalLabel = stringResource(R.string.interval_seconds_label)
    val help = uiState.helpExpanded
    val helpState = stringResource(if (help) R.string.expanded else R.string.collapsed)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_apps)
            Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            QuietButton(stringResource(R.string.history), onHistory, "history")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.setup_subtitle), color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StepHeading(1, stringResource(R.string.what_to_match), stringResource(R.string.one_type_required))
            TypeSettings(settings, onToggleType)
        }
        HorizontalDivider(color = Line)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StepHeading(2, stringResource(R.string.how_far), stringResource(R.string.how_far_detail))
            LevelChoices(settings.level, 1..3, onSelect, "level", !settings.loading)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.time_per_turn, interval), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { interval--; onInterval(interval) }, enabled = !settings.loading && interval > 1,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("interval_less")
                        .semantics { contentDescription = decreaseLabel }) { Text("−") }
                TextButton(onClick = { interval++; onInterval(interval) }, enabled = !settings.loading && interval < 30,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("interval_more")
                        .semantics { contentDescription = increaseLabel }) { Text("+") }
            }
            Slider(value = interval.toFloat(), onValueChange = { interval = it.roundToInt() },
                onValueChangeFinished = { onInterval(interval) }, valueRange = 1f..30f, steps = 28,
                enabled = !settings.loading, modifier = Modifier.fillMaxWidth().testTag("interval")
                    .semantics { contentDescription = intervalLabel })
            SettingsNotices(settings, onRetry)
        }
        HorizontalDivider(color = Line)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pluralStringResource(R.plurals.session_brief, settings.level, settings.level, (config.durationMillis / 1000).toInt()), style = MaterialTheme.typography.bodySmall, color = Muted)
            Text(stringResource(R.string.visual_brief, (config.exposureMillis / 1000).toInt()), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Column {
            ActionButton(stringResource(R.string.start), onStart, !settings.loading, "start")
            QuietButton(stringResource(R.string.practice_invitation), onPractice, "practice", Modifier.fillMaxWidth(), !settings.loading)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(pluralStringResource(R.plurals.look_back, settings.level, settings.level), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SessionExample(settings.modeMask, settings.level)
        }
        UnsavedSummary(history, onRetryAll)
        TextButton(onClick = { uiState.helpExpanded = !help }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help")
            .semantics { stateDescription = helpState }) {
            Text(stringResource(R.string.how_to_play), Modifier.weight(1f), textAlign = TextAlign.Start)
            AppIcon(R.drawable.ic_expand_more)
        }
        if (help) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TypeInstructions(settings.modeMask, settings.level)
            Text(pluralStringResource(R.plurals.session_details, settings.level, settings.level, (config.durationMillis / 1000).toInt()))
            Text(stringResource(R.string.timing_detail, pluralStringResource(R.plurals.seconds_duration, interval, interval),
                pluralStringResource(R.plurals.seconds_duration, (config.exposureMillis / 1000).toInt(), (config.exposureMillis / 1000).toInt())))
            Text(stringResource(R.string.visual_requirement))
            Text(stringResource(R.string.practice_description))
        }
    }
}

@Composable private fun StepHeading(step: Int, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).background(Apricot, CircleShape), contentAlignment = Alignment.Center) {
            Text(step.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable internal fun TypeSettings(settings: SettingsState, toggle: (StimulusType) -> Unit) {
    // Larger text reflows cards vertically instead of squeezing their labels.
    BoxWithConstraints {
        val vertical = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f
        if (vertical) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StimulusType.entries.forEach { TypeCard(it, settings, toggle, Modifier.fillMaxWidth(), true) }
        } else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StimulusType.entries.forEach { TypeCard(it, settings, toggle, Modifier.weight(1f).fillMaxHeight(), false) }
        }
    }
}

@Composable private fun TypeCard(type: StimulusType, settings: SettingsState, toggle: (StimulusType) -> Unit, modifier: Modifier, wide: Boolean) {
    val selected = settings.modeMask and type.bit != 0
    val icon = when (type) { StimulusType.POSITION -> R.drawable.ic_grid_view; StimulusType.COLOUR -> R.drawable.ic_palette; StimulusType.NUMBER -> R.drawable.ic_tag }
    val detail = stringResource(when (type) { StimulusType.POSITION -> R.string.card_position; StimulusType.COLOUR -> R.string.card_colour; StimulusType.NUMBER -> R.string.card_number })
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(if (selected) SelectedCard else Color.White)
        .border(1.dp, if (selected) SelectedEdge else Line, shape)
        .toggleable(selected, enabled = !settings.loading && settings.modeMask != type.bit, role = Role.Switch, onValueChange = { toggle(type) })
        .testTag("type_${type.bit}").padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon)
            if (wide) Text(typeLabel(type), Modifier.weight(1f).padding(horizontal = 10.dp), fontWeight = FontWeight.SemiBold)
            if (selected) AppIcon(R.drawable.ic_check_circle, Modifier.size(18.dp))
        }
        if (!wide) Text(typeLabel(type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable internal fun LevelChoices(selected: Int, levels: IntRange, select: (Int) -> Unit, prefix: String, enabled: Boolean = true) {
    val label = stringResource(if (prefix == "level") R.string.difficulty else R.string.history_filters)
    Row(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (level in levels) {
            val shape = RoundedCornerShape(12.dp)
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(shape)
                .background(if (selected == level) Ink else Color.White)
                .border(1.dp, if (selected == level) Ink else Line, shape)
                .selectable(level == selected, enabled = enabled, role = Role.RadioButton, onClick = { select(level) })
                .testTag("${prefix}_$level").padding(horizontal = 2.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(if (level == 0) stringResource(R.string.all_levels) else stringResource(R.string.level_option, level),
                    color = if (selected == level) Color.White else Ink, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable private fun SettingsNotices(settings: SettingsState, onRetry: () -> Unit) {
    if (settings.loading) Text(stringResource(R.string.settings_loading), Modifier.testTag("settings_loading"))
    if (settings.saving) Text(stringResource(R.string.settings_saving), Modifier.testTag("settings_saving"))
    settings.notice?.let { notice ->
        Text(stringResource(when (notice) {
            SettingsNotice.INTERVAL_RESET -> R.string.interval_reset
            SettingsNotice.SETTINGS_RESET -> R.string.settings_any_reset
            SettingsNotice.RESET -> R.string.settings_reset
            SettingsNotice.TYPES_RESET -> R.string.types_reset
            SettingsNotice.BOTH_RESET -> R.string.settings_both_reset
            SettingsNotice.LOAD_FAILED -> R.string.settings_load_failed
            SettingsNotice.SAVE_FAILED -> R.string.settings_save_failed
        }), Modifier.testTag("settings_notice").semantics { liveRegion = LiveRegionMode.Polite })
        if (notice == SettingsNotice.SAVE_FAILED) ActionButton(stringResource(R.string.retry), onRetry, tag = "retry")
    }
}
