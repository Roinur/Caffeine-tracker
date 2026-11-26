package com.roinur.caffeinetracker.feature.dashboard

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithCache
import com.roinur.caffeinetracker.core.caffeine.CaffeineCurve
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.caffeine.CaffeineCalculator
import com.roinur.caffeinetracker.core.ui.components.*
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import java.time.*
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private val intakeRowShape = RoundedCornerShape(18.dp)

@Immutable
data class IntakeRowModel(
    val intake: Intake,
    val shownName: String,
    val timeText: String,
    val servingText: String,
    val mgText: String,
    val imageKind: DrinkImage
)

fun buildIntakeRowModel(intake: Intake, language: AppLanguage): IntakeRowModel {
    val shownUnit = displayPortionText(intake.servingUnit, language)
    val timeText = clock(intake.timestamp) + if (intake.durationMinutes > 0) " · " + intake.durationMinutes + " min" else ""
    val servingText = if (shownUnit.isNotBlank()) decimal(intake.servings, language) + " × " + shownUnit else ""
    return IntakeRowModel(
        intake = intake,
        shownName = displayDrinkName(intake.name, language),
        timeText = timeText,
        servingText = servingText,
        mgText = mg(intake.caffeineMg),
        imageKind = drinkImage(intake.name, intake.category, intake.imageKey)
    )
}

