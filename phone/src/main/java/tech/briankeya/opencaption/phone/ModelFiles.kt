package tech.briankeya.opencaption.phone

import android.content.Context
import java.io.File

/**
 * Offline speech model files. They are not bundled in the APK (about 375 MB).
 * Copy them once to the app's external files folder (see docs/MODELS.md):
 *   <app files>/models/whisper-encoder.int8.onnx
 *   <app files>/models/whisper-decoder.int8.onnx
 *   <app files>/models/whisper-tokens.txt
 *   <app files>/models/silero_vad.onnx
 * After that the app works with no internet connection.
 */
object ModelFiles {
    const val WHISPER_ENCODER = "whisper-encoder.int8.onnx"
    const val WHISPER_DECODER = "whisper-decoder.int8.onnx"
    const val WHISPER_TOKENS = "whisper-tokens.txt"
    const val VAD = "silero_vad.onnx"

    /** Optional noise-reduction model (GTCRN). Captions still work without it. */
    const val DENOISER = "denoiser.onnx"

    private val REQUIRED = listOf(WHISPER_ENCODER, WHISPER_DECODER, WHISPER_TOKENS, VAD)

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "models")

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun missing(context: Context): List<String> =
        REQUIRED.filter { !file(context, it).isFile }

    fun isInstalled(context: Context): Boolean = missing(context).isEmpty()

    fun isDenoiserInstalled(context: Context): Boolean = file(context, DENOISER).isFile
}
