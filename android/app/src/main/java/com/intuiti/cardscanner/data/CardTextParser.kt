package com.intuiti.cardscanner.data

/**
 * Heuristic field extractor over OCR'd text. Shared between every on-device
 * engine that hands us raw recognized text (Tesseract, ML Kit-style flows).
 *
 * The Latin path is the original parser ported from the web prototype.
 * The Arabic path is intentionally minimal — Arabic business cards rarely
 * use the same suffix/keyword vocabulary, so we only try to pick out the
 * universal fields (email / phone / website) and leave the name as the
 * longest non-ASCII line. Users edit the form before saving anyway, and
 * AI mode (Claude / AICore) handles the heavy lifting on Arabic cards.
 */
object CardTextParser {

    fun parse(rawText: String, isArabic: Boolean): ContactFields {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ContactFields()
        return if (isArabic) parseArabic(lines) else parseLatin(lines)
    }

    // ---------- Latin ----------

    private fun parseLatin(lines: List<String>): ContactFields {
        val consumed = mutableSetOf<Int>()

        val email = findFirst(lines, consumed, EMAIL_REGEX)?.lowercase().orEmpty()

        val website = findFirstIndexed(lines, consumed) { line ->
            if (line.contains('@')) null else URL_REGEX.find(line)?.value
        }?.let(::normalizeUrl).orEmpty()

        val phones = collectPhones(lines, consumed)
        val mobile = phones.firstOrNull { it.isMobile }?.raw.orEmpty()
        val main = phones.firstOrNull { !it.isMobile }?.raw
            ?: phones.firstOrNull { it.raw != mobile }?.raw
            ?: ""

        val org = findFirstIndexed(lines, consumed) { line ->
            if (ORG_REGEX.containsMatchIn(line)) line.trimEnd('|', '·', '•') else null
        }.orEmpty()

        val title = findFirstIndexed(lines, consumed) { line ->
            if (TITLE_REGEX.containsMatchIn(line) && line.length < 60) line else null
        }.orEmpty()

        var firstName = ""
        var lastName = ""
        for (i in lines.indices) {
            if (i in consumed) continue
            val line = lines[i]
            if (looksLikeName(line)) {
                val parts = line.split(Regex("\\s+"))
                firstName = parts.firstOrNull().orEmpty()
                lastName = parts.drop(1).joinToString(" ")
                consumed += i
                break
            }
        }

        val addressParts = mutableListOf<String>()
        for (i in lines.indices) {
            if (i in consumed) continue
            val line = lines[i]
            if (ADDRESS_KEYWORDS.containsMatchIn(line) || ZIP_REGEX.containsMatchIn(line)) {
                addressParts += line
                consumed += i
            }
        }

        return ContactFields(
            firstName = firstName,
            lastName = lastName,
            title = title,
            org = org,
            phone = main,
            mobile = mobile,
            email = email,
            website = website,
            address = addressParts.joinToString(", "),
        )
    }

    // ---------- Arabic ----------

    private fun parseArabic(lines: List<String>): ContactFields {
        val consumed = mutableSetOf<Int>()
        val email = findFirst(lines, consumed, EMAIL_REGEX)?.lowercase().orEmpty()
        val website = findFirstIndexed(lines, consumed) { line ->
            if (line.contains('@')) null else URL_REGEX.find(line)?.value
        }?.let(::normalizeUrl).orEmpty()

        val phones = collectPhones(lines, consumed)
        val mobile = phones.firstOrNull { it.isMobile }?.raw.orEmpty()
        val main = phones.firstOrNull { !it.isMobile }?.raw
            ?: phones.firstOrNull { it.raw != mobile }?.raw
            ?: ""

        // Pick the first Arabic-looking line as the name candidate.
        var firstName = ""
        var lastName = ""
        for (i in lines.indices) {
            if (i in consumed) continue
            val line = lines[i]
            if (line.any(::isArabicLetter) && line.length in 4..50) {
                val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                firstName = parts.firstOrNull().orEmpty()
                lastName = parts.drop(1).joinToString(" ")
                consumed += i
                break
            }
        }

        // Anything else with Arabic letters gets stashed in the address field as a
        // last-resort holder; the user will tidy this up in the review form.
        val rest = lines.withIndex()
            .filter { (i, line) -> i !in consumed && line.any(::isArabicLetter) }
            .map { it.value }

        return ContactFields(
            firstName = firstName,
            lastName = lastName,
            phone = main,
            mobile = mobile,
            email = email,
            website = website,
            address = rest.joinToString(", "),
        )
    }

