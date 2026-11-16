package com.isotjs.todosian.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListGroupingTest {
    @Test
    fun listFolder_returns_parent_path_or_empty_for_root() {
        assertEquals("", listFolder("Inbox"))
        assertEquals("Projekte", listFolder("Projekte/Umzug Antwerpen"))
        assertEquals("Ziele/Wochen", listFolder("Ziele/Wochen/2026-W47"))
    }

    @Test
    fun isPausedStatus_matches_paused_and_archived_in_english_and_german() {
        assertTrue(isPausedStatus("Pausiert"))
        assertTrue(isPausedStatus(" archived "))
        assertFalse(isPausedStatus("Aktiv"))
        assertFalse(isPausedStatus(null))
    }

    @Test
    fun listFolderOrder_puts_root_first_then_alphabetical() {
        val sorted = listOf("Projekte", "", "bereiche", "Eingang").sortedWith(listFolderOrder)
        assertEquals(listOf("", "bereiche", "Eingang", "Projekte"), sorted)
    }
}
