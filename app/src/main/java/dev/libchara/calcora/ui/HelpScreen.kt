package dev.libchara.calcora.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.libchara.calcora.R
import dev.libchara.calcora.engine.HelpEntry
import dev.libchara.calcora.engine.HelpParser

private data class HelpCategory(
    val symbol: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val commands: List<String>
)

private val HELP_CATEGORIES = listOf(
    HelpCategory("x²", R.string.help_category_algebra, R.string.help_category_algebra_desc,
        listOf("solve", "factor", "expand", "simplify", "subst", "normal")),
    HelpCategory("∫", R.string.help_category_calculus, R.string.help_category_calculus_desc,
        listOf("diff", "integrate", "limit", "sum", "series")),
    HelpCategory("⌁", R.string.help_category_plotting, R.string.help_category_plotting_desc,
        listOf("plot", "plotfunc", "plotparam", "plot3d", "listplot")),
    HelpCategory("▦", R.string.help_category_linear_algebra, R.string.help_category_linear_algebra_desc,
        listOf("matrix", "det", "inv", "rank", "eigenvals")),
    HelpCategory("Σ", R.string.help_category_statistics, R.string.help_category_statistics_desc,
        listOf("mean", "median", "stddev", "variance", "quartile1")),
    HelpCategory("{ }", R.string.help_category_lists, R.string.help_category_lists_desc,
        listOf("makelist", "seq", "map", "select", "sort", "size"))
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(
    contentPadding: PaddingValues,
    initialFunc: String?,
    onInsert: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val helpReady = HelpParser.isReady.value
    var query by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    fun openEntry(name: String) {
        selectedName = name
        showAll = false
    }

    fun navigateBack() {
        if (selectedName != null) selectedName = null else showAll = false
    }

    LaunchedEffect(initialFunc) {
        if (!initialFunc.isNullOrBlank()) {
            query = ""
            showAll = false
            selectedName = initialFunc
        }
    }

    BackHandler(enabled = selectedName != null || showAll, onBack = ::navigateBack)

    val results = remember(query, showAll, helpReady) {
        if (!helpReady || (query.isBlank() && !showAll)) emptyList()
        else HelpParser.searchScored(query.trim())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        AnimatedContent(
            targetState = selectedName,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it / 5 } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it / 8 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 8 } + fadeIn())
                        .togetherWith(slideOutHorizontally { it / 5 } + fadeOut())
                }
            },
            label = "help-navigation",
            modifier = Modifier.weight(1f)
        ) { detailName ->
            if (detailName == null) {
                HelpBrowser(
                    query = query,
                    onQueryChange = {
                        query = it
                        showAll = false
                    },
                    showAll = showAll,
                    results = results,
                    helpReady = helpReady,
                    onShowAll = { showAll = true },
                    onBackFromAll = { showAll = false },
                    onOpenEntry = ::openEntry
                )
            } else {
                HelpDetail(
                    requestedName = detailName,
                    onBack = ::navigateBack,
                    onOpenEntry = ::openEntry,
                    onInsert = onInsert
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelpBrowser(
    query: String,
    onQueryChange: (String) -> Unit,
    showAll: Boolean,
    results: List<HelpParser.Scored>,
    helpReady: Boolean,
    onShowAll: () -> Unit,
    onBackFromAll: () -> Unit,
    onOpenEntry: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.help_title),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            HelpSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { results.firstOrNull()?.let { onOpenEntry(it.name) } },
                modifier = Modifier.width(236.dp)
            )
        }

        when {
            !helpReady -> HelpLoadingState()
            query.isNotBlank() || showAll -> HelpResults(
                results = results,
                showAll = showAll,
                onBackFromAll = onBackFromAll,
                onOpenEntry = onOpenEntry
            )
            else -> HelpHome(onShowAll = onShowAll, onOpenEntry = onOpenEntry)
        }
    }
}

@Composable
private fun HelpSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        placeholder = { Text(stringResource(R.string.help_search_hint)) },
        leadingIcon = {
            Text("⌕", fontSize = 25.sp, color = MaterialTheme.colorScheme.primary)
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Text("×", fontSize = 22.sp)
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelpHome(onShowAll: () -> Unit, onOpenEntry: (String) -> Unit) {
    val availableCategories = remember(HelpParser.isReady.value) {
        HELP_CATEGORIES.map { category ->
            category.copy(commands = category.commands.filter { HelpParser.lookup(it) != null })
        }.filter { it.commands.isNotEmpty() }
    }
    val commandCount = remember(HelpParser.isReady.value) { HelpParser.getAllNames().size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.help_explore),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        items(availableCategories, key = { it.title }) { category ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = category.symbol,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(stringResource(category.title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(category.description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        category.commands.forEach { command ->
                            AssistChip(
                                onClick = { onOpenEntry(command) },
                                label = { Text(command, fontFamily = FontFamily.Monospace) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onShowAll),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.help_all_commands), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.help_command_count, commandCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("→", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HelpResults(
    results: List<HelpParser.Scored>,
    showAll: Boolean,
    onBackFromAll: () -> Unit,
    onOpenEntry: (String) -> Unit
) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("⌕", fontSize = 40.sp, color = MaterialTheme.colorScheme.outline)
                Text(
                    stringResource(R.string.help_no_matches_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    stringResource(R.string.help_no_matches_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showAll) {
                    TextButton(onClick = onBackFromAll, contentPadding = PaddingValues(end = 10.dp)) {
                        Text("‹  " + stringResource(R.string.btn_back))
                    }
                }
                Text(
                    stringResource(R.string.help_result_count, results.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(results, key = { it.name }) { scored ->
            val entry = remember(scored.name) { HelpParser.lookup(scored.name) }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpenEntry(scored.name) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = scored.name,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        entry?.signature?.takeIf(String::isNotBlank)?.let { signature ->
                            Text(
                                text = "$scored.name($signature)",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        entry?.description?.takeIf(String::isNotBlank)?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                    Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun HelpLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            Text(
                stringResource(R.string.help_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
