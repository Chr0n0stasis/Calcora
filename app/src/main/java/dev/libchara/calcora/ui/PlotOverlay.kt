package dev.libchara.calcora.ui


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.libchara.calcora.R
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val curveColors = listOf(
    Color(0xFF58A6FF), Color(0xFF3FB950), Color(0xFFF0883E),
    Color(0xFFD2A8FF), Color(0xFFFF7B72), Color(0xFF79C0FF),
    Color(0xFFA5D6FF), Color(0xFF56D364)
)

private data class PlotItem(
    val type: String, val variable: String, val xminVal: Double, val xmaxVal: Double,
    val points: List<Pair<Double, Double>>,
    val var1: String, val var2: String, val yminVal: Double, val ymaxVal: Double,
    val nx: Int, val ny: Int, val zGrid: List<List<Double?>>,
    val surfaceFaces: List<SurfaceFace>, val zMin: Double, val zMax: Double
)

private data class SurfaceFace(
    val x0: Double, val y0: Double, val x1: Double, val y1: Double,
    val z00: Double, val z10: Double, val z01: Double, val z11: Double,
    val cz: Double
)

@Composable
fun PlotOverlay(plotData: String, visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // A full-screen modal barrier remains present for the complete exit
            // animation, so a quick second tap cannot reach controls underneath.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            )
            PlotContent(plotData = plotData, onDismiss = onDismiss)
        }
    }
}

@Composable
fun PlotPreviewCard(
    plotData: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val items = remember(plotData) { parsePlotData(plotData) }
    val is3D = items.any { it.type == "surface3d" }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(118.dp)
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 2.dp
    ) {
        Column {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colors.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (items.isEmpty()) return@Canvas

                val axisColor = colors.onSurfaceVariant.copy(alpha = 0.55f)
                if (is3D) {
                    val item = items.first { it.type == "surface3d" }
                    val rx = -0.6f
                    val rz = 0.8f
                    val xCenter = (item.xminVal + item.xmaxVal) / 2.0
                    val yCenter = (item.yminVal + item.ymaxVal) / 2.0
                    val zCenter = (item.zMin + item.zMax) / 2.0
                    val span = maxOf(
                        item.xmaxVal - item.xminVal,
                        item.ymaxVal - item.yminVal,
                        item.zMax - item.zMin,
                        1e-6
                    )
                    val scale = (minOf(size.width, size.height) / span * 0.68).toFloat()
                    val projectedCenterX = xCenter * cos(rz) - yCenter * sin(rz)
                    val rotatedCenterY = xCenter * sin(rz) + yCenter * cos(rz)
                    val projectedCenterY = rotatedCenterY * cos(rx) - zCenter * sin(rx)
                    val origin = Offset(
                        size.width / 2f - projectedCenterX.toFloat() * scale,
                        size.height / 2f + projectedCenterY.toFloat() * scale
                    )
                    drawSurface3D(item, origin, scale, rx, rz, false, curveColors.first())
                } else {
                    val points = items.flatMap { it.points }.filter { (x, y) -> x.isFinite() && y.isFinite() }
                    if (points.isNotEmpty()) {
                        val xMin = points.minOf { it.first }
                        val xMax = points.maxOf { it.first }
                        val yMin = points.minOf { it.second }
                        val yMax = points.maxOf { it.second }
                        val xSpan = (xMax - xMin).coerceAtLeast(1e-6)
                        val ySpan = (yMax - yMin).coerceAtLeast(xSpan * 0.28)
                        val scale = minOf(size.width / xSpan, size.height / ySpan).toFloat() * 0.86f
                        val xCenter = (xMin + xMax) / 2.0
                        val yCenter = (yMin + yMax) / 2.0
                        val origin = Offset(
                            size.width / 2f - xCenter.toFloat() * scale,
                            size.height / 2f + yCenter.toFloat() * scale
                        )
                        if (0.0 in yMin..yMax) {
                            drawLine(axisColor, Offset(0f, origin.y), Offset(size.width, origin.y), strokeWidth = 1f)
                        }
                        if (0.0 in xMin..xMax) {
                            drawLine(axisColor, Offset(origin.x, 0f), Offset(origin.x, size.height), strokeWidth = 1f)
                        }
                        items.forEachIndexed { index, item ->
                            val color = curveColors[index % curveColors.size]
                            if (item.type == "scatter") drawScatter(item, origin, scale, color)
                            else drawCurve(item, origin, scale, color)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.plot_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface
                )
                Text(
                    text = stringResource(R.string.btn_view_plot) + "  ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary
                )
            }
        }
    }
}

