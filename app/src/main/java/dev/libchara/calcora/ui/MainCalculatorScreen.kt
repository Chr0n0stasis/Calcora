package dev.libchara.calcora.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin


import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.libchara.calcora.R
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import dev.libchara.calcora.engine.CalcResult
import dev.libchara.calcora.engine.EvalMode
import dev.libchara.calcora.engine.ExpressionFormatter
import dev.libchara.calcora.engine.GiacEngine
import dev.libchara.calcora.engine.HelpParser
import dev.libchara.calcora.engine.MathSource
import dev.libchara.calcora.engine.NaturalMathEditing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainCalculatorScreen(
    contentPadding: PaddingValues,
    calcInput: TextFieldValue = TextFieldValue(""),
    calcResult: CalcResult? = null,
    calcMode: EvalMode = EvalMode.Auto,
    calcHistory: SnapshotStateList<HistoryLine> = mutableStateListOf(),
    onInputChange: (TextFieldValue) -> Unit = {},
    onResultChange: (CalcResult?) -> Unit = {},
    onModeChange: (EvalMode) -> Unit = {},
    restoreExpression: String?,
    onRestoreConsumed: () -> Unit,
    onResult: (CalcResult) -> Unit,
    onPlotRequest: (String) -> Unit,
    onNavigateTerminal: () -> Unit,
    onNavigateScript: () -> Unit = {},
    onNavigateHelp: (String?) -> Unit,
    autocompleteEnabled: Boolean = true,
    syntaxHighlighting: Boolean = true,
) {
    var input by remember { mutableStateOf(calcInput) }
    var result by remember { mutableStateOf(calcResult) }
    var mode by remember { mutableStateOf(calcMode) }
    var functionsExpanded by remember { mutableStateOf(false) }
    var varsExpanded by remember { mutableStateOf(false) }
    var fxExpanded by remember { mutableStateOf(false) }
    var giacDebugEnabled by rememberSaveable { mutableStateOf(false) }
    var previewPlotData by remember { mutableStateOf(calcResult?.plotData.orEmpty()) }

    var evaluating by remember { mutableStateOf(false) }
    var showSpinner by remember { mutableStateOf(false) }
    fun trunc(s: String) = if (s.length > 200) s.take(200) + "…" else s

    // Tokenizer — extract current identifier under cursor
    // Triggers autocomplete only for identifiers starting with a letter (handles plot3d etc.)
    fun currentWord(): String {
        val text = input.text
        val cursor = input.selection.start.coerceIn(0, text.length)
        var s = cursor
        while (s > 0 && text[s - 1].let { it.isLetterOrDigit() || it == '_' }) s--
        var e = cursor
        while (e < text.length && text[e].let { it.isLetterOrDigit() || it == '_' }) e++
        val w = text.substring(s, e)
        // identifier must start with a letter and contain at least one letter
        return if (w.isNotEmpty() && w[0].isLetter() && w.any { it.isLetter() }) w else ""
    }
    fun wordRange(): Pair<Int, Int> {
        val text = input.text
        val cursor = input.selection.start.coerceIn(0, text.length)
        var s = cursor
        while (s > 0 && text[s - 1].let { it.isLetterOrDigit() || it == '_' }) s--
        var e = cursor
        while (e < text.length && text[e].let { it.isLetterOrDigit() || it == '_' }) e++
        return s to e
    }
    var autocompleteVisible by remember { mutableStateOf(false) }
    val acTransitionState = remember { androidx.compose.animation.core.MutableTransitionState(false) }

    var resultDialog by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    fun resultMath(calcResult: CalcResult): Pair<String, MathSource> =
        if (!calcResult.isError && calcResult.latex.isNotBlank()) calcResult.latex to MathSource.Latex
        else calcResult.primary to MathSource.Xcas

    fun updateInput(next: TextFieldValue) {
        input = next
        onInputChange(next)
        if (next.text.isEmpty() && result != null) {
            result = null
            resultDialog = false
            onResultChange(null)
        }
    }

    LaunchedEffect(result?.isPlot, result?.plotData) {
        if (result?.isPlot == true) previewPlotData = result?.plotData.orEmpty()
    }

LaunchedEffect(restoreExpression) {
        val expression = restoreExpression
        if (!expression.isNullOrBlank()) {
            input = TextFieldValue(expression)
            result = null
            onInputChange(input)
            onResultChange(null)
            onRestoreConsumed()
        }
    }

    fun insert(text: String) {
        val template = text.replace("\u25A1", "")
        val firstPlaceholder = text.indexOf('\u25A1').takeIf { it >= 0 } ?: template.length
        val start = input.selection.min
        val end = input.selection.max
        val next = input.text.replaceRange(start, end, template)
        val proposedCursor = start + firstPlaceholder.coerceAtMost(template.length)
        val adjusted = NaturalMathEditing.adjust(
            input.text, input.selection.start, input.selection.end,
            next, proposedCursor, proposedCursor
        )
        input = TextFieldValue(
            adjusted.text,
            selection = androidx.compose.ui.text.TextRange(adjusted.selectionStart, adjusted.selectionEnd)
        )
        onInputChange(input)
    }

    fun extractHelpArg(input: String): String {
        val idx = input.indexOf('(')
        if (idx < 0) return ""
        var inner = input.substring(idx + 1)
        val end = inner.lastIndexOf(')')
        if (end < 0) return inner.trim()
        inner = inner.substring(0, end).trim().trim('"')
        // Strip trailing ()
        while (inner.endsWith("()")) inner = inner.removeSuffix("()")
        return inner
    }

    fun evaluate() {
        val committed = NaturalMathEditing.commitInferredDelimiters(
            input.text,
            input.selection.start,
            input.selection.end
        )
        if (committed.text != input.text) {
            input = TextFieldValue(
                committed.text,
                selection = androidx.compose.ui.text.TextRange(committed.selectionStart, committed.selectionEnd)
            )
            onInputChange(input)
        }
        val text = committed.text.trim()
        if (text.isEmpty() || evaluating) return
        if (text.startsWith("help(", ignoreCase = true)) {
            val arg = extractHelpArg(text)
            if (arg.isNotBlank() && arg.all { it.isLetterOrDigit() || it == '_' }) {
                onNavigateHelp(arg)
                return
            }
        }
        evaluating = true
        showSpinner = false
        scope.launch {
            // Show spinner after 2s of waiting
            val spinnerJob = launch {
                delay(2000)
                showSpinner = true
            }
            val evaluated = withContext(Dispatchers.Default) {
                GiacEngine.evaluate(text, mode)
            }
            spinnerJob.cancel()
            calcHistory.add(HistoryLine(text, evaluated))
            if (calcHistory.size > 8) calcHistory.removeAt(0)
            listState.animateScrollToItem(maxOf(0, calcHistory.size - 1))
            result = evaluated
            onResult(evaluated)
            onResultChange(evaluated)
            evaluating = false
            showSpinner = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { giacDebugEnabled = !giacDebugEnabled },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (giacDebugEnabled) colors.primaryContainer else colors.background,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Text(
                    "DBG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (giacDebugEnabled) colors.onPrimaryContainer else colors.onSurfaceVariant
                )
            }
            IconButton(onClick = onNavigateScript, modifier = Modifier.size(40.dp)) {
                Text("</>", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
            IconButton(onClick = onNavigateTerminal, modifier = Modifier.size(40.dp)) {
                Text(">_", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
        }

        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxWidth()
        ) {
            // Scrollable history
            if (calcHistory.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    items(calcHistory, key = { it.id }) { line ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            NaturalMathView(
                                source = line.input, fontSize = 13f,
                                color = colors.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().clickable { input = TextFieldValue(line.input); onInputChange(input) }
                            )
                            if (line.result.isPlot) {
                                Text(
                                    text = stringResource(R.string.btn_view_plot) + "  ›",
                                    fontSize = 13.sp,
                                    color = colors.primary.copy(alpha = 0.75f),
                                    modifier = Modifier.clickable { onPlotRequest(line.result.plotData) }.padding(vertical = 3.dp)
                                )
                            } else {
                                val (historyResultSource, historyResultKind) = resultMath(line.result)
                                NaturalMathView(
                                    source = historyResultSource, sourceKind = historyResultKind, fontSize = 15f,
                                    color = if (line.result.isError) colors.error.copy(alpha = 0.65f) else colors.primary.copy(alpha = 0.75f),
                                    modifier = Modifier.fillMaxWidth().clickable { input = TextFieldValue(line.result.primary); onInputChange(input) }
                                )
                            }
                            Spacer(Modifier.height(1.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.onSurface.copy(alpha = 0.08f)))
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Current input + result (pinned below history), autocomplete overlay
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    // Reserve space for autocomplete overlay when visible
                    if (autocompleteVisible) Spacer(Modifier.height(44.dp))
                    AnimatedVisibility(
                        visible = giacDebugEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val engineCommand = ExpressionFormatter.toEngineInput(input.text)
                        GiacCommandDebugPanel(
                            command = engineCommand,
                            onCopy = { clipboard.setText(AnnotatedString(engineCommand)) }
                        )
                    }
                AnimatedVisibility(
                    visible = result?.isPlot == true,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = 0.88f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        expandFrom = Alignment.Bottom
                    ) + fadeIn(tween(180)) + scaleIn(
                        animationSpec = spring(
                            dampingRatio = 0.88f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialScale = 0.96f,
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(210, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Bottom
                    ) + fadeOut(tween(150)) + scaleOut(
                        animationSpec = tween(190, easing = FastOutSlowInEasing),
                        targetScale = 0.97f,
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val displayedPlotData = result?.plotData
                        ?.takeIf { result?.isPlot == true }
                        ?: previewPlotData
                    PlotPreviewCard(
                        plotData = displayedPlotData,
                        onClick = { onPlotRequest(displayedPlotData) },
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (input.text.isNotEmpty()) Text("×", fontSize = 16.sp, color = colors.onSurface.copy(alpha = 0.3f), modifier = Modifier.clickable { updateInput(TextFieldValue("")) }.padding(end = 8.dp, bottom = 2.dp))
                    NaturalMathEditor(
                        value = input,
                        onValueChange = ::updateInput,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        fontSize = 25f,
                        syntaxHighlighting = syntaxHighlighting,
                        onDone = { evaluate() }
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (result?.isPlot != true) {
                    AnimatedContent(
                        targetState = result?.let { resultMath(it) } ?: ("" to MathSource.Xcas),
                        transitionSpec = { (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 }).togetherWith(fadeOut(tween(150))) },
                        label = "result", modifier = Modifier.fillMaxWidth().clickable { if (result?.primary?.isNotBlank() == true) resultDialog = true }
                    ) { (text, sourceKind) ->
                        val resultSize = when { text.length > 40 -> 18.sp; text.length > 20 -> 22.sp; else -> 26.sp }
                        NaturalMathView(
                            source = text,
                            sourceKind = sourceKind,
                            fontSize = resultSize.value,
                            color = if (result?.isError == true) colors.error else colors.primary,
                            modifier = Modifier.fillMaxWidth(), minHeight = 42.dp
                        )
                    }
                    result?.numeric?.takeIf { it.isNotBlank() && it != result?.primary && result?.isError != true }?.let { num ->
                        val tn = result?.numericLatex?.takeIf { it.isNotBlank() } ?: trunc(num)
                        val numericKind = if (result?.numericLatex.isNullOrBlank()) MathSource.Xcas else MathSource.Latex
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NaturalMathView(tn, sourceKind = numericKind, fontSize = 14f, color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                } // end inner Column

                // Autocomplete overlay — positioned at top-end, no layout impact
                val word = currentWord()
                val helpReady = HelpParser.isReady.value
                val autocompleteHints = remember(word, helpReady) {
                    if (autocompleteEnabled && word.length >= 1) GiacEngine.helpSearchScored(word).take(8) else emptyList()
                }
                LaunchedEffect(word) {
                    if (autocompleteEnabled) autocompleteVisible = word.length >= 1 && autocompleteHints.isNotEmpty()
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = autocompleteVisible,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = fadeIn(tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
                    exit = androidx.compose.animation.ExitTransition.None
                ) {
                    Row(
                        modifier = Modifier.height(44.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        autocompleteHints.forEach { hint ->
                            AssistChip(
                                onClick = {
                                    val (wStart, wEnd) = wordRange()
                                    val replacement = hint.name + "()"
                                    val newText = input.text.replaceRange(wStart, wEnd, replacement)
                                    val cursorPos = wStart + replacement.length - 1
                                    input = TextFieldValue(newText, selection = androidx.compose.ui.text.TextRange(cursorPos))
                                    onInputChange(input)
                                    autocompleteVisible = false
                                },
                                label = { Text(hint.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            } // end Box overlay
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            EvalMode.entries.forEach { item ->
                FilterChip(selected = mode == item, onClick = { mode = item; onModeChange(item) }, label = { val label = when (item) { EvalMode.Auto -> stringResource(R.string.eval_auto); EvalMode.Exact -> stringResource(R.string.eval_exact); EvalMode.Approx -> stringResource(R.string.eval_approx); EvalMode.RawXcas -> stringResource(R.string.eval_raw) }
                            Text(label, fontSize = 11.sp, maxLines = 1) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.onPrimary))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { insert("help(□)") }, label = { Text("?", fontSize = 13.sp, fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(4.dp))
                AssistChip(onClick = {
                    val cursor = input.selection.start.coerceAtLeast(0)
                    if (cursor > 0 || input.text.isNotEmpty()) {
                        val moved = NaturalMathEditing.moveHorizontally(input.text, cursor, -1)
                        input = TextFieldValue(moved.text, selection = androidx.compose.ui.text.TextRange(moved.selectionStart))
                        onInputChange(input)
                    }
                }, label = { Text("◀", fontSize = 13.sp) }, shape = RoundedCornerShape(14.dp))
                AssistChip(onClick = {
                    val cursor = input.selection.end
                    if (input.text.isNotEmpty()) {
                        val moved = NaturalMathEditing.moveHorizontally(input.text, cursor, 1)
                        input = TextFieldValue(moved.text, selection = androidx.compose.ui.text.TextRange(moved.selectionStart))
                        onInputChange(input)
                    }
                }, label = { Text("▶", fontSize = 13.sp) }, shape = RoundedCornerShape(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { varsExpanded = !varsExpanded; fxExpanded = false; functionsExpanded = false }, label = { Text(stringResource(R.string.panel_vars), fontSize = 11.sp) }, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(4.dp))
                AssistChip(onClick = { fxExpanded = !fxExpanded; varsExpanded = false; functionsExpanded = false }, label = { Text(stringResource(R.string.panel_fx), fontSize = 11.sp) }, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(4.dp))
                AssistChip(onClick = { functionsExpanded = !functionsExpanded; varsExpanded = false; fxExpanded = false }, label = { Text(stringResource(R.string.panel_funcs), fontSize = 11.sp) }, shape = RoundedCornerShape(14.dp))
            }
        }

        AnimatedVisibility(visible = varsExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).padding(horizontal = 10.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    VarPanel(onInsert = ::insert)
                }
            }
        }
        AnimatedVisibility(visible = fxExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).padding(horizontal = 10.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FxPanel(onInsert = ::insert)
                }
            }
        }
        AnimatedVisibility(visible = functionsExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).padding(horizontal = 10.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FunctionsPanel(onInsert = ::insert)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            CALCULATOR_KEY_ROWS.forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    row.forEach { key ->
                        val isWide = key.label == "0"
                        CalculatorKey(
                            label = key.label, role = key.role,
                            onClick = {
                                when (key.label) {
                                    "AC" -> { updateInput(TextFieldValue("")); calcHistory.clear() }
                                    "⌫" -> {
                                        val edited = NaturalMathEditing.backspace(input.text, input.selection.start, input.selection.end)
                                        updateInput(TextFieldValue(edited.text, selection = androidx.compose.ui.text.TextRange(edited.selectionStart)))
                                    }
                                    "EXE" -> evaluate()
                                    "\u00F7" -> insert("/")
                                    "\u00D7" -> insert("\u00D7")
                                    "\u2212" -> insert("\u2212")
                                    "," -> insert(",")
                                    else -> insert(key.label)
                                }
                            },
                            modifier = Modifier.weight(if (isWide) 2.1f else 1f),
                            onLongClick = if (key.label == "⌫") {
                                {
                                    updateInput(TextFieldValue(""))
                                }
                            } else null
                        )
                    }
                }
            }
        }

        if (resultDialog && result != null) {
            ResultDetailDialog(
                title = "Result",
                content = result!!.symbolic,
                secondary = result!!.numeric.takeIf { it.isNotBlank() && it != result!!.symbolic } ?: "",
                onDismiss = { resultDialog = false }
            )
        }
        } // end outer Column

        // Loading spinner overlay on top of everything
        AnimatedVisibility(
            visible = showSpinner,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primary, strokeWidth = 3.dp)
            }
        }
    } // end Box
}

@Composable
private fun GiacCommandDebugPanel(command: String, onCopy: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceVariant.copy(alpha = .58f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "GIAC",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary
            )
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Text(
                        text = command.ifEmpty { "—" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = colors.onSurface
                    )
                }
            }
            IconButton(
                onClick = onCopy,
                enabled = command.isNotEmpty(),
                modifier = Modifier.size(34.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_copy_short),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


data class HistoryLine(
    val input: String,
    val result: CalcResult,
    val id: Long = System.nanoTime()
)

private enum class KeyRole { Number, Operator, Equals, Clear, Backspace }
private data class KeySpec(val label: String, val role: KeyRole)

private val CALCULATOR_KEY_ROWS = listOf(
    listOf(KeySpec("AC", KeyRole.Clear), KeySpec("\u232B", KeyRole.Backspace), KeySpec("%", KeyRole.Operator), KeySpec("\u00F7", KeyRole.Operator)),
    listOf(KeySpec("7", KeyRole.Number), KeySpec("8", KeyRole.Number), KeySpec("9", KeyRole.Number), KeySpec("\u00D7", KeyRole.Operator)),
    listOf(KeySpec("4", KeyRole.Number), KeySpec("5", KeyRole.Number), KeySpec("6", KeyRole.Number), KeySpec("\u2212", KeyRole.Operator)),
    listOf(KeySpec("1", KeyRole.Number), KeySpec("2", KeyRole.Number), KeySpec("3", KeyRole.Number), KeySpec("+", KeyRole.Operator)),
    listOf(KeySpec("0", KeyRole.Number), KeySpec(".", KeyRole.Number), KeySpec(",", KeyRole.Number), KeySpec("EXE", KeyRole.Equals))
)

private val VAR_PANEL_ITEMS = listOf("x", "y", "z", "a", "b", "c", "n", "t", "k", "m", "pi", "e", ":=", ";", "(", ")", "[", "]", "{", "}", "->")

private val FX_PANEL_ITEMS = listOf(
    "sin(\u25A1)" to "sin", "cos(\u25A1)" to "cos", "tan(\u25A1)" to "tan",
    "asin(\u25A1)" to "asin", "acos(\u25A1)" to "acos", "atan(\u25A1)" to "atan",
    "ln(\u25A1)" to "ln", "log(\u25A1)" to "log", "sqrt(\u25A1)" to "sqrt",
    "abs(\u25A1)" to "abs", "exp(\u25A1)" to "exp", "^(\u25A1)" to "^"
)

private val FUNCTIONS_PANEL_ITEMS = listOf(
    "simplify(\u25A1)", "factor(\u25A1)", "expand(\u25A1)", "normal(\u25A1)", "solve(\u25A1=0,x)", "subst(\u25A1,x=\u25A1)",
    "diff(\u25A1,x)", "diff(\u25A1,x,2)", "integrate(\u25A1,x)", "integrate(\u25A1,x,0,1)", "limit(\u25A1,x=0)", "sum(\u25A1,k,1,n)",
    "det(\u25A1)", "inv(\u25A1)", "transpose(\u25A1)", "rank(\u25A1)",
    "ifactor(\u25A1)", "gcd(\u25A1,\u25A1)", "lcm(\u25A1,\u25A1)",
    "plot(\u25A1)", "plot3d(\u25A1)", "plotparam(\u25A1)", "plotlist(\u25A1)", "plotseq(\u25A1)", "plot(\u25A1,x=-5..5)",
    "makelist(\u25A1)", "makemat(\u25A1)", "fft(\u25A1)", "ifft(\u25A1)"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalculatorKey(
    label: String,
    role: KeyRole,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val bg = when (role) {
        KeyRole.Number -> colors.surfaceVariant.copy(alpha = 0.55f)
        KeyRole.Operator -> colors.primaryContainer
        KeyRole.Equals -> colors.primary
        KeyRole.Clear -> if (isSystemInDarkTheme()) colors.errorContainer.copy(alpha = 0.45f) else colors.errorContainer
        KeyRole.Backspace -> colors.surfaceVariant
    }
    val fg = when (role) {
        KeyRole.Equals -> colors.onPrimary
        KeyRole.Operator -> colors.onPrimaryContainer
        KeyRole.Clear -> colors.onErrorContainer
        else -> colors.onSurface
    }
    Surface(
        modifier = modifier.height(78.dp).combinedClickable(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
            onLongClick = onLongClick?.let { action ->
                {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    action()
                }
            }
        ),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = when { label.length > 2 -> 16.sp; role == KeyRole.Number || label == "," -> 24.sp; else -> 21.sp },
                    fontWeight = if (role == KeyRole.Equals || role == KeyRole.Number || label == ",") FontWeight.Medium else FontWeight.Normal,
                    letterSpacing = 0.sp, textAlign = TextAlign.Center
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VarPanel(onInsert: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VAR_PANEL_ITEMS.forEach { v ->
            AssistChip(onClick = { onInsert(v) }, label = { Text(v, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium) }, shape = RoundedCornerShape(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FxPanel(onInsert: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FX_PANEL_ITEMS.forEach { (template, _) ->
            val short = template.replace("\u25A1", "")
            AssistChip(onClick = { onInsert(template) }, label = { Text(short, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }, shape = RoundedCornerShape(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FunctionsPanel(onInsert: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FUNCTIONS_PANEL_ITEMS.forEach { template ->
            val short = template.replace("\u25A1", "").take(18)
            AssistChip(onClick = { onInsert(template) }, label = { Text(short, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }, shape = RoundedCornerShape(12.dp))
        }
    }
}
