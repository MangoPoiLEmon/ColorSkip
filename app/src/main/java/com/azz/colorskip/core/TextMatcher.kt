package com.azz.colorskip.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Keyword matching with an important safety rule:
 *
 * English keywords are matched as **whole words**, not substrings. Without this,
 * "Skip" matches "ColorSkip", "colorskip-logs.txt", "skipping", etc. — exactly
 * the false-positive pattern seen in Outlook/Syncthing/file-manager logs.
 *
 * Chinese keywords keep substring matching because Chinese text has no spaces.
 */
object TextMatcher {
    private val asciiPatterns = ConcurrentHashMap<String, Regex>()

    fun indexOf(text: String, keyword: String): Int {
        if (text.isEmpty() || keyword.isEmpty()) return -1
        if (keyword.any { it.code > 127 }) {
            return text.indexOf(keyword, ignoreCase = true)
        }
        val pattern = asciiPatterns.computeIfAbsent(keyword.lowercase()) {
            Regex(
                "(?<![A-Za-z0-9])${Regex.escape(keyword)}(?![A-Za-z0-9])",
                RegexOption.IGNORE_CASE,
            )
        }
        return pattern.find(text)?.range?.first ?: -1
    }

    fun contains(text: String, keyword: String): Boolean = indexOf(text, keyword) >= 0
}
