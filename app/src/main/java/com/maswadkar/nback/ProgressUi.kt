package com.maswadkar.nback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.maswadkar.nback.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class RecordSourceKey(val records: List<HistoryRecord>) {
    override fun equals(other: Any?) = other is RecordSourceKey && other.records === records
    override fun hashCode() = System.identityHashCode(records)
}

@Composable internal fun preparedHistory(records: List<HistoryRecord>, navigation: HistoryNavigation): PreparedHistory? {
    val filters = ResultFilters(navigation.filter, navigation.modeFilter, navigation.paceFilter, navigation.lengthFilter)
    val data by produceState<PreparedHistory?>(null, RecordSourceKey(records), filters) {
        value = withContext(Dispatchers.Default) { prepareHistory(records, filters) }
    }
    return data?.takeIf { it.source === records && it.filters == filters }
}

@Composable internal fun ResultsSections(progress: Boolean, select: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false, true).forEach { value ->
            OutlinedButton(onClick = { select(value) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .testTag(if (value) "progress_tab" else "sessions_tab").semantics { selected = progress == value; role = Role.Tab }) {
                Text(stringResource(if (value) R.string.progress_tab else R.string.sessions_tab))
            }
        }
    }
}

@Composable internal fun IntegerFilter(label: String, selected: Int, choices: List<Int>, tag: String, select: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${tag}_menu")) {
            Text(if (selected == 0) stringResource(R.string.all_levels) else selected.toString())
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { value ->
                DropdownMenuItem(text = { Text(if (value == 0) stringResource(R.string.all_levels) else value.toString()) },
                    onClick = { expanded = false; select(value) }, modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("${tag}_$value").semantics { this.selected = selected == value })
            }
        }
    }
}

@Composable private fun groupLabel(group: ComparisonGroup) =
    modeTitle(group.modeMask, group.level) + " · " + stringResource(R.string.length_pace, group.sessionLength, group.intervalSeconds)

