package com.intuiti.cardscanner.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device AI extraction via Google AICore (Gemini Nano).
 *
 * **Status:** the public AICore SDK is in restricted/experimental release. To
 * keep this app buildable on every developer machine and CI runner, the SDK is
 * not declared as a Gradle dependency. [checkAvailability] uses reflection to
 * see whether the SDK has been added to the classpath out-of-band, and the
 * extractor reports `SdkMissing` when it has not.
 *
 * Once the AI Edge SDK becomes a public Maven Central artifact, add the
 * dependency, then replace the reflective call below with a direct
 * `GenerativeModel(...)` invocation. The rest of the routing layer already
 * understands how to switch on availability.
 */
class AICoreExtractor(private val context: Context) {

    @Volatile
    private var availability: Availability = Availability.Unknown

    suspend fun checkAvailability(): Availability = withContext(Dispatchers.IO) {
        availability = try {
            // Reflection probe: try to find the GenerativeModel class. If absent,
            // mark SDK missing without touching the classpath.
            Class.forName("com.google.ai.edge.aicore.GenerativeModel")
            // SDK is present — but we still don't know whether *this* device has
            // an AICore service that supports vision. Optimistically say
            // text-only available; the real call site will fail loudly and the
            // router will fall back if not.
            Availability.AvailableTextOnly
        } catch (notFound: ClassNotFoundException) {
            Availability.SdkMissing(
                "Google AI Edge SDK is not bundled in this build. " +
                    "AICore vision support is still in restricted release.",
            )
        } catch (t: Throwable) {
            Log.i(TAG, "AICore probe failed", t)
            Availability.UnsupportedDevice(t.message ?: t.javaClass.simpleName)
        }
        availability
    }

    fun cachedAvailability(): Availability = availability

    suspend fun extract(imageUri: Uri): ExtractionResult {
        // Until the SDK is bundled, this engine never produces real output.
        // The router only calls extract() if checkAvailability() returned
        // AvailableTextOnly, but defend anyway.
        throw IllegalStateException(
            "AICore engine selected but the AI Edge SDK is not bundled in this build.",
        )
    }

    sealed interface Availability {
        data object Unknown : Availability
        data object AvailableTextOnly : Availability
        data class SdkMissing(val detail: String) : Availability
        data class UnsupportedDevice(val detail: String) : Availability
    }

    private companion object {
        const val TAG = "AICoreExtractor"
    }
}
