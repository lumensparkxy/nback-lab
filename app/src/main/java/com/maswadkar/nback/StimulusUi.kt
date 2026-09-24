package com.maswadkar.nback

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maswadkar.nback.engine.StimulusType
import com.maswadkar.nback.engine.activeTypes

internal object StimulusColours {
    val palette = listOf(Color(0xFFD32F2F), Color(0xFF1565C0), Color(0xFF2E7D32),
        Color(0xFFFDD835), Color(0xFF7B1FA2), Color(0xFFEF6C00))
    val neutral = Color(0xFF18675A)
    val inactive = Color(0xFFE1E6DF)
    fun foreground(fill: Color) = if (fill.luminance() > 0.179f) Color.Black else Color.White
}

@Composable internal fun typeLabel(type: StimulusType) = stringResource(when (type) {
    StimulusType.POSITION -> R.string.type_position
    StimulusType.COLOUR -> R.string.type_colour
    StimulusType.NUMBER -> R.string.type_number
})
@Composable internal fun modeLabel(mask: Int) = activeTypes(mask).map { typeLabel(it) }.joinToString(" + ")
@Composable internal fun modeTitle(mask: Int, n: Int, practice: Boolean = false) =
    stringResource(if (practice) R.string.practice_mode_title else R.string.mode_title, modeLabel(mask), n)
@Composable internal fun valueLabel(type: StimulusType, value: Int): String = when (type) {
    StimulusType.POSITION -> stringResource(R.string.cell_value, value + 1)
    StimulusType.NUMBER -> (value + 1).toString()
    StimulusType.COLOUR -> stringResource(listOf(R.string.colour_red, R.string.colour_blue, R.string.colour_green,
        R.string.colour_yellow, R.string.colour_purple, R.string.colour_orange)[value])
}

@Composable internal fun TypeInstructions(mask: Int, n: Int) {
    Text(pluralStringResource(R.plurals.type_instructions, n, n), style = MaterialTheme.typography.bodyLarge)
    for (type in activeTypes(mask)) Text(stringResource(when (type) {
        StimulusType.POSITION -> R.string.position_rule
        StimulusType.COLOUR -> R.string.colour_rule
        StimulusType.NUMBER -> R.string.number_rule
    }))
    if (mask and StimulusType.COLOUR.bit != 0) Text(stringResource(R.string.colour_palette))
}

@Composable internal fun SessionExample(mask: Int, n: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0..n) {
            val example = if (i == n) 0 else i
            Column(Modifier.weight(1f)) {
                StimulusView(activeTypes(mask).associateWith { if (it == StimulusType.POSITION) example * 4 else example },
                    mask, Modifier.fillMaxWidth().height(56.dp), "example_$i")
                Text(if (i == n) "A" else ('A' + i).toString(), Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text(stringResource(R.string.example_types, ((0 until n).map { ('A' + it).toString() } + "A").joinToString(" → ")), style = MaterialTheme.typography.bodySmall, color = Muted)
}

/** No changing stimulus semantics: the visual values are never automatically announced. */
@Composable internal fun StimulusView(values: Map<StimulusType, Int>, mask: Int, modifier: Modifier = Modifier, tag: String = "grid") {
    val spatial = mask and 1 != 0
    val description = stringResource(if (spatial) R.string.visual_grid else R.string.visual_stimulus)
    BoxWithConstraints(modifier.semantics { contentDescription = description }.testTag(tag), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 420.dp)
        Canvas(Modifier.size(side)) {
            val gap = size.width * 0.035f
            val cellSide = (size.width - gap * 2) / 3
            val highlighted = if (spatial) values[StimulusType.POSITION] else 4.takeIf { values.keys.any { mask and it.bit != 0 } }
            repeat(9) { index ->
                if (!spatial && index != 4) return@repeat
                val offset = Offset((index % 3) * (cellSide + gap), (index / 3) * (cellSide + gap))
                val radius = CornerRadius(cellSide * 0.14f)
                val active = index == highlighted
                val colour = values[StimulusType.COLOUR]?.takeIf { mask and StimulusType.COLOUR.bit != 0 }
                val fill = if (active) colour?.let { StimulusColours.palette[it] } ?: StimulusColours.neutral else StimulusColours.inactive
                drawRoundRect(fill, offset, Size(cellSide, cellSide), radius)
                if (active) {
                    val foreground = StimulusColours.foreground(fill)
                    if (colour != null) drawRoundRect(Color.Black, offset, Size(cellSide, cellSide), radius, style = Stroke(2.dp.toPx()))
                    // Keep the inset border from crowding digits in small Home examples.
                    val inset = minOf(4.dp.toPx(), cellSide * 0.08f)
                    drawRoundRect(foreground, offset + Offset(inset, inset), Size(cellSide - inset * 2, cellSide - inset * 2), radius, style = Stroke(minOf(2.dp.toPx(), cellSide * 0.06f)))
                    values[StimulusType.NUMBER]?.takeIf { mask and StimulusType.NUMBER.bit != 0 }?.let { number ->
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = foreground.toArgb(); textAlign = Paint.Align.CENTER
                            textSize = cellSide * 0.60f; isFakeBoldText = true
                        }
                        drawContext.canvas.nativeCanvas.drawText((number + 1).toString(), offset.x + cellSide / 2,
                            offset.y + cellSide / 2 - (paint.ascent() + paint.descent()) / 2, paint)
                    }
                }
            }
        }
    }
}
