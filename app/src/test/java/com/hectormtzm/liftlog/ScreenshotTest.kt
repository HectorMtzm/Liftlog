package com.hectormtzm.liftlog

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    private fun settle() = repeat(30) {
        rule.mainClock.advanceTimeBy(50)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        Thread.sleep(30)
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
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
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

    @Test
    fun captureNutrition() {
        runBlocking {
            val n = c.nutrition
            val today = java.time.LocalDate.now().toEpochDay()
            val egg = com.hectormtzm.liftlog.data.nutrition.FoodEntity(
                id = "usda_t1", name = "Egg, whole, cooked, scrambled", source = "usda",
                kcal100 = 149.0, protein100 = 10.0, carbs100 = 1.6, fat100 = 11.0, portions = "1 large=61.0",
            )
            val bread = egg.copy(id = "usda_t2", name = "Bread, whole-wheat, toasted", kcal100 = 306.0, protein100 = 12.5,
                carbs100 = 51.0, fat100 = 4.3, portions = "1 slice=28.0")
            val yogurt = egg.copy(id = "usda_t3", name = "Yogurt, Greek, plain, nonfat", kcal100 = 59.0, protein100 = 10.3,
                carbs100 = 3.6, fat100 = 0.4, portions = "1 container (170 g)=170.0")
            listOf(egg, bread, yogurt).forEach { n.saveFood(it) }
            val B = com.hectormtzm.liftlog.data.nutrition.MealType.BREAKFAST
            n.addFood(today, B, null, egg, 2.0, "1 large", 61.0)
            n.addFood(today, B, null, bread, 2.0, "1 slice", 28.0)
            n.addFood(today, com.hectormtzm.liftlog.data.nutrition.MealType.SNACK, null, yogurt, 1.0, "1 container (170 g)", 170.0)
            n.saveEstimate(
                today, com.hectormtzm.liftlog.data.nutrition.MealType.LUNCH, null,
                com.hectormtzm.liftlog.data.nutrition.MealEstimate(
                    "Chicken burrito bowl",
                    listOf(
                        com.hectormtzm.liftlog.data.nutrition.EstimatedItem("Grilled chicken breast", 150.0, "g", 1.0, 248.0, 46.5, 0.0, 5.4),
                        com.hectormtzm.liftlog.data.nutrition.EstimatedItem("White rice, cooked", 1.0, "cup", 158.0, 205.0, 4.3, 44.5, 0.4),
                        com.hectormtzm.liftlog.data.nutrition.EstimatedItem("Black beans", 0.5, "cup", 172.0, 114.0, 7.6, 20.4, 0.5),
                        com.hectormtzm.liftlog.data.nutrition.EstimatedItem("Guacamole", 2.0, "tbsp", 15.0, 50.0, 0.6, 2.6, 4.4),
                        com.hectormtzm.liftlog.data.nutrition.EstimatedItem("Pico de gallo", 0.25, "cup", 40.0, 7.0, 0.3, 1.5, 0.0),
                    ),
                    "Rice portion is the biggest uncertainty.",
                ),
                "text", null,
            )
        }
        rule.mainClock.autoAdvance = false
        rule.setContent { LiftlogTheme { LiftlogRoot(openWorkoutRequests = 0) } }
        waitText("Nutrition")
        rule.onNodeWithText("Nutrition", useUnmergedTree = true).performClick()
        waitText("Chicken burrito bowl")
        shot("10_nutrition_day")

        rule.onNodeWithText("Chicken burrito bowl", useUnmergedTree = true).performClick()
        waitText("Grilled chicken breast")
        settle()
        shot("11_meal_editor")
        back()

        waitText("Log food")
        repeat(20) { rule.mainClock.advanceTimeBy(50); Thread.sleep(10) }
        rule.onNodeWithText("Log food", useUnmergedTree = true).performClick()
        waitText("Search foods")
        shot("12_add_food_recent")
        repeat(20) { rule.mainClock.advanceTimeBy(50); Thread.sleep(10) }
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("egg")
        settle()
        shot("13_add_food_search")

    }
}
