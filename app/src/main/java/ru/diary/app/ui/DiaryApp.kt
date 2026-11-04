package ru.diary.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.diary.app.di.AppContainer
import ru.diary.app.data.settings.SettingsManager
import ru.diary.app.data.datastore.dataStore
import ru.diary.app.data.datastore.UserPrefs
import ru.diary.app.ui.calendar.CalendarScreen
import ru.diary.app.ui.notes.NoteEditScreen
import ru.diary.app.ui.notes.NotesScreen
import ru.diary.app.ui.onboarding.PrivacyConsentScreen
import ru.diary.app.ui.settings.SettingsScreen
import ru.diary.app.ui.settings.PrivacyPolicyScreen
import ru.diary.app.ui.tasks.TasksScreen
import ru.diary.app.ui.TrashScreen
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability
import ru.rustore.sdk.review.RuStoreReviewManagerFactory
import ru.rustore.sdk.review.model.ReviewInfo

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun DiaryApp(container: AppContainer, settingsManager: SettingsManager) {
    val context = LocalContext.current
    val userPrefs = remember { UserPrefs(context.dataStore) }
    val privacyAccepted by userPrefs.privacyAccepted.collectAsState(initial = null)
    val privacyVersion by userPrefs.privacyVersionAccepted.collectAsState(initial = null)
    val currentPolicyVersion = 1

    // Пока DataStore не отдал сохранённые значения, не показываем ни экран
    // согласия, ни приложение — иначе при запуске на секунду мелькает политика.
    val accepted = privacyAccepted ?: return
    val acceptedVersion = privacyVersion ?: return

    // Разрешения (микрофон / уведомления) запрашиваются только после того,
    // как пользователь принял политику конфиденциальности.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* результат игнорируем — приложение работает и без этих разрешений */ }
    var permissionsRequested by rememberSaveable { mutableStateOf(false) }

    // Аналитика и разрешения запускаются исключительно после согласия.
    // Для уже согласившегося пользователя аналитика поднимается при запуске
    // (один раз), а разрешения запрашиваются единожды.
    LaunchedEffect(accepted, acceptedVersion) {
        if (!accepted || acceptedVersion < currentPolicyVersion) return@LaunchedEffect

        (context.applicationContext as? ru.diary.app.DiaryApp)?.initAnalyticsIfNeeded()

        if (permissionsRequested) return@LaunchedEffect
        permissionsRequested = true

        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    if (!accepted || acceptedVersion < currentPolicyVersion) {
        var showPolicy by remember { mutableStateOf(false) }
        var policyRead by remember { mutableStateOf(false) }
        val consentScope = rememberCoroutineScope()
        if (showPolicy) {
            PrivacyPolicyScreen(onBack = { showPolicy = false })
        } else {
            PrivacyConsentScreen(
                isPolicyRead = policyRead,
                onAccept = {
                    consentScope.launch(Dispatchers.IO) {
                        userPrefs.setPrivacyAccepted(accepted = true, version = currentPolicyVersion)
                    }
                },
                onOpenPrivacyPolicy = {
                    policyRead = true
                    showPolicy = true
                }
            )
        }
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // На широких экранах (планшет, альбом) навигация уезжает в боковой рейл,
    // на телефоне остаётся нижняя панель.
    val activity = context as? Activity
    val widthSizeClass = activity
        ?.let { calculateWindowSizeClass(it).widthSizeClass }
        ?: WindowWidthSizeClass.Compact
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    // Один раз через несколько дней после установки предлагаем оценить приложение
    // через встроенный диалог RuStore — оценка ставится не выходя из приложения.
    val reviewManager = remember { RuStoreReviewManagerFactory.create(context) }
    var reviewInfo by remember { mutableStateOf<ReviewInfo?>(null) }

    LaunchedEffect(Unit) {
        if (settingsManager.isReviewPromptDue()) {
            // Отметку «показано» ставим только после успешного показа диалога:
            // иначе сбой RuStore (нет приложения, лимит запросов) навсегда
            // отключал бы предложение оценить приложение.
            reviewManager.requestReviewFlow()
                .addOnSuccessListener { reviewInfo = it }
                .addOnFailureListener {
                    // Нет RuStore, лимит запросов или другой сбой — попробуем позже.
                }
        }
    }

    val pendingReviewInfo = reviewInfo
    if (pendingReviewInfo != null) {
        LaunchedEffect(pendingReviewInfo) {
            delay(1_000)
            reviewManager.launchReviewFlow(pendingReviewInfo)
                .addOnSuccessListener {
                    reviewInfo = null
                    settingsManager.markReviewPromptShown()
                }
                .addOnFailureListener { reviewInfo = null }
        }
    }

    // Раз в сутки проверяем обновление; при наличии сразу запускаем нативный
    // диалог RuStore (startUpdateFlow). Установку завершаем сами (completeUpdate),
    // когда всё приложение уходит в фон — при FLEXIBLE это перезапускает процесс,
    // поэтому не делаем это посреди сессии и не показываем свой диалог.
    val updateManager = remember { RuStoreAppUpdateManagerFactory.create(context) }
    var updateDownloaded by remember { mutableStateOf(false) }
    var installAttempted by remember { mutableStateOf(false) }
    var downloadingListener by remember { mutableStateOf<InstallStateUpdateListener?>(null) }

    LaunchedEffect(Unit) {
        if (settingsManager.isUpdateCheckDue()) {
            settingsManager.markUpdateCheckDone()
            updateManager.getAppUpdateInfo()
                .addOnSuccessListener { info ->
                    if (info.updateAvailability != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener
                    if (info.installStatus == InstallStatus.DOWNLOADED) {
                        // Скачано ранее — установим при уходе приложения в фон.
                        updateDownloaded = true
                    } else {
                        val listener = InstallStateUpdateListener { state ->
                            if (state.installStatus == InstallStatus.DOWNLOADED) {
                                updateDownloaded = true
                                downloadingListener?.let { updateManager.unregisterListener(it) }
                                downloadingListener = null
                            }
                        }
                        downloadingListener = listener
                        updateManager.registerListener(listener)
                        updateManager.startUpdateFlow(info, AppUpdateOptions.Builder().build())
                    }
                }
                .addOnFailureListener {
                    // RuStore недоступен или не установлен — молча пропускаем.
                }
        }
    }

    // Завершаем установку, когда всё приложение уходит в фон (а не при любом
    // перекрытии активности). Флаг installAttempted защищает от цикла
    // перезапусков, если скачанное обновление по какой-то причине не применилось.
    val processLifecycleOwner = ProcessLifecycleOwner.get()
    DisposableEffect(processLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && updateDownloaded && !installAttempted) {
                installAttempted = true
                updateManager.completeUpdate(
                    AppUpdateOptions.Builder().appUpdateType(AppUpdateType.FLEXIBLE).build()
                )
            }
        }
        processLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            processLifecycleOwner.lifecycle.removeObserver(observer)
            downloadingListener?.let { updateManager.unregisterListener(it) }
        }
    }

    val selectTab: (BottomTab) -> Unit = { tab ->
        if (currentRoute?.startsWith(tab.route) != true) {
            navController.navigate(tab.route) {
                popUpTo(BottomTab.Tasks.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail) {
            NavigationRail {
                bottomTabs.forEach { tab ->
                    val selected = currentRoute?.startsWith(tab.route) == true
                    NavigationRailItem(
                        selected = selected,
                        onClick = { selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.iconSelected else tab.icon,
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }

        Scaffold(
            // Верхние отступы обрабатывает топ-бар каждого экрана, нижние —
            // NavigationBar. Здесь добавляем только нижний inset для режима рейла.
            contentWindowInsets = if (useRail) {
                WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
            } else {
                WindowInsets(0, 0, 0, 0)
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        bottomTabs.forEach { tab ->
                            val selected = currentRoute?.startsWith(tab.route) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.iconSelected else tab.icon,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = BottomTab.Tasks.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(BottomTab.Tasks.route) {
                        val selectedDate = it.savedStateHandle
                            .getStateFlow<String?>("selectedDate", null)
                            .collectAsState().value
                        TasksScreen(
                            repository = container.taskRepository,
                            alarmScheduler = container.alarmScheduler,
                            initialDate = selectedDate,
                            onOpenTrash = { navController.navigate("trash") }
                        )
                    }
                    composable(BottomTab.Calendar.route) {
                        CalendarScreen(
                            repository = container.taskRepository,
                            noteRepository = container.noteRepository,
                            onDateSelected = { date ->
                                navController.getBackStackEntry(BottomTab.Tasks.route)
                                    .savedStateHandle["selectedDate"] = date

                                navController.navigate(BottomTab.Tasks.route) {
                                    popUpTo(BottomTab.Tasks.route) { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable(BottomTab.Notes.route) {
                        NotesScreen(
                            repository = container.noteRepository,
                            onNoteClick = { noteId ->
                                navController.navigate("note_edit/$noteId")
                            },
                            onAddNote = {
                                navController.navigate("note_edit/0")
                            },
                            onOpenTrash = { navController.navigate("trash") }
                        )
                    }
                    composable(BottomTab.Settings.route) {
                        SettingsScreen(
                            settingsManager = settingsManager,
                            container = container,
                            onOpenPrivacyPolicy = { navController.navigate("privacy_policy") }
                        )
                    }
                    composable("privacy_policy") {
                        PrivacyPolicyScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "note_edit/{noteId}",
                        arguments = listOf(navArgument("noteId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
                        NoteEditScreen(
                            noteId = noteId,
                            repository = container.noteRepository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("trash") {
                        TrashScreen(
                            taskRepository = container.taskRepository,
                            noteRepository = container.noteRepository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
