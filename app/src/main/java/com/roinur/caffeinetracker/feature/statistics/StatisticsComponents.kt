package com.roinur.caffeinetracker.feature.statistics

import androidx.compose.material3.Text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun CaffeineStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String = "",
    reserveSupporting: Boolean = false
) {
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (supporting.isNotBlank() || reserveSupporting) {
            Text(
                if (supporting.isNotBlank()) supporting else " ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CaffeineVerticalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(66.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    )
}

@Composable
// Adapted from Book Tracker BookLineChart: same grid, filled line, touch selection and layout.
internal fun CaffeineLineChart(
    title: String,
    points: List<CaffeineDailyPoint>,
    range: CaffeineStatsRange,
    metric: CaffeineGraphMetric,
    selectedDate: LocalDate?,
    onPointSelected: (CaffeineDailyPoint) -> Unit,
    metricSelector: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    val monthYearFormatter = remember(language) { DateTimeFormatter.ofPattern("MMM yyyy", language.locale) }
    val fullDateFormatter = remember(language) { DateTimeFormatter.ofPattern("d MMM yyyy", language.locale) }
    val monthShortFormatter = remember(language) { DateTimeFormatter.ofPattern("MMM", language.locale) }
    val dayMonthFormatter = remember(language) { DateTimeFormatter.ofPattern("d MMM", language.locale) }
    val visiblePoints = points
    val selectedPoint = visiblePoints.firstOrNull { it.date == selectedDate }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = remember(visiblePoints) { visiblePoints.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0 }
    val yLabelPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 24f
            isAntiAlias = true
        }
    }
    val xLabelPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 23f
            isAntiAlias = true
        }
    }

    fun selectPoint(x: Float, sizeWidth: Float) {
        if(visiblePoints.isEmpty()) return
        val width = (sizeWidth - 44f - 28f).coerceAtLeast(1f)
        val step = if(visiblePoints.size <= 1) width else width / (visiblePoints.size - 1)
        val index = ((x - 44f) / step).roundToInt().coerceIn(0,visiblePoints.lastIndex)
        onPointSelected(visiblePoints[index])
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val unit = if (metric == CaffeineGraphMetric.CAFFEINE) "mg" else strings.statsIntakesUnit
                val point = selectedPoint
                val monthly = range == CaffeineStatsRange.YEAR || (points.size > 1 && java.time.temporal.ChronoUnit.DAYS.between(points[0].date, points[1].date) > 27)
                val headerFormatter = if (monthly) monthYearFormatter else fullDateFormatter
                Text(
                    if (point == null) " " else "${point.date.format(headerFormatter)}: ${point.value.roundToInt()} $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            metricSelector()
        }
        val xFormatter = if (range == CaffeineStatsRange.YEAR) monthShortFormatter else dayMonthFormatter
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(visiblePoints, range, metric) {
                    detectTapGestures { selectPoint(it.x, size.width.toFloat()) }
                }
                .pointerInput(visiblePoints, range, metric) {
                    detectHorizontalDragGestures(onDragStart = { selectPoint(it.x,size.width.toFloat()) }) { change, _ ->
                        selectPoint(change.position.x, size.width.toFloat())
                    }
                }
        ) {
            val left = 44f
            val right = size.width - 28f
            val top = 8f
            val bottom = size.height - 34f
            val width = (right - left).coerceAtLeast(1f)
            val height = (bottom - top).coerceAtLeast(1f)
            repeat(4) { index ->
                val y = top + height * index / 3f
                drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                val label = ((maxValue * (3 - index)) / 3.0).roundToInt().toString()
                drawContext.canvas.nativeCanvas.drawText(label, 0f, y + 5f, yLabelPaint)
            }
            val step = if (visiblePoints.size <= 1) width else width / (visiblePoints.size - 1).toFloat()
            val offsets = visiblePoints.mapIndexed { index, point ->
                val x = left + step * index
                val y = bottom - (point.value.toFloat() / maxValue.toFloat()) * height
                Offset(x, y)
            }
            val fillPath = Path().apply {
                offsets.firstOrNull()?.let { first ->
                    moveTo(first.x, bottom)
                    offsets.forEach { lineTo(it.x, it.y) }
                    lineTo(offsets.last().x, bottom)
                    close()
                }
            }
            drawPath(fillPath, primary.copy(alpha = 0.20f))
            offsets.zipWithNext().forEach { (a, b) ->
                drawLine(primary, a, b, strokeWidth = 5f, cap = StrokeCap.Round)
            }
            offsets.forEachIndexed { index, offset ->
                if (selectedPoint?.date == visiblePoints[index].date) {
                    drawLine(primary.copy(alpha = 0.55f), Offset(offset.x, top), Offset(offset.x, bottom), strokeWidth = 2f)
                    drawCircle(primary.copy(alpha = 0.24f), radius = 16f, center = offset)
                    drawCircle(primary, radius = 9f, center = offset)
                } else {
                    drawCircle(primary, radius = 7f, center = offset)
                }
            }
            val labelStride = when (range) {
                CaffeineStatsRange.WEEK -> 1
                CaffeineStatsRange.MONTH -> 5
                CaffeineStatsRange.YEAR -> 2
                CaffeineStatsRange.ALL_TIME -> (visiblePoints.size / 5).coerceAtLeast(1)
            }
            visiblePoints.forEachIndexed { index, point ->
                if (index % labelStride != 0 && index != visiblePoints.lastIndex) return@forEachIndexed
                val x = left + step * index
                val label = point.date.format(xFormatter)
                val labelWidth = xLabelPaint.measureText(label)
                val labelX = (x - labelWidth / 2f).coerceIn(0f, size.width - labelWidth)
                drawContext.canvas.nativeCanvas.drawText(label, labelX, size.height - 4f, xLabelPaint)
            }
        }
    }
}
