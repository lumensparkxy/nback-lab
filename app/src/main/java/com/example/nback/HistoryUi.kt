package com.example.nback

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.nback.engine.SessionResult
import com.example.nback.engine.StimulusType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HistoryTime(val locale: Locale = Locale.getDefault(), val zone: ZoneId = ZoneId.systemDefault(), val use24Hour: Boolean = true) {
    private val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale,
        if (use24Hour) "yMMMdHm" else "yMMMdhm"), locale).withZone(zone)
    fun format(timestamp: Long, detail: Boolean = false): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val label = formatter.format(instant)
        val offset = instant.atZone(zone).offset.id.let { if (it == "Z") "+00:00" else it }
        return if (detail) "$label · UTC$offset" else label
    }
}

@Composable internal fun historyTime(revision: Int): HistoryTime {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(revision, configuration) {
        HistoryTime(configuration.locales[0], ZoneId.systemDefault(), DateFormat.is24HourFormat(context))
    }
}

@Composable internal fun ResultSummary(result: SessionResult, level: Int, completedAt: Long?, time: HistoryTime) =
    MultiResultSummary(mapOf(StimulusType.POSITION to result), level, completedAt, time)

@Composable internal fun MultiResultSummary(results: Map<StimulusType, SessionResult>, level: Int, completedAt: Long?, time: HistoryTime) {
    var details by rememberSaveable(results, level, completedAt) { mutableStateOf(false) }
    Text(modeTitle(results.keys.sumOf { it.bit }, level), style = MaterialTheme.typography.titleMedium)
    completedAt?.let { Text(time.format(it, detail = true), Modifier.testTag("completed_at"), style = MaterialTheme.typography.bodySmall, color = Muted) }
    for ((type, result) in results) {
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(typeLabel(type), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.accuracy, result.accuracy), Modifier.weight(1f)
                    .testTag(if (type == StimulusType.POSITION) "accuracy" else "accuracy_${type.bit}"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.hits_denominator, result.hits) + " · " + stringResource(R.string.outcome_count, stringResource(R.string.misses), result.misses), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.false_alarms_denominator, result.falseAlarms), style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
    Text(stringResource(R.string.results_baseline), style = MaterialTheme.typography.bodyMedium, color = Muted)
    val expandedLabel = stringResource(if (details) R.string.expanded else R.string.collapsed)
    TextButton(onClick = { details = !details }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .testTag("result_details").semantics { stateDescription = expandedLabel }) {
        Text(stringResource(R.string.result_details), Modifier.weight(1f))
        AppIcon(R.drawable.ic_expand_more)
    }
    if (details) for ((type, result) in results) {
        val label = typeLabel(type)
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.correct, result.correct), style = MaterialTheme.typography.titleMedium)
        ResultOutcome(stringResource(R.string.hits_denominator, result.hits), stringResource(R.string.type_hits_detail, label))
        ResultOutcome(stringResource(R.string.outcome_count, stringResource(R.string.misses), result.misses), stringResource(R.string.type_misses_detail, label))
        ResultOutcome(stringResource(R.string.false_alarms_denominator, result.falseAlarms), stringResource(R.string.type_false_detail, label))
        ResultOutcome(stringResource(R.string.outcome_count, stringResource(R.string.correct_rejections), result.correctRejections), stringResource(R.string.type_rejections_detail, label))
    }
}

@Composable private fun ResultOutcome(label: String, detail: String) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp)
        .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun SaveNotice(status: SaveStatus, clearing: Boolean, retry: () -> Unit) {
    StatusText(stringResource(when (status) {
        SaveStatus.PENDING -> R.string.history_saving
        SaveStatus.SAVED -> R.string.history_saved
        SaveStatus.FAILED -> R.string.history_save_failed
        SaveStatus.REMOVED -> R.string.history_removed
    }))
    if (status == SaveStatus.PENDING || status == SaveStatus.FAILED) Text(stringResource(R.string.history_unsaved_warning))
    if (status == SaveStatus.FAILED) ActionButton(stringResource(R.string.retry), retry, !clearing, "retry_result")
}

@Composable internal fun UnsavedSummary(state: HistoryState, retry: () -> Unit) {
    if (state.pending + state.failed == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.pending > 0) StatusText(stringResource(R.string.history_pending_count, state.pending))
        if (state.failed > 0) StatusText(stringResource(R.string.history_failed_count, state.failed))
        Text(stringResource(R.string.history_unsaved_warning))
        if (state.failed > 0) ActionButton(stringResource(R.string.history_retry_saving), retry, !state.clearing, "retry_saving")
    }
}

