package com.roinur.caffeinetracker.feature.statistics

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.components.*
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.roundToInt

/** Book Tracker period navigation and statistic rows, with caffeine-specific measures. */
@Composable
fun StatisticsScreen(intakes: List<Intake>, now: Long, drinks: List<Drink> = emptyList(), onShowIntakes: (IntakeDateRange) -> Unit) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    var rangeName by rememberSaveable { mutableStateOf(CaffeineStatsRange.MONTH.name) }
    var metricName by rememberSaveable { mutableStateOf(CaffeineGraphMetric.CAFFEINE.name) }
    var periodOffset by rememberSaveable { mutableIntStateOf(0) }
    val range = CaffeineStatsRange.valueOf(rangeName)
    val metric = CaffeineGraphMetric.valueOf(metricName)
    val today = dayOf(now)
    val eligible = remember(intakes, now) { intakes.filter { it.timestamp <= now } }
    val period = remember(eligible, today, range, periodOffset) {
        caffeinePeriod(today, range, periodOffset, eligible.minOfOrNull { dayOf(it.timestamp) })
    }
    val rows = remember(eligible, period) { eligible.filter { dayOf(it.timestamp) in period.start..period.end } }
    val summary = remember(rows) { caffeinePeriodSummary(rows) }
    val drinksByKey = remember(drinks) { drinks.associateBy { canonicalDrinkKey(it.name) } }
    val points = remember(rows, period, metric) { caffeineDailyPoints(rows, period, metric) }
    val chart = remember(points, range) { caffeineChartPoints(points, range) }
    val byDay = remember(eligible) { eligible.groupBy { dayOf(it.timestamp) }.mapValues { it.value.sumOf { row -> row.caffeineMg } } }
    val total = points.sumOf { it.value }
    val highest = points.maxByOrNull { it.value }
    var selectedDateText by rememberSaveable(rangeName, periodOffset, metricName) { mutableStateOf<String?>(null) }
    val selectedDate = selectedDateText?.let(LocalDate::parse)
    val monthly = range == CaffeineStatsRange.YEAR || (range == CaffeineStatsRange.ALL_TIME && points.size > 180)
    val dateFormat = remember(language) { DateTimeFormatter.ofPattern("d MMM yyyy", language.locale) }
    val shortDateFormat = remember(language) { DateTimeFormatter.ofPattern("d MMM", language.locale) }
    fun value(amount: Double) = if(metric == CaffeineGraphMetric.CAFFEINE) mg(amount)
        else if(amount % 1.0 == 0.0) amount.roundToInt().toString()
        else String.format(language.locale, "%.1f", amount)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "summary") { TrackerCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CaffeineStatCell(strings.statsIntakes, summary.intakes.toString(), Modifier.weight(1f))
                CaffeineVerticalDivider()
                CaffeineStatCell(strings.statsLoggedDays, summary.loggedDays.toString(), Modifier.weight(1f))
                CaffeineVerticalDivider()
                CaffeineStatCell(strings.statsDrinks, summary.uniqueDrinks.toString(), Modifier.weight(1f))
            }
        } }
        item { TrackerCard {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if(periodOffset == 0 || range == CaffeineStatsRange.ALL_TIME) when(range) {
                        CaffeineStatsRange.WEEK -> strings.statsThisWeek
                        CaffeineStatsRange.MONTH -> strings.statsThisMonth
                        CaffeineStatsRange.YEAR -> strings.statsThisYear
                        CaffeineStatsRange.ALL_TIME -> strings.statsAllTime
                    } else "${period.start.format(dateFormat)} – ${period.end.format(dateFormat)}",
                    Modifier.weight(1f),
                    style = if(periodOffset == 0 || range == CaffeineStatsRange.ALL_TIME) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if(range != CaffeineStatsRange.ALL_TIME) {
                    IconButton(onClick = { periodOffset++ }) {
                        TrackerGlyph(TrackerIcon.Chevron, Modifier.graphicsLayer(rotationZ = 180f), strings.previousPeriod)
                    }
                    IconButton(onClick = { periodOffset-- }, enabled = periodOffset > 0) {
                        TrackerGlyph(TrackerIcon.Chevron, description = strings.nextPeriod)
                    }
                }
            }
            // Copied BookStatsScreen range row; click handling uses standard Compose.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CaffeineStatsRange.entries.forEach { option ->
                    val selected = range == option
                    val shape = RoundedCornerShape(9.dp)
                    Box(contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f).height(40.dp).clip(shape)
                            .background(if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                            .clickable { rangeName = option.name; periodOffset = 0 }) {
                        Text(option.localizedLabel(strings), style = MaterialTheme.typography.labelLarge,
                            fontWeight = if(selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            CaffeineLineChart(
                title = if(monthly) strings.perMonth else strings.perDay, points = chart, range = range, metric = metric,
                selectedDate = selectedDate, onPointSelected = { selectedDateText = it.date.toString() }, metricSelector = {
                    Row {
                        CaffeineGraphMetric.entries.forEach { option ->
                            TextButton(onClick = { metricName = option.name }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Text(option.localizedLabel(strings), color = if(metric == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if(metric == option) FontWeight.SemiBold else FontWeight.Medium)
                            }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().height(240.dp))
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterEnd) {
                val date = selectedDate
                if (date != null) {
                    val window = chartSelectionRange(date, monthly, period)
                    TextButton(onClick = { onShowIntakes(window) }) {
                        TrackerGlyph(TrackerIcon.History); Spacer(Modifier.width(6.dp))
                        Text(if(monthly) strings.showMonthIntakes else strings.showIntakes)
                    }
                } else {
                    Text(
                        if (monthly) strings.tapChartMonth else strings.tapChartDay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CaffeineStatCell(strings.statsTotal, value(total), Modifier.weight(1f), reserveSupporting = true)
                CaffeineVerticalDivider()
                CaffeineStatCell(strings.statsDailyAverage, value(total / period.days), Modifier.weight(1f), supporting = strings.statsAllDays, reserveSupporting = true)
                CaffeineVerticalDivider()
                CaffeineStatCell(strings.statsHighestDay, value(highest?.value ?: 0.0), Modifier.weight(1f),
                    supporting = highest?.takeIf { it.value > 0 }?.date?.format(shortDateFormat).orEmpty(),
                    reserveSupporting = true)
            }
        } }
        item { TrackerCard {
            DetailSection(strings.intakeCalendar, strings.last13Weeks) { IntakeHeatmap(byDay, today, onShowIntakes) }
        } }
        item(key = "top-drinks", contentType = "top_drinks") { TrackerCard {
            DetailSection(strings.topDrinks, strings.inSelectedPeriod) {
            val topDrinks = remember(rows, drinksByKey, language, strings.totalSuffix, strings.countSuffix) {
                rows.groupBy { canonicalDrinkKey(it.name) }
                    .entries
                    .sortedByDescending { it.value.size }
                    .take(8)
                    .map { (key, logs) ->
                        val latest = logs.maxByOrNull { it.timestamp }
                        val catalog = drinksByKey[key]
                        val displayName = statsGroupLabel(logs.map { it.name }, language)
                        val category = latest?.category ?: catalog?.category ?: "Övrigt"
                        val imageKey = latest?.imageKey ?: catalog?.imageKey
                        val kind = drinkImage(displayName, category, imageKey)
                        Triple(
                            displayName,
                            mg(logs.sumOf { it.caffeineMg }) + strings.totalSuffix,
                            Pair(logs.size.toString() + strings.countSuffix, kind)
                        )
                    }
            }
            if(topDrinks.isEmpty()) Text(strings.noIntakesInPeriod, color = MaterialTheme.colorScheme.onSurfaceVariant)
            topDrinks.forEach { (displayName, totalText, countAndKind) ->
                val (countText, kind) = countAndKind
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DrinkThumbnail(kind = kind, modifier = Modifier.size(40.dp), showContainer = false)
                    Column(Modifier.weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(totalText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(countText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            }
        } }
    }
}

@Composable
private fun IntakeHeatmap(amounts: Map<LocalDate, Double>, today: LocalDate, onShowIntakes: (IntakeDateRange) -> Unit) {
    val strings = LocalStrings.current
    val lastSunday = today.plusDays(7L - today.dayOfWeek.value)
    val first = lastSunday.minusDays(90)
    var selectedText by rememberSaveable(today.toString()) { mutableStateOf(today.toString()) }
    val selected = LocalDate.parse(selectedText)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val border = MaterialTheme.colorScheme.onSurface
    val max = amounts.filterKeys { it in first..today }.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(first.toString(), style = MaterialTheme.typography.labelSmall)
        Text(today.toString(), style = MaterialTheme.typography.labelSmall)
    }
    Canvas(Modifier.fillMaxWidth().height(145.dp)
        .semantics { contentDescription = strings.calendarSemantic(selected.toString(), mg(amounts[selected] ?: 0.0)) }
        .pointerInput(first, today) {
            detectTapGestures { point ->
                val cellW = size.width / 13f
                val cellH = size.height / 7f
                val week = floor(point.x / cellW).toInt().coerceIn(0,12)
                val day = floor(point.y / cellH).toInt().coerceIn(0,6)
                val chosen = first.plusDays((week * 7 + day).toLong())
                if (chosen <= today) selectedText = chosen.toString()
            }
        }) {
        val cellW = size.width / 13
        val cellH = size.height / 7
        for (week in 0..12) for (day in 0..6) {
            val date = first.plusDays((week * 7 + day).toLong())
            val value = amounts[date] ?: 0.0
            val color = when {
                date > today -> empty.copy(alpha = .25f)
                value <= 0 -> empty
                else -> primary.copy(alpha = (.25 + .75 * value / max).toFloat().coerceIn(.25f,1f))
            }
            val offset = Offset(week * cellW + 2.dp.toPx(), day * cellH + 2.dp.toPx())
            val cellSize = androidx.compose.ui.geometry.Size(cellW - 4.dp.toPx(), cellH - 4.dp.toPx())
            drawRoundRect(color, offset, cellSize, CornerRadius(3.dp.toPx()))
            if (date == selected) drawRoundRect(border, offset, cellSize, CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(selected.toString(), style = MaterialTheme.typography.bodySmall)
        Text(mg(amounts[selected] ?: 0.0), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
    TextButton(onClick = { onShowIntakes(IntakeDateRange(selected)) }, modifier = Modifier.fillMaxWidth()) {
        TrackerGlyph(TrackerIcon.History); Spacer(Modifier.width(6.dp)); Text(strings.showIntakes)
    }
}
