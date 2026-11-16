package com.isotjs.todosian.data.settings

import com.isotjs.todosian.utils.ContextTags
import com.isotjs.todosian.utils.TaskMarkers

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val dailyFocusMode: DailyFocusMode = DailyFocusMode.TODAY,
    val categorySort: CategorySort = CategorySort.A_Z,
    val todoGrouping: TodoGrouping = TodoGrouping.GROUPED,
    val todoSort: TodoSort = TodoSort.FILE_ORDER,
    val newTodoFilePosition: NewTodoFilePosition = NewTodoFilePosition.BOTTOM,
    val enableTasksPluginSupport: Boolean = false,
    val tasksPluginUseEmojisInUi: Boolean = false,
    val createdMarker: String? = null,
    val startMarker: String? = null,
    val scheduledMarker: String? = null,
    val dueMarker: String? = null,
    val doneMarker: String? = null,
    val recurrenceMarker: String? = null,
    val customStatuses: String? = null,
    /** One line per labelled status: the status character followed by its label, e.g. "wWartet". */
    val customStatusLabels: String? = null,
    val enableSubtasks: Boolean = false,
    val includeSubfolders: Boolean = false,
    val ignoredPaths: String? = null,
    /** Tag prefix that marks a GTD context, e.g. "#kontext/"; blank means [ContextTags.DEFAULT_PREFIX]. */
    val contextTagPrefix: String? = null,
    /** The context Today, Week and lists are filtered to, or null for all tasks. */
    val activeContext: String? = null,
    val reminderTimeHour: Int = 19,
    val reminderTimeMinute: Int = 0,
) {
    /** Resolves the configured markers, falling back to the emoji default when a field is blank. */
    fun toTaskMarkers(): TaskMarkers = TaskMarkers(
        created = createdMarker?.takeIf { it.isNotBlank() } ?: TaskMarkers.Default.created,
        start = startMarker?.takeIf { it.isNotBlank() } ?: TaskMarkers.Default.start,
        scheduled = scheduledMarker?.takeIf { it.isNotBlank() } ?: TaskMarkers.Default.scheduled,
        due = dueMarker?.takeIf { it.isNotBlank() } ?: TaskMarkers.Default.due,
        done = doneMarker?.takeIf { it.isNotBlank() } ?: TaskMarkers.Default.done,
        recurrence = recurrenceMarker?.takeIf { it.isNotBlank() } ?: TaskMarkers.Default.recurrence,
    )

    /** Statuses offered when long-pressing a task: open, done, then each custom status character. */
    fun statusChoices(): List<Char> =
        (listOf(' ', 'x') + customStatuses.orEmpty().filterNot { it.isWhitespace() || it == ']' }.toList())
            .distinct()

    fun contextPrefix(): String = ContextTags.normalizePrefix(contextTagPrefix)

    /** Labels the user gave their custom status characters. */
    fun statusLabels(): Map<Char, String> =
        customStatusLabels.orEmpty().lines()
            .filter { it.length > 1 }
            .associate { it[0] to it.substring(1).trim() }
            .filterValues { it.isNotEmpty() }
}

/** A custom status as edited in Settings: its character (if typed yet) and an optional label. */
data class CustomStatus(val char: Char?, val label: String)

enum class DailyFocusMode {
    TODAY,
    OVERDUE,
    TODAY_AND_OVERDUE,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class CategorySort {
    A_Z,
    MOST_REMAINING,
}

enum class TodoGrouping {
    GROUPED,
    FILE_ORDER,
}

enum class TodoSort {
    FILE_ORDER,
    PRIORITY_HIGH_TO_LOW,
    CREATED_DATE_NEWEST_FIRST,
    DUE_DATE_EARLIEST_FIRST,
}

enum class NewTodoFilePosition {
    TOP,
    BOTTOM,
}
