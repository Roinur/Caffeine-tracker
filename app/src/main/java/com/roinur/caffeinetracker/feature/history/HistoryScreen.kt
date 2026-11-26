package com.roinur.caffeinetracker.feature.history

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.components.*
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import com.roinur.caffeinetracker.feature.dashboard.IntakeRow
import com.roinur.caffeinetracker.feature.dashboard.IntakeRowModel
import com.roinur.caffeinetracker.feature.dashboard.buildIntakeRowModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private sealed interface HistoryListItem {
    val key: String
    val contentType: String

    @Immutable
    data class DayHeader(override val key: String, val title: String, val totalMg: String) : HistoryListItem {
        override val contentType: String get() = "date"
    }

    @Immutable
    data class Entry(val model: IntakeRowModel) : HistoryListItem {
        override val key: String get() = model.intake.id
        override val contentType: String get() = "intake"
    }
}

@Composable
fun HistoryScreen(intakes: List<Intake>, onEdit: (Intake) -> Unit, onImport: () -> Unit, dateRange: IntakeDateRange?, onDateRange: (IntakeDateRange?) -> Unit, now: Long) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    var search by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val dateFormat = remember(language) { DateTimeFormatter.ofPattern("d MMM yyyy", language.locale) }
    val longDateFormat = remember(language) { DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale) }
    val todayDate = remember(now / 60_000L) { dayOf(now) }
    val yesterdayDate = remember(todayDate) { todayDate.minusDays(1) }

    val filteredCountAndItems = remember(intakes, search, dateRange, todayDate, language) {
        val filtered = intakes.filter {
            (dateRange == null || dateRange.contains(it.timestamp)) && matchesDrinkSearch(it.name, it.category, search, it.note)
        }
        val groups = filtered.sortedByDescending { it.timestamp }.groupBy { dayOf(it.timestamp) }
        val flat = ArrayList<HistoryListItem>(filtered.size + groups.size)
        groups.forEach { (date, rows) ->
            val title = when (date) {
                todayDate -> strings.todayLabel
                yesterdayDate -> strings.yesterdayLabel
                else -> date.format(longDateFormat)
            }
            flat.add(HistoryListItem.DayHeader("date:$date", title, mg(rows.sumOf { it.caffeineMg })))
            rows.forEach { intake ->
                flat.add(HistoryListItem.Entry(buildIntakeRowModel(intake, language)))
            }
        }
        filtered.size to flat
    }
    val filteredCount = filteredCountAndItems.first
    val flatItems = filteredCountAndItems.second

    fun chooseDates() {
        val from = dateRange?.start ?: todayDate
        DatePickerDialog(context, { _, y, m, d ->
            val start = LocalDate.of(y, m + 1, d)
            val until = dateRange?.end?.coerceAtLeast(start) ?: start
            val picker = DatePickerDialog(context, { _, ey, em, ed ->
                onDateRange(IntakeDateRange(start, LocalDate.of(ey, em + 1, ed)))
            }, until.year, until.monthValue - 1, until.dayOfMonth)
            picker.setTitle(strings.toDate)
            picker.datePicker.minDate = start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            picker.show()
        }, from.year, from.monthValue - 1, from.dayOfMonth).apply { setTitle(strings.fromDate) }.show()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "history-header", contentType = "header") {
            SectionTitle(strings.yourHistory, filteredCount.toString() + if (search.isNotBlank() || dateRange != null) strings.matchingIntakesSuffix else strings.savedIntakesSuffix)
        }
        item(key = "history-search", contentType = "search") {
            PaletteOutlinedTextField(
                search,
                { search = it },
                Modifier.fillMaxWidth(),
                label = { Text(strings.searchDrinkOrNote) },
                singleLine = true,
                leadingIcon = { TrackerGlyph(TrackerIcon.Search) }
            )
        }
        item(key = "history-dates", contentType = "dates") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = dateRange != null,
                    onClick = ::chooseDates,
                    label = {
                        Text(dateRange?.let {
                            if (it.start == it.end) it.start.format(dateFormat)
                            else it.start.format(dateFormat) + " – " + it.end.format(dateFormat)
                        } ?: strings.dateChip)
                    },
                    leadingIcon = { TrackerGlyph(TrackerIcon.Calendar) }
                )
                if (dateRange != null) IconButton(onClick = { onDateRange(null) }) { TrackerGlyph(TrackerIcon.Close, description = strings.clearDateFilter) }
            }
        }
        if (flatItems.isEmpty()) {
            item(key = "history-empty", contentType = "empty") {
                EmptyState(
                    if (intakes.isEmpty()) strings.historyStartsHere else strings.noMatches,
                    if (intakes.isEmpty()) strings.historyEmptyDetail else strings.historyNoMatchesDetail,
                    TrackerIcon.History,
                    action = if (intakes.isEmpty()) ({ TextButton(onClick = onImport) { Text(strings.importHistoryButton) } }) else null
                )
            }
        } else {
            items(flatItems, key = { it.key }, contentType = { it.contentType }) { item ->
                when (item) {
                    is HistoryListItem.DayHeader -> Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(item.totalMg, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is HistoryListItem.Entry -> IntakeRow(model = item.model, enabled = true, onEdit = onEdit)
                }
            }
        }
    }
}