private fun parsePlotData(json: String): List<PlotItem> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val type = obj.optString("type", "curve")
                val ptsArr = obj.optJSONArray("pts")
                val pts = if (ptsArr != null) buildList {
                    for (j in 0 until ptsArr.length()) {
                        val pt = ptsArr.getJSONArray(j)
                        add(Pair(pt.getDouble(0), pt.getDouble(1)))
                    }
                } else emptyList()
                val zArr = obj.optJSONArray("z")
                val zGrid = if (zArr != null) buildList {
                    for (r in 0 until zArr.length()) {
                        val row = zArr.getJSONArray(r)
                        add(buildList {
                            for (c in 0 until row.length()) {
                                val v = if (row.isNull(c)) null else row.optDouble(c, Double.NaN).let { if (it.isNaN()) null else it }
                                add(v)
                            }
                        })
                    }
                } else emptyList()
                val xmin = obj.optDouble("xmin", 0.0)
                val xmax = obj.optDouble("xmax", 1.0)
                val ymin = obj.optDouble("ymin", 0.0)
                val ymax = obj.optDouble("ymax", 1.0)
                val nx = obj.optInt("nx", 0)
                val ny = obj.optInt("ny", 0)
                val zBounds = computeZBounds(zGrid)
                add(PlotItem(type = type, variable = obj.optString("var", "x"),
                    xminVal = xmin, xmaxVal = xmax,
                    points = pts,
                    var1 = obj.optString("var1", "x"), var2 = obj.optString("var2", "y"),
                    yminVal = ymin, ymaxVal = ymax,
                    nx = nx, ny = ny, zGrid = zGrid,
                    surfaceFaces = buildSurfaceFaces(xmin, xmax, ymin, ymax, nx, ny, zGrid),
                    zMin = zBounds.first, zMax = zBounds.second))
            }
        }
    }.getOrDefault(emptyList())
}

private fun computeZBounds(zGrid: List<List<Double?>>): Pair<Double, Double> {
    var zMin = Double.MAX_VALUE
    var zMax = -Double.MAX_VALUE
    for (row in zGrid) {
        for (z in row) {
            if (z != null) {
                if (z < zMin) zMin = z
                if (z > zMax) zMax = z
            }
        }
    }
    return if (zMin == Double.MAX_VALUE) -5.0 to 5.0 else zMin to zMax
}

private fun buildSurfaceFaces(
    xmin: Double,
    xmax: Double,
    ymin: Double,
    ymax: Double,
    nx: Int,
    ny: Int,
    zGrid: List<List<Double?>>
): List<SurfaceFace> {
    if (nx < 2 || ny < 2) return emptyList()
    val xRange = xmax - xmin
    val yRange = ymax - ymin
    if (xRange <= 0 || yRange <= 0) return emptyList()

    fun zAt(ix: Int, iy: Int): Double = zGrid.getOrNull(ix)?.getOrNull(iy) ?: Double.NaN

    return buildList {
        for (ix in 0 until nx - 1) {
            for (iy in 0 until ny - 1) {
                val z00 = zAt(ix, iy)
                val z10 = zAt(ix + 1, iy)
                val z01 = zAt(ix, iy + 1)
                val z11 = zAt(ix + 1, iy + 1)
                if (!z00.isFinite() || !z10.isFinite() || !z01.isFinite() || !z11.isFinite()) continue
                val x0 = xmin + ix.toDouble() / (nx - 1) * xRange
                val y0 = ymin + iy.toDouble() / (ny - 1) * yRange
                val x1 = xmin + (ix + 1).toDouble() / (nx - 1) * xRange
                val y1 = ymin + (iy + 1).toDouble() / (ny - 1) * yRange
                add(SurfaceFace(x0, y0, x1, y1, z00, z10, z01, z11, (z00 + z10 + z01 + z11) / 4.0))
            }
        }
        sortByDescending { it.cz }
    }
}

