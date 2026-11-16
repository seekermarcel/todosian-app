package com.isotjs.todosian.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isotjs.todosian.BuildConfig
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.PreferencesManager
import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.ui.components.ChangelogBottomSheet
import com.isotjs.todosian.ui.dailyfocus.AgendaRange
import com.isotjs.todosian.ui.dailyfocus.AgendaScreen
import kotlinx.coroutines.flow.collectLatest

private enum class HomeTab { TODAY, WEEK, LISTS }

/**
 * Start screen: a Today tab and a Lists tab. Without Tasks plugin support there are no dates,
 * so there is no "today" and only the lists are shown.
 */
@Composable
fun HomeScreen(
    fileRepository: FileRepository,
    appSettingsRepository: AppSettingsRepository,
    preferencesManager: PreferencesManager,
    onOpenCategory: (android.net.Uri) -> Unit,
    onOpenSettings: () -> Unit,
    onAddTask: () -> Unit,
    refreshSignal: Long,
    onRequireOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(fileRepository, appSettingsRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showChangelogSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val folderUri = preferencesManager.getFolderUri()
        val lastSeenVersion = preferencesManager.getLastSeenVersionCode()
        if (folderUri != null && lastSeenVersion < BuildConfig.VERSION_CODE) {
            showChangelogSheet = true
            preferencesManager.saveLastSeenVersionCode(BuildConfig.VERSION_CODE)
        }
    }
    if (showChangelogSheet) {
        ChangelogBottomSheet(onDismissRequest = { showChangelogSheet = false })
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Re-scan on every resume so tasks checked in the widget, edits made in Obsidian,
            // or sync changes that happened while the app was away are picked up.
            viewModel.refresh(showLoading = false)
        }
    }
    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0L) viewModel.refresh()
    }

    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeViewModel.Event.ShowMessage -> {
                    snackbarHostState.showSnackbar(resources.getString(event.messageResId))
                }
                HomeViewModel.Event.RequireOnboarding -> onRequireOnboarding()
                is HomeViewModel.Event.OpenCategory -> onOpenCategory(event.uri)
            }
        }
    }

    // Null until the stored settings arrive, so the tabs don't flash in or out on start.
    val settings: AppSettings = appSettingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
        .value ?: return
    val showTabs = settings.enableTasksPluginSupport
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.TODAY) }

    Scaffold(
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.TODAY,
                        onClick = { selectedTab = HomeTab.TODAY },
                        icon = { Icon(imageVector = Icons.Outlined.Today, contentDescription = null) },
                        label = { Text(text = stringResource(R.string.home_tab_today)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.WEEK,
                        onClick = { selectedTab = HomeTab.WEEK },
                        icon = { Icon(imageVector = Icons.Outlined.DateRange, contentDescription = null) },
                        label = { Text(text = stringResource(R.string.home_tab_week)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.LISTS,
                        onClick = { selectedTab = HomeTab.LISTS },
                        icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.List, contentDescription = null) },
                        label = { Text(text = stringResource(R.string.home_tab_lists)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            val agendaRange = when (selectedTab) {
                HomeTab.TODAY -> AgendaRange.TODAY
                HomeTab.WEEK -> AgendaRange.WEEK
                HomeTab.LISTS -> null
            }
            if (showTabs && agendaRange != null) {
                AgendaScreen(
                    range = agendaRange,
                    fileRepository = fileRepository,
                    appSettingsRepository = appSettingsRepository,
                    refreshSignal = refreshSignal,
                    onOpenSettings = onOpenSettings,
                    onAddTask = onAddTask,
                )
            } else {
                ListsScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    categorySort = settings.categorySort,
                    title = stringResource(if (showTabs) R.string.home_tab_lists else R.string.home_title),
                    onOpenCategory = onOpenCategory,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}
