package com.intuiti.cardscanner.data

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Routes extraction requests to the appropriate concrete engine, falling back
 * to Tesseract on AI failure so the user always gets a result they can edit.
 */
class CardExtractor(context: Context) {
    val tesseract = TesseractExtractor(context)
    val aiCore = AICoreExtractor(context)
    private val claude = ClaudeExtractor(context)

    suspend fun extract(
        imageUri: Uri,
        preference: EnginePreference,
        apiKey: String,
        aiCoreAvailable: Boolean,
    ): ExtractionResult {
        val resolved = resolveEngine(preference, apiKey, aiCoreAvailable)
        return when (resolved) {
            ResolvedEngine.Tesseract -> tesseract.extract(imageUri)
            ResolvedEngine.AICore -> runAICoreOrFallback(imageUri)
            ResolvedEngine.Claude -> runClaudeOrFallback(imageUri, apiKey)
        }
    }

    private suspend fun runAICoreOrFallback(imageUri: Uri): ExtractionResult =
        runCatching { aiCore.extract(imageUri) }.getOrElse { error ->
            Log.w(TAG, "AICore failed; falling back to Tesseract", error)
            tesseract.extract(imageUri).copy(
                source = ExtractionSource.TesseractFallback,
                errorMessage = "AICore: ${friendly(error)}",
            )
        }

    private suspend fun runClaudeOrFallback(imageUri: Uri, apiKey: String): ExtractionResult =
        runCatching { claude.extract(imageUri, apiKey) }.getOrElse { error ->
            Log.w(TAG, "Claude failed; falling back to Tesseract", error)
            tesseract.extract(imageUri).copy(
                source = ExtractionSource.TesseractFallback,
                errorMessage = "Claude: ${friendly(error)}",
            )
        }

    private fun friendly(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName

    private fun resolveEngine(
        preference: EnginePreference,
        apiKey: String,
        aiCoreAvailable: Boolean,
    ): ResolvedEngine = when (preference) {
        EnginePreference.Tesseract -> ResolvedEngine.Tesseract
        EnginePreference.AICore -> if (aiCoreAvailable) ResolvedEngine.AICore else ResolvedEngine.Tesseract
        EnginePreference.Claude -> if (apiKey.isNotBlank()) ResolvedEngine.Claude else ResolvedEngine.Tesseract
        EnginePreference.Auto -> when {
            aiCoreAvailable -> ResolvedEngine.AICore
            else -> ResolvedEngine.Tesseract
        }
    }

    private enum class ResolvedEngine { Tesseract, AICore, Claude }

    companion object {
        private const val TAG = "CardExtractor"
    }
}
