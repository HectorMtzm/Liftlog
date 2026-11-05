package com.hectormtzm.liftlog

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.hectormtzm.liftlog.ui.exercises.ExerciseLibraryScreen
import com.hectormtzm.liftlog.ui.exercises.PickMode
import com.hectormtzm.liftlog.ui.theme.LiftlogTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Long option lists (17 muscles) must be fully reachable, down to the last entries. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-night")
class PickerUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Swipes the open dialog's list up until [text] is laid out on screen. */
    private fun scrollDialogUntil(text: String) {
        repeat(10) {
            rule.waitForIdle()
            val visible = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().any { n ->
                n.boundsInRoot.height > 0 && n.boundsInRoot.top >= 0
            }
            if (visible) return
            // The dialog's list is the last scrollable node (after the filter row and results).
            rule.onAllNodes(hasScrollToNodeAction()).let { it[it.fetchSemanticsNodes().size - 1] }.performTouchInput { swipeUp() }
        }
        error("$text never became visible")
    }

    @Test
    fun everyMuscleCanBeSelected() {
        runBlocking { (ApplicationProvider.getApplicationContext<LiftlogApp>()).container.exercises.seedIfNeeded() }
        rule.setContent { LiftlogTheme { ExerciseLibraryScreen(rememberNavController(), PickMode.BROWSE, -1L) } }
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("3/4 Sit-Up")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()

        rule.onNodeWithText("Muscle").performClick()
        scrollDialogUntil("Triceps")
        rule.onNodeWithText("Triceps").performClick()
        // Dialog closes and the chip shows the choice; results are triceps exercises.
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Triceps")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Triceps  ·", substring = true)).fetchSemanticsNodes().isNotEmpty() }

        rule.waitForIdle()
        rule.onNodeWithText("Equipment").performClick()
        scrollDialogUntil("Upper Body Ergometer")
        rule.onNodeWithText("Upper Body Ergometer").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Upper Body Ergometer")).fetchSemanticsNodes().isNotEmpty() }
    }
}
