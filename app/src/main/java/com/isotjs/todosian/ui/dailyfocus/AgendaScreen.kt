package com.isotjs.todosian.ui.dailyfocus

import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.isotjs.todosian.ui.components.todosianFabShape
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.DailyFocusMode
import com.isotjs.todosian.ui.components.ContextFilterRow
import com.isotjs.todosian.ui.components.ContextPicker
import com.isotjs.todosian.ui.components.TasksMetaEditor
import com.isotjs.todosian.ui.components.rememberVaultContexts
import com.isotjs.todosian.utils.ContextTags
import com.isotjs.todosian.ui.components.TodoRow
import com.isotjs.todosian.ui.components.TodoSheetMode
import com.isotjs.todosian.ui.components.TodosianDimens
import com.isotjs.todosian.ui.components.TodosianEmptyState
import com.isotjs.todosian.ui.components.TodosianLoadingIndicator
import com.isotjs.todosian.ui.components.TodosianSectionHeader
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AgendaScreen(
    range: AgendaRange,
    fileRepository: FileRepository,
    appSettingsRepository: AppSettingsRepository,
    refreshSignal: Long,
    onOpenSettings: () -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Collect settings once to seed the ViewModel factory
    val settings by appSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val statusChoices = remember(settings.customStatuses) { settings.statusChoices() }
    val statusLabels = remember(settings.customStatusLabels) { settings.statusLabels() }

    val viewModel: DailyFocusViewModel = viewModel(
        key = "agenda_${range.name}_${settings.dailyFocusMode.name}_${settings.enableTasksPluginSupport}",
        factory = DailyFocusViewModelFactory(
            fileRepository = fileRepository,
            mode = settings.dailyFocusMode,
            range = range,
            enableTasksPluginSupport = settings.enableTasksPluginSupport,
            appSettingsRepository = appSettingsRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        // Skip the first resume: the ViewModel has just loaded. Later resumes pick up outside edits.
        var resumedBefore = false
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (resumedBefore) viewModel.refreshFromDisk(showLoading = false)
            resumedBefore = true
        }
    }
    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0L) viewModel.refreshFromDisk(showLoading = false)
    }

    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is DailyFocusViewModel.Event.ShowMessage -> {
                    snackbarHostState.showSnackbar(resources.getString(event.messageResId))
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val todoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sheetMode by remember { mutableStateOf<TodoSheetMode?>(null) }
    var sheetText by remember { mutableStateOf("") }
    var sheetMeta by remember { mutableStateOf(MarkdownParser.TasksMeta()) }
    var sheetContexts by remember { mutableStateOf(emptyList<String>()) }
    val contextPrefix = settings.contextPrefix()
    var sheetTask by remember { mutableStateOf<DailyFocusTask?>(null) }
    var deleteTodoTarget by remember { mutableStateOf<DailyFocusTask?>(null) }

    fun dismissSheet() {
        sheetMode = null
        sheetText = ""
        sheetContexts = emptyList()
        sheetMeta = MarkdownParser.TasksMeta()
        sheetTask = null
    }

    if (sheetMode != null) {
        ModalBottomSheet(
            onDismissRequest = { dismissSheet() },
            sheetState = todoSheetState,
        ) {
            val scrollState = rememberScrollState()
            val todoTextBringIntoView = remember { BringIntoViewRequester() }
            val recurrenceBringIntoView = remember { BringIntoViewRequester() }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(bottom = 16.dp)
                    .verticalScroll(scrollState),
            ) {
                Text(
                    text = stringResource(R.string.category_edit_todo_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = sheetText,
                    onValueChange = { sheetText = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.category_edit_todo_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(todoTextBringIntoView)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                scope.launch { todoTextBringIntoView.bringIntoView() }
                            }
                        },
                )

                Spacer(modifier = Modifier.height(12.dp))
                ContextPicker(
                    selected = sheetContexts,
                    known = rememberVaultContexts(fileRepository, contextPrefix),
                    onChange = { sheetContexts = it },
                )

                if (settings.enableTasksPluginSupport) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TasksMetaEditor(
                        mode = sheetMode,
                        meta = sheetMeta,
                        onMetaChange = { sheetMeta = it },
                        useEmojisInUi = settings.tasksPluginUseEmojisInUi,
                        modifier = Modifier
                            .bringIntoViewRequester(recurrenceBringIntoView)
                            .onFocusEvent { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch { recurrenceBringIntoView.bringIntoView() }
                                }
                            },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                todoSheetState.hide()
                                dismissSheet()
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            val task = sheetTask
                            if (task != null) {
                                viewModel.editTodo(
                                    task = task,
                                    newText = ContextTags.withContexts(sheetText, contextPrefix, sheetContexts),
                                    meta = if (settings.enableTasksPluginSupport) sheetMeta else null,
                                )
                            }
                            scope.launch {
                                todoSheetState.hide()
                                dismissSheet()
                            }
                        },
                        enabled = sheetText.trim().isNotEmpty(),
                    ) {
                        Text(text = stringResource(R.string.action_save))
                    }
                }
            }
        }
    }

    if (deleteTodoTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTodoTarget = null },
            title = { Text(text = stringResource(R.string.category_delete_todo_title)) },
            text = { Text(text = stringResource(R.string.category_delete_todo_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTodoTarget
                        if (target != null) viewModel.deleteTodo(target)
                        deleteTodoTarget = null
                    },
                ) {
                    Text(text = stringResource(R.string.category_delete_todo_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTodoTarget = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                shape = todosianFabShape(),
                // Concave FAB shape: no shadow, see ListsScreen.
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add_task),
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                TodosianLoadingIndicator()
            }
            return@Scaffold
        }

        // Recomputed with every reload, so the header follows the date after midnight.
        val activeContext = settings.activeContext
        val tasks = remember(uiState.tasks, activeContext, contextPrefix) {
            if (activeContext == null) {
                uiState.tasks
            } else {
                uiState.tasks.filter { ContextTags.hasContext(it.todo.text, contextPrefix, activeContext) }
            }
        }
        // Chips for the contexts on this screen, plus the active one so it can always be cleared.
        val contextsHere = remember(uiState.tasks, activeContext, contextPrefix) {
            (uiState.tasks.flatMap { ContextTags.contextsIn(it.todo.text, contextPrefix) } + listOfNotNull(activeContext))
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }
        val contextFilter: (@Composable () -> Unit)? = if (contextsHere.isEmpty()) {
            null
        } else {
            {
                ContextFilterRow(
                    contexts = contextsHere,
                    active = activeContext,
                    onSelect = { appSettingsRepository.setActiveContext(it) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        val today = remember(uiState.tasks) { LocalDate.now() }
        val todayKey = today.toString()
        // TODAY splits into overdue and due today; WEEK into this week and next week.
        val (overdue, dueToday) = remember(tasks, todayKey) {
            tasks.partition { task -> task.todo.focusDate?.let { it < todayKey } == true }
        }
        val endOfThisWeek = remember(today) { endOfWeek(today, firstDayOfWeek()) }
        val (thisWeek, nextWeek) = remember(tasks, endOfThisWeek) {
            tasks.partition { task -> task.todo.focusDate?.let { it <= endOfThisWeek.toString() } == true }
        }

        fun openEditor(task: DailyFocusTask) {
            sheetMode = TodoSheetMode.Edit(task.todo)
            sheetText = ContextTags.withoutContexts(task.todo.text, contextPrefix)
            sheetContexts = ContextTags.contextsIn(task.todo.text, contextPrefix)
            sheetMeta = MarkdownParser.TasksMeta(
                dueDate = task.todo.dueDate,
                startDate = task.todo.startDate,
                scheduledDate = task.todo.scheduledDate,
                completionDate = task.todo.completionDate,
                createdDate = task.todo.createdDate,
                priority = task.todo.priority,
                recurrence = task.todo.recurrence,
                onCompletion = task.todo.onCompletion,
            )
            sheetTask = task
        }

        @Composable
        fun TaskRow(task: DailyFocusTask, containerColor: Color, modifier: Modifier = Modifier) {
            TodoRow(
                todo = task.todo,
                enableTasksPluginSupport = settings.enableTasksPluginSupport,
                useEmojisInUi = settings.tasksPluginUseEmojisInUi,
                onToggle = { viewModel.toggleTodo(task) },
                statusChoices = statusChoices,
                statusLabels = statusLabels,
                onSetStatus = { viewModel.toggleTodo(task, it) },
                onEdit = { openEditor(task) },
                onAddSubtask = {},
                onRequestDelete = { deleteTodoTarget = task },
                onRequestMove = null,
                allowMove = false,
                showSubtaskButton = false,
                supportingText = task.categoryName.substringAfterLast('/'),
                containerColor = containerColor,
                contextPrefix = contextPrefix,
                modifier = modifier,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TodosianDimens.ScreenHorizontalPadding),
        ) {
            if (range == AgendaRange.WEEK) {
                weekContent(
                    today = today,
                    endOfWeek = endOfThisWeek,
                    thisWeek = thisWeek,
                    nextWeekCount = nextWeek.size,
                    contextFilter = contextFilter,
                    taskRow = { task, rowModifier ->
                        TaskRow(
                            task = task,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = rowModifier,
                        )
                    },
                )
                return@LazyColumn
            }

            item(key = "header") {
                TodayHeader(
                    date = today,
                    dueTodayCount = dueToday.size,
                    modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
                )
            }
            if (contextFilter != null) item(key = "contexts") { contextFilter() }

            if (tasks.isEmpty()) {
                item(key = "empty") {
                    TodosianEmptyState(
                        icon = Icons.Outlined.Spa,
                        title = stringResource(R.string.daily_focus_empty_title),
                        subtitle = stringResource(R.string.daily_focus_empty_subtitle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
            }

            if (overdue.isNotEmpty()) {
                item(key = "overdue") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.extraLarge,
                            )
                            .animateContentSize()
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.today_overdue_count, overdue.size, overdue.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        overdue.forEach { task ->
                            key("${task.categoryUri}:${task.todo.id}") {
                                TaskRow(task = task, containerColor = MaterialTheme.colorScheme.errorContainer)
                            }
                        }
                    }
                }
            }

            if (dueToday.isNotEmpty()) {
                item(key = "today_header") {
                    TodosianSectionHeader(
                        text = stringResource(R.string.home_tab_today),
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .animateItem(),
                    )
                }
                items(
                    items = dueToday,
                    key = { "${it.categoryUri}:${it.todo.id}" },
                ) { task ->
                    TaskRow(
                        task = task,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .animateItem(
                                fadeInSpec = tween(durationMillis = 180),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                fadeOutSpec = tween(durationMillis = 160),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Tomorrow through the end of this week, one section per day that has tasks, then a count of
 * next week's tasks so the tab isn't blank late in the week.
 */
private fun LazyListScope.weekContent(
    today: LocalDate,
    endOfWeek: LocalDate,
    thisWeek: List<DailyFocusTask>,
    nextWeekCount: Int,
    contextFilter: (@Composable () -> Unit)?,
    taskRow: @Composable (DailyFocusTask, Modifier) -> Unit,
) {
    item(key = "header") {
        WeekHeader(
            from = today.plusDays(1),
            to = endOfWeek,
            taskCount = thisWeek.size,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
    }
    if (contextFilter != null) item(key = "contexts") { contextFilter() }

    if (thisWeek.isEmpty()) {
        item(key = "empty") {
            TodosianEmptyState(
                icon = Icons.Outlined.Spa,
                title = stringResource(R.string.week_empty_title),
                subtitle = stringResource(R.string.week_empty_subtitle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
            )
        }
    }

    // Tasks arrive sorted by date, so grouping keeps the days in order.
    thisWeek.groupBy { it.todo.focusDate.orEmpty() }.forEach { (date, tasks) ->
        item(key = "day:$date") {
            DayHeader(
                date = LocalDate.parse(date),
                isTomorrow = date == today.plusDays(1).toString(),
                taskCount = tasks.size,
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 8.dp)
                    .animateItem(),
            )
        }
        items(
            items = tasks,
            key = { "${it.categoryUri}:${it.todo.id}" },
        ) { task ->
            taskRow(
                task,
                Modifier
                    .padding(bottom = 8.dp)
                    .animateItem(
                        fadeInSpec = tween(durationMillis = 180),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = tween(durationMillis = 160),
                    ),
            )
        }
    }

    if (nextWeekCount > 0) {
        item(key = "next_week") {
            Text(
                text = pluralStringResource(R.plurals.week_next_week_count, nextWeekCount, nextWeekCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp, top = 24.dp)
                    .animateItem(),
            )
        }
    }
}

@Composable
private fun WeekHeader(
    from: LocalDate,
    to: LocalDate,
    taskCount: Int,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dayAndMonth = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale)
    }
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.week_title),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
        // On the last day of the week there is no "rest of the week" to describe.
        if (!from.isAfter(to)) {
            val span = if (from == to) {
                from.format(dayAndMonth)
            } else {
                stringResource(R.string.week_date_range, from.format(dayAndMonth), to.format(dayAndMonth))
            }
            Text(
                text = pluralStringResource(R.plurals.today_subtitle, taskCount, span, taskCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    isTomorrow: Boolean,
    taskCount: Int,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val weekday = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val dayAndMonth = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale))
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        Text(
            text = if (isTomorrow) stringResource(R.string.week_tomorrow) else weekday,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = if (isTomorrow) "$weekday, $dayAndMonth" else dayAndMonth,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = taskCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The weekday as the screen's headline, with the date and how much is due today below it. */
@Composable
private fun TodayHeader(
    date: LocalDate,
    dueTodayCount: Int,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val weekday = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val dayAndMonth = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale))
    }
    Column(modifier = modifier) {
        Text(
            text = weekday,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = pluralStringResource(R.plurals.today_subtitle, dueTodayCount, dayAndMonth, dueTodayCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private class DailyFocusViewModelFactory(
    private val fileRepository: FileRepository,
    private val mode: DailyFocusMode,
    private val range: AgendaRange,
    private val enableTasksPluginSupport: Boolean,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DailyFocusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DailyFocusViewModel(
                fileRepository = fileRepository,
                mode = mode,
                range = range,
                enableTasksPluginSupport = enableTasksPluginSupport,
                appSettingsRepository = appSettingsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
