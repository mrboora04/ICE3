package com.mrboora.plate.data

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration

/** One menu item's nutrition, found on the web by Claude. Values are for one item as served. */
data class AiFood(
    val name: String,
    val restaurant: String,
    val serving: String,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    /** "official", "third_party" or "estimate". */
    val confidence: String,
    val note: String,
    val sources: List<Pair<String, String>>,
) {
    fun toFoodItem() = FoodItem(
        name = name, brand = restaurant, kcal100 = kcal, protein100 = protein, carbs100 = carbs, fat100 = fat,
        servingG = 1f, servingLabel = serving, source = FoodItem.Source.CUSTOM, perItem = true,
    )
}

class AiLookupException(message: String) : Exception(message)

/**
 * Looks up restaurant and other foods by letting Claude search the web.
 * The user supplies their own Anthropic API key; it is only sent to api.anthropic.com.
 */
object AiLookup {
    const val MODEL = "claude-opus-5-5"

    private val SYSTEM = """
        You find nutrition facts for one food or menu item the user names, usually from a restaurant chain.
        Search the web. Prefer, in order: the restaurant's own nutrition guide or website for the user's country,
        then reputable nutrition databases, then other sites. Menus differ between countries (e.g. A&W Canada and
        A&W US are different companies), so match the country given.
        If sources disagree, use the official figure; with no official figure, use the most common value and say so in "note".
        Give values for exactly one item as normally served (not a combo) unless the user asks for a combo or size.
        Never present a guess as fact: if you found no published numbers, estimate from similar items, set
        confidence to "estimate" and explain in "note". If the item can't be identified at all, set found to false.

        Reply with only one JSON object and no other text:
        {"found": true, "name": "...", "restaurant": "... (empty for generic foods)", "serving": "e.g. 1 burger, 230 g",
         "kcal": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0,
         "confidence": "official" | "third_party" | "estimate",
         "note": "one short sentence for the user",
         "sources": [{"title": "...", "url": "https://..."}]}
    """.trimIndent()

    suspend fun lookup(apiKey: String, query: String, country: String): AiFood = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(120))
            .build()
        try {
            val base = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                .system(SYSTEM)
                // A lookup is a short, well-specified task: low effort keeps it quick and cheap.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addTool(WebSearchTool20260209.builder().maxUses(5L).build())
                .addUserMessage("Country: $country\nFood: $query")
            val response = try {
                run(client, withFallbacks(base.build()))
            } catch (e: BadRequestException) {
                // If the server-side fallback option is rejected, retry once without it.
                if (e.message?.contains("fallback", ignoreCase = true) == true) run(client, base.build()) else throw e
            }
            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                throw AiLookupException("Claude declined this lookup. Try rewording it.")
            }
            val text = response.content().mapNotNull { b -> b.text().orElse(null)?.text() }.joinToString("\n")
            parse(text) ?: throw AiLookupException("Couldn't read the answer. Try again or be more specific.")
        } catch (e: UnauthorizedException) {
            throw AiLookupException("Your Anthropic API key was rejected. Check it in Profile → AI lookup.")
        } catch (e: AnthropicServiceException) {
            throw AiLookupException("AI lookup failed (${e.statusCode()}). Try again in a moment.")
        } finally {
            client.close()
        }
    }

    /** Server-side refusal fallbacks: routes a refused request to another model instead of failing. */
    private fun withFallbacks(p: MessageCreateParams): MessageCreateParams = p.toBuilder()
        .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        .build()

    /** Web search can pause a long turn; continue it a few times by sending the partial reply back. */
    private fun run(client: com.anthropic.client.AnthropicClient, first: MessageCreateParams): Message {
        var params = first
        var response = client.messages().create(params)
        var rounds = 0
        while (response.stopReason().orElse(null) == StopReason.PAUSE_TURN && rounds < 3) {
            params = params.toBuilder().addMessage(response).build()
            response = client.messages().create(params)
            rounds++
        }
        return response
    }

    /** Pulls the JSON object out of the reply, tolerating stray text or code fences. */
    fun parse(text: String): AiFood? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() ?: return null
        if (!o.optBoolean("found", true)) throw AiLookupException(o.optString("note").ifBlank { "Couldn't find that item." })
        val kcal = o.optDouble("kcal", Double.NaN)
        if (kcal.isNaN() || kcal < 0) return null
        val sources = o.optJSONArray("sources")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { s ->
                    val url = s.optString("url")
                    if (url.startsWith("http")) s.optString("title").ifBlank { url } to url else null
                }
            }
        }.orEmpty()
        return AiFood(
            name = o.optString("name").ifBlank { return null },
            restaurant = o.optString("restaurant"),
            serving = o.optString("serving").ifBlank { "1 serving" },
            kcal = kcal.toFloat(),
            protein = o.optDouble("protein_g", 0.0).toFloat(),
            carbs = o.optDouble("carbs_g", 0.0).toFloat(),
            fat = o.optDouble("fat_g", 0.0).toFloat(),
            confidence = o.optString("confidence", "estimate"),
            note = o.optString("note"),
            sources = sources,
        )
    }
}
