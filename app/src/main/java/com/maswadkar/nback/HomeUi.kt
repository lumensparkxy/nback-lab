package com.maswadkar.nback

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
import com.maswadkar.nback.engine.SessionConfig
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
import com.maswadkar.nback.engine.StimulusType

/** Retained by the session ViewModel; deliberately absent from process restoration. */
internal enum class HomeDestination { HOME, SETTINGS, HELP }
internal class HomeUiState { var destination by mutableStateOf(HomeDestination.HOME) }
internal fun SettingsState.config() = SessionConfig(level, modeMask = modeMask,
    intervalSeconds = intervalSeconds, sessionLength = sessionLength)

@Composable internal fun GuidedHome(
    settings: SettingsState, onSelect: (Int) -> Unit, onStart: () -> Unit,
    onPractice: () -> Unit, onRetry: () -> Unit, history: HistoryState,
    onHistory: () -> Unit, onRetryAll: () -> Unit, onToggleType: (StimulusType) -> Unit,
    uiState: HomeUiState, onInterval: (Int) -> Unit, onLength: (Int) -> Unit = {},
) {
    val home = { uiState.destination = HomeDestination.HOME }
    when (uiState.destination) {
        HomeDestination.SETTINGS -> SessionSettings(settings, onSelect, onToggleType, onInterval, onLength, onRetry, home)
        HomeDestination.HELP -> HowToPlay(settings, onPractice, { uiState.destination = HomeDestination.SETTINGS }, home)
        HomeDestination.HOME -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_apps)
                Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                QuietButton(stringResource(R.string.results_hub), onHistory, "history")
            }
            PageTitle(stringResource(R.string.home_title))
            Text(stringResource(R.string.home_subtitle), color = Muted)
            androidx.compose.material3.OutlinedButton(onClick = { uiState.destination = HomeDestination.SETTINGS },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("setup_summary")) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConfigurationSummary(settings.config())
                    Text(stringResource(R.string.change_settings), style = MaterialTheme.typography.labelLarge)
                }
            }
            SettingsNotices(settings, onRetry)
            Text(stringResource(R.string.visual_home_notice), color = Muted, style = MaterialTheme.typography.bodySmall)
            ActionButton(stringResource(R.string.start), onStart, !settings.loading, "start")
            QuietButton(stringResource(R.string.practice_invitation), onPractice, "practice", Modifier.fillMaxWidth(), !settings.loading)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton(stringResource(R.string.settings), { uiState.destination = HomeDestination.SETTINGS }, "settings", Modifier.weight(1f))
                QuietButton(stringResource(R.string.how_to_play), { uiState.destination = HomeDestination.HELP }, "help", Modifier.weight(1f))
            }
            UnsavedSummary(history, onRetryAll)
            PrivacyControls()
        }
    }
}

@Composable internal fun ConfigurationSummary(config: SessionConfig) {
    Text(modeTitle(config.modeMask, config.level), fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.length_pace, config.sessionLength, config.intervalSeconds))
    Text(pluralStringResource(R.plurals.warmup_duration, config.level, config.level, elapsedLabel((config.level + config.sessionLength) * config.intervalMillis)), color = Muted)
}

@Composable private fun SessionSettings(settings: SettingsState, onSelect: (Int) -> Unit,
    onToggleType: (StimulusType) -> Unit, onInterval: (Int) -> Unit, onLength: (Int) -> Unit,
    onRetry: () -> Unit, back: () -> Unit) {
    var interval by remember(settings.intervalSeconds) { mutableIntStateOf(settings.intervalSeconds) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings_screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuietButton(stringResource(R.string.back), back, "settings_back")
        PageTitle(stringResource(R.string.settings))
        Text(stringResource(R.string.next_session_settings), color = Muted)
        Text(stringResource(R.string.session_length), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (length in com.maswadkar.nback.engine.SessionRules.LENGTHS) {
                androidx.compose.material3.OutlinedButton(onClick = { onLength(length) }, enabled = !settings.loading,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("length_$length")
                        .semantics { selected = settings.sessionLength == length; role = Role.RadioButton },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = if (settings.sessionLength == length) Ink else Color.White,
                        contentColor = if (settings.sessionLength == length) Color.White else Ink),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 12.dp)) { Text(length.toString()) }
            }
        }
        Text(stringResource(R.string.length_explanation), color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.what_to_match), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.one_type_required), color = Muted)
        TypeSettings(settings, onToggleType)
        Text(stringResource(R.string.how_far), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.how_far_detail), color = Muted)
        LevelChoices(settings.level, 1..3, onSelect, "level", !settings.loading)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.time_per_turn, interval), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            val less = stringResource(R.string.decrease_interval)
            val more = stringResource(R.string.increase_interval)
            TextButton(onClick = { interval--; onInterval(interval) }, enabled = !settings.loading && interval > 1,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("interval_less").semantics { contentDescription = less }) { Text("−") }
            TextButton(onClick = { interval++; onInterval(interval) }, enabled = !settings.loading && interval < 30,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("interval_more").semantics { contentDescription = more }) { Text("+") }
        }
        val intervalLabel = stringResource(R.string.interval_seconds_label)
        Slider(value = interval.toFloat(), onValueChange = { interval = it.roundToInt() },
            onValueChangeFinished = { onInterval(interval) }, valueRange = 1f..30f, steps = 28,
            enabled = !settings.loading, modifier = Modifier.fillMaxWidth().testTag("interval").semantics { contentDescription = intervalLabel })
        Text(stringResource(R.string.visual_brief, (settings.config().copy(intervalSeconds = interval).exposureMillis / 1000).toInt()), color = Muted)
        ConfigurationSummary(settings.config().copy(intervalSeconds = interval))
        SettingsNotices(settings, onRetry)
        QuietButton(stringResource(R.string.back), back, "settings_done", Modifier.fillMaxWidth())
    }
}