@Composable
fun IntakeRow(model: IntakeRowModel, enabled: Boolean = true, onEdit: (Intake) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(intakeRowShape)
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant.copy(alpha = .35f), intakeRowShape)
            .clickable(enabled = enabled) { onEdit(model.intake) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrinkThumbnail(kind = model.imageKind, modifier = Modifier.size(52.dp), showContainer = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(model.shownName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(model.timeText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            if (model.servingText.isNotBlank()) {
                Text(model.servingText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (model.intake.note.isNotBlank()) {
                Text(model.intake.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(model.mgText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun IntakeRow(intake: Intake, onClick: () -> Unit) {
    val language = LocalAppLanguage.current
    val model = remember(intake, language) { buildIntakeRowModel(intake, language) }
    IntakeRow(model = model, enabled = true, onEdit = { onClick() })
}

@Composable
fun CaffeineChart(
    intakes: List<Intake>,
    settings: TrackerSettings,
    now: Long,
    bedtime: Long,
    curve: CaffeineCurve?,
    nowAmount: Double,
    bedtimeAmount: Double,
    compact: Boolean = false,
    chartModifier: Modifier = Modifier.fillMaxWidth().height(if (compact) 108.dp else 180.dp),
    showLegend: Boolean = false,
    headerStart: (@Composable () -> Unit)? = null
) {
    val zone = ZoneId.systemDefault()
    val start = remember(now / 60000) { Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() }
    val end = start + 30 * 3600000L
    val relevantIntakes = remember(intakes, start, end, settings.halfLifeHours) {
        intakes.filter { it.timestamp <= end && it.timestamp >= start - CaffeineCalculator.activeWindowMs(it, settings.halfLifeHours) }
    }
    val values = curve?.takeIf { it.start == start }?.points.orEmpty()
    var selected by remember(start) { mutableStateOf<Long?>(null) }
    val chosen = selected ?: now
    val chosenAmount = remember(relevantIntakes, chosen, settings.halfLifeHours, selected, nowAmount) {
        if(selected == null) nowAmount else CaffeineCalculator.total(relevantIntakes, chosen, settings.halfLifeHours)
    }
    val peak = maxOf(values.maxOfOrNull { it.second } ?: 0.0, nowAmount, chosenAmount)
    val maxY = maxOf(100.0, maxOf(peak * 1.12, settings.bedtimeThresholdMg * 1.25))
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    val readoutText = clock(chosen) + " · " + mg(chosenAmount)
    val readoutModifier = if (selected != null) Modifier.clickable(onClickLabel = strings.returnToNow) { selected = null } else Modifier
    if (headerStart != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            headerStart()
            Text(readoutText, readoutModifier, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Text(readoutText, readoutModifier, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val thresholdLabel = remember(settings.bedtimeThresholdMg) { mg(settings.bedtimeThresholdMg) }
    val bedtimeLabel = settings.bedtime
    Spacer(chartModifier
        .semantics { contentDescription = strings.chartSemantic(mg(nowAmount), mg(bedtimeAmount)) }
        .pointerInput(start) {
            fun selectAt(x: Float) {
                val left = 8.dp.toPx()
                val right = (size.width - 8.dp.toPx()).coerceAtLeast(left + 1f)
                val fraction = ((x - left) / (right - left)).coerceIn(0f, 1f)
                selected = start + ((end - start) * fraction).toLong()
            }
            detectTapGestures { offset -> selectAt(offset.x) }
        }
        .pointerInput(start) {
            fun selectAt(x: Float) {
                val left = 8.dp.toPx()
                val right = (size.width - 8.dp.toPx()).coerceAtLeast(left + 1f)
                val fraction = ((x - left) / (right - left)).coerceIn(0f, 1f)
                selected = start + ((end - start) * fraction).toLong()
            }
            detectHorizontalDragGestures(
                onDragStart = { offset -> selectAt(offset.x) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    selectAt(change.position.x)
                }
            )
        }.drawWithCache {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val top = 12.dp.toPx()
        fun x(t: Long) = left + (t - start).toFloat() / (end - start) * (right - left)
        fun y(v: Double) = bottom - (v / maxY).toFloat() * (bottom - top)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted.copy(alpha = 0.78f).toArgb(); textSize = 11.sp.toPx() }
        val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted.copy(alpha = 0.85f).toArgb(); textSize = 11.sp.toPx() }
        val line = Path()
        values.forEachIndexed { i, point -> if (i == 0) line.moveTo(x(point.first), y(point.second)) else line.lineTo(x(point.first), y(point.second)) }
        val fill = Path().apply { addPath(line); lineTo(right, bottom); lineTo(left, bottom); close() }
        val areaBrush = Brush.verticalGradient(
            colors = listOf(primary.copy(alpha = .34f), primary.copy(alpha = .02f)),
            startY = top,
            endY = bottom
        )
        val lineStroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val thresholdDash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
        val scrubDash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        val hourLabels = listOf(0, 6, 12, 18, 24, 30).map { hour -> hour to (hour % 24).toString().padStart(2, '0') }
        val safeBottom = maxOf(top + 1f, bottom)
        val thresholdY = y(settings.bedtimeThresholdMg).coerceIn(top, safeBottom)
        val minLabelY = top + 10.dp.toPx()
        val maxLabelY = maxOf(minLabelY, safeBottom - 4.dp.toPx())
        val thresholdLabelY = (thresholdY - 5.dp.toPx()).coerceIn(minLabelY, maxLabelY)
        val chartBedtime = if (bedtime in start..end) bedtime else runCatching {
            Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                .atTime(LocalTime.parse(settings.bedtime)).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()?.takeIf { it in start..end }
        val bedtimeVisible = chartBedtime != null
        val bedtimeX = if (chartBedtime != null) x(chartBedtime).coerceIn(left, right) else 0f
        val bedtimeLabelW = thresholdPaint.measureText(bedtimeLabel)
        val bedtimeLabelX = if (bedtimeX + 5.dp.toPx() + bedtimeLabelW <= right) {
            bedtimeX + 5.dp.toPx()
        } else {
            (bedtimeX - 5.dp.toPx() - bedtimeLabelW).coerceAtLeast(left)
        }
        onDrawBehind {
            drawLine(muted.copy(alpha = .38f), Offset(left, thresholdY), Offset(right, thresholdY), 1.dp.toPx(), pathEffect = thresholdDash)
            drawContext.canvas.nativeCanvas.drawText(thresholdLabel, left, thresholdLabelY, thresholdPaint)
            if (bedtimeVisible) {
                drawLine(muted.copy(alpha = .38f), Offset(bedtimeX, top), Offset(bedtimeX, safeBottom), 1.dp.toPx(), pathEffect = thresholdDash)
                drawContext.canvas.nativeCanvas.drawText(bedtimeLabel, bedtimeLabelX, top + 10.dp.toPx(), thresholdPaint)
            }
            drawPath(fill, areaBrush)
            drawPath(line, primary, style = lineStroke)
            val cx = x(chosen)
            val cy = y(chosenAmount)
            drawLine(primary.copy(alpha = .38f), Offset(cx, cy), Offset(cx, bottom), 1.dp.toPx(), pathEffect = scrubDash)
            drawCircle(primary.copy(alpha = .24f), 8.5.dp.toPx(), Offset(cx, cy))
            drawCircle(primary, 4.5.dp.toPx(), Offset(cx, cy))
            for ((hour, label) in hourLabels) {
                val tw = labelPaint.measureText(label)
                val tx = (x(start + hour * 3600000L) - tw / 2f).coerceIn(left, right - tw)
                drawContext.canvas.nativeCanvas.drawText(label, tx, size.height - 2.dp.toPx(), labelPaint)
            }
        }
    })
    if (showLegend) Text(
        strings.chartLegend(mg(settings.bedtimeThresholdMg), settings.bedtime, decimal(settings.halfLifeHours, language)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}