    // ---------- Helpers ----------

    private data class Phone(val raw: String, val isMobile: Boolean)

    private fun collectPhones(lines: List<String>, consumed: MutableSet<Int>): List<Phone> {
        val phones = mutableListOf<Phone>()
        for (i in lines.indices) {
            if (i in consumed) continue
            val line = lines[i]
            val matches = PHONE_REGEX.findAll(line).map { it.value }.toList()
            if (matches.isEmpty()) continue
            val isMobile = MOBILE_LABEL.containsMatchIn(line)
            for (raw in matches) {
                val digits = raw.filter(Char::isDigit).length
                if (digits in 7..15) phones += Phone(raw.trim(), isMobile)
            }
            consumed += i
            if (phones.size >= 2) break
        }
        return phones
    }

    private fun findFirst(lines: List<String>, consumed: MutableSet<Int>, regex: Regex): String? {
        for (i in lines.indices) {
            if (i in consumed) continue
            val match = regex.find(lines[i])
            if (match != null) {
                consumed += i
                return match.value
            }
        }
        return null
    }

    private fun findFirstIndexed(
        lines: List<String>,
        consumed: MutableSet<Int>,
        match: (String) -> String?,
    ): String? {
        for (i in lines.indices) {
            if (i in consumed) continue
            val value = match(lines[i])
            if (value != null) {
                consumed += i
                return value
            }
        }
        return null
    }

    private fun looksLikeName(line: String): Boolean {
        if (line.length > 50) return false
        if (line.any(Char::isDigit)) return false
        if ('@' in line || '/' in line) return false
        val words = line.split(Regex("\\s+"))
        if (words.size !in 2..4) return false
        return words.all { NAME_WORD.matches(it) || ALL_CAPS_WORD.matches(it) }
    }

    private fun normalizeUrl(raw: String): String =
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw
        else "https://$raw"

    private fun isArabicLetter(c: Char): Boolean =
        c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' || c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿'

    private val EMAIL_REGEX = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val URL_REGEX = Regex(
        "\\b((https?://)?(www\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+(/[^\\s]*)?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val PHONE_REGEX = Regex("\\+?\\d[\\d\\s().-]{7,}\\d")
    private val MOBILE_LABEL = Regex("\\b(m|mob|mobile|cell|c)\\b[\\s.:]", RegexOption.IGNORE_CASE)
    private val ORG_REGEX = Regex(
        "\\b(inc\\.?|llc|ltd\\.?|gmbh|s\\.?a\\.?|co\\.?|corp\\.?|company|group|studio|labs|technologies|solutions|consulting|partners|holdings|agency)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val TITLE_REGEX = Regex(
        "\\b(ceo|cto|cfo|coo|cmo|founder|co-founder|president|vp|vice president|director|manager|engineer|developer|designer|architect|consultant|analyst|specialist|officer|head of|lead|principal|associate|coordinator|administrator|owner|partner|advisor|strategist|producer|editor|writer|sales|marketing|product|hr|account)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val ADDRESS_KEYWORDS = Regex(
        "\\b(street|st\\.?|avenue|ave\\.?|road|rd\\.?|blvd|boulevard|suite|ste\\.?|floor|fl\\.?|drive|dr\\.?|lane|ln\\.?|way|court|ct\\.?|po box|p\\.o\\. box)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val ZIP_REGEX = Regex("\\b\\d{5}(-\\d{4})?\\b")
    private val NAME_WORD = Regex("^[A-Z][A-Za-zÀ-ÖØ-öø-ÿ'’.-]+$")
    private val ALL_CAPS_WORD = Regex("^[A-ZÀ-Ö'’.-]+$")
}
