package com.azz.colorskip.core

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Pure rule matching over an AccessibilityNodeInfo tree. The service owns
 * clicking/gesture dispatch; this class only decides what should be clicked.
 */
class RuleEngine(private val rulesProvider: () -> List<SkipRule>) {

    data class NodeMatch(
        val rule: SkipRule,
        val node: AccessibilityNodeInfo,
        val clickTarget: AccessibilityNodeInfo,
        val label: String,
        val viewId: String?,
        val matchedBy: String,
        val score: Int,
        val targetBounds: Rect,
    )

    private data class Frame(val node: AccessibilityNodeInfo, val depth: Int)

    fun findNodeMatch(root: AccessibilityNodeInfo, packageName: String): NodeMatch? {
        val rules = rulesProvider().filter {
            it.enabled && !it.isCoordinate && it.hasNodeMatcher() && it.matchesPackage(packageName)
        }
        if (rules.isEmpty()) return null

        val candidates = LinkedHashMap<String, NodeMatch>()
        val queue = ArrayDeque<Frame>()
        queue.add(Frame(root, 0))
        val screen = Rect().also { root.getBoundsInScreen(it) }
        var visited = 0
        var adContext = false

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val frame = queue.removeFirst()
            val node = frame.node
            visited++

            val text = clean(node.text)
            val desc = clean(node.contentDescription)
            val hint = clean(node.hintText)
            val viewId = node.viewIdResourceName
            val shortId = viewId?.substringAfterLast('/')?.lowercase().orEmpty()
            val visible = node.isVisibleToUser

            if (!adContext && (looksLikeAd(text) || looksLikeAd(desc) || looksLikeAd(shortId))) {
                adContext = true
            }

            if (visible && frame.depth <= MAX_DEPTH) {
                for (rule in rules) {
                    val match = evaluate(rule, node, text, desc, hint, viewId, shortId, screen)
                    if (match != null) {
                        val key = "${match.targetBounds.boundsKey()}|${match.label}"
                        val old = candidates[key]
                        if (old == null || match.score > old.score) candidates[key] = match
                    }
                }
            }

            if (frame.depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                    queue.add(Frame(child, frame.depth + 1))
                }
            }
        }

        return candidates.values
            .asSequence()
            .filter { !it.rule.requireAdContext || adContext }
            .maxByOrNull { it.score }
    }

    fun findCoordinateRule(packageName: String): SkipRule? =
        rulesProvider()
            .asSequence()
            .filter { it.enabled && it.isCoordinate && it.matchesPackage(packageName) }
            .maxByOrNull { it.priority }

    /**
     * Used before a blind coordinate tap. This is intentionally conservative:
     * if a coordinate rule asks for ad context, at least one ad marker must be
     * present in the current tree.
     */
    fun hasAdContext(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 900) {
            val node = queue.removeFirst()
            visited++
            val text = clean(node.text)
            val desc = clean(node.contentDescription)
            val id = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
            if (looksLikeAd(text) || looksLikeAd(desc) || looksLikeAd(id)) return true
            for (i in 0 until node.childCount) {
                runCatching { node.getChild(i) }.getOrNull()?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun evaluate(
        rule: SkipRule,
        node: AccessibilityNodeInfo,
        text: String,
        desc: String,
        hint: String,
        viewId: String?,
        shortId: String,
        screen: Rect,
    ): NodeMatch? {
        if (!node.isEnabled) return null
        if (node.className?.toString()?.contains("EditText") == true) return null

        val texts = listOf(text, desc, hint).filter { it.isNotBlank() }
        if (texts.any { t -> rule.excludeKeywords.any { t.contains(it, ignoreCase = true) } }) {
            return null
        }

        var bestScore = 0
        var matchedBy = ""
        var label = ""

        for (t in texts) {
            for (keyword in rule.keywords) {
                if (keyword.isBlank()) continue
                val idx = TextMatcher.indexOf(t, keyword)
                if (idx < 0) continue
                val score = when {
                    t.equals(keyword, ignoreCase = true) -> 100
                    idx == 0 -> 90
                    else -> 75
                } - if (t.length > keyword.length + 18) 18 else 0
                if (score > bestScore) {
                    bestScore = score
                    matchedBy = if (t == desc) "contentDescription:$keyword" else "text:$keyword"
                    label = t
                }
            }
        }

        for (pattern in rule.contentDescriptions) {
            if (desc.isNotBlank() && desc.contains(pattern, ignoreCase = true)) {
                val score = 92
                if (score > bestScore) {
                    bestScore = score
                    matchedBy = "contentDescription:$pattern"
                    label = desc
                }
            }
        }

        for (pattern in rule.viewIds) {
            if (shortId.isBlank()) continue
            val hit = if (pattern.contains('*') || pattern.contains('?')) {
                Glob.matches(pattern, shortId) || Glob.matches(pattern, viewId.orEmpty())
            } else {
                shortId.contains(pattern, ignoreCase = true) ||
                    viewId.orEmpty().contains(pattern, ignoreCase = true)
            }
            if (hit) {
                val score = 84
                if (score > bestScore) {
                    bestScore = score
                    matchedBy = "viewId:$pattern"
                    label = texts.firstOrNull() ?: rule.name
                }
            }
        }

        if (bestScore == 0) return null

        val clickable = clickableAncestor(node)
        val target = clickable ?: node
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            node.getBoundsInScreen(bounds)
        }
        if (bounds.width() <= 0 || bounds.height() <= 0) return null

        var score = bestScore + (rule.priority / 2).coerceIn(-40, 60)
        if (clickable != null) score += 6
        val nodeBounds = Rect()
        node.getBoundsInScreen(nodeBounds)
        // Splash-ad skip controls usually live near a top corner.
        if (screen.width() > 0 && nodeBounds.top < screen.height() / 3) score += 4
        if (screen.width() > 0 && nodeBounds.right > screen.width() * 2 / 3) score += 3

        return NodeMatch(
            rule = rule,
            node = node,
            clickTarget = target,
            label = label.trim().take(64).ifBlank { rule.name },
            viewId = viewId,
            matchedBy = matchedBy,
            score = score,
            targetBounds = bounds,
        )
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && node.isEnabled) return node
        var current: AccessibilityNodeInfo? = node.parent
        var level = 0
        while (current != null && level < 4) {
            if (current.isClickable && current.isEnabled && current.isVisibleToUser) return current
            current = current.parent
            level++
        }
        return null
    }

    private fun clean(value: CharSequence?): String =
        value?.toString()?.replace('\u00A0', ' ')?.trim().orEmpty()

    private fun looksLikeAd(value: String): Boolean {
        if (value.isBlank()) return false
        val v = value.lowercase()
        if (v.contains("广告") || v.contains("赞助") || v.contains("推广") ||
            v.contains("开屏") || v.contains("advert") || v.contains("sponsored")
        ) {
            return true
        }
        return Regex("(^|[^a-z])ad([^a-z]|$)").containsMatchIn(v)
    }

    private fun Rect.boundsKey(): String = "$left,$top,$right,$bottom"

    companion object {
        private const val MAX_NODES = 1800
        private const val MAX_DEPTH = 26
    }
}
