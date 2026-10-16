package com.example.pfptodo.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

class StringResourceParityTest {

    private val expectedLanguages = listOf(
        "values" to "English (Default)",
        "values-fa" to "Persian",
        "values-es" to "Spanish",
        "values-fr" to "French",
        "values-de" to "German",
        "values-it" to "Italian",
        "values-pt" to "Portuguese",
        "values-ru" to "Russian",
        "values-ar" to "Arabic",
        "values-tr" to "Turkish",
        "values-zh-rCN" to "Simplified Chinese",
        "values-ja" to "Japanese"
    )

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Cannot find Android res directory in candidates: $candidates")
    }

    private fun parseStringKeys(xmlFile: File): Map<String, String> {
        assertTrue("Resource file ${xmlFile.path} must exist", xmlFile.exists())
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xmlFile)
        val stringNodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            val name = node.getAttribute("name")
            val text = node.textContent
            result[name] = text
        }
        return result
    }

    private fun extractPlaceholders(text: String): List<String> {
        val pattern = Pattern.compile("(%\\d+\\$[sdf]|%[sdf]|%%)")
        val matcher = pattern.matcher(text)
        val placeholders = mutableListOf<String>()
        while (matcher.find()) {
            matcher.group(1)?.let { placeholders.add(it) }
        }
        return placeholders
    }

    @Test
    fun `all 12 languages exist and have exact 1-to-1 key parity with base strings`() {
        val resDir = findResDirectory()
        val baseFile = File(resDir, "values/strings.xml")
        val baseStrings = parseStringKeys(baseFile)

        assertTrue("Base strings.xml must have string resources", baseStrings.isNotEmpty())

        for ((folder, languageName) in expectedLanguages) {
            val langFile = File(resDir, "$folder/strings.xml")
            assertTrue("strings.xml must exist for $languageName in $folder", langFile.exists())

            val langStrings = parseStringKeys(langFile)

            val missingKeys = baseStrings.keys - langStrings.keys
            assertTrue(
                "Language $languageName ($folder) is missing keys: $missingKeys",
                missingKeys.isEmpty()
            )

            val extraKeys = langStrings.keys - baseStrings.keys
            assertTrue(
                "Language $languageName ($folder) has extra keys not in base: $extraKeys",
                extraKeys.isEmpty()
            )

            assertEquals(
                "Key count for $languageName ($folder) must match base exactly",
                baseStrings.size,
                langStrings.size
            )
        }
    }

    @Test
    fun `persian fa strings have exact bidirectional key parity with base strings`() {
        val resDir = findResDirectory()
        val baseStrings = parseStringKeys(File(resDir, "values/strings.xml"))
        val faStrings = parseStringKeys(File(resDir, "values-fa/strings.xml"))

        val missingInFa = baseStrings.keys - faStrings.keys
        assertTrue(
            "Persian (values-fa) is missing keys present in base: $missingInFa",
            missingInFa.isEmpty()
        )

        val extraInFa = faStrings.keys - baseStrings.keys
        assertTrue(
            "Persian (values-fa) has keys not present in base: $extraInFa",
            extraInFa.isEmpty()
        )

        assertEquals(
            "Persian key count must match base exactly",
            baseStrings.size,
            faStrings.size
        )
    }

    @Test
    fun `all format placeholders match between base strings and all translated strings`() {
        val resDir = findResDirectory()
        val baseFile = File(resDir, "values/strings.xml")
        val baseStrings = parseStringKeys(baseFile)

        for ((folder, languageName) in expectedLanguages) {
            if (folder == "values") continue
            val langFile = File(resDir, "$folder/strings.xml")
            val langStrings = parseStringKeys(langFile)

            for ((key, baseText) in baseStrings) {
                val basePlaceholders = extractPlaceholders(baseText).filter { it != "%%" }
                val translatedText = langStrings[key]
                assertNotNull("Key $key must exist in $languageName", translatedText)
                val translatedPlaceholders = extractPlaceholders(translatedText!!).filter { it != "%%" }

                // Strict TYPE check: the exact set of format specifiers must match,
                // e.g. a base '%1$s' mistranslated as '%1$d' must fail the build.
                // Sorted (order-insensitive) so positional reordering in RTL/localised
                // word order stays legal.
                assertEquals(
                    "Placeholder specifiers for key '$key' in $languageName ($folder) must exactly match base. Base: '$baseText' vs Translated: '$translatedText'",
                    basePlaceholders.sorted(),
                    translatedPlaceholders.sorted()
                )
            }
        }
    }
}
