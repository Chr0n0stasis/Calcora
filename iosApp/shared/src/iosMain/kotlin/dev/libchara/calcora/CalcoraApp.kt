package dev.libchara.calcora

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import dev.libchara.calcora.data.AppLanguage
import dev.libchara.calcora.data.HistoryEntry
import dev.libchara.calcora.data.HistoryStore
import dev.libchara.calcora.data.ReleaseInfo
import dev.libchara.calcora.data.SettingsStore
import dev.libchara.calcora.data.ThemeMode
import dev.libchara.calcora.data.UpdateCheckResult
import dev.libchara.calcora.data.UpdateChecker
import dev.libchara.calcora.engine.CalcResult
import dev.libchara.calcora.engine.EvalMode
import dev.libchara.calcora.engine.GiacEngine
import dev.libchara.calcora.ui.CasTerminalScreen
import dev.libchara.calcora.ui.HelpScreen
import dev.libchara.calcora.ui.HistoryLine
import dev.libchara.calcora.ui.HistoryScreen
import dev.libchara.calcora.ui.MainCalculatorScreen
import dev.libchara.calcora.ui.PlotOverlay
import dev.libchara.calcora.ui.ScriptEditorScreen
import dev.libchara.calcora.ui.SettingsScreen
import dev.libchara.calcora.ui.theme.CalcoraTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { CalcoraApp() }

private enum class Destination(val label: StringResource, val symbol: String) {
    Calculator(R.string.tab_calc, "π"), Help(R.string.tab_help, "?"),
    History(R.string.tab_hist, "☰"), Settings(R.string.tab_set, "⚙")
}

private enum class FullScreen { Terminal, Script }

