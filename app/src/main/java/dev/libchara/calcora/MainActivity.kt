package dev.libchara.calcora

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libchara.calcora.data.AppLanguage
import dev.libchara.calcora.data.AppSettings
import dev.libchara.calcora.data.HistoryEntry
import dev.libchara.calcora.data.HistoryStore
import dev.libchara.calcora.data.ReleaseInfo
import dev.libchara.calcora.data.SettingsStore
import dev.libchara.calcora.data.ThemeMode
import dev.libchara.calcora.data.UpdateCheckResult
import dev.libchara.calcora.data.UpdateChecker
import dev.libchara.calcora.engine.GiacEngine
import dev.libchara.calcora.ScriptActivity
import dev.libchara.calcora.ui.HelpScreen
import dev.libchara.calcora.ui.HistoryLine
import dev.libchara.calcora.ui.HistoryScreen
import dev.libchara.calcora.ui.MainCalculatorScreen
import dev.libchara.calcora.ui.PlotOverlay
import dev.libchara.calcora.ui.SettingsScreen
import dev.libchara.calcora.ui.theme.CalcoraTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val langName = prefs.getString("lang", "System") ?: "System"
        val locale = when (langName) { "Chinese" -> Locale("zh"); "System" -> Locale.getDefault(); else -> Locale("en") }
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        @Suppress("DEPRECATION") super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initDest = Destination.entries.getOrNull(intent.getIntExtra("restore_dest", 0))
        setContent { CalcoraApp(initialDestination = initDest) }
    }
}

@Composable
private fun CalcoraApp(initialDestination: Destination? = null) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { GiacEngine.initialize(context) }
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val historyStore = remember { HistoryStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var history by remember { mutableStateOf(historyStore.load()) }
    var destination by remember { mutableStateOf(initialDestination ?: Destination.Calculator) }
    var restoreRequest by remember { mutableStateOf<HistoryEntry?>(null) }
    var plotData by remember { mutableStateOf<String?>(null) }
    var helpFunc by remember { mutableStateOf<String?>(null) }
    var localeKey by remember { mutableStateOf(0) }
    var calcInput by remember { mutableStateOf(TextFieldValue("")) }
    var calcResult by remember { mutableStateOf<dev.libchara.calcora.engine.CalcResult?>(null) }
    var calcMode by remember { mutableStateOf(settings.defaultEvalMode) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateDialogRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    val calcHistoryLines = remember { mutableStateListOf<HistoryLine>() }

    fun openReleasePage(release: ReleaseInfo) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
    }

    fun checkForUpdates(showDialogOnUpdate: Boolean = false) {
        if (checkingUpdate) return
        checkingUpdate = true
        updateResult = null
        scope.launch {
            val result = UpdateChecker.checkLatestRelease()
            updateResult = result
            if (showDialogOnUpdate && result is UpdateCheckResult.UpdateAvailable) {
                updateDialogRelease = result.release
            }
            checkingUpdate = false
        }
    }

    LaunchedEffect(Unit) { checkForUpdates(showDialogOnUpdate = true) }

    LaunchedEffect(settings.language) {
        val code = when (settings.language) {
            AppLanguage.Chinese -> 8; AppLanguage.English -> 2
            AppLanguage.System -> if (Locale.getDefault().language == "zh") 8 else 2
        }
        GiacEngine.setLanguage(code)
    }

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) { ThemeMode.System -> systemDark; ThemeMode.Light -> false; ThemeMode.Dark -> true }

    CalcoraTheme(darkTheme = darkTheme, dynamicColor = true) {
        androidx.compose.runtime.key(localeKey) {
            AdaptiveNavigationScaffold(
                destination = destination,
                onDestinationChange = {
                    destination = it
                    if (it == Destination.Help) helpFunc = null
                }
            ) { padding ->
                AnimatedContent(targetState = destination,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(400, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f))) { dir * it } + fadeIn(tween(400, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f))))
                            .togetherWith(slideOutHorizontally(tween(400, easing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f))) { -dir * it } + fadeOut(tween(200, easing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f))))
                    }, label = "nav") { dest ->
                    when (dest) {
                        Destination.Calculator -> MainCalculatorScreen(
                            contentPadding = padding, calcInput = calcInput, calcResult = calcResult, calcMode = calcMode, calcHistory = calcHistoryLines,
                            onInputChange = { calcInput = it }, onResultChange = { calcResult = it }, onModeChange = { calcMode = it },
                            restoreExpression = restoreRequest?.expression,
                            onRestoreConsumed = { restoreRequest = null },
                            onResult = { history = historyStore.add(it, settings.historyLimit) },
                            onPlotRequest = { plotData = it },
                            onNavigateTerminal = { context.startActivity(Intent(context, TerminalActivity::class.java)) },
                            onNavigateScript = { context.startActivity(Intent(context, ScriptActivity::class.java)) },
                            autocompleteEnabled = settings.autocompleteEnabled,
                            syntaxHighlighting = settings.syntaxHighlighting,
                            onNavigateHelp = { func -> helpFunc = func; destination = Destination.Help })
                        Destination.Help -> HelpScreen(
                            contentPadding = padding, initialFunc = helpFunc,
                            onInsert = { func ->
                                restoreRequest = HistoryEntry(
                                    id = 0, expression = func, result = "", numeric = "",
                                    mode = dev.libchara.calcora.engine.EvalMode.Auto, timestamp = 0
                                )
                                destination = Destination.Calculator })
                        Destination.History -> HistoryScreen(
                            contentPadding = padding, history = history,
                            onRestore = { restoreRequest = it; destination = Destination.Calculator },
                            onClear = { historyStore.clear(); history = emptyList() },
                        onDelete = { history = historyStore.remove(it) },
                            onPlotReplay = { plotData = it })
                        Destination.Settings -> SettingsScreen(
                            contentPadding = padding, settings = settings,
                            onSettingsChange = {
                                val langChanged = settings.language != it.language
                                settings = it; settingsStore.save(it)
                                if (langChanged) {
                                    (context as android.app.Activity).intent.putExtra("restore_dest", destination.ordinal)
                                    (context as android.app.Activity).recreate()
                                }
                            },
                            onClearHistory = { historyStore.clear(); history = emptyList() },
                            onResetSession = { GiacEngine.resetSession() },
                            updateResult = updateResult,
                            checkingUpdate = checkingUpdate,
                            onCheckUpdate = { checkForUpdates() })
                    }
                }
            }

            PlotOverlay(plotData = plotData.orEmpty(), visible = plotData != null, onDismiss = { plotData = null })
            updateDialogRelease?.let { release ->
                AlertDialog(
                    onDismissRequest = { updateDialogRelease = null },
                    title = { Text(stringResource(R.string.update_dialog_title)) },
                    text = { Text(stringResource(R.string.update_dialog_message, release.version)) },
                    confirmButton = {
                        TextButton(onClick = {
                            updateDialogRelease = null
                            openReleasePage(release)
                        }) {
                            Text(stringResource(R.string.settings_open_release))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateDialogRelease = null }) {
                            Text(stringResource(R.string.update_dialog_later))
                        }
                    }
                )
            }
        }
    }
}

private enum class Destination(val labelRes: Int, val symbol: String) {
    Calculator(R.string.tab_calc, "\u03C0"), Help(R.string.tab_help, "?"),
    History(R.string.tab_hist, "\u2630"), Settings(R.string.tab_set, "\u2699")
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
                                label = { Text(stringResource(item.labelRes), fontSize = 11.sp) }
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
                                label = { Text(stringResource(item.labelRes), fontSize = 11.sp) }
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