@Composable internal fun ProgressScreen(session: SessionViewModel, time: HistoryTime, modifier: Modifier) {
    val nav = session.historyNavigation
    val history = session.history.state
    val data = preparedHistory(history.records, nav)
    LaunchedEffect(data, history.load, nav.group) {
        if (history.load == HistoryLoad.READY && nav.group == null && data?.groups?.isNotEmpty() == true)
            session.selectGroup(data.groups.first().key)
    }
    val group = data?.groups?.find { it.key == nav.group }
    if (history.load == HistoryLoad.READY && data == null) {
        Column(modifier) { Text(stringResource(R.string.history_loading)) }
        return
    }
    val scroll = rememberLazyListState(nav.progressIndex, nav.progressOffset)
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> session.rememberProgressScroll(index, offset) }
    }
    val table = nav.progressTable
    LazyColumn(modifier.testTag("progress_list"), state = scroll, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuietButton(stringResource(R.string.back), session::back, "history_back")
                QuietButton(stringResource(R.string.home), session::home, "home")
            }
            PageTitle(stringResource(R.string.results_hub))
            ResultsSections(true, session::showProgress)
        }
        item { UnsavedSummary(history) { session.history.retry() } }
        when {
            history.load == HistoryLoad.FAILED -> item {
                Text(stringResource(R.string.history_failed))
                ActionButton(stringResource(R.string.retry), session.history::reload, tag = "retry_load")
            }
            history.load == HistoryLoad.LOADING || data == null -> item { Text(stringResource(R.string.history_loading)) }
            else -> {
                item {
                    Text(stringResource(R.string.saved_count, history.records.size), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.saved_count_note), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (data.groups.isEmpty()) item { Text(stringResource(R.string.progress_empty)) }
                else {
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        Text(stringResource(R.string.comparison_group))
                        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("group_menu")) {
                            Text(nav.group?.let { groupLabel(it) } ?: stringResource(R.string.comparison_group))
                        }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            data.groups.forEachIndexed { index, entry ->
                                DropdownMenuItem(text = { Text("${groupLabel(entry.key)} (${entry.sessions.size})") },
                                    onClick = { expanded = false; session.selectGroup(entry.key) }, modifier = Modifier.heightIn(min = 48.dp)
                                        .testTag("group_$index").semantics { selected = nav.group == entry.key })
                            }
                        }
                    }
                    if (group == null) item { Text(stringResource(R.string.group_missing)) }
                    else {
                        item {
                            Text(stringResource(R.string.group_count, group.sessions.size))
                            group.latest.results().forEach { (type, score) ->
                                Text(stringResource(R.string.progress_latest, typeLabel(type), score.accuracy))
                            }
                            if (group.sessions.size == 1) Text(stringResource(R.string.progress_single))
                        }
                        item { ProgressChart(group, time) }
                        item {
                            Text(stringResource(R.string.results_baseline), color = Muted)
                            TextButton(onClick = session::toggleProgressTable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress_table")) {
                                Text(stringResource(R.string.progress_table))
                            }
                        }
                        if (table) items(group.sessions, key = { it.id }) { record ->
                            OutlinedButton(onClick = { session.openDetail(record.id) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("progress_record_${record.id}")) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(time.format(record.completedAt, detail = true))
                                    Text(groupLabel(group.key))
                                    record.results().forEach { (type, result) ->
                                        val targets = record.sessionLength * 3 / 10
                                        val nonTargets = record.sessionLength - targets
                                        Text(stringResource(R.string.progress_rates, typeLabel(type), result.accuracy,
                                            result.hits, targets, (100.0 * result.hits / targets).roundToInt(),
                                            result.falseAlarms, nonTargets, (100.0 * result.falseAlarms / nonTargets).roundToInt()))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ProgressChart(group: ProgressGroup, time: HistoryTime) {
    var visible by rememberSaveable(group.key) { mutableIntStateOf(group.key.modeMask) }
    val types = activeTypes(group.key.modeMask)
    val labels = types.associateWith { typeLabel(it) }
    // A single Canvas draws all retained points; the exact values are in a lazy table.
    val scores = group.scores
    val description = stringResource(R.string.progress_axis) + ". " + stringResource(R.string.group_count, scores.size)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(200.dp).padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("100%", "50%", "0%").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
            Canvas(Modifier.weight(1f).height(200.dp).testTag("progress_chart").semantics { contentDescription = description }) {
                listOf(0f, .5f, 1f).forEach { y -> drawLine(Line, Offset(0f, size.height*y), Offset(size.width, size.height*y)) }
                types.forEach { type ->
                    if (visible and type.bit == 0) return@forEach
                    val colour = chartColours.getValue(type)
                    val path = Path()
                    scores.forEachIndexed { index, results ->
                        val x = if (scores.size == 1) size.width/2 else size.width * index/(scores.size-1)
                        val y = size.height * (1-results.getValue(type).percentage.toFloat()/100)
                        if (index == 0) path.moveTo(x,y) else path.lineTo(x,y)
                        val r = 3.dp.toPx(); val center = Offset(x,y)
                        when (type) {
                            StimulusType.POSITION -> drawCircle(colour,r,center)
                            StimulusType.COLOUR -> drawRect(colour, center-Offset(r,r), Size(2*r,2*r))
                            StimulusType.NUMBER -> drawPath(Path().apply { moveTo(x,y-r); lineTo(x+r,y+r); lineTo(x-r,y+r); close() },colour)
                        }
                    }
                    val dash = when(type) {
                        StimulusType.POSITION -> null
                        StimulusType.COLOUR -> PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),4.dp.toPx()))
                        StimulusType.NUMBER -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),4.dp.toPx()))
                    }
                    drawPath(path,colour,style=Stroke(2.dp.toPx(),pathEffect=dash))
                }
            }
        }
        Text(time.format(group.sessions.first().completedAt) + " → " + time.format(group.sessions.last().completedAt), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.progress_axis))
        Text(stringResource(R.string.progress_spacing), color = Muted, style = MaterialTheme.typography.bodySmall)
        types.forEach { type ->
            val selected = visible and type.bit != 0
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(selected,role=Role.Checkbox,onValueChange={ visible = visible xor type.bit })
                .testTag("progress_type_${type.bit}"), verticalAlignment=Alignment.CenterVertically) {
                Checkbox(selected,onCheckedChange=null)
                Text("${when(type) { StimulusType.POSITION -> "●"; StimulusType.COLOUR -> "■"; StimulusType.NUMBER -> "▲" }} ${labels.getValue(type)}",color=chartColours.getValue(type))
            }
        }
    }
}