@Composable
private fun PlotContent(plotData: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var scale by remember { mutableFloatStateOf(42f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var rotX by remember { mutableFloatStateOf(-0.6f) }
    var rotZ by remember { mutableFloatStateOf(0.8f) }
    var showGrid by remember { mutableStateOf(true) }

    val items = remember(plotData) { parsePlotData(plotData) }
    val is3D = items.any { it.type == "surface3d" }
    val totalPoints = items.sumOf { if (it.type == "surface3d") it.nx * it.ny else it.points.size }
    val label = when {
        items.isEmpty() -> "no data"
        is3D -> "3D · " + items[0].nx + "×" + items[0].ny
        items.size == 1 && items[0].type == "scatter" -> "scatter \u00B7 " + items[0].points.size + " pts"
        else -> items.size.toString() + " curves \u00B7 " + totalPoints + " pts"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.padding(8.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium), color = colors.onBackground)
                Text(if (is3D) stringResource(R.string.plot_hint_3d) else stringResource(R.string.plot_hint), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showGrid = !showGrid }) { Text(if (showGrid) stringResource(R.string.btn_grid) else stringResource(R.string.btn_no_grid), fontSize = 13.sp) }
                TextButton(onClick = { scale = 42f; panX = 0f; panY = 0f; rotX = -0.6f; rotZ = 0.8f }) { Text(stringResource(R.string.btn_reset), fontSize = 13.sp) }
                FilledIconButton(onClick = onDismiss, modifier = Modifier.size(36.dp), shape = CircleShape) { Text("✕", fontSize = 18.sp) }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth().weight(1f)
                .background(colors.surfaceVariant.copy(alpha = 0.3f))
                .padding(12.dp)
                .pointerInput(is3D) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        scale = (scale * zoom).coerceIn(12f, 260f)
                        if (is3D) {
                            if (zoom != 1f) {
                                // Two-finger pinch/pan: translate view
                                val hw = size.width.toFloat() / 2f
                                val hh = size.height.toFloat() / 2f
                                panX = centroid.x - hw - (centroid.x - hw - panX) * scale / oldScale + pan.x
                                panY = centroid.y - hh - (centroid.y - hh - panY) * scale / oldScale + pan.y
                            } else {
                                // Single-finger drag: rotate
                                rotZ += pan.x * 0.01f
                                rotX += pan.y * 0.01f
                            }
                        } else {
                            val hw = size.width.toFloat() / 2f
                            val hh = size.height.toFloat() / 2f
                            panX = centroid.x - hw - (centroid.x - hw - panX) * scale / oldScale + pan.x
                            panY = centroid.y - hh - (centroid.y - hh - panY) * scale / oldScale + pan.y
                        }
                    }
                }
        ) {
            val origin = Offset(size.width / 2f + panX, size.height / 2f + panY)
            val axisColor = colors.onSurfaceVariant
            val gridColor = colors.outline.copy(alpha = 0.25f)

            if (!is3D && showGrid) {
                val step = scale
                var x = origin.x % step
                while (x < size.width) { drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.7f); x += step }
                var y = origin.y % step
                while (y < size.height) { drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.7f); y += step }
            }
            if (!is3D) {
                drawLine(axisColor, Offset(0f, origin.y), Offset(size.width, origin.y), strokeWidth = 1.8f)
                drawLine(axisColor, Offset(origin.x, 0f), Offset(origin.x, size.height), strokeWidth = 1.8f)
            }

            items.forEachIndexed { ci, item ->
                when (item.type) {
                    "surface3d" -> drawSurface3D(item, origin, scale, rotX, rotZ, showGrid, curveColors[ci % curveColors.size])
                    "scatter" -> drawScatter(item, origin, scale, curveColors[ci % curveColors.size])
                    else -> drawCurve(item, origin, scale, curveColors[ci % curveColors.size])
                }
            }
        }
    }
}

