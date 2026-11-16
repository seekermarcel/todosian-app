package com.isotjs.todosian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnoredPathsTest {

    @Test
    fun emptyOrCommentOnlyIgnoresNothing() {
        assertTrue(IgnoredPaths(null).isEmpty())
        assertTrue(IgnoredPaths("\n  \n# comment\n").isEmpty())
        assertFalse(IgnoredPaths("").isIgnored("Inbox.md", isDirectory = false))
    }

    @Test
    fun nameWithoutSlashMatchesAtAnyDepth() {
        val ignored = IgnoredPaths("_templates\n*.excalidraw.md")

        assertTrue(ignored.isIgnored("_templates", isDirectory = true))
        assertTrue(ignored.isIgnored("Areas/_templates", isDirectory = true))
        assertTrue(ignored.isIgnored("Drawing.excalidraw.md", isDirectory = false))
        assertTrue(ignored.isIgnored("Projects/Plan.excalidraw.md", isDirectory = false))
        assertFalse(ignored.isIgnored("Projects/Plan.md", isDirectory = false))
    }

    @Test
    fun trailingSlashMatchesFoldersOnly() {
        val ignored = IgnoredPaths("Archive/")

        assertTrue(ignored.isIgnored("Archive", isDirectory = true))
        assertFalse(ignored.isIgnored("Archive", isDirectory = false))
    }

    @Test
    fun patternWithSlashIsAnchoredToSelectedFolder() {
        val ignored = IgnoredPaths("/Goals/Weeks/2025-*\nProjects/**/Old.md")

        assertTrue(ignored.isIgnored("Goals/Weeks/2025-W01.md", isDirectory = false))
        assertFalse(ignored.isIgnored("Goals/Weeks/2026-W01.md", isDirectory = false))
        assertFalse(ignored.isIgnored("Other/Goals/Weeks/2025-W01.md", isDirectory = false))
        assertTrue(ignored.isIgnored("Projects/Old.md", isDirectory = false))
        assertTrue(ignored.isIgnored("Projects/A/B/Old.md", isDirectory = false))
    }

    @Test
    fun specialCharactersAreLiteral() {
        val ignored = IgnoredPaths("Notes (old).md\n[draft].md")

        assertTrue(ignored.isIgnored("Notes (old).md", isDirectory = false))
        assertTrue(ignored.isIgnored("[draft].md", isDirectory = false))
        assertFalse(ignored.isIgnored("d.md", isDirectory = false))
    }

    @Test
    fun ignoredFolderIsNotListed() {
        val listed = mutableListOf<String>()
        val tree = mapOf(
            "root" to listOf(
                DocumentEntry("inbox", "Inbox.md", isDirectory = false),
                DocumentEntry("archive", "Archive", isDirectory = true),
                DocumentEntry("tpl", "Template.md", isDirectory = false),
            ),
            "archive" to listOf(DocumentEntry("old", "Old.md", isDirectory = false)),
        )

        val result = walkMarkdownTree("root", recursive = true, IgnoredPaths("Archive/\nTemplate.md")) {
            listed.add(it)
            tree[it].orEmpty()
        }

        assertEquals(listOf(MarkdownEntry("inbox", "Inbox")), result.files)
        assertEquals(listOf("root"), listed)
    }
}
