package com.isotjs.todosian.utils

import com.isotjs.todosian.data.model.TasksPriority
import com.isotjs.todosian.data.model.Todo
import java.time.LocalDate
import java.util.UUID

data class TaskMarkers(
    val created: String = "➕",
    val start: String = "🛫",
    val scheduled: String = "⏳",
    val due: String = "📅",
    val done: String = "✅",
    val recurrence: String = "🔁",
) {
    companion object {
        val Default = TaskMarkers()
    }
}

private const val ON_COMPLETION_MARKER = "🏁"
private const val ON_COMPLETION_DELETE = "delete"

object MarkdownParser {
    // Any single status character counts, like the Obsidian Tasks plugin's custom statuses.
    private val todoRegex = Regex("""^([ \t]*)- \[([^\]])\] (.*)$""")

    private val prioritySuffixRegex = Regex("""\s(🔺|⏫|🔼|🔽|⏬️?|⏬)\s*$""")

    // Obsidian Tasks "on completion" field; the plugin only defines keep and delete.
    private val onCompletionSuffixRegex = Regex("""\s🏁\s*(keep|delete)\s*$""", RegexOption.IGNORE_CASE)

    private val tagSuffixRegex = Regex("""\s#[^\s#]+\s*$""")

    private fun dateSuffixRegex(marker: String) =
        Regex("""\s${Regex.escape(marker)}\s*(\d{4}-\d{2}-\d{2})\s*$""")

    private fun recurrenceSuffixRegex(marker: String) =
        Regex("""\s${Regex.escape(marker)}\s*(.+?)\s*$""")

    private fun completionAnywhereRegex(marker: String) =
        Regex("""\s${Regex.escape(marker)}\s*\d{4}-\d{2}-\d{2}(?=\s|$)""")

    private fun isTodoLine(line: String): Boolean = todoRegex.matches(line)

    fun parse(lines: List<String>, markers: TaskMarkers = TaskMarkers.Default): List<Todo> {
        return lines.mapIndexedNotNull { index, line ->
            val match = todoRegex.matchEntire(line) ?: return@mapIndexedNotNull null
            val indentPrefix = match.groupValues[1]
            val status = match.groupValues[2][0]
            val remainder = match.groupValues[3]

            val parsed = parseRemainder(remainder, markers)
            Todo(
                id = UUID.randomUUID().toString(),
                text = parsed.mainText,
                isDone = isClosedStatus(status),
                status = status,
                lineIndex = index,
                indentLevel = indentLevel(indentPrefix),
                dueDate = parsed.meta.dueDate,
                startDate = parsed.meta.startDate,
                scheduledDate = parsed.meta.scheduledDate,
                completionDate = parsed.meta.completionDate,
                createdDate = parsed.meta.createdDate,
                priority = parsed.meta.priority,
                recurrence = parsed.meta.recurrence,
                onCompletion = parsed.meta.onCompletion,
            )
        }
    }

    /** Done (`x`, `X`) and cancelled (`-`) tasks are closed; every other status is open. */
    fun isClosedStatus(status: Char): Boolean = status == 'x' || status == 'X' || status == '-'

    private fun isDoneStatus(status: Char): Boolean = status == 'x' || status == 'X'

