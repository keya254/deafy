package tech.briankeya.opencaption.phone

import android.content.Context
import android.net.Uri
import tech.briankeya.opencaption.shared.AccessibilityProfile
import tech.briankeya.opencaption.shared.MobileSettings

/**
 * Imports an accessibility profile from the AI for Accessibility Toolkit
 * (its AbilityModel JSON, from `librarian.getAbilityModel()`) and applies the parts this app supports.
 *
 * Applied: caption text size and "start captions when the app opens".
 * Not used by this app yet: dark theme, high contrast, reduced motion, bold text,
 * large buttons, read-aloud speed, simpler language. These are listed in the result
 * so the user can see what was left out.
 */
object ProfileImport {
    private const val MAX_CHARS = 256_000

    /** Reads the file, applies it, and returns the note shown in Settings. */
    fun importFrom(context: Context, uri: Uri): String {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Could not open the file.")
        if (text.length > MAX_CHARS) {
            throw IllegalArgumentException("This file is too large to be a profile.")
        }
        val settings = try {
            AccessibilityProfile.renderMobileSettings(AccessibilityProfile.parse(text))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("This file is not an accessibility profile.", e)
        }
        return apply(settings)
    }

    /** Applies the supported settings and returns the note. Pure apart from [Settings]. */
    fun apply(settings: MobileSettings): String {
        val applied = mutableListOf<String>()
        if (settings.textScalePercent != 100) {
            Settings.captionTextScale = settings.textScalePercent / 100f
            applied += "caption text size ${settings.textScalePercent}%"
        }
        if (settings.captions) {
            Settings.startCaptionsOnOpen = true
            applied += "captions start when the app opens"
        }

        val notUsed = buildList {
            if (settings.darkMode) add("dark theme")
            if (settings.highContrast) add("high contrast")
            if (settings.boldText) add("bold text")
            if (settings.reduceMotion) add("reduced motion")
            if (settings.largeTargets) add("large buttons")
            if (settings.speechRate != 1.0) add("read-aloud speed")
            if (settings.simplifyLanguage) add("simpler language")
        }

        val note = buildString {
            append(if (applied.isEmpty()) "Profile read. Nothing in it changes captions yet." else "Applied: ${applied.joinToString(", ")}.")
            if (notUsed.isNotEmpty()) append(" Not used by this app yet: ${notUsed.joinToString(", ")}.")
        }
        Settings.profileNote = note
        return note
    }
}
