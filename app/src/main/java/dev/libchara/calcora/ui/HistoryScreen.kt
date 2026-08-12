package dev.libchara.calcora.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.libchara.calcora.R
import androidx.compose.ui.unit.sp
import dev.libchara.calcora.data.HistoryEntry
import dev.libchara.calcora.engine.MathSource

@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    history: List<HistoryEntry>,
    onRestore: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onDelete: (HistoryEntry) -> Unit = {},
    onPlotReplay: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.hist_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.hist_clear_all), fontSize = 14.sp)
            }
        }

        if (history.isEmpty()) {
            Text(
                stringResource(R.string.hist_empty),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(history, key = { it.id }) { item ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onRestore(item) }, onLongClick = { onDelete(item) }),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NaturalMathView(item.expression, modifier = Modifier.weight(1f), fontSize = 17f)
                                if (item.isPlot) {
                                    TextButton(onClick = { onPlotReplay(item.plotData) }) {
                                        Text(stringResource(R.string.hist_plot), fontSize = 13.sp, color = colors.primary)
                                    }
                                }
                            }
                            if (!item.isPlot) {
                                NaturalMathView(
                                    source = item.latex.ifBlank { item.result },
                                    sourceKind = if (item.latex.isBlank()) MathSource.Xcas else MathSource.Latex,
                                    modifier = Modifier.fillMaxWidth(), fontSize = 18f, color = colors.primary
                                )
                            }
                            Text(
                                text = item.mode.label + " \u00B7 " + item.formattedTime,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
