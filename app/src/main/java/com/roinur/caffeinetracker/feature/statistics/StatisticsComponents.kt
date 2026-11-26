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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        if (visiblePoints.isEmpty()) return
        val left = 44f
        val right = sizeWidth - 16f
        val width = (right - left).coerceAtLeast(1f)
        val slotWidth = width / visiblePoints.size.toFloat()
        val index = ((x - left) / slotWidth).toInt().coerceIn(0, visiblePoints.lastIndex)
        onPointSelected(visiblePoints[index])
    }
    val avgValue = remember(visiblePoints) {
        if (visiblePoints.size > 1) visiblePoints.sumOf { it.value } / visiblePoints.size else 0.0
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
                    detectHorizontalDragGestures(onDragStart = { selectPoint(it.x, size.width.toFloat()) }) { change, _ ->
                        change.consume()
                        selectPoint(change.position.x, size.width.toFloat())
                    }
                }
        ) {
            val left = 44f
            val right = size.width - 16f
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

            if (visiblePoints.isNotEmpty()) {
                val slotWidth = width / visiblePoints.size.toFloat()
                val maxBarW = 24.dp.toPx()
                val minBarW = 3.dp.toPx()
                val barWidth = (slotWidth * 0.58f).coerceIn(minBarW, maxBarW)
                val minNonZeroH = 4.dp.toPx()
                val zeroDotH = 3.dp.toPx()
                val hasSelection = selectedPoint != null
                val defaultBrush = Brush.verticalGradient(
                    colors = listOf(primary.copy(alpha = 0.92f), primary.copy(alpha = 0.52f)),
                    startY = top,
                    endY = bottom
                )
                val dimmedColor = primary.copy(alpha = 0.28f)

                visiblePoints.forEachIndexed { index, point ->
                    val centerX = left + slotWidth * index + slotWidth / 2f
                    val isSelected = selectedPoint?.date == point.date
                    val barLeft = centerX - barWidth / 2f

                    if (isSelected) {
                        val haloW = (barWidth + 6.dp.toPx()).coerceAtMost(slotWidth)
                        drawRoundRect(
                            color = primary.copy(alpha = 0.10f),
                            topLeft = Offset(centerX - haloW / 2f, top),
                            size = Size(haloW, height),
                            cornerRadius = CornerRadius(haloW / 2f, haloW / 2f)
                        )
                    }

                    if (point.value <= 0.0) {
                        val dotR = zeroDotH / 2f
                        drawRoundRect(
                            color = if (isSelected) primary.copy(alpha = 0.65f) else primary.copy(alpha = 0.16f),
                            topLeft = Offset(barLeft, bottom - zeroDotH),
                            size = Size(barWidth, zeroDotH),
                            cornerRadius = CornerRadius(dotR, dotR)
                        )
                    } else {
                        val rawH = (point.value.toFloat() / maxValue.toFloat()) * height
                        val barH = rawH.coerceIn(minNonZeroH, height)
                        val radius = minOf(barWidth, barH) / 2f
                        val topLeft = Offset(barLeft, bottom - barH)
                        val barSize = Size(barWidth, barH)
                        val corner = CornerRadius(radius, radius)
                        when {
                            isSelected -> drawRoundRect(color = primary, topLeft = topLeft, size = barSize, cornerRadius = corner)
                            hasSelection -> drawRoundRect(color = dimmedColor, topLeft = topLeft, size = barSize, cornerRadius = corner)
                            else -> drawRoundRect(brush = defaultBrush, topLeft = topLeft, size = barSize, cornerRadius = corner)
                        }
                    }
                }

                if (avgValue > 0.0) {
                    val avgY = (bottom - (avgValue.toFloat() / maxValue.toFloat()) * height).coerceIn(top, bottom)
                    drawLine(
                        color = primary.copy(alpha = 0.45f),
                        start = Offset(left, avgY),
                        end = Offset(right, avgY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    )
                }

                val labelStride = when (range) {
                    CaffeineStatsRange.WEEK -> 1
                    CaffeineStatsRange.MONTH -> 5
                    CaffeineStatsRange.YEAR -> 2
                    CaffeineStatsRange.ALL_TIME -> (visiblePoints.size / 5).coerceAtLeast(1)
                }
                visiblePoints.forEachIndexed { index, point ->
                    if (index % labelStride != 0 && index != visiblePoints.lastIndex) return@forEachIndexed
                    val centerX = left + slotWidth * index + slotWidth / 2f
                    val label = point.date.format(xFormatter)
                    val labelWidth = xLabelPaint.measureText(label)
                    val labelX = (centerX - labelWidth / 2f).coerceIn(0f, size.width - labelWidth)
                    drawContext.canvas.nativeCanvas.drawText(label, labelX, size.height - 4f, xLabelPaint)
                }
            }
        }
    }
}
