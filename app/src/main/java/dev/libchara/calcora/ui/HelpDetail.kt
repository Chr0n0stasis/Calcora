package dev.libchara.calcora.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.libchara.calcora.R
import dev.libchara.calcora.engine.HelpEntry
import dev.libchara.calcora.engine.HelpParser

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HelpDetail(
    requestedName: String,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onInsert: (String) -> Unit
) {
    val entry = remember(requestedName, HelpParser.isReady.value) { HelpParser.lookup(requestedName) }
    val suggestions = remember(requestedName, HelpParser.isReady.value) {
        if (entry == null) HelpParser.searchScored(requestedName).take(8) else emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹  " + stringResource(R.string.btn_back)) }
            Text(
                text = stringResource(R.string.help_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (entry == null) {
            MissingHelpDetail(requestedName, suggestions, onOpenEntry)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { HelpDetailHeader(entry) }
            if (entry.description.isNotBlank()) {
                item {
                    HelpSection(title = stringResource(R.string.help_desc)) {
                        Text(entry.description, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp)
                    }
                }
            }
            if (entry.exampleLines.isNotEmpty()) {
                item {
                    HelpSection(
                        title = stringResource(R.string.help_examples),
                        subtitle = stringResource(R.string.help_example_hint)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            entry.exampleLines.forEach { example ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { onInsert(example) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = example,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = stringResource(R.string.help_use_example) + "  →",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (entry.related.isNotEmpty()) {
                item {
                    HelpSection(title = stringResource(R.string.help_related)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            entry.related.distinct().forEach { related ->
                                AssistChip(
                                    onClick = { onOpenEntry(related) },
                                    label = { Text(related, fontFamily = FontFamily.Monospace) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = { onInsert(entry.name + "(") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 13.dp)
            ) {
                Text(stringResource(R.string.help_insert_function, entry.name))
            }
        }
    }
}

@Composable
private fun HelpDetailHeader(entry: HelpEntry) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                entry.name,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                stringResource(R.string.help_syntax),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                entry.syntax,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun HelpSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun MissingHelpDetail(
    requestedName: String,
    suggestions: List<HelpParser.Scored>,
    onOpenEntry: (String) -> Unit
) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.help_no_result) + ": " + requestedName,
            style = MaterialTheme.typography.titleMedium
        )
        if (suggestions.isNotEmpty()) {
            Text(stringResource(R.string.help_see_also), color = MaterialTheme.colorScheme.onSurfaceVariant)
            suggestions.forEach { suggestion ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenEntry(suggestion.name) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(suggestion.name, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text("›", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