private fun DrawScope.drawCurve(item: PlotItem, origin: Offset, scale: Float, color: Color) {
    if (item.points.isEmpty()) return
    val path = Path()
    var started = false
    var prevX = Float.NaN
    val gapThresh = if (item.xmaxVal > item.xminVal)
        (3.0 * (item.xmaxVal - item.xminVal) / item.points.size.coerceAtLeast(1) * scale).toFloat() else 30f

    for ((x, y) in item.points) {
        val px = origin.x + x.toFloat() * scale
        val py = origin.y - y.toFloat() * scale
        val inBounds = py in -size.height * 2f..size.height * 3f
        val gap = started && prevX.isFinite() && abs(px - (origin.x + prevX * scale)) > gapThresh
        if (!inBounds || gap) started = false
        else if (!started) { path.moveTo(px, py); started = true }
        else path.lineTo(px, py)
        prevX = x.toFloat()
    }
    drawPath(path, color, style = Stroke(width = 3.5f))
}

private fun DrawScope.drawScatter(item: PlotItem, origin: Offset, scale: Float, color: Color) {
    if (item.points.isEmpty()) return
    // Dots-only for readability
    for ((x, y) in item.points) {
        val px = origin.x + x.toFloat() * scale
        val py = origin.y - y.toFloat() * scale
        if (py in -size.height * 2f..size.height * 3f) {
            drawCircle(color, 5f, Offset(px, py))
        }
    }
    // Connect with thin lines if points are ordered
    if (item.points.size > 1) {
        val path = Path()
        var started = false
        for ((x, y) in item.points) {
            val px = origin.x + x.toFloat() * scale
            val py = origin.y - y.toFloat() * scale
            val inBounds = py in -size.height * 2f..size.height * 3f
            if (!inBounds) started = false
            else if (!started) { path.moveTo(px, py); started = true }
            else path.lineTo(px, py)
        }
        drawPath(path, color.copy(alpha = 0.4f), style = Stroke(width = 1.5f))
    }
}

