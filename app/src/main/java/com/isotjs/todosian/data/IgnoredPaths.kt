package com.isotjs.todosian.data

/**
 * Gitignore-style patterns, one per line, matched against paths relative to the selected folder
 * (files with their `.md` suffix, e.g. `Projects/Old/Note.md`).
 *
 * - `*` and `?` match within one name, `**` matches across folders.
 * - A pattern without `/` matches a name at any depth; with `/` it matches from the selected folder.
 * - A trailing `/` matches folders only. Blank lines and lines starting with `#` are ignored.
 */
internal class IgnoredPaths(patterns: String?) {
    private val rules = patterns.orEmpty()
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith('#') }
        .mapNotNull(::compileRule)

    fun isEmpty(): Boolean = rules.isEmpty()

    fun isIgnored(relativePath: String, isDirectory: Boolean): Boolean = rules.any { rule ->
        (isDirectory || !rule.directoryOnly) &&
            rule.regex.matches(if (rule.anchored) relativePath else relativePath.substringAfterLast('/'))
    }

    private class Rule(val regex: Regex, val directoryOnly: Boolean, val anchored: Boolean)

    private fun compileRule(raw: String): Rule? {
        val directoryOnly = raw.endsWith('/')
        val pattern = raw.trim('/')
        if (pattern.isEmpty()) return null
        return Rule(globToRegex(pattern), directoryOnly, anchored = '/' in pattern)
    }

    private fun globToRegex(glob: String): Regex {
        val regex = StringBuilder()
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when {
                glob.startsWith("**/", i) -> { regex.append("(?:.*/)?"); i += 3; continue }
                glob.startsWith("**", i) -> { regex.append(".*"); i += 2; continue }
                c == '*' -> regex.append("[^/]*")
                c == '?' -> regex.append("[^/]")
                else -> regex.append(Regex.escape(c.toString()))
            }
            i++
        }
        return Regex(regex.toString())
    }
}