@Composable private fun HowToPlay(settings: SettingsState, practice: () -> Unit, settingsAction: () -> Unit, back: () -> Unit) {
    val config = settings.config()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("help_screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuietButton(stringResource(R.string.back), back, "help_back")
        PageTitle(stringResource(R.string.how_to_play))
        ConfigurationSummary(config)
        Text(pluralStringResource(R.plurals.look_back, config.level, config.level), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.turns_not_seconds))
        TypeInstructions(config.modeMask, config.level)
        Text(stringResource(R.string.help_types))
        HelpExample(config)
        Text(stringResource(R.string.help_answer_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.help_answer))
        Text(stringResource(R.string.help_timing_title), style = MaterialTheme.typography.titleLarge)
        Text(pluralStringResource(R.plurals.help_warmup, config.level, config.level))
        Text(stringResource(R.string.help_timing, config.sessionLength,
            pluralStringResource(R.plurals.seconds_duration, config.intervalSeconds, config.intervalSeconds),
            pluralStringResource(R.plurals.seconds_duration, (config.exposureMillis / 1000).toInt(), (config.exposureMillis / 1000).toInt()),
            pluralStringResource(R.plurals.seconds_duration, ((config.intervalMillis - config.exposureMillis) / 1000).toInt(), ((config.intervalMillis - config.exposureMillis) / 1000).toInt())))
        Text(stringResource(R.string.timing_detail, pluralStringResource(R.plurals.seconds_duration, config.intervalSeconds, config.intervalSeconds),
            pluralStringResource(R.plurals.seconds_duration, (config.exposureMillis / 1000).toInt(), (config.exposureMillis / 1000).toInt())))
        Text(stringResource(R.string.visual_requirement))
        Text(stringResource(R.string.help_results_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.help_results))
        Text(stringResource(R.string.results_baseline))
        Text(stringResource(R.string.help_graphs))
        Text(stringResource(R.string.practice_description))
        Text(stringResource(R.string.practice_length_note))
        ActionButton(stringResource(R.string.practice_invitation), practice, !settings.loading, "help_practice")
        QuietButton(stringResource(R.string.change_settings), settingsAction, "help_settings", Modifier.fillMaxWidth())
    }
}

@Composable private fun HelpExample(config: SessionConfig) {
    Text(stringResource(R.string.worked_example), style = MaterialTheme.typography.titleLarge)
    // Each diagram has an equivalent textual description, including the exact reference gap.
    for (index in 0..config.level) {
        val first = index == 0
        val current = index == config.level
        val values = config.types.associateWith { type -> when (type) {
            StimulusType.POSITION -> if (first || current) 0 else 8
            StimulusType.COLOUR -> if (first || current) 1 else 5
            StimulusType.NUMBER -> if (first) 6 else if (current) 3 else 1
        } }
        val label = if (first) pluralStringResource(R.plurals.example_reference, config.level, config.level) else if (current)
            stringResource(R.string.this_turn) else stringResource(R.string.example_between, index)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StimulusView(values, config.modeMask, Modifier.size(88.dp), "help_example_$index")
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(config.types.map { "${typeLabel(it)}: ${valueLabel(it, values.getValue(it))}" }.joinToString(" · "))
            }
        }
    }
    config.types.forEach { type ->
        Text(stringResource(if (type == StimulusType.NUMBER) R.string.example_wait_type else R.string.example_match_type, typeLabel(type)))
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
            SettingsNotice.LENGTH_RESET -> R.string.length_reset
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
