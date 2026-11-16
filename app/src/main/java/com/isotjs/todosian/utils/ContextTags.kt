package com.isotjs.todosian.utils

/**
 * GTD contexts written as tags with a common prefix, e.g. `#kontext/zuhause`. The context itself
 * ("zuhause") is whatever follows the prefix; contexts are found in the files, not configured.
 */
object ContextTags {
    const val DEFAULT_PREFIX = "#context/"

    /** The prefix as a tag: blank falls back to the default, a missing `#` is added. */
    fun normalizePrefix(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "#") return DEFAULT_PREFIX
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    // Obsidian tag characters; stops at punctuation such as a closing backtick or period.
    private fun tagRegex(prefix: String) =
        Regex("""(^|\s+)${Regex.escape(prefix)}([\p{L}\p{N}_/-]+)""", RegexOption.IGNORE_CASE)

    /** Contexts tagged in [text], in order, without duplicates (tags are case-insensitive). */
    fun contextsIn(text: String, prefix: String): List<String> =
        tagRegex(prefix).findAll(text)
            .map { it.groupValues[2] }
            .distinctBy { it.lowercase() }
            .toList()

    /** [text] with its context tags removed, for display and editing. */
    fun withoutContexts(text: String, prefix: String): String =
        text.replace(tagRegex(prefix), "").trim()

    /** [text] with its context tags replaced by [contexts], appended at the end. */
    fun withContexts(text: String, prefix: String, contexts: Collection<String>): String =
        (listOf(withoutContexts(text, prefix)) + contexts.map { "$prefix$it" })
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    fun hasContext(text: String, prefix: String, context: String): Boolean =
        contextsIn(text, prefix).any { it.equals(context, ignoreCase = true) }

    /** "zuhause" → "Zuhause" for labels and chips. */
    fun displayName(context: String): String = context.replaceFirstChar { it.titlecase() }
}