    /**
     * Sets the status of the task at [lineIndex] to [newStatus], or toggles between open and done when it is null.
     * Becoming done adds the done date and the next recurrence; leaving done removes the done date.
     */
    fun toggleLine(
        lines: List<String>,
        lineIndex: Int,
        enableTasksPlugin: Boolean,
        today: LocalDate = LocalDate.now(),
        markers: TaskMarkers = TaskMarkers.Default,
        newStatus: Char? = null,
    ): List<String> {
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines

        val indentPrefix = match.groupValues[1]
        val currentStatus = match.groupValues[2][0]
        val remainder = match.groupValues[3]
        val status = newStatus ?: if (isClosedStatus(currentStatus)) ' ' else 'x'
        if (status == currentStatus) return lines

        val wasDone = isDoneStatus(currentStatus)
        val becomesDone = isDoneStatus(status) && !wasDone

        val newRemainder = when {
            !enableTasksPlugin -> remainder
            becomesDone -> removeCompletionDate(remainder, markers) + " ${markers.done} $today"
            wasDone && !isDoneStatus(status) -> removeCompletionDate(remainder, markers)
            else -> remainder
        }

        val newLine = "$indentPrefix- [$status] ${newRemainder.trimEnd()}"
        val updatedLines = lines.toMutableList().apply {
            this[lineIndex] = newLine
        }

        if (becomesDone && enableTasksPlugin) {
            val parsed = parseRemainder(remainder, markers)
            val nextLine = parsed.meta.recurrence?.takeIf { it.isNotBlank() }?.let { rule ->
                buildRecurredTaskLine(
                    indentPrefix = indentPrefix,
                    taskText = parsed.mainText,
                    originalMeta = parsed.meta,
                    rule = rule,
                    today = today,
                    markers = markers,
                )
            }
            // 🏁 delete drops the finished line like Obsidian Tasks does, unless subtasks would lose their parent.
            val deleteDone = parsed.meta.onCompletion.equals(ON_COMPLETION_DELETE, ignoreCase = true) &&
                !hasSubtasks(lines, lineIndex)
            if (nextLine != null || deleteDone) {
                return updatedLines.toMutableList().apply {
                    if (deleteDone) removeAt(lineIndex)
                    if (nextLine != null) add(lineIndex, nextLine)
                }
            }
        }

        return updatedLines
    }

    fun tryToggleLine(
        lines: List<String>,
        lineIndex: Int,
        enableTasksPlugin: Boolean,
        today: LocalDate = LocalDate.now(),
        markers: TaskMarkers = TaskMarkers.Default,
        newStatus: Char? = null,
    ): List<String>? {
        if (lineIndex !in lines.indices) return null
        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return toggleLine(lines, lineIndex, enableTasksPlugin, today, markers, newStatus)
    }

    fun addTodo(
        lines: List<String>,
        text: String,
        meta: TasksMeta? = null,
        enableTasksPlugin: Boolean = false,
        addAtStart: Boolean = false,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return lines

        val newLine = buildTodoLine(
            text = cleaned,
            meta = meta,
            enableTasksPlugin = enableTasksPlugin,
            indentPrefix = "",
            markers = markers,
        )

        if (lines.isEmpty()) return listOf(newLine)
        return lines.toMutableList().apply {
            if (addAtStart) add(frontmatterEndIndex(this), newLine) else add(newLine)
        }
    }

    private fun frontmatterEndIndex(lines: List<String>): Int {
        if (lines.firstOrNull() != "---") return 0
        for (i in 1 until lines.size) {
            if (lines[i] == "---") return i + 1
        }
        return 0
    }

    fun addSubTodo(
        lines: List<String>,
        parentLineIndex: Int,
        text: String,
        meta: TasksMeta? = null,
        enableTasksPlugin: Boolean = false,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String>? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        if (parentLineIndex !in lines.indices) return null

        val parentLine = lines[parentLineIndex]
        val match = todoRegex.matchEntire(parentLine) ?: return null
        val parentIndentPrefix = match.groupValues[1]
        val parentIndentLevel = indentLevel(parentIndentPrefix)

        val indentUnit = inferIndentUnit(parentIndentPrefix)
        val newIndentPrefix = parentIndentPrefix + indentUnit

        val newLine = buildTodoLine(
            text = cleaned,
            meta = meta,
            enableTasksPlugin = enableTasksPlugin,
            indentPrefix = newIndentPrefix,
            markers = markers,
        )

        var insertIndex = parentLineIndex + 1
        while (insertIndex < lines.size) {
            val nextLine = lines[insertIndex]
            val nextMatch = todoRegex.matchEntire(nextLine) ?: break
            val nextIndent = nextMatch.groupValues[1]
            if (indentLevel(nextIndent) > parentIndentLevel) {
                insertIndex++
            } else {
                break
            }
        }

        return lines.toMutableList().apply { add(insertIndex, newLine) }
    }

