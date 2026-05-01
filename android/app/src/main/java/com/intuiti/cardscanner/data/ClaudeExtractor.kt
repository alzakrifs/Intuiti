package com.intuiti.cardscanner.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.intuiti.cardscanner.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ClaudeExtractor(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun extract(imageUri: Uri, apiKey: String): ExtractionResult = withContext(Dispatchers.IO) {
        val resized = ImageUtils.resizeToJpeg(context, imageUri, MAX_LONG_EDGE_PX)
        val base64 = Base64.encodeToString(resized, Base64.NO_WRAP)

        val body = buildRequestBody(base64)
        val request = Request.Builder()
            .url(API_URL)
            .post(body.toString().toRequestBody(MEDIA_TYPE_JSON))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .build()

        val response = httpClient.newCall(request).await()
        response.use {
            val payload = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val message = extractErrorMessage(payload) ?: "HTTP ${it.code}"
                throw ClaudeApiException(message)
            }
            val text = extractText(payload)
                ?: throw ClaudeApiException("Empty response from Claude")
            val fields = parseFields(text)
            ExtractionResult(fields = fields, rawText = text, source = ExtractionSource.Claude)
        }
    }

    private fun buildRequestBody(base64Image: String): JsonObject = buildJsonObject {
        put("model", MODEL_ID)
        put("max_tokens", 1024)
        put(
            "system",
            "You extract structured contact information from photos of business cards. " +
                "Return only fields visible on the card. Use empty strings for missing fields. " +
                "Preserve phone numbers in their original format. " +
                "For 'phone', use the main work or office number; for 'mobile', use a number labeled cell or mobile. " +
                "Combine multi-line addresses into one comma-separated string.",
        )
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "image")
                                        put(
                                            "source",
                                            buildJsonObject {
                                                put("type", "base64")
                                                put("media_type", "image/jpeg")
                                                put("data", base64Image)
                                            },
                                        )
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", "Extract the contact information from this business card.")
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        put(
            "output_config",
            buildJsonObject {
                put("format", schemaFormat())
            },
        )
    }

    private fun schemaFormat(): JsonObject = buildJsonObject {
        put("type", "json_schema")
        put(
            "schema",
            buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        FIELD_KEYS.forEach { key ->
                            put(key, buildJsonObject { put("type", "string") })
                        }
                    },
                )
                put("required", buildJsonArray { FIELD_KEYS.forEach(::add) })
                put("additionalProperties", false)
            },
        )
    }

    private fun extractText(payload: String): String? {
        val root = parseRoot(payload) ?: return null
        val content = root["content"] as? JsonArray ?: return null
        val builder = StringBuilder()
        for (block in content) {
            val obj = block as? JsonObject ?: continue
            val type = (obj["type"] as? JsonPrimitive)?.content
            if (type == "text") {
                (obj["text"] as? JsonPrimitive)?.content?.let(builder::append)
            }
        }
        val text = builder.toString().trim()
        return text.ifEmpty { null }
    }

    private fun extractErrorMessage(payload: String): String? {
        val root = parseRoot(payload) ?: return null
        val error = root["error"] as? JsonObject ?: return null
        return (error["message"] as? JsonPrimitive)?.content
    }

    private fun parseRoot(payload: String): JsonObject? =
        runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()

    private fun parseFields(text: String): ContactFields {
        val parsed = runCatching { json.decodeFromString<RawFields>(text) }.getOrElse {
            throw ClaudeApiException("Could not parse JSON response")
        }
        return ContactFields(
            firstName = parsed.firstName.orEmpty().trim(),
            lastName = parsed.lastName.orEmpty().trim(),
            title = parsed.title.orEmpty().trim(),
            org = parsed.org.orEmpty().trim(),
            phone = parsed.phone.orEmpty().trim(),
            mobile = parsed.mobile.orEmpty().trim(),
            email = parsed.email.orEmpty().trim(),
            website = parsed.website.orEmpty().trim(),
            address = parsed.address.orEmpty().trim(),
        )
    }

    @Serializable
    private data class RawFields(
        val firstName: String? = null,
        val lastName: String? = null,
        val title: String? = null,
        val org: String? = null,
        val phone: String? = null,
        val mobile: String? = null,
        val email: String? = null,
        val website: String? = null,
        val address: String? = null,
    )

    private companion object {
        const val MODEL_ID = "claude-opus-4-7"
        const val API_URL = "https://api.anthropic.com/v1/messages"
        const val MAX_LONG_EDGE_PX = 1568
        val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
        val FIELD_KEYS = listOf(
            "firstName", "lastName", "title", "org",
            "phone", "mobile", "email", "website", "address",
        )
    }
}

class ClaudeApiException(message: String) : RuntimeException(message)

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
