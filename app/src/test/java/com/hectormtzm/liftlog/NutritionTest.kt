package com.hectormtzm.liftlog

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hectormtzm.liftlog.data.LiftlogDatabase
import com.hectormtzm.liftlog.data.nutrition.ClaudeNutrition
import com.hectormtzm.liftlog.data.nutrition.EstimatedItem
import com.hectormtzm.liftlog.data.nutrition.FoodEntity
import com.hectormtzm.liftlog.data.nutrition.MealEstimate
import com.hectormtzm.liftlog.data.nutrition.MealType
import com.hectormtzm.liftlog.data.nutrition.NutritionAiException
import com.hectormtzm.liftlog.data.nutrition.NutritionRepository
import com.hectormtzm.liftlog.data.nutrition.OpenFoodFacts
import com.hectormtzm.liftlog.data.nutrition.Portion
import com.hectormtzm.liftlog.data.nutrition.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class NutritionTest {
    private val app: LiftlogApp = ApplicationProvider.getApplicationContext()
    private val repo get() = app.container.nutrition

    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LiftlogDatabase::class.java)

    private fun near(expected: Double, actual: Double, tol: Double = 0.01) =
        assertTrue("expected $expected but was $actual", abs(expected - actual) <= tol)

    private val egg = FoodEntity(
        id = "usda_egg", name = "Egg, whole, cooked, scrambled", source = Source.USDA,
        kcal100 = 149.0, protein100 = 10.0, carbs100 = 1.6, fat100 = 11.0,
        portions = FoodEntity.encodePortions(listOf(Portion("1 large", 61.0))),
    )

    @Test
    fun migratesWorkoutDatabaseToV2KeepingData() {
        migrations.createDatabase("mig.db", 1).apply {
            execSQL("INSERT INTO plans (id, name, notes, position, createdAt) VALUES (1, 'Push', '', 0, 0)")
            close()
        }
        val db = migrations.runMigrationsAndValidate("mig.db", 2, true)
        db.query("SELECT name FROM plans").use { c -> c.moveToFirst(); assertEquals("Push", c.getString(0)) }
        db.query("SELECT COUNT(*) FROM meals").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }

    @Test
    fun manualFoodsGoIntoOneMealPerTypeAndRecalculate() = runBlocking {
        val day = 20000L
        val id1 = repo.addFood(day, MealType.BREAKFAST, null, egg, 2.0, "1 large", 61.0)
        val bread = egg.copy(id = "usda_bread", name = "Bread, whole-wheat", kcal100 = 254.0, protein100 = 12.0, carbs100 = 43.0, fat100 = 3.5, portions = "1 slice=32.0")
        val id2 = repo.addFood(day, MealType.BREAKFAST, null, bread, 2.0, "1 slice", 32.0)
        assertEquals("same breakfast meal", id1, id2)

        val meal = repo.observeMeal(id1).first()!!
        assertEquals(2, meal.items.size)
        // 2 eggs = 122 g -> 181.78 kcal; 2 slices = 64 g -> 162.56 kcal
        near(181.78 + 162.56, meal.macros.kcal)
        near(12.2 + 7.68, meal.macros.protein)

        // Change quantity: 3 eggs
        val eggItem = meal.sortedItems.first()
        repo.updateItem(eggItem.copy(quantity = 3.0))
        near(272.67 + 162.56, repo.observeMeal(id1).first()!!.macros.kcal)

        // Recently used foods are remembered and ranked first.
        assertEquals("usda_bread", repo.observeRecentFoods().first().first().id)

        assertEquals("usda_egg", repo.searchLocal("eggs").first().id)
        assertTrue(repo.searchLocal("whole wheat bread").any { it.id == "usda_bread" })

        val day1 = repo.observeDay(day).first()
        assertEquals(1, day1.size)
        assertEquals(MealType.BREAKFAST.name, day1.single().meal.mealType)
    }

    @Test
    fun claudeEstimateBecomesEditableMeal() = runBlocking {
        val est = MealEstimate(
            "Eggs on toast",
            listOf(
                EstimatedItem("Scrambled eggs", 2.0, "large", 61.0, 182.0, 12.2, 2.0, 13.4),
                EstimatedItem("Butter", 1.0, "tbsp", 14.0, 100.0, 0.1, 0.0, 11.3),
            ),
            "Butter amount is a guess.",
        )
        val id = repo.saveEstimate(20001L, MealType.LUNCH, null, est, Source.TEXT, null)
        val meal = repo.observeMeal(id).first()!!
        assertEquals("Eggs on toast", meal.meal.name)
        near(282.0, meal.macros.kcal)
        // Doubling the butter doubles its macros.
        val butter = meal.sortedItems[1]
        repo.updateItem(butter.copy(quantity = 2.0))
        near(382.0, repo.observeMeal(id).first()!!.macros.kcal)
        // Switching eggs to grams keeps the same weight -> same calories.
        val eggs = meal.sortedItems[0]
        repo.updateItem(eggs.copy(unit = "g", gramsPerUnit = 1.0, quantity = 122.0))
        near(382.0, repo.observeMeal(id).first()!!.macros.kcal)
        val portions = repo.portionsFor(eggs).map { it.label }
        assertTrue(portions.containsAll(listOf("large", "g")))
    }

    @Test
    fun searchRanksSensibly() {
        val foods = listOf(
            egg,
            egg.copy(id = "a", name = "Egg, whole, raw, fresh"),
            egg.copy(id = "b", name = "Eggnog"),
            egg.copy(id = "c", name = "Bread, egg"),
            egg.copy(id = "d", name = "Egg, white, raw, fresh"),
        )
        val r = NutritionRepository.rankFoods(foods, "eggs", 10).map { it.name }
        assertEquals("Egg, whole, raw, fresh", r.first())
        assertTrue("Bread, egg" in r)
        assertEquals(listOf("Egg, white, raw, fresh"), NutritionRepository.rankFoods(foods, "egg white", 10).map { it.name })
    }

    /** Same format tools/build_usda_foods.py writes. */
    @Test
    fun parsesBundledUsdaFoods() {
        val foods = NutritionRepository.parseBundledFoods(
            """[["172686","Bread, whole-wheat, commercially prepared",252.0,12.45,42.71,3.5,"1 slice=32"],
               ["171287","Egg, whole, raw, fresh",143.0,12.56,0.72,9.51,"1 cup=243|1 large=50|1 extra large=56"]]"""
        )
        assertEquals("usda_171287", foods[1].id)
        assertEquals(listOf("1 cup", "1 large", "1 extra large"), foods[1].portionList.map { it.label })
        assertEquals(50.0, foods[1].portionList[1].grams, 0.0)
        assertEquals(Source.USDA, foods[0].source)
    }

    @Test
    fun parsesClaudeJson() {
        val e = ClaudeNutrition.parse(
            """{"meal_name":"Burrito","items":[{"name":"Tortilla","quantity":1,"unit":"large","grams_per_unit":70,
               "calories":210,"protein_g":6,"carbs_g":36,"fat_g":5},{"name":"","quantity":1,"unit":"x","grams_per_unit":1,
               "calories":1,"protein_g":0,"carbs_g":0,"fat_g":0}],"notes":""}"""
        )
        assertEquals("Burrito", e.mealName)
        assertEquals(1, e.items.size)
        try {
            ClaudeNutrition.parse("not json"); fail()
        } catch (_: NutritionAiException) {
        }
    }

    @Test
    fun parsesOpenFoodFactsProduct() {
        val p = JSONObject(
            """{"code":"3017624010701","product_name":"Nutella","brands":"Ferrero,Nutella",
               "serving_size":"15 g","serving_quantity":15,
               "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9}}"""
        )
        val f = OpenFoodFacts.parseProduct(p, null)!!
        assertEquals("off_3017624010701", f.id)
        assertEquals("Ferrero", f.brand)
        assertEquals(15.0, f.portionList.single().grams, 0.0)
        near(539.0, f.kcal100)
    }

    /** Checks the exact request the app sends to the Messages API, against a local mock server. */
    @Test
    fun claudeRequestShape() = runBlocking {
        val server = MockWebServer()
        val reply = """{"meal_name":"Toast","items":[{"name":"Toast","quantity":2,"unit":"slice","grams_per_unit":30,
            "calories":160,"protein_g":6,"carbs_g":28,"fat_g":2}],"notes":""}"""
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                JSONObject()
                    .put("id", "msg_1").put("type", "message").put("role", "assistant")
                    .put("model", ClaudeNutrition.MODEL)
                    .put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", reply)))
                    .put("stop_reason", "end_turn").put("stop_sequence", JSONObject.NULL)
                    .put("usage", JSONObject().put("input_tokens", 10).put("output_tokens", 10))
                    .toString()
            )
        )
        server.start()
        try {
            val claude = ClaudeNutrition({ "sk-ant-test" }, baseUrl = server.url("/").toString().trimEnd('/'))
            val est = claude.estimateFromPhoto(byteArrayOf(1, 2, 3), "cooked in butter")
            assertEquals("Toast", est.items.single().name)

            val req = server.takeRequest()
            assertEquals("/v1/messages", req.requestUrl!!.encodedPath)
            // SDK 3.x sends the key as a bearer token; the API treats both forms as an API key.
            val auth = req.getHeader("x-api-key") ?: req.getHeader("Authorization")?.removePrefix("Bearer ")
            assertEquals("sk-ant-test", auth)
            assertTrue(req.getHeader("anthropic-beta")!!.contains("server-side-fallback-2026-07-01"))
            val body = JSONObject(req.body.readUtf8())
            assertEquals("claude-opus-5-5", body.getString("model"))
            assertTrue("system prompt must be top-level", body.getString("system").startsWith("You estimate nutrition"))
            assertEquals("only the user turn", 1, body.getJSONArray("messages").length())
            assertEquals("user", body.getJSONArray("messages").getJSONObject(0).getString("role"))
            assertEquals("default", body.getString("fallbacks"))
            assertEquals("low", body.getJSONObject("output_config").getString("effort"))
            val fmt = body.getJSONObject("output_config").getJSONObject("format")
            assertEquals("json_schema", fmt.getString("type"))
            assertEquals(false, fmt.getJSONObject("schema").getBoolean("additionalProperties"))
            val content = body.getJSONArray("messages").getJSONObject(body.getJSONArray("messages").length() - 1).getJSONArray("content")
            assertEquals("image", content.getJSONObject(0).getString("type"))
            assertEquals("image/jpeg", content.getJSONObject(0).getJSONObject("source").getString("media_type"))
            assertTrue(content.getJSONObject(1).getString("text").contains("cooked in butter"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun selectedModelIsUsedAndHaikuSkipsFallbacks() = runBlocking {
        val server = MockWebServer()
        fun reply() = MockResponse().setHeader("Content-Type", "application/json").setBody(
            JSONObject().put("id", "m").put("type", "message").put("role", "assistant").put("model", "x")
                .put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", """{"meal_name":"","items":[],"notes":"none"}""")))
                .put("stop_reason", "end_turn").put("stop_sequence", JSONObject.NULL)
                .put("usage", JSONObject().put("input_tokens", 1).put("output_tokens", 1)).toString()
        )
        server.enqueue(reply()); server.enqueue(reply())
        server.start()
        try {
            val url = server.url("/").toString().trimEnd('/')
            ClaudeNutrition({ "k" }, { com.hectormtzm.liftlog.data.ClaudeModel.HAIKU }, url).estimateFromText("tea")
            val haiku = server.takeRequest()
            val hb = JSONObject(haiku.body.readUtf8())
            assertEquals("claude-haiku-5-5", hb.getString("model"))
            assertTrue(!hb.has("fallbacks"))
            assertTrue(haiku.getHeader("anthropic-beta")?.contains("server-side-fallback") != true)

            ClaudeNutrition({ "k" }, { com.hectormtzm.liftlog.data.ClaudeModel.SONNET }, url).estimateFromText("tea")
            val sb = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("claude-sonnet-5-5", sb.getString("model"))
            assertEquals("default", sb.getString("fallbacks"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun pastMealsCanBeReAdded() = runBlocking {
        val est = MealEstimate("Oatmeal", listOf(EstimatedItem("Oats", 1.0, "cup", 80.0, 300.0, 10.0, 54.0, 5.0)), "")
        val first = repo.saveEstimate(20010L, MealType.BREAKFAST, null, est, Source.TEXT, null)
        repo.saveEstimate(20011L, MealType.BREAKFAST, null, est, Source.TEXT, null) // same meal again
        val past = repo.pastMeals()
        assertEquals("duplicates collapse", 1, past.count { it.title == "Oatmeal" })

        val copy = repo.copyMeal(first, 20020L, MealType.DINNER, null)
        val m = repo.observeMeal(copy).first()!!
        assertEquals("Oatmeal", m.meal.name)
        assertEquals(MealType.DINNER.name, m.meal.mealType)
        assertEquals(20020L, m.meal.epochDay)
        near(300.0, m.macros.kcal)
        // Editing the copy leaves the original alone.
        repo.updateItem(m.items.single().copy(quantity = 2.0))
        near(300.0, repo.observeMeal(first).first()!!.macros.kcal)
    }

    @Test
    fun migratesV2ToV3() {
        migrations.createDatabase("mig3.db", 2).apply {
            execSQL("INSERT INTO plans (id, name, notes, position, createdAt) VALUES (1, 'Push', '', 0, 0)")
            execSQL("INSERT INTO plan_exercises (id, planId, exerciseId, position, restSeconds, notes) VALUES (1, 1, 'x', 0, 90, '')")
            close()
        }
        val db = migrations.runMigrationsAndValidate("mig3.db", 3, true)
        db.query("SELECT customName FROM plan_exercises").use { c -> c.moveToFirst(); assertTrue(c.isNull(0)) }
    }

    @Test
    fun migratesV3ToV4() {
        migrations.createDatabase("mig4.db", 3).apply {
            execSQL("INSERT INTO exercises (id, name, category, equipment, level, force, mechanic, primaryMuscles, secondaryMuscles, instructions, images, isCustom) VALUES ('edb_0001', '3/4 Sit-Up', '', '', '', '', '', 'abs', '', 'Lie flat', '', 0)")
            close()
        }
        val db = migrations.runMigrationsAndValidate("mig4.db", 4, true)
        db.query("SELECT instructionsByLang FROM exercises").use { c -> c.moveToFirst(); assertTrue(c.isNull(0)) }
    }

    @Test
    fun missingKeyGivesFriendlyError() = runBlocking {
        try {
            ClaudeNutrition({ "" }).estimateFromText("2 eggs"); fail()
        } catch (e: NutritionAiException) {
            assertTrue(e.message!!.contains("Settings"))
        }
    }
}