@Composable private fun StatusText(text: String) {
    Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable internal fun HistoryScreen(session: SessionViewModel, time: HistoryTime) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val navigation = session.historyNavigation
    val history = session.history.state
    Scaffold { insets ->
        val modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp, vertical = 16.dp)
        if (navigation.detailId != null) {
            Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageTitle(stringResource(R.string.results))
                when (history.load) {
                    HistoryLoad.LOADING -> StatusText(stringResource(R.string.history_loading))
                    HistoryLoad.FAILED -> LoadFailure(session.history::reload)
                    HistoryLoad.READY -> {
                        val record = history.records.find { it.id == navigation.detailId }
                        if (record == null) StatusText(stringResource(R.string.history_missing))
                        else MultiResultSummary(record.results(), record.level, record.completedAt, time)
                    }
                }
                BackButton(session::back)
                HomeButton(session::home)
            }
        } else key(navigation.filter, navigation.modeFilter) {
            val scroll = rememberLazyListState(navigation.scrollIndex, navigation.scrollOffset)
            LaunchedEffect(scroll) {
                snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }.collect { (index, offset) ->
                    session.rememberHistoryScroll(index, offset)
                }
            }
            val rows = remember(history.records, navigation.filter, navigation.modeFilter) {
                history.records.filter { (navigation.filter == 0 || it.level == navigation.filter) &&
                    (navigation.modeFilter == 0 || it.modeMask == navigation.modeFilter) }
            }
            LazyColumn(modifier.testTag("history_list"), state = scroll, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "heading") {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            QuietButton(stringResource(R.string.back), session::back, "history_back")
                            QuietButton(stringResource(R.string.home), session::home, "home")
                        }
                        PageTitle(stringResource(R.string.history))
                    }
                }
                item(key = "filters") { HistoryFilters(navigation.filter, session::filterHistory) }
                item(key = "modes") { ModeFilter(navigation.modeFilter, session::filterHistoryMode) }
                item(key = "comparison") { Text(stringResource(R.string.history_comparison), style = MaterialTheme.typography.bodySmall, color = Muted) }
                item(key = "unsaved") { UnsavedSummary(history) { session.history.retry() } }
                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryActions(history.canClear, session::askClear)
                        if (history.clearing) StatusText(stringResource(R.string.history_clearing))
                        if (history.clearFailed) {
                            StatusText(stringResource(R.string.history_clear_failed))
                            ActionButton(stringResource(R.string.retry), session::askClear, history.canClear, "retry_clear")
                            QuietButton(stringResource(R.string.cancel), session.history::dismissClearFailure, "cancel_clear_failure")
                        }
                    }
                }
                when (history.load) {
                    HistoryLoad.LOADING -> item(key = "loading") { StatusText(stringResource(R.string.history_loading)) }
                    HistoryLoad.FAILED -> item(key = "error") { LoadFailure(session.history::reload) }
                    HistoryLoad.READY -> {
                        if (rows.isEmpty()) item(key = "empty") {
                            Text(if (navigation.modeFilter != 0) stringResource(R.string.empty_mode)
                                else if (navigation.filter == 0) stringResource(R.string.history_empty)
                                else stringResource(R.string.history_empty_level, navigation.filter))
                        }
                        items(rows, key = { "session:${it.id}" }, contentType = { "result" }) { record ->
                            OutlinedButton(onClick = { session.openDetail(record.id) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("record_${record.id}")) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(time.format(record.completedAt))
                                    Text(modeTitle(record.modeMask, record.level))
                                    record.results().forEach { (type, result) ->
                                        Text(stringResource(R.string.type_accuracy, typeLabel(type), result.accuracy), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (navigation.confirmClear) AlertDialog(
        onDismissRequest = session::cancelClear,
        properties = DialogProperties(usePlatformDefaultWidth = !landscape),
        modifier = if (landscape) Modifier.fillMaxWidth().padding(horizontal = 24.dp) else Modifier,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("clear_explanation"),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.clear_title), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.clear_body))
            }
        },
        confirmButton = { TextButton(onClick = session::confirmClear, enabled = history.canClear,
            modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_clear")) { Text(stringResource(R.string.clear_confirm)) } },
        dismissButton = { TextButton(onClick = session::cancelClear,
            modifier = Modifier.heightIn(min = 48.dp).testTag("cancel_clear")) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable private fun HistoryFilters(selected: Int, select: (Int) -> Unit) {
    LevelChoices(selected, 0..3, select, "filter")
}

@Composable private fun HistoryActions(canClear: Boolean, clear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        QuietButton(stringResource(R.string.history_actions), { expanded = true }, "history_actions")
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.clear_history)) },
                onClick = { expanded = false; clear() }, enabled = canClear,
                modifier = Modifier.heightIn(min = 48.dp).testTag("clear_history"))
        }
    }
}

@Composable private fun LoadFailure(retry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusText(stringResource(R.string.history_failed))
        ActionButton(stringResource(R.string.retry), retry, tag = "retry_load")
    }
}

@Composable private fun BackButton(back: () -> Unit) {
    QuietButton(stringResource(R.string.back), back, tag = "history_back")
}

@Composable private fun ModeFilter(selected: Int, select: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.mode_filters), fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mode_filter_menu")) {
            Text(if (selected == 0) stringResource(R.string.all_levels) else modeLabel(selected))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (mask in 0..7) DropdownMenuItem(
                text = { Text(if (mask == 0) stringResource(R.string.all_levels) else modeLabel(mask)) },
                onClick = { expanded = false; select(mask) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("mode_filter_$mask").semantics { this.selected = selected == mask },
            )
        }
    }
}
