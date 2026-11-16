package com.isotjs.todosian.ui.home

/** Folder part of a list's relative path, "" for files in the selected folder itself. */
internal fun listFolder(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")

private val PAUSED_STATUSES = setOf("paused", "pausiert", "archived", "archiviert")

/** True for a frontmatter status that marks a project or area as parked. */
internal fun isPausedStatus(status: String?): Boolean = status?.trim()?.lowercase() in PAUSED_STATUSES

/** Files in the selected folder first, then folders alphabetically. */
internal val listFolderOrder: Comparator<String> =
    compareBy<String> { it.isNotEmpty() }.thenBy { it.lowercase() }
