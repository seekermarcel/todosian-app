package com.isotjs.todosian.data.model

import android.net.Uri

data class Category(
    val displayName: String,
    val uri: Uri,
    val todoCount: Int,
    val doneCount: Int,
    val dueTodayCount: Int,
    val overdueCount: Int,
    /** Frontmatter `icon` property, usually an emoji. */
    val icon: String? = null,
    /** Frontmatter `status` property, e.g. "Aktiv" or "Pausiert". */
    val status: String? = null,
)
