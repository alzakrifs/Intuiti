package com.intuiti.cardscanner.data

import kotlinx.serialization.Serializable

@Serializable
data class ContactFields(
    val firstName: String = "",
    val lastName: String = "",
    val title: String = "",
    val org: String = "",
    val phone: String = "",
    val mobile: String = "",
    val email: String = "",
    val website: String = "",
    val address: String = "",
) {
    val isEmpty: Boolean
        get() = firstName.isBlank() && lastName.isBlank() && org.isBlank() &&
            phone.isBlank() && mobile.isBlank() && email.isBlank()

    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

enum class ExtractionSource {
    Claude,
    AICore,
    Tesseract,
    /** AI engine (Claude or AICore) was selected, failed, and Tesseract picked up the slack. */
    TesseractFallback,
}

data class ExtractionResult(
    val fields: ContactFields,
    val rawText: String,
    val source: ExtractionSource,
    val errorMessage: String? = null,
)
