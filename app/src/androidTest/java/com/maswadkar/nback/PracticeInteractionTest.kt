package com.maswadkar.nback

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maswadkar.nback.engine.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PracticeInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var time = 0L
    private val game = VisualSession(MonotonicClock { time }) { n -> generateSequence(Random(1), n) }
    private val state = mutableStateOf(game.state)
    private val settings = mutableStateOf(SettingsState(loading = false))
    private val scale = mutableStateOf(1f)
    private var retries = 0
    private fun publish() { state.value = game.state }
    private fun at(t: Long) { compose.runOnIdle { time = t; game.advance(); publish() } }
    @Before fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                SessionContent(state.value, { game.start(state.value.config.level); publish() }, { game.match(); publish() }, { game.home(); publish() },
                    settings.value, { settings.value = settings.value.copy(level = it) },
                    { game.start(settings.value.level, true); publish() }, { game.nextExample(it); publish() }, { retries++ })
            }
        }
    }
    private fun practice() = compose.onNodeWithTag("practice").performScrollTo().performClick()

    @Test fun settingsLoadingSelectionSemanticsAndRetryAreUsable() {
        compose.runOnIdle { settings.value = SettingsState(loading = true) }
        compose.onNodeWithTag("settings").performScrollTo().performClick()
        compose.onNodeWithTag("level_1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("settings_back").performScrollTo().performClick()
        compose.onNodeWithTag("start").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("practice").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { settings.value = SettingsState(loading = false) }
        compose.onNodeWithTag("settings").performScrollTo().performClick()
        for (n in 1..3) {
            compose.onNodeWithTag("level_$n").performScrollTo().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
            compose.onNodeWithText("Position · $n-back").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("$n warm-up ${if (n == 1) "turn" else "turns"} · ${elapsedLabel((n+20)*3000L)} total (minutes:seconds)", substring=true).performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { settings.value = settings.value.copy(notice = SettingsNotice.SAVE_FAILED) }
        compose.onNodeWithTag("retry").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @CriticalCi @Test fun practiceExplanationsAndExplicitCompletionActionsStaySeparate() {
        practice()
        var start = 6_000L
        repeat(4) { step ->
            at(start)
            if (step < 2) compose.onNodeWithTag("match").performClick()
            compose.onNodeWithTag("feedback_answer").assertDoesNotExist()
            at(start + 3_000)
            compose.onNodeWithText(if (step % 2 == 0) "Position matched." else "Position differed.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(if (step < 2) "You tapped Position match." else "You waited.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("accuracy").assertDoesNotExist()
            if (step < 3) {
                at(time + 90_000)
                assertEquals(step + 1, game.state.progress)
                compose.onNodeWithTag("next").performScrollTo().performClick()
                start = time
            }
        }
        compose.onNodeWithText("Practice complete").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("practice_again").performScrollTo().performClick()
        assertTrue(game.state.config.practice); assertEquals(1, game.state.trial)
        compose.onNodeWithTag("skip").performClick()
        assertEquals(SessionScreen.HOME, game.state.screen)
    }

    @Test fun heldMatchCannotActivateNextWhenFeedbackReplacesControls() {
        practice(); at(8_999)
        compose.onNodeWithTag("match").performTouchInput { down(center) }
        at(9_000)
        compose.onRoot().performTouchInput { up() }
        assertEquals(SessionScreen.PRACTICE_FEEDBACK, game.state.screen)
        assertFalse(game.state.feedback!!.responded)
        compose.onNodeWithTag("next").performScrollTo().performClick()
        assertEquals(2, game.state.progress)
    }

    @Test fun practiceGridAndControlsRemainUsableAtLargeFont() {
        compose.runOnIdle { scale.value = 2f }
        practice()
        val warm = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot
        at(6_000)
        val prompt = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("match").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(warm,prompt)
        assertEquals(prompt,compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("skip").assertIsDisplayed()
    }
}