@Composable
fun CalcoraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val historyStore = remember { HistoryStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var history by remember { mutableStateOf(historyStore.load()) }
    var destination by remember { mutableStateOf(Destination.Calculator) }
    var fullScreen by remember { mutableStateOf<FullScreen?>(null) }
    var restoreRequest by remember { mutableStateOf<HistoryEntry?>(null) }
    var plotData by remember { mutableStateOf<String?>(null) }
    var helpFunction by remember { mutableStateOf<String?>(null) }
    var localeKey by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf(TextFieldValue()) }
    var result by remember { mutableStateOf<CalcResult?>(null) }
    var mode by remember { mutableStateOf(settings.defaultEvalMode) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf<ReleaseInfo?>(null) }
    val calculatorHistory = remember { mutableStateListOf<HistoryLine>() }

    fun checkForUpdates(showDialog: Boolean = false) {
        if (checkingUpdate) return
        checkingUpdate = true
        scope.launch {
            updateResult = UpdateChecker.checkLatestRelease()
            if (showDialog) updateDialog = (updateResult as? UpdateCheckResult.UpdateAvailable)?.release
            checkingUpdate = false
        }
    }

    LaunchedEffect(Unit) {
        GiacEngine.initialize(context)
        checkForUpdates(showDialog = true)
    }
    LaunchedEffect(settings.language) {
        val systemChinese = NSLocale.currentLocale.languageCode == "zh"
        GiacEngine.setLanguage(when (settings.language) {
            AppLanguage.Chinese -> 8
            AppLanguage.English -> 2
            AppLanguage.System -> if (systemChinese) 8 else 2
        })
    }

    val darkTheme = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    CalcoraTheme(darkTheme = darkTheme) {
        key(localeKey) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AdaptiveNavigationScaffold(
                    destination = destination,
                    onDestinationChange = {
                        destination = it
                        if (it == Destination.Help) helpFunction = null
                    }
                ) { padding ->
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(tween(400, easing = CubicBezierEasing(0f, 0f, .2f, 1f))) { direction * it } + fadeIn(tween(400)))
                                .togetherWith(slideOutHorizontally(tween(400)) { -direction * it } + fadeOut(tween(200)))
                        }, label = "navigation"
                    ) { page ->
                        when (page) {
                            Destination.Calculator -> MainCalculatorScreen(
                                contentPadding = padding, calcInput = input, calcResult = result,
                                calcMode = mode, calcHistory = calculatorHistory,
                                onInputChange = { input = it }, onResultChange = { result = it },
                                onModeChange = { mode = it }, restoreExpression = restoreRequest?.expression,
                                onRestoreConsumed = { restoreRequest = null },
                                onResult = { history = historyStore.add(it, settings.historyLimit) },
                                onPlotRequest = { plotData = it },
                                onNavigateTerminal = { fullScreen = FullScreen.Terminal },
                                onNavigateScript = { fullScreen = FullScreen.Script },
                                autocompleteEnabled = settings.autocompleteEnabled,
                                syntaxHighlighting = settings.syntaxHighlighting,
                                onNavigateHelp = { helpFunction = it; destination = Destination.Help }
                            )
                            Destination.Help -> HelpScreen(
                                contentPadding = padding, initialFunc = helpFunction,
                                onInsert = {
                                    restoreRequest = HistoryEntry(0, it, "", "", mode = EvalMode.Auto, timestamp = 0)
                                    destination = Destination.Calculator
                                }
                            )
                            Destination.History -> HistoryScreen(
                                contentPadding = padding, history = history,
                                onRestore = { restoreRequest = it; destination = Destination.Calculator },
                                onClear = { historyStore.clear(); history = emptyList() },
                                onDelete = { history = historyStore.remove(it) },
                                onPlotReplay = { plotData = it }
                            )
                            Destination.Settings -> SettingsScreen(
                                contentPadding = padding, settings = settings,
                                onSettingsChange = { updated ->
                                    val languageChanged = settings.language != updated.language
                                    settings = updated
                                    settingsStore.save(updated)
                                    if (languageChanged) {
                                        val locale = when (updated.language) {
                                            AppLanguage.Chinese -> listOf("zh-Hans")
                                            AppLanguage.English -> listOf("en")
                                            AppLanguage.System -> emptyList<String>()
                                        }
                                        if (locale.isEmpty()) NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
                                        else NSUserDefaults.standardUserDefaults.setObject(locale, "AppleLanguages")
                                        localeKey++
                                    }
                                },
                                onClearHistory = { historyStore.clear(); history = emptyList() },
                                onResetSession = GiacEngine::resetSession,
                                updateResult = updateResult, checkingUpdate = checkingUpdate,
                                onCheckUpdate = { checkForUpdates() }
                            )
                        }
                    }
                }

                PlotOverlay(plotData.orEmpty(), plotData != null, onDismiss = { plotData = null })
                when (fullScreen) {
                    FullScreen.Terminal -> CasTerminalScreen(onClose = { fullScreen = null })
                    FullScreen.Script -> ScriptEditorScreen(onClose = { fullScreen = null })
                    null -> Unit
                }
            }

            updateDialog?.let { release ->
                AlertDialog(
                    onDismissRequest = { updateDialog = null },
                    title = { Text(stringResource(R.string.update_dialog_title)) },
                    text = { Text(stringResource(R.string.update_dialog_message, release.version)) },
                    confirmButton = {
                        TextButton(onClick = {
                            updateDialog = null
                            context.startActivity(android.content.Intent(
                                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(release.pageUrl)))
                        }) { Text(stringResource(R.string.settings_open_release)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateDialog = null }) {
                            Text(stringResource(R.string.update_dialog_later))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdaptiveNavigationScaffold(
    destination: Destination,
    onDestinationChange: (Destination) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember {
        movableContentOf { currentContent(PaddingValues(0.dp)) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Scaffold { safeAreaPadding ->
                Row(Modifier.fillMaxSize().padding(safeAreaPadding)) {
                    NavigationRail(Modifier.fillMaxHeight()) {
                        Destination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = destination == item,
                                onClick = { onDestinationChange(item) },
                                icon = { Text(item.symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                                label = { Text(stringResource(item.label), fontSize = 11.sp) }
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        movableContent()
                    }
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar(Modifier.fillMaxWidth()) {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { onDestinationChange(item) },
                                icon = { Text(item.symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                                label = { Text(stringResource(item.label), fontSize = 11.sp) }
                            )
                        }
                    }
                }
            ) { safeAreaPadding ->
                Box(Modifier.fillMaxSize().padding(safeAreaPadding)) {
                    movableContent()
                }
            }
        }
    }
}
