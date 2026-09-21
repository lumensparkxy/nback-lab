package com.example.nback

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal val Ink = Color(0xFF182F5C)
internal val Paper = Color(0xFFFCFAF7)
internal val Muted = Color(0xFF606173)
internal val Cell = Color(0xFFE9E7ED)
internal val Apricot = Color(0xFFF9DDC8)
internal val SelectedCard = Color(0xFFFFF0E5)
internal val SelectedEdge = Color(0xFFE8CEBA)
internal val Line = Color(0xFFDEDAD6)

@Composable internal fun NBackTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Ink, onPrimary = Color.White, primaryContainer = Apricot, onPrimaryContainer = Ink,
        secondary = Muted, onSecondary = Color.White, secondaryContainer = Cell, onSecondaryContainer = Ink,
        tertiary = Color(0xFF78503B), onTertiary = Color.White, tertiaryContainer = Apricot, onTertiaryContainer = Ink,
        background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
        surfaceVariant = Cell, onSurfaceVariant = Muted, surfaceTint = Ink,
        surfaceDim = Line, surfaceBright = Paper, surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF6F2ED), surfaceContainer = Color(0xFFF0ECE7),
        surfaceContainerHigh = Cell, surfaceContainerHighest = Line,
        outline = Color(0xFF797781), outlineVariant = Line,
        inverseSurface = Ink, inverseOnSurface = Paper, inversePrimary = Apricot,
        error = Color(0xFFAA332C), onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    ), content = content)
}

@Composable internal fun AppIcon(id: Int, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription = null, modifier = modifier.size(24.dp))
}

@Composable internal fun QuietButton(label: String, action: () -> Unit, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = action, enabled = enabled, modifier = modifier.heightIn(min = 48.dp).testTag(tag)) {
        Text(label)
    }
}