    fun tryDeleteTodoWithSubtasks(lines: List<String>, lineIndex: Int): List<String>? {
        if (lineIndex !in lines.indices) return null
        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return null
        val parentIndent = indentLevel(match.groupValues[1])

        var endIndex = lineIndex + 1
        while (endIndex < lines.size) {
            val nextLine = lines[endIndex]
            val nextMatch = todoRegex.matchEntire(nextLine) ?: break
            val nextIndent = indentLevel(nextMatch.groupValues[1])
            if (nextIndent > parentIndent) {
                endIndex++
            } else {
                break
            }
        }

        return lines.toMutableList().apply {
            subList(lineIndex, endIndex).clear()
        }
    }

    fun hasSubtasks(lines: List<String>, lineIndex: Int): Boolean {
        if (lineIndex !in lines.indices) return false
        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return false
        val parentIndent = indentLevel(match.groupValues[1])

        val nextIndex = lineIndex + 1
        if (nextIndex !in lines.indices) return false
        val nextLine = lines[nextIndex]
        val nextMatch = todoRegex.matchEntire(nextLine) ?: return false
        val nextIndent = indentLevel(nextMatch.groupValues[1])
        return nextIndent > parentIndent
    }

    fun editTodoText(
        lines: List<String>,
        lineIndex: Int,
        newText: String,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String> {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return lines
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines
        val indentPrefix = match.groupValues[1]
        val mark = match.groupValues[2]
        val remainder = match.groupValues[3]
        val parsed = parseRemainder(remainder, markers)

        val newRemainder = buildString {
            append(cleaned)
            if (parsed.suffixRaw.isNotEmpty()) append(parsed.suffixRaw)
        }

        val newLine = "$indentPrefix- [$mark] ${newRemainder.trimEnd()}"
        return lines.toMutableList().apply { this[lineIndex] = newLine }
    }

    fun tryEditTodoText(
        lines: List<String>,
        lineIndex: Int,
        newText: String,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String>? {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return null
        if (lineIndex !in lines.indices) return null

        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return editTodoText(lines, lineIndex, cleaned, markers)
    }

    fun tryCopyTodoLine(
        sourceLines: List<String>,
        lineIndex: Int,
        targetLines: List<String>,
    ): Pair<List<String>, List<String>>? {
        if (lineIndex !in sourceLines.indices) return null
        val line = sourceLines[lineIndex]
        if (!isTodoLine(line)) return null

        val newTargetLines = targetLines.toMutableList().apply { add(line) }
        return sourceLines to newTargetLines
    }

    fun tryMoveTodoLine(
        sourceLines: List<String>,
        lineIndex: Int,
        targetLines: List<String>,
    ): Pair<List<String>, List<String>>? {
        if (lineIndex !in sourceLines.indices) return null
        val line = sourceLines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return null
        val parentIndent = indentLevel(match.groupValues[1])

        var endIndex = lineIndex + 1
        while (endIndex < sourceLines.size) {
            val nextLine = sourceLines[endIndex]
            val nextMatch = todoRegex.matchEntire(nextLine) ?: break
            val nextIndent = indentLevel(nextMatch.groupValues[1])
            if (nextIndent > parentIndent) {
                endIndex++
            } else {
                break
            }
        }

        val block = sourceLines.subList(lineIndex, endIndex)
        val newSourceLines = sourceLines.toMutableList().apply {
            subList(lineIndex, endIndex).clear()
        }
        val newTargetLines = targetLines.toMutableList().apply { addAll(block) }
        return newSourceLines to newTargetLines
    }

    fun editTodo(
        lines: List<String>,
        lineIndex: Int,
        newText: String,
        meta: TasksMeta?,
        enableTasksPlugin: Boolean,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String> {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return lines
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines
        val indentPrefix = match.groupValues[1]
        val mark = match.groupValues[2]

        val resolvedMeta = if (enableTasksPlugin) meta else null
        val newLine = buildString {
            append(indentPrefix)
            append("- [")
            append(mark)
            append("] ")
            append(cleaned)
            val suffix = resolvedMeta?.toSuffixString(markers).orEmpty()
            if (suffix.isNotEmpty()) append(suffix)
        }

        return lines.toMutableList().apply { this[lineIndex] = newLine }
    }

    private fun todayString(today: LocalDate = LocalDate.now()): String = today.toString()

    private fun buildRecurredTaskLine(
        indentPrefix: String,
        taskText: String,
        originalMeta: TasksMeta,
        rule: String,
        today: LocalDate,
        markers: TaskMarkers,
    ): String? {
        val next = RecurrenceEngine.nextOccurrence(
            ruleText = rule,
            dueDate = originalMeta.dueDate,
            scheduledDate = originalMeta.scheduledDate,
            startDate = originalMeta.startDate,
            today = today,
        ) ?: return null

        val newMeta = TasksMeta(
            dueDate = next.dueDate?.toString(),
            scheduledDate = next.scheduledDate?.toString(),
            startDate = next.startDate?.toString(),
            completionDate = null,
            createdDate = if (originalMeta.createdDate != null) today.toString() else null,
            priority = originalMeta.priority,
            recurrence = originalMeta.recurrence,
            onCompletion = originalMeta.onCompletion,
        )

        return buildString {
            append(indentPrefix)
            append("- [ ] ")
            append(taskText)
            val suffix = newMeta.toSuffixString(markers)
            if (suffix.isNotEmpty()) append(suffix)
        }
    }

    private fun buildTodoLine(
        text: String,
        meta: TasksMeta?,
        enableTasksPlugin: Boolean,
        indentPrefix: String,
        markers: TaskMarkers,
    ): String {
        val resolvedMeta = if (enableTasksPlugin) {
            (meta ?: TasksMeta()).let { current ->
                if (current.createdDate == null) current.copy(createdDate = todayString()) else current
            }
        } else {
            null
        }

        return buildString {
            append(indentPrefix)
            append("- [ ] ")
            append(text)
            val suffix = resolvedMeta?.toSuffixString(markers).orEmpty()
            if (suffix.isNotEmpty()) append(suffix)
        }
    }

    private fun indentLevel(prefix: String): Int {
        if (prefix.isEmpty()) return 0
        var width = 0
        for (char in prefix) {
            width += if (char == '\t') 4 else 1
        }
        return (width / 2).coerceAtLeast(0)
    }

    private fun inferIndentUnit(prefix: String): String {
        return if (prefix.contains('\t')) "\t" else "  "
    }

    private fun removeCompletionDate(remainder: String, markers: TaskMarkers): String {
        val removed = remainder.replace(completionAnywhereRegex(markers.done), "")
        return removed.replace(Regex("""\s{2,}"""), " ").trimEnd()
    }

    private data class ParsedRemainder(
        val mainText: String,
        val suffixRaw: String,
        val meta: TasksMeta,
    )

    data class TasksMeta(
        val dueDate: String? = null,
        val startDate: String? = null,
        val scheduledDate: String? = null,
        val completionDate: String? = null,
        val createdDate: String? = null,
        val priority: TasksPriority? = null,
        val recurrence: String? = null,
        /** Obsidian Tasks on-completion action (`🏁 keep` or `🏁 delete`). */
        val onCompletion: String? = null,
    ) {
        fun toSuffixString(markers: TaskMarkers = TaskMarkers.Default): String {
            val parts = ArrayList<String>(8)
            if (createdDate != null) parts.add("${markers.created} $createdDate")
            if (startDate != null) parts.add("${markers.start} $startDate")
            if (scheduledDate != null) parts.add("${markers.scheduled} $scheduledDate")
            if (dueDate != null) parts.add("${markers.due} $dueDate")
            if (completionDate != null) parts.add("${markers.done} $completionDate")
            if (!recurrence.isNullOrBlank()) parts.add("${markers.recurrence} ${recurrence.trim()}")
            if (!onCompletion.isNullOrBlank()) parts.add("$ON_COMPLETION_MARKER ${onCompletion.trim()}")
            val prio = priority
            if (prio != null && prio != TasksPriority.NONE) {
                parts.add(priorityToEmoji(prio))
            }

            return if (parts.isEmpty()) {
                ""
            } else {
                " " + parts.joinToString(separator = " ")
            }
        }
    }

    private fun parseRemainder(remainder: String, markers: TaskMarkers): ParsedRemainder {
        var working = remainder.trimEnd()

        val completionRegex = dateSuffixRegex(markers.done)
        val dueRegex = dateSuffixRegex(markers.due)
        val startRegex = dateSuffixRegex(markers.start)
        val scheduledRegex = dateSuffixRegex(markers.scheduled)
        val createdRegex = dateSuffixRegex(markers.created)
        val recurrenceRegex = recurrenceSuffixRegex(markers.recurrence)

        val suffixParts = mutableListOf<String>()

        var dueDate: String? = null
        var startDate: String? = null
        var scheduledDate: String? = null
        var completionDate: String? = null
        var createdDate: String? = null
        var priority: TasksPriority? = null
        var recurrence: String? = null
        var onCompletion: String? = null

        // Tags found while peeling fields off the end. Tags that sit between fields move into the
        // description (as in Obsidian Tasks); tags left of every field stay exactly as written.
        var beforeTags: String? = null
        val pendingTags = mutableListOf<String>()
        val movedTags = mutableListOf<String>()

        fun peel(match: MatchResult) {
            suffixParts.add(0, working.substring(match.range.first))
            working = working.substring(0, match.range.first).trimEnd()
            movedTags.addAll(0, pendingTags)
            pendingTags.clear()
            beforeTags = null
        }

        while (true) {
            completionRegex.find(working)?.let { completionDate = it.groupValues[1]; peel(it); continue }
            dueRegex.find(working)?.let { dueDate = it.groupValues[1]; peel(it); continue }
            startRegex.find(working)?.let { startDate = it.groupValues[1]; peel(it); continue }
            scheduledRegex.find(working)?.let { scheduledDate = it.groupValues[1]; peel(it); continue }
            createdRegex.find(working)?.let { createdDate = it.groupValues[1]; peel(it); continue }
            prioritySuffixRegex.find(working)?.let { priority = emojiToPriority(it.groupValues[1]); peel(it); continue }
            onCompletionSuffixRegex.find(working)?.let { onCompletion = it.groupValues[1].lowercase(); peel(it); continue }

            val tag = tagSuffixRegex.find(working)
            if (tag != null) {
                if (beforeTags == null) beforeTags = working
                pendingTags.add(0, working.substring(tag.range.first).trim())
                working = working.substring(0, tag.range.first).trimEnd()
                continue
            }

            // Last: the rule runs to the end of the line, so every other field must be peeled first.
            recurrenceRegex.find(working)?.let { recurrence = it.groupValues[1].trim(); peel(it); continue }

            break
        }
        beforeTags?.let { working = it }
        if (movedTags.isNotEmpty()) working = (listOf(working.trimEnd()) + movedTags).joinToString(" ")

        return ParsedRemainder(
            mainText = working.trimEnd(),
            suffixRaw = suffixParts.joinToString(separator = ""),
            meta = TasksMeta(
                dueDate = dueDate,
                startDate = startDate,
                scheduledDate = scheduledDate,
                completionDate = completionDate,
                createdDate = createdDate,
                priority = priority,
                recurrence = recurrence,
                onCompletion = onCompletion,
            ),
        )
    }

    private fun priorityToEmoji(priority: TasksPriority): String {
        return when (priority) {
            TasksPriority.HIGHEST -> "🔺"
            TasksPriority.HIGH -> "⏫"
            TasksPriority.MEDIUM -> "🔼"
            TasksPriority.LOW -> "🔽"
            TasksPriority.LOWEST -> "⏬"
            TasksPriority.NONE -> ""
        }
    }

    private fun emojiToPriority(emoji: String): TasksPriority? {
        return when (emoji) {
            "🔺" -> TasksPriority.HIGHEST
            "⏫" -> TasksPriority.HIGH
            "🔼" -> TasksPriority.MEDIUM
            "🔽" -> TasksPriority.LOW
            "⏬", "⏬️" -> TasksPriority.LOWEST
            else -> null
        }
    }
}
