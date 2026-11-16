package com.isotjs.todosian.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.model.Category
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.CategorySort
import com.isotjs.todosian.ui.components.TodosianEmptyState
import com.isotjs.todosian.ui.components.TodosianLoadingIndicator
import com.isotjs.todosian.ui.components.TodosianPullToRefreshBox
import com.isotjs.todosian.ui.components.TodosianSectionHeader
import com.isotjs.todosian.ui.components.todosianFabShape
import kotlinx.coroutines.launch

/** The notes of the selected folder, grouped by subfolder, with create, rename and delete. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListsScreen(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    categorySort: CategorySort,
    title: String,
    onOpenCategory: (android.net.Uri) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewCategorySheet by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    var renameTarget by remember { mutableStateOf<Category?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }

    val scope = rememberCoroutineScope()
    val newCategorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val renameCategorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showNewCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showNewCategorySheet = false
                newCategoryName = ""
            },
            sheetState = newCategorySheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_new_category_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.home_new_category_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                newCategorySheetState.hide()
                                showNewCategorySheet = false
                                newCategoryName = ""
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            viewModel.createCategory(newCategoryName)
                            scope.launch {
                                newCategorySheetState.hide()
                                showNewCategorySheet = false
                                newCategoryName = ""
                            }
                        },
                        enabled = newCategoryName.trim().isNotEmpty(),
                    ) {
                        Text(text = stringResource(R.string.action_save))
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        val validation = remember(renameValue) { validateCategoryNameForRename(renameValue) }
        val errorText = validation.errorMessageResId?.let { stringResource(it) }
        ModalBottomSheet(
            onDismissRequest = {
                renameTarget = null
                renameValue = ""
            },
            sheetState = renameCategorySheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_rename_category_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.home_rename_category_hint)) },
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
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
                                renameCategorySheetState.hide()
                                renameTarget = null
                                renameValue = ""
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            val target = renameTarget
                            val normalized = validation.normalizedFileName
                            if (target != null && normalized != null) {
                                viewModel.renameCategory(target.uri, normalized)
                            }
                            scope.launch {
                                renameCategorySheetState.hide()
                                renameTarget = null
                                renameValue = ""
                            }
                        },
                        enabled = validation.normalizedFileName != null,
                    ) {
                        Text(text = stringResource(R.string.action_save))
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(R.string.home_delete_category_title)) },
            text = {
                Text(
                    text = stringResource(R.string.home_delete_category_body),
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTarget
                        if (target != null) viewModel.deleteCategory(target.uri)
                        deleteTarget = null
                    },
                ) {
                    Text(text = stringResource(R.string.home_delete_category_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
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
                onClick = { showNewCategorySheet = true },
                shape = todosianFabShape(),
                // The cookie shape is concave; Skia's spot-shadow tessellation for concave
                // outlines is pathologically slow and freezes the RenderThread during
                // navigation animations. Keep depth via tonal colors instead of a shadow.
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add_category),
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        val sections = remember(uiState.categories, categorySort) {
            // Notes without any task lines (overview pages, plain notes, an empty inbox) are hidden.
            val withTasks = uiState.categories.filter { it.todoCount > 0 }
            val sorted = when (categorySort) {
                CategorySort.A_Z -> withTasks.sortedBy { it.displayName.lowercase() }
                CategorySort.MOST_REMAINING -> withTasks.sortedByDescending { it.todoCount - it.doneCount }
            }
            sorted.groupBy { listFolder(it.displayName) }
                .toSortedMap(listFolderOrder)
                .map { (folder, lists) ->
                    ListSection(
                        folder = folder,
                        active = lists.filterNot { isPausedStatus(it.status) },
                        paused = lists.filter { isPausedStatus(it.status) },
                    )
                }
        }
        var expandedPausedFolders by remember { mutableStateOf(emptySet<String>()) }

        val contentState = when {
            uiState.isLoading -> HomeContentState.LOADING
            sections.isEmpty() -> HomeContentState.EMPTY
            else -> HomeContentState.DATA
        }

        TodosianPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh(showLoading = false, showRefreshing = true) },
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = contentState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith
                        fadeOut(animationSpec = tween(160))
                },
                label = "lists-content",
                modifier = Modifier.fillMaxSize(),
            ) { state ->
                when (state) {
                    HomeContentState.LOADING -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            TodosianLoadingIndicator()
                        }
                    }

                    HomeContentState.EMPTY -> {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val viewportHeight = maxHeight
                            // Scrollable so pull-to-refresh still works on the empty state.
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    EmptyHomeState(
                                        hasHiddenLists = uiState.categories.isNotEmpty(),
                                        onCreateCategory = { showNewCategorySheet = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(viewportHeight),
                                    )
                                }
                            }
                        }
                    }

                    HomeContentState.DATA -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 96.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            sections.forEach { section ->
                                val lone = section.active.singleOrNull()
                                val headerIsRedundant = section.paused.isEmpty() &&
                                    lone != null &&
                                    lone.displayName.substringAfterLast('/')
                                        .equals(section.folder.substringAfterLast('/'), ignoreCase = true)
                                if (section.folder.isNotEmpty() && !headerIsRedundant) {
                                    item(key = "header:${section.folder}") {
                                        TodosianSectionHeader(
                                            text = section.folder,
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp)
                                                .padding(top = 8.dp),
                                        )
                                    }
                                }
                                items(
                                    items = section.active,
                                    key = { it.uri.toString() },
                                ) { category ->
                                    ListRow(
                                        category = category,
                                        dimmed = false,
                                        onClick = { onOpenCategory(category.uri) },
                                        onRename = {
                                            renameTarget = category
                                            renameValue = category.displayName.substringAfterLast('/')
                                        },
                                        onDelete = { deleteTarget = category },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                if (section.paused.isNotEmpty()) {
                                    val expanded = section.folder in expandedPausedFolders
                                    item(key = "paused:${section.folder}") {
                                        PausedToggleRow(
                                            count = section.paused.size,
                                            expanded = expanded,
                                            onClick = {
                                                expandedPausedFolders = if (expanded) {
                                                    expandedPausedFolders - section.folder
                                                } else {
                                                    expandedPausedFolders + section.folder
                                                }
                                            },
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                    if (expanded) {
                                        items(
                                            items = section.paused,
                                            key = { it.uri.toString() },
                                        ) { category ->
                                            ListRow(
                                                category = category,
                                                dimmed = true,
                                                onClick = { onOpenCategory(category.uri) },
                                                onRename = {
                                                    renameTarget = category
                                                    renameValue = category.displayName.substringAfterLast('/')
                                                },
                                                onDelete = { deleteTarget = category },
                                                modifier = Modifier.animateItem(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ListSection(
    val folder: String,
    val active: List<Category>,
    val paused: List<Category>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    category: Category,
    dimmed: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(category.uri) { mutableStateOf(false) }
    val openCount = category.todoCount - category.doneCount
    val contentColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = category.displayName.substringAfterLast('/'),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                val icon = category.icon
                if (icon != null) {
                    Text(
                        text = icon,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 24.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.List,
                        contentDescription = null,
                    )
                }
            },
            trailingContent = if (openCount > 0) {
                {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = openCount.toString(),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            } else {
                null
            },
            colors = ListItemDefaults.colors(headlineColor = contentColor),
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true },
                onLongClickLabel = stringResource(R.string.cd_overflow_menu),
            ),
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.home_category_action_rename)) },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.home_category_action_delete)) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun PausedToggleRow(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.padding(start = 56.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.padding(start = 4.dp))
        Text(text = pluralStringResource(R.plurals.lists_paused_count, count, count))
    }
}

private enum class HomeContentState {
    LOADING,
    EMPTY,
    DATA,
}

private data class RenameCategoryValidation(
    val normalizedFileName: String?,
    @param:StringRes val errorMessageResId: Int?,
)

private fun validateCategoryNameForRename(input: String): RenameCategoryValidation {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return RenameCategoryValidation(
            normalizedFileName = null,
            errorMessageResId = R.string.home_rename_category_error_empty,
        )
    }

    if (trimmed.contains("sync-conflict", ignoreCase = true)) {
        return RenameCategoryValidation(
            normalizedFileName = null,
            errorMessageResId = R.string.home_rename_category_error_sync_conflict,
        )
    }

    val invalidChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    if (trimmed.any { it in invalidChars }) {
        return RenameCategoryValidation(
            normalizedFileName = null,
            errorMessageResId = R.string.home_rename_category_error_invalid_chars,
        )
    }

    val base = trimmed.removeSuffix(".md").removeSuffix(".MD").removeSuffix(".Md").removeSuffix(".mD").trim()
    if (base.isEmpty()) {
        return RenameCategoryValidation(
            normalizedFileName = null,
            errorMessageResId = R.string.home_rename_category_error_empty,
        )
    }

    val normalized = "$base.md"
    return RenameCategoryValidation(
        normalizedFileName = normalized,
        errorMessageResId = null,
    )
}

@Composable
private fun EmptyHomeState(
    hasHiddenLists: Boolean,
    onCreateCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TodosianEmptyState(
        icon = Icons.Outlined.CreateNewFolder,
        title = stringResource(if (hasHiddenLists) R.string.lists_all_empty_title else R.string.home_empty_title),
        subtitle = stringResource(if (hasHiddenLists) R.string.lists_all_empty_subtitle else R.string.home_empty_subtitle),
        actionLabel = stringResource(R.string.home_create_category),
        onAction = onCreateCategory,
        modifier = modifier,
    )
}

internal class HomeViewModelFactory(
    private val fileRepository: FileRepository,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(fileRepository, appSettingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
