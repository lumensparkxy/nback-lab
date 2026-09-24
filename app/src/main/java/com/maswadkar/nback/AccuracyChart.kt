package com.maswadkar.nback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maswadkar.nback.engine.*
import kotlin.math.roundToInt

internal fun elapsedLabel(millis: Long): String = "${millis / 60000}:${(millis / 1000 % 60).toString().padStart(2, '0')}" + if (millis % 1000 == 0L) "" else ".${millis % 1000 / 100}"
private val chartColours = mapOf(StimulusType.POSITION to Color(0xFF1857A4), StimulusType.COLOUR to Color(0xFF8039A2), StimulusType.NUMBER to Color(0xFF16745E))

@Composable internal fun AccuracyChart(config: SessionConfig, outcomes: Map<StimulusType, List<Outcome>>) {
    if (outcomes.isEmpty()) {
        Text(stringResource(R.string.chart_unavailable), color = Muted, modifier = Modifier.testTag("timeline_unavailable"))
        return
    }
    val points = remember(config, outcomes) { outcomes.mapValues { accuracyTimeline(config, it.value) } }
    var visible by rememberSaveable(config, outcomes) { mutableIntStateOf(config.modeMask) }
    var table by rememberSaveable(config, outcomes) { mutableStateOf(false) }
    val labels = config.types.associateWith { typeLabel(it) }
    val summary = labels.entries.joinToString("; ") { (type, label) -> "$label: ${points.getValue(type).last().percentage.roundToInt()}% final accuracy" }
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp)
        .testTag("accuracy_timeline"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chart_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.chart_warmup, elapsedLabel(config.level * config.intervalMillis)), style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(200.dp).padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("100%", "50%", "0%").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
            Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp).semantics { contentDescription = summary }) {
                val duration = config.durationMillis.toFloat()
                fun x(ms: Long) = size.width * ms / duration
                fun point(p: AccuracyPoint) = Offset(x(p.elapsedMillis), size.height * (1 - p.percentage.toFloat() / 100))
                drawRect(Color(0xFFF2EEE8), size = Size(x(config.level * config.intervalMillis), size.height))
                listOf(0f, .5f, 1f).forEach { y -> drawLine(Line, Offset(0f, size.height * y), Offset(size.width, size.height * y), 1.dp.toPx()) }
                points.forEach { (type, series) ->
                    if (visible and type.bit == 0) return@forEach
                    val colour = chartColours.getValue(type)
                    val path = Path()
                    var previous = point(series.first())
                    path.moveTo(previous.x, previous.y)
                    series.drop(1).forEach { p ->
                        val next = point(p)
                        path.lineTo(next.x, previous.y); path.lineTo(next.x, next.y); previous = next
                    }
                    val dash = when (type) {
                        StimulusType.POSITION -> null
                        StimulusType.COLOUR -> PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
                        StimulusType.NUMBER -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                    }
                    drawPath(path, colour, style = Stroke(2.dp.toPx(), pathEffect = dash))
                    series.forEach { p ->
                        val center = point(p); val r = 3.dp.toPx()
                        when (type) {
                            StimulusType.POSITION -> drawCircle(colour, r, center)
                            StimulusType.COLOUR -> drawRect(colour, center - Offset(r, r), Size(2*r, 2*r))
                            StimulusType.NUMBER -> drawPath(Path().apply { moveTo(center.x, center.y-r); lineTo(center.x+r, center.y+r); lineTo(center.x-r, center.y+r); close() }, colour)
                        }
                    }
                }
            }
            // Labels use the same origin and width as the Canvas, including its Y-label gutter.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Text(elapsedLabel(0), Modifier.align(Alignment.CenterStart), style = MaterialTheme.typography.labelSmall)
                if (maxWidth >= 220.dp && LocalDensity.current.fontScale <= 1.3f)
                    Text(elapsedLabel(config.durationMillis / 2), Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelSmall)
                Text(elapsedLabel(config.durationMillis), Modifier.align(Alignment.CenterEnd), style = MaterialTheme.typography.labelSmall)
            }
            }
        }
        Text(stringResource(R.string.chart_axis), style = MaterialTheme.typography.bodySmall, color = Muted)
        for (type in config.types) {
            val selected = visible and type.bit != 0
            val style = when (type) { StimulusType.POSITION -> "●"; StimulusType.COLOUR -> "■"; StimulusType.NUMBER -> "▲" }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(selected, role = Role.Checkbox, onValueChange = { visible = visible xor type.bit })
                .testTag("chart_type_${type.bit}"), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected, onCheckedChange = null)
                Text("$style ${labels.getValue(type)}", color = chartColours.getValue(type), modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(stringResource(R.string.chart_caveat), style = MaterialTheme.typography.bodySmall, color = Muted)
        val tableState = stringResource(if (table) R.string.expanded else R.string.collapsed)
        TextButton(onClick = { table = !table }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("timeline_table")
            .semantics { stateDescription = tableState }) { Text(stringResource(R.string.chart_table)) }
        if (table) for (index in 0 until 20) {
            Column(Modifier.fillMaxWidth().testTag("timeline_turn_${index + 1}").semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.chart_row, index + 1, elapsedLabel(points.values.first()[index].elapsedMillis)), fontWeight = FontWeight.SemiBold)
                for (type in config.types) Text("${labels.getValue(type)}: ${points.getValue(type)[index].percentage.roundToInt()}%")
            }
            HorizontalDivider(color = Line)
        }
    }
}
