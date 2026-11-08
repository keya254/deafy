/*
 * Port of the mobile renderer from the AI for Accessibility Toolkit.
 *
 * Adapted from toolkit/surfaces/mobile.js and toolkit/core/strength.js in
 * https://github.com/AI-for-Accessibility-Collective/AI-for-Accessibility-Toolkit
 * (Copyright the AI for Accessibility Collective, Apache License 2.0).
 * The rules are the same as the original, so one profile means the same thing in both
 * projects. Changes made here: Kotlin types, and only the settings this app can use are
 * applied on the phone. See NOTICE.
 */
package tech.briankeya.opencaption.shared

import org.json.JSONArray
import org.json.JSONObject

/** One stated need from a profile, e.g. textSize = 1.5 (floor). */
data class ProfileNeed(val dimension: String, val value: Any?, val strength: String)

/** The part of an AbilityModel this app reads: supportAreas and needs. */
data class AbilityModel(val supportAreas: List<String>, val needs: List<ProfileNeed>)

/** Mobile accessibility settings, named like the iOS and Android accessibility settings. */
data class MobileSettings(
    val textScalePercent: Int,
    val lineSpacing: Double,
    val boldText: Boolean,
    val darkMode: Boolean,
    val highContrast: Boolean,
    val reduceTransparency: Boolean,
    val reduceMotion: Boolean,
    val captions: Boolean,
    val speechRate: Double,
    val simplifyLanguage: Boolean,
    val largeTargets: Boolean,
    val minTargetPt: Int,
)

object AccessibilityProfile {
    private const val SCHEMA_VERSION = 1
    private const val MIN_TARGET_PT_DEFAULT = 44 // iOS Human Interface Guidelines baseline
    private const val MIN_TARGET_PT_LARGE = 48   // Android accessibility guidance minimum

    /**
     * Reads an AbilityModel JSON object, as produced by the toolkit's
     * `librarian.getAbilityModel()`. Throws [IllegalArgumentException] if the text is not a profile.
     */
    fun parse(json: String): AbilityModel {
        val obj = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a JSON object", e)
        }
        if (!obj.has("needs") && !obj.has("supportAreas")) {
            throw IllegalArgumentException("Missing 'needs' and 'supportAreas'")
        }
        val version = obj.optInt("schemaVersion", SCHEMA_VERSION)
        if (version != SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported profile version $version")
        }

        val needs = obj.optJSONArray("needs").orEmpty().mapObjects { item ->
            val dimension = item.optString("dimension", "")
            if (dimension.isBlank()) null
            else ProfileNeed(
                dimension = dimension,
                value = if (item.has("value") && !item.isNull("value")) item.get("value") else null,
                strength = item.optString("strength", "preference"),
            )
        }.filterNotNull()

        return AbilityModel(
            supportAreas = obj.optJSONArray("supportAreas").orEmpty().mapStrings(),
            needs = needs,
        )
    }

    /** Turns a model into mobile settings. A model with no needs gives the OS defaults. */
    fun renderMobileSettings(model: AbilityModel): MobileSettings {
        val winners = resolveNeeds(model.needs)

        val textSize = (winners["textSize"] as? Number)?.toDouble() ?: 1.0
        val lineSpacing = (winners["lineSpacing"] as? Number)?.toDouble() ?: 1.0
        val contrast = truthy(winners["contrast"])
        val largeTarget = model.supportAreas.contains("motor")

        return MobileSettings(
            textScalePercent = Math.round(textSize * 100.0).toInt(),
            lineSpacing = lineSpacing,
            boldText = contrast,
            darkMode = truthy(winners["darkTheme"]),
            highContrast = contrast,
            reduceTransparency = contrast,
            reduceMotion = truthy(winners["reduceMotion"]),
            captions = truthy(winners["captions"]),
            speechRate = (winners["readAloudRate"] as? Number)?.toDouble() ?: 1.0,
            simplifyLanguage = truthy(winners["simplify"]),
            largeTargets = largeTarget,
            minTargetPt = if (largeTarget) MIN_TARGET_PT_LARGE else MIN_TARGET_PT_DEFAULT,
        )
    }

    /**
     * One winning value per dimension. A stronger strength wins; on a tie the later entry wins.
     * This matches the toolkit's rule, so all its surfaces agree.
     */
    private fun resolveNeeds(needs: List<ProfileNeed>): Map<String, Any?> {
        val winners = LinkedHashMap<String, Any?>()
        val strengthByDimension = HashMap<String, String>()
        for (need in needs) {
            val existing = strengthByDimension[need.dimension]
            if (existing != null && rankOf(need.strength) < rankOf(existing)) continue
            winners[need.dimension] = need.value
            strengthByDimension[need.dimension] = need.strength
        }
        return winners
    }

    /** floor > preference > hint. Unknown strengths count as preference. */
    private fun rankOf(strength: String): Int = when (strength) {
        "floor" -> 2
        "hint" -> 0
        else -> 1
    }

    /** JavaScript-style truthiness, so a need written as true, "light" or 1 all turn a setting on. */
    private fun truthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is String -> value.isNotEmpty()
        else -> true
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private fun <T : Any> JSONArray.mapObjects(transform: (JSONObject) -> T?): List<T?> =
        (0 until length()).map { i -> optJSONObject(i)?.let(transform) }

    private fun JSONArray.mapStrings(): List<String> =
        (0 until length()).mapNotNull { i -> optString(i, "").ifBlank { null } }
}
