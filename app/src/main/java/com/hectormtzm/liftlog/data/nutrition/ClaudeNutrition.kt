package com.hectormtzm.liftlog.data.nutrition

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration

/** One food as estimated by Claude. Nutrients are totals for [quantity] x [unit]. */
data class EstimatedItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val gramsPerUnit: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

data class MealEstimate(val mealName: String, val items: List<EstimatedItem>, val notes: String)

class NutritionAiException(message: String) : Exception(message)

/**
 * Estimates foods and macros with Claude, from a photo or a text description.
 * Uses structured outputs so the reply is always JSON matching [schema].
 */
class ClaudeNutrition(
    private val apiKey: () -> String,
    private val baseUrl: String? = null, // tests only
) {

    suspend fun estimateFromPhoto(jpeg: ByteArray, note: String): MealEstimate {
        val image = BetaContentBlockParam.ofImage(
            BetaImageBlockParam.builder()
                .source(
                    BetaBase64ImageSource.builder()
                        .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                        .data(java.util.Base64.getEncoder().encodeToString(jpeg))
                        .build()
                )
                .build()
        )
        val text = buildString {
            append("Estimate the foods, portions and macros in this photo of a meal.")
            if (note.isNotBlank()) append("\nThe person added this note (trust it over the photo): ").append(note.trim())
        }
        return request(listOf(image, BetaContentBlockParam.ofText(text)))
    }

    suspend fun estimateFromText(description: String): MealEstimate =
        request(listOf(BetaContentBlockParam.ofText("Estimate the macros of what I ate:\n${description.trim()}")))

    /** Re-estimate a meal after the user corrected item names or amounts. */
    suspend fun reestimate(items: List<String>, jpeg: ByteArray?): MealEstimate {
        val text = "I corrected the items of this meal. Keep exactly these items, quantities and units " +
            "(one output item per line, same order), and estimate grams per unit and macros for each:\n" +
            items.joinToString("\n") { "- $it" }
        val blocks = buildList {
            if (jpeg != null) add(
                BetaContentBlockParam.ofImage(
                    BetaImageBlockParam.builder().source(
                        BetaBase64ImageSource.builder()
                            .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                            .data(java.util.Base64.getEncoder().encodeToString(jpeg))
                            .build()
                    ).build()
                )
            )
            add(BetaContentBlockParam.ofText(text))
        }
        return request(blocks)
    }

    private suspend fun request(content: List<BetaContentBlockParam>): MealEstimate = withContext(Dispatchers.IO) {
        val key = apiKey()
        if (key.isBlank()) throw NutritionAiException("Add your Anthropic API key in Settings to use Claude.")
        val client: AnthropicClient = AnthropicOkHttpClient.builder()
            .apiKey(key)
            .timeout(Duration.ofSeconds(120))
            .maxRetries(2)
            .apply { if (baseUrl != null) baseUrl(baseUrl) }
            .build()
        try {
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(8000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(
                    BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.LOW)
                        .format(BetaJsonOutputFormat.builder().schema(schema()).build())
                        .build()
                )
                // If a safety classifier declines, retry server-side on a suitable model.
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessageOfContentBlockParams(content)
                .build()
            val response = client.beta().messages().create(params)
            val stop = response.stopReason().orElse(null)
            if (stop == BetaStopReason.REFUSAL) {
                throw NutritionAiException("Claude declined to analyze this. Try a different photo or describe the meal.")
            }
            if (stop == BetaStopReason.MAX_TOKENS) {
                throw NutritionAiException("The response was cut off. Try again with fewer items.")
            }
            val json = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            parse(json)
        } catch (e: NutritionAiException) {
            throw e
        } catch (e: UnauthorizedException) {
            throw NutritionAiException("Your Anthropic API key was rejected. Check it in Settings.")
        } catch (e: PermissionDeniedException) {
            throw NutritionAiException("This API key does not have access to $MODEL.")
        } catch (e: RateLimitException) {
            throw NutritionAiException("Rate limited by the API. Wait a moment and try again.")
        } catch (e: AnthropicServiceException) {
            throw NutritionAiException("Claude API error (${e.statusCode()}). Try again.")
        } catch (e: AnthropicIoException) {
            throw NutritionAiException("Could not reach Claude. Check your internet connection.")
        } catch (e: Exception) {
            // Never crash the app over an estimate.
            throw NutritionAiException("Something went wrong talking to Claude (${e.javaClass.simpleName}).")
        } finally {
            client.close()
        }
    }

    companion object {
        const val MODEL = "claude-opus-5-5"

        internal val SYSTEM_PROMPT = """
            You estimate nutrition for a food logging app. The person will review and correct your
            estimate, so give your single best guess rather than ranges, and never ask questions.

            - Split the meal into the separate foods a person would log (e.g. "Scrambled eggs",
              "Whole wheat toast", "Butter"), including cooking oil, sauces and drinks when they are
              visible or clearly implied.
            - For each item give a natural quantity and unit ("2 large", "1 slice", "1 cup", "1 tbsp",
              or "150 g" when nothing else fits), the weight in grams of ONE unit, and the calories,
              protein, carbs and fat in grams for the whole quantity.
            - Use standard nutrition values (USDA-style) for the food as prepared. For photos, judge
              portion size from the plate, utensils and packaging.
            - meal_name is a short name for the whole meal, like "Eggs on toast".
            - If there is no food, return an empty items list and say why in notes. Otherwise use
              notes for one short sentence about the biggest uncertainty, or leave it empty.
        """.trimIndent()

        private fun schema(): BetaJsonOutputFormat.Schema {
            val num = mapOf("type" to "number")
            val str = mapOf("type" to "string")
            val item = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to str,
                    "quantity" to num,
                    "unit" to str,
                    "grams_per_unit" to num,
                    "calories" to num,
                    "protein_g" to num,
                    "carbs_g" to num,
                    "fat_g" to num,
                ),
                "required" to listOf("name", "quantity", "unit", "grams_per_unit", "calories", "protein_g", "carbs_g", "fat_g"),
                "additionalProperties" to false,
            )
            return BetaJsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty(
                    "properties", JsonValue.from(
                        mapOf(
                            "meal_name" to str,
                            "items" to mapOf("type" to "array", "items" to item),
                            "notes" to str,
                        )
                    )
                )
                .putAdditionalProperty("required", JsonValue.from(listOf("meal_name", "items", "notes")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build()
        }

        /** Parses the structured JSON reply. Internal for tests. */
        internal fun parse(json: String): MealEstimate {
            val o = try {
                JSONObject(json)
            } catch (e: Exception) {
                throw NutritionAiException("Could not read Claude's answer. Try again.")
            }
            val arr = o.optJSONArray("items")
            val items = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                val it = arr!!.getJSONObject(i)
                val qty = it.optDouble("quantity", 1.0).takeIf { q -> q.isFinite() && q > 0 } ?: 1.0
                EstimatedItem(
                    name = it.optString("name").trim().ifEmpty { return@mapNotNull null },
                    quantity = qty,
                    unit = it.optString("unit").trim().ifEmpty { "serving" },
                    gramsPerUnit = it.optDouble("grams_per_unit", 0.0).takeIf { g -> g.isFinite() && g >= 0 } ?: 0.0,
                    kcal = it.optDouble("calories", 0.0).coerceAtLeast(0.0),
                    protein = it.optDouble("protein_g", 0.0).coerceAtLeast(0.0),
                    carbs = it.optDouble("carbs_g", 0.0).coerceAtLeast(0.0),
                    fat = it.optDouble("fat_g", 0.0).coerceAtLeast(0.0),
                )
            }
            return MealEstimate(o.optString("meal_name").trim(), items, o.optString("notes").trim())
        }
    }
}