private fun DrawScope.drawSurface3D(item: PlotItem, origin: Offset, scale: Float, rx: Float, rz: Float, showGrid: Boolean, color: Color) {
    if (item.zGrid.isEmpty() || item.nx < 2 || item.ny < 2) return
    val nx = item.nx; val ny = item.ny
    val xRange = item.xmaxVal - item.xminVal
    val yRange = item.ymaxVal - item.yminVal
    if (xRange <= 0 || yRange <= 0) return

    fun zAt(ix: Int, iy: Int): Double = item.zGrid.getOrNull(ix)?.getOrNull(iy) ?: Double.NaN

    val cosZ = cos(rz)
    val sinZ = sin(rz)
    val cosX = cos(rx)
    val sinX = sin(rx)

    // Project 3D point to 2D screen
    fun proj(x3: Double, y3: Double, z3: Double): Offset {
        val x1 = x3 * cosZ - y3 * sinZ
        val y1 = x3 * sinZ + y3 * cosZ
        val y2f = (y1 * cosX - z3 * sinX).toFloat()
        return Offset(origin.x + x1.toFloat() * scale, origin.y - y2f * scale)
    }

    val zMin = item.zMin
    val zMax = item.zMax
    val zSpan = if (zMax > zMin) zMax - zMin else 1.0

    for (face in item.surfaceFaces) {
        val t = ((face.cz - zMin) / zSpan).toFloat().coerceIn(0f, 1f)
        val faceColor = Color(
            red = 0.15f + t * 0.4f,
            green = 0.25f + t * 0.3f,
            blue = 0.5f + t * 0.3f,
            alpha = 0.35f
        )
        val p00 = proj(face.x0, face.y0, face.z00); val p10 = proj(face.x1, face.y0, face.z10)
        val p01 = proj(face.x0, face.y1, face.z01); val p11 = proj(face.x1, face.y1, face.z11)
        val path = Path().apply {
            moveTo(p00.x, p00.y); lineTo(p10.x, p10.y); lineTo(p11.x, p11.y); lineTo(p01.x, p01.y)
            close()
        }
        drawPath(path, faceColor)
    }

    if (showGrid) {
        // ---- Wireframe overlay ----
        for (ix in 0 until nx - 1) {
            for (iy in 0 until ny) {
                val z1 = zAt(ix, iy); val z2 = zAt(ix + 1, iy)
                if (z1.isFinite() && z2.isFinite()) {
                    val x3a = item.xminVal + ix.toDouble() / (nx - 1) * xRange
                    val y3a = item.yminVal + iy.toDouble() / (ny - 1) * yRange
                    val x3b = item.xminVal + (ix + 1).toDouble() / (nx - 1) * xRange
                    drawLine(color.copy(alpha = 0.5f), proj(x3a, y3a, z1), proj(x3b, y3a, z2), strokeWidth = 1f)
                }
            }
        }
        for (ix in 0 until nx) {
            for (iy in 0 until ny - 1) {
                val z1 = zAt(ix, iy); val z2 = zAt(ix, iy + 1)
                if (z1.isFinite() && z2.isFinite()) {
                    val x3a = item.xminVal + ix.toDouble() / (nx - 1) * xRange
                    val y3a = item.yminVal + iy.toDouble() / (ny - 1) * yRange
                    val y3b = item.yminVal + (iy + 1).toDouble() / (ny - 1) * yRange
                    drawLine(color.copy(alpha = 0.5f), proj(x3a, y3a, z1), proj(x3a, y3b, z2), strokeWidth = 1f)
                }
            }
        }
    }

    // ---- Coordinate axes ----
    val axisLen = maxOf(xRange, yRange, zSpan) * 1.2
    val axO = proj(0.0, 0.0, 0.0)
    drawLine(Color.Red.copy(alpha = 0.85f), axO, proj(axisLen, 0.0, 0.0), strokeWidth = 2.2f)
    drawLine(Color.Green.copy(alpha = 0.85f), axO, proj(0.0, axisLen, 0.0), strokeWidth = 2.2f)
    drawLine(Color(0xFF79C0FF).copy(alpha = 0.85f), axO, proj(0.0, 0.0, axisLen), strokeWidth = 2.2f)
    // Negative axes (dashed appearance: shorter + lower alpha)
    drawLine(Color.Red.copy(alpha = 0.3f), axO, proj(-axisLen * 0.5, 0.0, 0.0), strokeWidth = 1f)
    drawLine(Color.Green.copy(alpha = 0.3f), axO, proj(0.0, -axisLen * 0.5, 0.0), strokeWidth = 1f)
    drawLine(Color(0xFF79C0FF).copy(alpha = 0.3f), axO, proj(0.0, 0.0, -axisLen * 0.5), strokeWidth = 1f)

    if (showGrid) {
        // ---- Base grid on Z=zMin plane ----
        val gridA = 0.12f
        val nDivs = 5
        for (i in 0..nDivs) {
            val t = i.toDouble() / nDivs
            val gx = item.xminVal + t * xRange
            val gy = item.yminVal + t * yRange
            drawLine(Color.White.copy(alpha = gridA), proj(gx, item.yminVal, zMin), proj(gx, item.ymaxVal, zMin), strokeWidth = 0.5f)
            drawLine(Color.White.copy(alpha = gridA), proj(item.xminVal, gy, zMin), proj(item.xmaxVal, gy, zMin), strokeWidth = 0.5f)
        }
    }
}
