@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.roinur.caffeinetracker.feature.dashboard.home

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roinur.caffeinetracker.core.caffeine.*
import com.roinur.caffeinetracker.core.diagnostics.GitHubMediaSession
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.components.*
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import com.roinur.caffeinetracker.data.backup.BackupCodec
import com.roinur.caffeinetracker.feature.dashboard.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun DashboardScreen(
    snapshot: TrackerSnapshot,
    now: Long,
    enabled: Boolean,
    onSave: (Intake) -> Unit,
    onEdit: (Intake) -> Unit,
    onNavigate: (Int) -> Unit,
    onEditDrink: (Drink) -> Unit,
    receipt: IntakeChange?,
    incomingPreset: Drink?,
    onPresetConsumed: () -> Unit,
    onFavorite: (Drink) -> Unit,
    operationVersion: Long,
    active: Boolean = true,
    cachedCurve: CaffeineCurve? = null
) {
    val currentEntries = remember(snapshot.intakes, now) { snapshot.intakes.filter { it.timestamp <= now } }
    val today = remember(snapshot.intakes, dayOf(now)) { snapshot.intakes.filter { dayOf(it.timestamp) == dayOf(now) } }
    val todayEntries = remember(today, now) { today.filter { it.timestamp <= now }.sortedByDescending { it.timestamp } }
    val favorites = remember(snapshot.drinks) { snapshot.drinks.filter { it.favorite } }
    val favoritePages = remember(favorites) { favorites.chunked(6) }
    val recentNames = remember(snapshot.intakes) { snapshot.intakes.map { it.name }.distinct().take(12) }
    val current = remember(currentEntries, now, snapshot.settings.halfLifeHours) { CaffeineCalculator.total(currentEntries, now, snapshot.settings.halfLifeHours) }
    val bedtime = CaffeineCalculator.nextBedtime(now, snapshot.settings.bedtime)
    val evening = remember(currentEntries, bedtime, snapshot.settings.halfLifeHours) { CaffeineCalculator.total(currentEntries, bedtime, snapshot.settings.halfLifeHours) }
    val zone = ZoneId.systemDefault()
    val start = remember(dayOf(now), zone) { Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() }
    val localCurve by produceState<CaffeineCurve?>(cachedCurve, cachedCurve, currentEntries, snapshot.settings.halfLifeHours, start) {
        if (cachedCurve != null && cachedCurve.start == start) {
            value = cachedCurve
        } else {
            value = withContext(Dispatchers.Default) {
                android.os.Trace.beginSection("CaffeineCurve.sample")
                try { sampleCaffeineCurve(currentEntries, snapshot.settings.halfLifeHours, start) }
                finally { android.os.Trace.endSection() }
            }
        }
    }
    val curve = cachedCurve?.takeIf { it.start == start } ?: localCurve
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    val todayModels = remember(todayEntries, language) { todayEntries.map { buildIntakeRowModel(it, language) } }
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var preset by remember { mutableStateOf<Drink?>(null) }
    var composerOpen by remember { mutableStateOf(false) }
    var composerCloseSignal by remember { mutableIntStateOf(0) }
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var composerBounds by remember { mutableStateOf<Rect?>(null) }
    var lastQuickAdd by remember { mutableStateOf<Pair<String, Long>?>(null) }

    fun quickAddFavorite(drink: Drink) {
        if (!enabled || composerOpen) return
        val elapsed = SystemClock.elapsedRealtime()
        val prev = lastQuickAdd
        if (prev != null && prev.first == drink.id && elapsed - prev.second in 0..700) return
        lastQuickAdd = drink.id to elapsed
        val intake = Intake(
            name = displayDrinkName(drink.name, language),
            caffeineMg = drink.caffeineMg,
            timestamp = GitHubMediaSession.clockTime(),
            category = displayCategory(drink.category, language),
            servingUnit = displayPortion(drink, language),
            imageKey = drink.imageKey
        )
        runCatching { BackupCodec.validIntake(intake) }.onSuccess {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSave(intake)
        }
    }

    LaunchedEffect(incomingPreset) {
        incomingPreset?.let { preset = it; listState.animateScrollToItem(0); onPresetConsumed() }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoords = it }
            .pointerInput(composerOpen) {
                if (!composerOpen) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startPos = down.position
                    val downInRoot = rootCoords?.localToRoot(startPos)
                    val insideComposer = downInRoot != null && composerBounds?.contains(downInRoot) == true
                    if (insideComposer) return@awaitEachGesture
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position - startPos).getDistance() > viewConfiguration.touchSlop) {
                            moved = true
                        }
                        if (!change.pressed) {
                            if (!moved) {
                                change.consume()
                                composerCloseSignal++
                            }
                            break
                        }
                    }
                }
            }
    ) {
        val viewportHeight = maxHeight
        val compactViewport = viewportHeight < 700.dp || fontScale > 1.15f
        val verticalPad = if (compactViewport) 8.dp else 10.dp
        val sectionGap = if (compactViewport) 8.dp else 10.dp
        val aboveFoldHeight = with(density) {
            (constraints.maxHeight - verticalPad.roundToPx() * 2).coerceAtLeast(0).toDp()
        }
        val favRows = when {
            favorites.isEmpty() -> 0
            favorites.size <= 3 -> 1
            else -> 2
        }
        val estimatedTopChrome = ((256f + favRows * (if (compactViewport) 96f else 108f)) * fontScale).dp
        val minCurveHeight = 132.dp
        val fitAboveFold = !composerOpen && aboveFoldHeight >= estimatedTopChrome + minCurveHeight
        val favoriteTileHeight = if (compactViewport) 58.dp else 68.dp
        val favoriteImgSize = if (compactViewport) 42.dp else 50.dp
        val favoriteRowHeight = favoriteTileHeight + (44f * fontScale).dp
        val stackedCellHeight = if (compactViewport) 62.dp else 68.dp
        val heroValueSize = if (current >= 999.5) 28.sp else if (compactViewport) 32.sp else 38.sp
        val stackedBaseSize = if (compactViewport) 21.sp else 24.sp
        val todayTotal = todayEntries.sumOf { it.caffeineMg }

        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            state = listState,
            userScrollEnabled = todayEntries.isNotEmpty() || composerOpen || !fitAboveFold,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = verticalPad),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "above-fold") {
                Column(
                    modifier = if (fitAboveFold) Modifier.fillMaxWidth().height(aboveFoldHeight)
                    else Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(sectionGap)
                ) {
                    // Option A: 1 hero Kvar nu + 2 stacked cards, preserving gradient design.
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusCell(
                            label = strings.remainingNow,
                            value = current,
                            icon = TrackerIcon.Coffee,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalPadding = if (compactViewport) 12.dp else 14.dp,
                            horizontalPadding = 14.dp,
                            iconSize = if (compactViewport) 18.dp else 20.dp,
                            labelStyle = MaterialTheme.typography.titleSmall,
                            valueSize = heroValueSize,
                            unitSize = 15.sp,
                            gradientAlpha = 0.13f,
                            fillHeight = true
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusCell(
                                label = strings.atTimePrefix + clock(bedtime),
                                value = evening,
                                icon = TrackerIcon.Moon,
                                modifier = Modifier.fillMaxWidth().heightIn(min = stackedCellHeight),
                                verticalPadding = if (compactViewport) 9.dp else 10.dp,
                                horizontalPadding = 12.dp,
                                iconSize = 16.dp,
                                labelStyle = MaterialTheme.typography.labelMedium,
                                valueSize = if (evening >= 999.5) 19.sp else stackedBaseSize,
                                unitSize = 12.sp,
                                gradientAlpha = 0.10f,
                                fillHeight = false
                            )
                            StatusCell(
                                label = strings.intakeToday,
                                value = todayTotal,
                                icon = TrackerIcon.Calendar,
                                modifier = Modifier.fillMaxWidth().heightIn(min = stackedCellHeight),
                                verticalPadding = if (compactViewport) 9.dp else 10.dp,
                                horizontalPadding = 12.dp,
                                iconSize = 16.dp,
                                labelStyle = MaterialTheme.typography.labelMedium,
                                valueSize = if (todayTotal >= 999.5) 19.sp else stackedBaseSize,
                                unitSize = 12.sp,
                                gradientAlpha = 0.10f,
                                fillHeight = false
                            )
                        }
                    }

                    Box(Modifier.fillMaxWidth().onGloballyPositioned { composerBounds = it.boundsInRoot() }) {
                        IntakeComposer(
                            drinks = snapshot.drinks,
                            now = now,
                            enabled = enabled,
                            preset = preset,
                            onPresetConsumed = { preset = null },
                            onSave = onSave,
                            receipt = receipt,
                            onFavorite = onFavorite,
                            operationVersion = operationVersion,
                            recentNames = recentNames,
                            closeSignal = composerCloseSignal,
                            onOpenChange = { composerOpen = it },
                            active = active
                        )
                    }

                    if (favorites.isEmpty()) {
                        Text(
                            strings.noFavoritesHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (favoritePages.size == 1) {
                        FavoriteGridPage(
                            drinks = favoritePages.first(),
                            enabled = enabled,
                            tileHeight = favoriteTileHeight,
                            imgSize = favoriteImgSize,
                            rowHeight = favoriteRowHeight,
                            onQuickAdd = ::quickAddFavorite,
                            onEditDrink = onEditDrink
                        )
                    } else {
                        val pagerState = rememberPagerState(pageCount = { favoritePages.size })
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HorizontalPager(
                                state = pagerState,
                                pageSpacing = 14.dp,
                                modifier = Modifier.fillMaxWidth().height(favoriteRowHeight * 2 + 10.dp),
                                verticalAlignment = Alignment.Top
                            ) { page ->
                                FavoriteGridPage(
                                    drinks = favoritePages[page],
                                    enabled = enabled,
                                    tileHeight = favoriteTileHeight,
                                    imgSize = favoriteImgSize,
                                    rowHeight = favoriteRowHeight,
                                    minRows = 2,
                                    onQuickAdd = ::quickAddFavorite,
                                    onEditDrink = onEditDrink
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(favoritePages.size) { index ->
                                    Box(
                                        Modifier
                                            .size(if (pagerState.currentPage == index) 6.dp else 5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            Triple(strings.history, TrackerIcon.History, 1),
                            Triple(strings.drinks, TrackerIcon.Drinks, 2),
                            Triple(strings.statistics, TrackerIcon.Chart, 3)
                        ).forEach { (label, icon, destination) ->
                            TextButton(
                                onClick = { onNavigate(destination) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = if (compactViewport) 4.dp else 6.dp)
                            ) {
                                TrackerGlyph(icon); Spacer(Modifier.width(5.dp)); Text(label)
                            }
                        }
                    }

                    // Calm, borderless ambient curve — never stretches to overpower the screen.
                    Box(
                        modifier = if (fitAboveFold) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            modifier = if (fitAboveFold) Modifier.fillMaxWidth().heightIn(max = 224.dp).fillMaxHeight().padding(horizontal = 2.dp)
                            else Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CaffeineChart(
                                intakes = currentEntries,
                                settings = snapshot.settings,
                                now = now,
                                bedtime = bedtime,
                                curve = curve,
                                nowAmount = current,
                                bedtimeAmount = evening,
                                chartModifier = if (fitAboveFold) Modifier.fillMaxWidth().weight(1f).heightIn(min = 96.dp)
                                else Modifier.fillMaxWidth().height(156.dp),
                                showLegend = false,
                                headerStart = {
                                    Text(strings.caffeineCurve, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                            )
                        }
                    }
                }
            }

            items(todayModels, key = { it.intake.id }, contentType = { "intake" }) { model ->
                IntakeRow(model = model, enabled = enabled, onEdit = onEdit)
            }
        }
    }
}

@Composable
private fun FavoriteGridPage(
    drinks: List<Drink>,
    enabled: Boolean,
    tileHeight: Dp,
    imgSize: Dp,
    rowHeight: Dp,
    minRows: Int = 1,
    onQuickAdd: (Drink) -> Unit,
    onEditDrink: (Drink) -> Unit
) {
    val rows = remember(drinks) { drinks.chunked(3) }
    val rowCount = maxOf(rows.size, minRows)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rowCount) { rowIndex ->
            val row = rows.getOrNull(rowIndex).orEmpty()
            Row(Modifier.fillMaxWidth().height(rowHeight), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (row.isEmpty()) {
                    Spacer(Modifier.weight(1f).fillMaxHeight())
                } else {
                    row.forEach { drink ->
                        FavoriteGridTile(
                            drink = drink,
                            enabled = enabled,
                            tileHeight = tileHeight,
                            imgSize = imgSize,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onQuickAdd(drink) },
                            onLongClick = { if (enabled) onEditDrink(drink) }
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val favoriteTileShape = RoundedCornerShape(18.dp)
private val statusCellShape = RoundedCornerShape(18.dp)

@Composable
private fun FavoriteGridTile(
    drink: Drink,
    enabled: Boolean,
    tileHeight: Dp,
    imgSize: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val shownName = displayDrinkName(drink.name, language)
    val imageKind = remember(drink.name, drink.category, drink.imageKey) { drinkImage(drink.name, drink.category, drink.imageKey) }
    Column(
        modifier = modifier
            .clip(favoriteTileShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onClickLabel = strings.addDrinkAction(shownName),
                onLongClick = onLongClick,
                onLongClickLabel = strings.editFavoriteAction
            )
            .padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tileHeight)
                .clip(favoriteTileShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            DrinkThumbnail(kind = imageKind, modifier = Modifier.size(imgSize), showContainer = false)
            TrackerGlyph(
                TrackerIcon.Favorite,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(13.dp),
                tint = primaryColor
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                shownName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
                        append(mg(drink.caffeineMg))
                    }
                    withStyle(SpanStyle(color = mutedColor)) {
                        append(" · ")
                        append(displayPortion(drink, language))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusCell(
    label: String,
    value: Double,
    icon: TrackerIcon,
    modifier: Modifier,
    verticalPadding: Dp = 12.dp,
    horizontalPadding: Dp = 12.dp,
    iconSize: Dp = 18.dp,
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
    valueSize: TextUnit = 26.sp,
    unitSize: TextUnit = 13.sp,
    gradientAlpha: Float = 0.11f,
    fillHeight: Boolean = false
) {
    val palette = MaterialTheme.colorScheme
    Surface(
        modifier,
        shape = statusCellShape,
        color = palette.surfaceContainerLow,
        border = BorderStroke(1.dp, palette.outlineVariant.copy(alpha = .28f))
    ) {
        Column(
            (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .background(Brush.verticalGradient(listOf(palette.primary.copy(alpha = gradientAlpha), palette.surfaceContainerLow)))
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = if (fillHeight) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrackerGlyph(icon, Modifier.size(iconSize), tint = palette.primary)
                Text(label, style = labelStyle, color = palette.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = valueSize)) { append(mg(value).removeSuffix(" mg")) }
                    withStyle(SpanStyle(fontSize = unitSize, color = palette.onSurfaceVariant)) { append(" mg") }
                },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
