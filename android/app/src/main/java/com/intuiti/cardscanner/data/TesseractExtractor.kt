package com.intuiti.cardscanner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.intuiti.cardscanner.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device OCR via Tesseract 4 with the LSTM English + Arabic language packs.
 * Language packs (~5–10 MB total) are downloaded into the app's private files
 * directory the first time the engine is used, then cached forever.
 */
class TesseractExtractor(private val context: Context) {

    private val tessDir: File = File(context.filesDir, "tessdata").apply { mkdirs() }
    private val initLock = Mutex()
    private var initialized: Boolean = false

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    suspend fun extract(imageUri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        ensureLanguagePacksDownloaded()
        val bitmap = decodeBitmap(imageUri)
        val text = recognize(bitmap)
        bitmap.recycle()
        val fields = parseFields(text)
        ExtractionResult(fields = fields, rawText = text, source = ExtractionSource.Tesseract)
    }

    private suspend fun ensureLanguagePacksDownloaded() {
        initLock.withLock {
            for (lang in LANGUAGES) {
                val target = File(tessDir, "$lang.traineddata")
                if (target.exists() && target.length() > MIN_TRAINEDDATA_BYTES) continue
                _status.value = Status.Downloading(lang)
                try {
                    download(traineddataUrl(lang), target)
                } catch (e: Throwable) {
                    if (target.exists()) target.delete()
                    _status.value = Status.Error("Could not download $lang language pack: ${e.message}")
                    throw e
                }
            }
            _status.value = Status.Ready
        }
    }

    private fun download(url: String, target: File) {
        val tmp = File(target.parentFile, "${target.name}.part")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw RuntimeException("HTTP ${connection.responseCode} fetching $url")
            }
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(target)) {
                throw RuntimeException("Could not move temp file into place")
            }
        } finally {
            connection.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        // Reuse the same EXIF-correcting downsample path the Claude extractor uses.
        val jpeg = ImageUtils.resizeToJpeg(context, uri, MAX_LONG_EDGE_PX, jpegQuality = 90)
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: error("Could not decode captured image")
    }

    private fun recognize(bitmap: Bitmap): String {
        val tess = TessBaseAPI()
        return try {
            // tess.init expects the *parent* of the tessdata directory.
            val ok = tess.init(context.filesDir.absolutePath, LANGUAGE_KEY)
            if (!ok) error("Tesseract init failed")
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tess.setImage(bitmap)
            tess.utF8Text.orEmpty()
        } finally {
            runCatching { tess.recycle() }
        }
    }

    /**
     * Picks the right field heuristic based on whether the recognized text contains
     * Arabic characters. Latin parsing reuses the same regexes the old ML Kit
     * implementation used.
     */
    private fun parseFields(rawText: String): ContactFields {
        val containsArabic = rawText.any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' }
        return CardTextParser.parse(rawText, isArabic = containsArabic)
    }

    sealed interface Status {
        data object Idle : Status
        data class Downloading(val language: String) : Status
        data object Ready : Status
        data class Error(val message: String) : Status
    }

    private companion object {
        const val MAX_LONG_EDGE_PX = 1600
        const val MIN_TRAINEDDATA_BYTES = 100_000L
        const val LANGUAGE_KEY = "eng+ara"
        val LANGUAGES = listOf("eng", "ara")
        const val TAG = "TesseractExtractor"

        fun traineddataUrl(lang: String): String =
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$lang.traineddata"
    }
}
