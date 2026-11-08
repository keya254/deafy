package tech.briankeya.opencaption.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected values come from running the toolkit's own renderMobileSettings
 * (toolkit/surfaces/mobile.js) on the same inputs.
 */
class AccessibilityProfileTest {

    @Test
    fun noNeedsGivesOsDefaults() {
        val settings = render("""{"schemaVersion":1,"supportAreas":[],"needs":[]}""")
        assertEquals(100, settings.textScalePercent)
        assertEquals(1.0, settings.lineSpacing, 0.0)
        assertFalse(settings.boldText)
        assertFalse(settings.darkMode)
        assertFalse(settings.highContrast)
        assertFalse(settings.reduceTransparency)
        assertFalse(settings.reduceMotion)
        assertFalse(settings.captions)
        assertEquals(1.0, settings.speechRate, 0.0)
        assertFalse(settings.simplifyLanguage)
        assertFalse(settings.largeTargets)
        assertEquals(44, settings.minTargetPt)
    }

    @Test
    fun fullProfileMatchesToolkit() {
        val settings = render(
            """
            {"schemaVersion":1,"supportAreas":["vision","hearing"],"needs":[
              {"dimension":"textSize","value":1.6,"strength":"floor"},
              {"dimension":"contrast","value":"light","strength":"preference"},
              {"dimension":"darkTheme","value":true,"strength":"preference"},
              {"dimension":"reduceMotion","value":true,"strength":"preference"},
              {"dimension":"captions","value":true,"strength":"floor"},
              {"dimension":"readAloudRate","value":1.25,"strength":"preference"},
              {"dimension":"simplify","value":true,"strength":"hint"}
            ]}
            """.trimIndent()
        )
        assertEquals(160, settings.textScalePercent)
        assertTrue(settings.boldText)
        assertTrue(settings.darkMode)
        assertTrue(settings.highContrast)
        assertTrue(settings.reduceTransparency)
        assertTrue(settings.reduceMotion)
        assertTrue(settings.captions)
        assertEquals(1.25, settings.speechRate, 0.0)
        assertTrue(settings.simplifyLanguage)
        assertFalse(settings.largeTargets)
        assertEquals(44, settings.minTargetPt)
    }

    @Test
    fun strongerStrengthWinsAndMotorEnlargesTargets() {
        val settings = render(
            """
            {"supportAreas":["motor"],"needs":[
              {"dimension":"textSize","value":1.3,"strength":"hint"},
              {"dimension":"textSize","value":1.5,"strength":"floor"},
              {"dimension":"textSize","value":1.1,"strength":"preference"}
            ]}
            """.trimIndent()
        )
        assertEquals(150, settings.textScalePercent)
        assertTrue(settings.largeTargets)
        assertEquals(48, settings.minTargetPt)
        assertFalse(settings.captions)
    }

    @Test
    fun equalStrengthLaterEntryWins() {
        val settings = render(
            """
            {"needs":[
              {"dimension":"textSize","value":1.2,"strength":"preference"},
              {"dimension":"textSize","value":1.4,"strength":"preference"}
            ]}
            """.trimIndent()
        )
        assertEquals(140, settings.textScalePercent)
    }

    @Test
    fun unknownStrengthCountsAsPreference() {
        val settings = render(
            """
            {"needs":[
              {"dimension":"captions","value":true,"strength":"mystery"},
              {"dimension":"captions","value":false,"strength":"hint"}
            ]}
            """.trimIndent()
        )
        assertTrue(settings.captions)
    }

    @Test
    fun rejectsNonProfileJson() {
        assertThrows(IllegalArgumentException::class.java) { AccessibilityProfile.parse("not json") }
        assertThrows(IllegalArgumentException::class.java) { AccessibilityProfile.parse("{\"hello\":1}") }
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            AccessibilityProfile.parse("""{"schemaVersion":2,"needs":[]}""")
        }
    }

    @Test
    fun skipsNeedsWithoutDimension() {
        val model = AccessibilityProfile.parse("""{"needs":[{"value":true},{"dimension":"","value":true}]}""")
        assertTrue(model.needs.isEmpty())
    }

    private fun render(json: String): MobileSettings =
        AccessibilityProfile.renderMobileSettings(AccessibilityProfile.parse(json))
}
