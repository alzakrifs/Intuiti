package com.intuiti.cardscanner.data

import android.content.Context
import android.net.Uri
import android.util.Log

class CardExtractor(context: Context) {
    private val mlKit = MlKitExtractor(context)
    private val claude = ClaudeExtractor(context)

    suspend fun extract(imageUri: Uri, apiKey: String): ExtractionResult {
        if (apiKey.isBlank()) {
            return mlKit.extract(imageUri)
        }
        return runCatching { claude.extract(imageUri, apiKey) }
            .getOrElse { error ->
                Log.w(TAG, "Claude extraction failed; falling back to ML Kit", error)
                val fallback = mlKit.extract(imageUri)
                fallback.copy(
                    source = ExtractionSource.MlKitFallback,
                    errorMessage = error.message?.takeIf { it.isNotBlank() }
                        ?: error.javaClass.simpleName,
                )
            }
    }

    companion object {
        private const val TAG = "CardExtractor"
    }
}
