package com.hectormtzm.liftlog

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hectormtzm.liftlog.data.WeightUnit
import com.hectormtzm.liftlog.ui.LiftlogRoot
import com.hectormtzm.liftlog.ui.theme.LiftlogTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real app with sample data and writes PNGs to build/screenshots.
 * Not an assertion test: it is a way to eyeball the UI without a device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-night-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: LiftlogApp = ApplicationProvider.getApplicationContext()
    private val c get() = app.container
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        repeat(10) { rule.mainClock.advanceTimeBy(100); Thread.sleep(20) }
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun back() {
        repeat(20) { rule.mainClock.advanceTimeBy(50); Thread.sleep(10) }
        rule.onAllNodesWithContentDescription("Back")[0].performClick()
    }

    private fun waitText(text: String) = waitFor {
        rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** The clock is manual (the UI has ticking timers), so advance it while polling. */
    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() > end) { shot("zz_timeout"); error("timed out") }
            rule.mainClock.advanceTimeBy(50)
            Thread.sleep(20)
        }
    }

    private fun seed() = runBlocking {
        c.exercises.seedIfNeeded()
        val lb = WeightUnit.LB
        val planId = c.plans.createPlan("Push Day")
        c.plans.createPlan("Pull Day")
        c.plans.createPlan("Legs")
        c.plans.addExercises(planId, listOf("Barbell_Bench_Press_-_Medium_Grip", "Dumbbell_Shoulder_Press", "Cable_Incline_Pushdown"))
        val pes = c.plans.observeExercises(planId).first()
        c.plans.applyToAllSets(pes[0].item.id, 4, 8, lb.toKg(135.0)); c.plans.setRest(pes[0].item.id, 180)
        c.plans.applyToAllSets(pes[1].item.id, 3, 10, lb.toKg(40.0))
        c.plans.applyToAllSets(pes[2].item.id, 3, 12, lb.toKg(50.0)); c.plans.setRest(pes[2].item.id, 60)

        // A few past sessions with progression.
        val day = 86_400_000L
        listOf(135.0, 140.0, 140.0, 145.0, 150.0).forEachIndexed { i, w ->
            val wid = c.workouts.startFromPlan(planId)
            c.workouts.observeExercises(wid).first().forEach { we ->
                we.sets.forEach { s ->
                    if (we.item.position == 0) c.workouts.updateSet(s.id, s.reps, lb.toKg(w))
                    c.workouts.toggleSet(s.id)
                }
            }
            c.workouts.finish(wid)
            val dao = c.db.workoutDao()
            val wk = dao.getWorkout(wid)!!
            val start = System.currentTimeMillis() - (5 - i) * 3 * day
            dao.updateWorkout(wk.copy(startedAt = start, finishedAt = start + 52 * 60_000))
        }
    }

    @Test
    fun captureScreens() {
        seed()
        rule.mainClock.autoAdvance = false
        rule.setContent { LiftlogTheme { LiftlogRoot(openWorkoutRequests = 0) } }
        waitText("Push Day")
        shot("01_plans")

        rule.onNodeWithText("Push Day").performClick()
        waitText("Barbell Bench Press")
        shot("02_plan_edit")

        rule.onNodeWithText("Start workout", useUnmergedTree = true).performClick()
        waitText("PREVIOUS")
        shot("03_workout")
        // Mark the first two sets done -> rest timer bar appears
        rule.onAllNodesWithContentDescription("Mark set done")[0].performClick()
        waitFor { rule.onAllNodesWithContentDescription("Mark set not done").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription("Mark set done")[0].performClick()
        waitFor { rule.onAllNodesWithContentDescription("Mark set not done").fetchSemanticsNodes().size >= 2 }
        waitText("Rest")
        shot("04_workout_rest_timer")

        rule.onNodeWithText("Barbell Bench Press - Medium Grip", useUnmergedTree = true).performClick()
        waitText("Records")
        shot("05_exercise_detail")

        back()
        waitText("PREVIOUS")
        back()
        waitText("Resume workout")
        back()
        waitText("Workout in progress")
        shot("06_plans_active_banner")
        rule.onNodeWithText("History", useUnmergedTree = true).performClick()
        waitText("Duration")
        shot("07_history")

        rule.onNodeWithText("Exercises", useUnmergedTree = true).performClick()
        waitText("Search")
        shot("08_library")
    }
}
