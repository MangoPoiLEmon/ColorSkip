package com.azz.colorskip.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuleParserTest {

    @Test
    fun parsesJsonRule() {
        val raw = """
            {
              "rules": [
                {
                  "name": "测试",
                  "packages": ["com.example.app"],
                  "keywords": ["跳过", "Skip"],
                  "excludeKeywords": ["跳过教程"],
                  "viewIds": ["splash_skip"],
                  "cooldownMs": 1800,
                  "priority": 88
                }
              ]
            }
        """.trimIndent()
        val result = RuleParser.parse(raw)
        assertEquals("JSON", result.format)
        assertEquals(1, result.rules.size)
        val rule = result.rules.first()
        assertEquals("测试", rule.name)
        assertEquals(listOf("com.example.app"), rule.packages)
        assertTrue(rule.keywords.contains("Skip"))
        assertEquals(1800L, rule.cooldownMs)
        assertEquals(88, rule.priority)
        assertTrue(rule.hasNodeMatcher())
    }

    @Test
    fun parsesYamlRuleAndCoordinatePercent() {
        val raw = """
            rules:
              - name: Flutter 开屏
                packages: [com.example.flutter]
                clickMode: coordinate
                xPercent: 90
                yPercent: 8
                delayMs: 600
                requireAdContext: false
        """.trimIndent()
        val result = RuleParser.parse(raw)
        assertEquals("YAML", result.format)
        assertEquals(1, result.rules.size)
        val rule = result.rules.first()
        assertTrue(rule.isCoordinate)
        assertEquals(0.90f, rule.xPercent ?: 0f, 0.001f)
        assertEquals(0.08f, rule.yPercent ?: 0f, 0.001f)
        assertEquals(false, rule.requireAdContext)
    }

    @Test
    fun coordinateRuleWithoutPackageIsRejected() {
        val raw = """
            rules:
              - name: bad
                clickMode: coordinate
                xPercent: 90
                yPercent: 8
        """.trimIndent()
        val result = RuleParser.parse(raw)
        assertTrue(result.rules.isEmpty())
        assertNotNull(result.error)
    }

    @Test
    fun roundTripsExportedRules() {
        val first = RuleParser.parse(
            """{"rules":[{"name":"x","packages":["com.a"],"keywords":["跳过"]}]}""",
        ).rules
        val exported = RuleParser.toJson(first)
        val second = RuleParser.parse(exported).rules
        assertEquals(1, second.size)
        assertEquals("x", second.first().name)
        assertEquals(listOf("com.a"), second.first().packages)
    }

    @Test
    fun builtinRulesParseAndHaveCoverage() {
        val file = File("src/main/assets/rules/builtin_rules.json")
        if (!file.exists()) return
        val result = RuleParser.parse(file.readText())
        assertTrue(result.error, result.rules.size >= 8)
        val packages = result.rules.flatMap { it.packages }.toSet()
        assertTrue(packages.contains("com.taobao.taobao"))
        assertTrue(packages.contains("tv.danmaku.bili"))
        assertTrue(packages.contains("com.sina.weibo"))
    }

    @Test
    fun englishKeywordsRequireWordBoundaries() {
        assertEquals(-1, TextMatcher.indexOf("ColorSkip", "Skip"))
        assertEquals(-1, TextMatcher.indexOf("colorskip-logs.txt", "Skip"))
        assertEquals(-1, TextMatcher.indexOf("ColorSkip-1.0.1-release.apk", "Skip"))
        assertEquals(-1, TextMatcher.indexOf("Skipping", "Skip"))
        assertEquals(0, TextMatcher.indexOf("Skip 5", "Skip"))
        assertEquals(3, TextMatcher.indexOf("跳过 Skip", "Skip"))
        assertEquals(0, TextMatcher.indexOf("跳过 05", "跳过"))
    }

    @Test
    fun genericCloseRuleNoLongerMatchesBareClose() {
        val file = File("src/main/assets/rules/builtin_rules.json")
        if (!file.exists()) return
        val result = RuleParser.parse(file.readText())
        val close = result.rules.first { it.id == "builtin.ad.close" }
        assertTrue(close.keywords.contains("关闭广告"))
        assertTrue(!close.keywords.contains("关闭"))
    }

    @Test
    fun blocksLaunchersAndRecents() {
        assertTrue(PackageBlocklist.isBlocked("com.coloros.launcher"))
        assertTrue(PackageBlocklist.isBlocked("com.oplus.launcher"))
        assertTrue(PackageBlocklist.isBlocked("com.android.launcher3"))
        assertTrue(PackageBlocklist.isBlocked("com.android.quickstep"))
        assertTrue(PackageBlocklist.isBlocked("com.oplus.recents"))
        assertTrue(PackageBlocklist.isBlocked("com.azz.colorskip"))
        assertTrue(!PackageBlocklist.isBlocked("com.taobao.taobao"))
    }
}
