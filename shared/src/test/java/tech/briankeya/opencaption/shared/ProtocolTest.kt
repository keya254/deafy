package tech.briankeya.opencaption.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun captionRoundTrip() {
        val original = CaptionMsg("habari yako", final = true, ts = 1_700_000_000_000L)
        assertEquals(original, CaptionMsg.from(original.toBytes()))
    }

    @Test
    fun alertRoundTrip() {
        val original = AlertMsg("Car horn", 0.73f, 1_700_000_000_000L, VibePattern.URGENT.name)
        val decoded = AlertMsg.from(original.toBytes())
        assertEquals(original.label, decoded.label)
        assertEquals(original.ts, decoded.ts)
        assertEquals(original.pattern, decoded.pattern)
        assertEquals(original.confidence, decoded.confidence, 0.001f)
    }

    @Test
    fun statusRoundTrip() {
        val original = StatusMsg(captions = true, sound = false, note = "Listening")
        assertEquals(original, StatusMsg.from(original.toBytes()))
    }

    @Test
    fun styleRoundTrip() {
        val original = StyleMsg(textScale = 1.4f, holdSeconds = 12, rightToLeft = true)
        assertEquals(original, StyleMsg.from(original.toBytes()))
    }

    @Test
    fun historyPageRoundTrip() {
        val lines = listOf(StoredLine("one", 1L), StoredLine("two", 2L))
        val decoded = HistoryPageMsg.from(HistoryPageMsg(lines, hasMore = true).toBytes())
        assertEquals(lines, decoded.lines)
        assertTrue(decoded.hasMore)
    }

    @Test
    fun commandRoundTrip() {
        assertEquals(Command.START_SOUND, Command.decode(Command.encode(Command.START_SOUND)))
        assertEquals(42L, Command.decodeSince(Command.encodeSync(42L)))
        val (before, limit) = Command.decodeHistory(Command.encodeHistory(before = 99L, limit = 20))
        assertEquals(99L, before)
        assertEquals(20, limit)
    }

    @Test
    fun unknownPatternFallsBackToShort() {
        assertEquals(VibePattern.SHORT, VibePattern.fromName("not-a-pattern"))
        assertEquals(VibePattern.SHORT, VibePattern.fromName(null))
    }

    @Test
    fun defaultSoundsAreAllKnownLabels() {
        for (label in SoundLabels.DEFAULT_ENABLED) {
            assertTrue("$label is enabled by default but not in SoundLabels.ALL", label in SoundLabels.ALL)
        }
    }

    @Test
    fun everyPatternKeyIsAKnownLabel() {
        for (label in SoundLabels.DEFAULT_PATTERN.keys) {
            assertTrue("$label has a pattern but is not in SoundLabels.ALL", label in SoundLabels.ALL)
        }
    }

    @Test
    fun shortNamesAreUniqueAcrossAlertsAndEvents() {
        val names = SoundLabels.ALL.values + SoundLabels.EVENTS.values
        assertEquals("Watch labels must be unique", names.size, names.toSet().size)
    }

    @Test
    fun captionEventsDoNotAlsoAlert() {
        for (event in SoundLabels.DEFAULT_EVENTS) {
            assertFalse("$event is a caption event and must not be an alert", event in SoundLabels.ALL)
        }
    }
}
