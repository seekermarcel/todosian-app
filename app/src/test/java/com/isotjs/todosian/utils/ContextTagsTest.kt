package com.isotjs.todosian.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTagsTest {
    private val prefix = "#kontext/"

    @Test
    fun normalizePrefix_defaults_and_adds_hash() {
        assertEquals("#context/", ContextTags.normalizePrefix(null))
        assertEquals("#context/", ContextTags.normalizePrefix("  "))
        assertEquals("#kontext/", ContextTags.normalizePrefix("kontext/"))
        assertEquals("#@", ContextTags.normalizePrefix("#@"))
    }

    @Test
    fun contextsIn_finds_tags_anywhere_without_duplicates_or_punctuation() {
        assertEquals(listOf("unterwegs"), ContextTags.contextsIn("Einkaufen #kontext/unterwegs", prefix))
        assertEquals(
            listOf("telefon", "pc"),
            ContextTags.contextsIn("#kontext/telefon Call #Kontext/Telefon about #kontext/pc.", prefix),
        )
        // Inline code is not a tag, as in Obsidian.
        assertEquals(emptyList<String>(), ContextTags.contextsIn("Kontexte sind Tags wie `#kontext/telefon`.", prefix))
        assertEquals(emptyList<String>(), ContextTags.contextsIn("Read #kontextual and a#kontext/x", prefix))
    }

    @Test
    fun withoutContexts_removes_only_context_tags() {
        assertEquals("Einkaufen", ContextTags.withoutContexts("Einkaufen #kontext/unterwegs", prefix))
        assertEquals("Call mom #family", ContextTags.withoutContexts("#kontext/telefon Call #kontext/pc mom #family", prefix))
    }

    @Test
    fun withContexts_replaces_tags_at_the_end() {
        assertEquals(
            "Offsite-Kopie prüfen #kontext/pc",
            ContextTags.withContexts("Offsite-Kopie #kontext/zuhause prüfen", prefix, listOf("pc")),
        )
        assertEquals("Task", ContextTags.withContexts("Task #kontext/pc", prefix, emptyList()))
    }

    @Test
    fun hasContext_is_case_insensitive() {
        assertTrue(ContextTags.hasContext("Task #kontext/PC", prefix, "pc"))
        assertFalse(ContextTags.hasContext("Task #kontext/pc", prefix, "zuhause"))
    }
}
