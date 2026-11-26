@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.roinur.caffeinetracker.feature.drinks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.components.*
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private val drinkRowShape = RoundedCornerShape(18.dp)
private val drinkRowInnerShape = RoundedCornerShape(14.dp)
private val drinkMenuItemShape = RoundedCornerShape(12.dp)

@Immutable
private data class DrinkRowUi(
    val drink: Drink,
    val shownName: String,
    val subtitle: String,
    val imageKind: DrinkImage
)

private fun buildDrinkRowUi(drink: Drink, language: AppLanguage, customBadge: String): DrinkRowUi {
    val shownName = displayDrinkName(drink.name, language)
    val subtitle = displayPortion(drink, language) + " · " + mg(drink.caffeineMg) +
        if (isCustomCategory(drink.category)) " · " + customBadge else ""
    return DrinkRowUi(drink, shownName, subtitle, drinkImage(drink.name, drink.category, drink.imageKey))
}

@Composable
fun DrinksScreen(
    drinks: List<Drink>,
    enabled: Boolean,
    operationVersion: Long,
    onAdd: (Drink) -> Unit,
    onEdit: (Drink) -> Unit,
    onNew: () -> Unit,
    onFavorite: (Drink) -> Unit,
    onVariant: (Drink) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    var search by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var customOnly by rememberSaveable { mutableStateOf(false) }
    val allVisible = remember(drinks) { drinks.filterNot { it.id == "other" && it.name == "Eget intag" } }
    val hasCustomDrinks = remember(allVisible) { allVisible.any { isCustomCategory(it.category) } }
    val visibleDrinks = remember(allVisible, customOnly) {
        if (customOnly) allVisible.filter { isCustomCategory(it.category) } else allVisible
    }
    val favorites = remember(visibleDrinks, language) {
        visibleDrinks.filter { it.favorite }.sortedWith(compareBy<Drink> { it.favoriteOrder }.thenBy { searchKey(displayDrinkName(it.name, language)) })
    }
    val favoriteIds = remember(favorites) { favorites.map { it.id } }
    val drinksById = remember(visibleDrinks) { visibleDrinks.associateBy { it.id } }

    var draftFavoriteOrder by remember { mutableStateOf(favoriteIds) }
    var pendingOrder by remember { mutableStateOf<List<String>?>(null) }
    var pendingAtVersion by remember { mutableLongStateOf(-1L) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedViewportTop by remember { mutableFloatStateOf(0f) }
    var settlingId by remember { mutableStateOf<String?>(null) }
    val settleOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(favoriteIds, operationVersion) {
        if (draggedId == null) {
            val settled = pendingOrder == null ||
                favoriteIds == pendingOrder ||
                favoriteIds.toSet() != pendingOrder!!.toSet() ||
                operationVersion != pendingAtVersion
            if (settled) {
                pendingOrder = null
                draftFavoriteOrder = favoriteIds
            }
        }
    }

    val orderedFavorites = remember(draftFavoriteOrder, favorites, drinksById) {
        val kept = draftFavoriteOrder.mapNotNull { id -> drinksById[id]?.takeIf { it.favorite } }
        val keptIds = kept.mapTo(HashSet()) { it.id }
        kept + favorites.filter { it.id !in keptIds }
    }
    val otherDrinks = remember(visibleDrinks, language) {
        visibleDrinks.filter { !it.favorite }.sortedBy { searchKey(displayDrinkName(it.name, language)) }
    }
    val searched = remember(visibleDrinks, search, favoritesOnly, language) {
        if (search.isBlank()) emptyList()
        else searchDrinkRows(visibleDrinks.filter { !favoritesOnly || it.favorite }, search, language)
    }

    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val speed = with(LocalDensity.current) { 7.dp.toPx() }
    val currentOrder by rememberUpdatedState(orderedFavorites.map { it.id })
    val currentCommitted by rememberUpdatedState(favoriteIds)
    val currentOperationVersion by rememberUpdatedState(operationVersion)
    val currentOnReorder by rememberUpdatedState(onReorder)

    fun moveFavoriteStep(id: String, delta: Int): Boolean {
        val order = currentOrder
        val from = order.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in order.indices) return false
        val next = order.toMutableList().apply { add(to, removeAt(from)) }
        draftFavoriteOrder = next
        pendingOrder = next
        pendingAtVersion = currentOperationVersion
        currentOnReorder(next)
        return true
    }

    fun moveDragged() {
        val id = draggedId ?: return
        val order = draftFavoriteOrder
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val center = draggedViewportTop + info.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            val key = it.key as? String
            key != null && key != id && key in order && center >= it.offset && center < it.offset + it.size
        } ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(target.key as String)
        if (from >= 0 && to >= 0 && from != to) {
            draftFavoriteOrder = order.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    LaunchedEffect(draggedId) {
        while (isActive && draggedId != null) {
            val layout = listState.layoutInfo
            val order = draftFavoriteOrder
            val item = layout.visibleItemsInfo.firstOrNull { it.key == draggedId }
            if (item != null && order.isNotEmpty()) {
                val center = draggedViewportTop + item.size / 2f
                val firstFav = layout.visibleItemsInfo.firstOrNull { it.key == order.first() }
                val lastFav = layout.visibleItemsInfo.firstOrNull { it.key == order.last() }
                val canScrollUp = firstFav == null || firstFav.offset < layout.viewportStartOffset
                val canScrollDown = lastFav == null || lastFav.offset + lastFav.size > layout.viewportEndOffset
                val scroll = when {
                    center < layout.viewportStartOffset + edge && canScrollUp -> {
                        val maxUp = if (firstFav != null) (layout.viewportStartOffset - firstFav.offset).toFloat().coerceAtLeast(0f) else speed
                        -minOf(speed, maxUp)
                    }
                    center > layout.viewportEndOffset - edge && canScrollDown -> {
                        val maxDown = if (lastFav != null) (lastFav.offset + lastFav.size - layout.viewportEndOffset).toFloat().coerceAtLeast(0f) else speed
                        minOf(speed, maxDown)
                    }
                    else -> 0f
                }
                if (scroll != 0f) {
                    listState.scrollBy(scroll)
                    moveDragged()
                }
            }
            delay(16)
        }
    }

    val searchActive = search.isNotBlank()
    val canDragFavorites = enabled && !searchActive && orderedFavorites.size > 1
    val showFavorites = if (searchActive) emptyList() else orderedFavorites
    val showOthers = if (searchActive || favoritesOnly) emptyList() else otherDrinks
    val rowUiById = remember(visibleDrinks, language, strings.customBadge) {
        visibleDrinks.associate { it.id to buildDrinkRowUi(it, language, strings.customBadge) }
    }
    val searchedUi = remember(searched, rowUiById, language, strings.customBadge) {
        searched.map { rowUiById[it.id] ?: buildDrinkRowUi(it, language, strings.customBadge) }
    }
    val showFavoritesUi = remember(showFavorites, rowUiById, language, strings.customBadge) {
        showFavorites.map { rowUiById[it.id] ?: buildDrinkRowUi(it, language, strings.customBadge) }
    }
    val showOthersUi = remember(showOthers, rowUiById, language, strings.customBadge) {
        showOthers.map { rowUiById[it.id] ?: buildDrinkRowUi(it, language, strings.customBadge) }
    }
    val isEmpty = if (searchActive) searchedUi.isEmpty() else (showFavoritesUi.isEmpty() && showOthersUi.isEmpty())
    val placementSpec = remember {
        spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "controls") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text(strings.favoritesOnly) })
                if (hasCustomDrinks) {
                    FilterChip(customOnly, { customOnly = !customOnly }, label = { Text(strings.customOnly) })
                }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = enabled, onClick = onNew) {
                    TrackerGlyph(TrackerIcon.Plus); Spacer(Modifier.width(6.dp)); Text(strings.newDrink)
                }
            }
        }
        item(key = "search") {
            PaletteOutlinedTextField(
                search,
                { search = it },
                Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.searchDrink) },
                leadingIcon = { TrackerGlyph(TrackerIcon.Search) },
                trailingIcon = if (search.isNotBlank()) ({
                    IconButton(onClick = { search = "" }) {
                        TrackerGlyph(TrackerIcon.Close, description = strings.clearSearch)
                    }
                }) else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            )
        }
        if (isEmpty) {
            item(key = "empty", contentType = "empty") {
                EmptyState(strings.noDrinkFoundTitle, strings.noDrinkFoundDetail, TrackerIcon.Drinks)
            }
        } else if (searchActive) {
            items(searchedUi, key = { it.drink.id }, contentType = { "drink_static" }) { row ->
                StaticDrinkLibraryRow(
                    row = row,
                    enabled = enabled,
                    onAdd = onAdd,
                    onEdit = onEdit,
                    onFavorite = onFavorite,
                    onVariant = onVariant
                )
            }
        } else {
            if (showFavoritesUi.isNotEmpty()) {
                item(key = "favorites-header", contentType = "section_header") {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(strings.favoritesHeader, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (canDragFavorites) {
                            Text(
                                strings.dragFavoritesHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                itemsIndexed(showFavoritesUi, key = { _, it -> it.drink.id }, contentType = { _, _ -> "drink_fav" }) { index, row ->
                    val drink = row.drink
                    val dragging = draggedId == drink.id
                    val settling = settlingId == drink.id
                    val placementModifier = if (!dragging && !settling) Modifier.animateItemPlacement(placementSpec) else Modifier
                    if (!canDragFavorites && !dragging && !settling) {
                        StaticDrinkLibraryRow(
                            row = row,
                            enabled = enabled,
                            modifier = placementModifier,
                            onAdd = onAdd,
                            onEdit = onEdit,
                            onFavorite = onFavorite,
                            onVariant = onVariant
                        )
                    } else {
                    DraggableDrinkLibraryRow(
                        row = row,
                        enabled = enabled,
                        dragging = dragging,
                        settling = settling,
                        translationYProvider = {
                            when {
                                dragging -> {
                                    val actualOffset = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == drink.id }?.offset?.toFloat()
                                    if (actualOffset != null) draggedViewportTop - actualOffset else 0f
                                }
                                settling -> settleOffset.value
                                else -> 0f
                            }
                        },
                        canDrag = canDragFavorites,
                        canMoveUp = canDragFavorites && index > 0,
                        canMoveDown = canDragFavorites && index < showFavoritesUi.lastIndex,
                        modifier = placementModifier,
                        onAdd = onAdd,
                        onEdit = onEdit,
                        onFavorite = onFavorite,
                        onVariant = onVariant,
                        onMoveUp = { moveFavoriteStep(drink.id, -1) },
                        onMoveDown = { moveFavoriteStep(drink.id, 1) },
                        onDragStart = {
                            val currentOffset = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == drink.id }?.offset?.toFloat()
                            if (currentOffset != null) {
                                val visualOffset = if (settlingId == drink.id) settleOffset.value else 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                settlingId = null
                                scope.launch { settleOffset.snapTo(0f) }
                                draftFavoriteOrder = currentOrder
                                draggedViewportTop = currentOffset + visualOffset
                                draggedId = drink.id
                            }
                        },
                        onDragDelta = { deltaY ->
                            draggedViewportTop += deltaY
                            moveDragged()
                        },
                        onDragEnd = {
                            val actualOffset = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == drink.id }?.offset?.toFloat()
                            val residual = if (actualOffset != null) draggedViewportTop - actualOffset else 0f
                            val finalOrder = draftFavoriteOrder
                            draggedId = null
                            if (finalOrder != currentCommitted) {
                                pendingOrder = finalOrder
                                pendingAtVersion = currentOperationVersion
                                currentOnReorder(finalOrder)
                            }
                            if (abs(residual) > 0.5f) {
                                settlingId = drink.id
                                scope.launch {
                                    settleOffset.snapTo(residual)
                                    settleOffset.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                    )
                                    if (settlingId == drink.id) settlingId = null
                                }
                            }
                        },
                        onDragCancel = {
                            draggedId = null
                            settlingId = null
                            draftFavoriteOrder = currentCommitted
                        }
                    )
                    }
                }
            }
            if (showOthersUi.isNotEmpty()) {
                if (showFavoritesUi.isNotEmpty()) {
                    item(key = "others-header", contentType = "section_header") {
                        Text(
                            strings.otherDrinksHeader,
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(showOthersUi, key = { it.drink.id }, contentType = { "drink_static" }) { row ->
                    StaticDrinkLibraryRow(
                        row = row,
                        enabled = enabled,
                        onAdd = onAdd,
                        onEdit = onEdit,
                        onFavorite = onFavorite,
                        onVariant = onVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StaticDrinkLibraryRow(
    row: DrinkRowUi,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onAdd: (Drink) -> Unit,
    onEdit: (Drink) -> Unit,
    onFavorite: (Drink) -> Unit,
    onVariant: (Drink) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(drinkRowShape)
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.25f), drinkRowShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrinkLibraryRowContent(
            row = row,
            enabled = enabled,
            dragging = false,
            contentModifier = Modifier.weight(1f),
            onAdd = onAdd,
            onEdit = onEdit,
            onFavorite = onFavorite,
            onVariant = onVariant
        )
    }
}

@Composable
private fun DraggableDrinkLibraryRow(
    row: DrinkRowUi,
    enabled: Boolean,
    dragging: Boolean,
    settling: Boolean,
    translationYProvider: () -> Float,
    canDrag: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier = Modifier,
    onAdd: (Drink) -> Unit,
    onEdit: (Drink) -> Unit,
    onFavorite: (Drink) -> Unit,
    onVariant: (Drink) -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val strings = LocalStrings.current
    val drink = row.drink
    val updatedStart by rememberUpdatedState(onDragStart)
    val updatedDelta by rememberUpdatedState(onDragDelta)
    val updatedEnd by rememberUpdatedState(onDragEnd)
    val updatedCancel by rememberUpdatedState(onDragCancel)
    val scale by animateFloatAsState(
        targetValue = if (dragging) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "drinkDragScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (dragging) 12.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "drinkDragElevation"
    )
    val containerColor by animateColorAsState(
        targetValue = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "drinkDragContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (dragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        label = "drinkDragBorder"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 2f else if (settling) 1f else 0f)
            .graphicsLayer {
                translationY = translationYProvider()
                scaleX = scale
                scaleY = scale
                this.shape = drinkRowShape
                shadowElevation = elevation.toPx()
            },
        shape = drinkRowShape,
        color = containerColor,
        border = BorderStroke(if (dragging) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrinkLibraryRowContent(
                row = row,
                enabled = enabled,
                dragging = dragging,
                contentModifier = Modifier
                    .weight(1f)
                    .semantics {
                        val actions = buildList {
                            if (canMoveUp) add(CustomAccessibilityAction(strings.moveUp) { onMoveUp() })
                            if (canMoveDown) add(CustomAccessibilityAction(strings.moveDown) { onMoveDown() })
                        }
                        if (actions.isNotEmpty()) customActions = actions
                    }
                    .then(
                        if (canDrag) Modifier.pointerInput(drink.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { updatedStart() },
                                onDragEnd = { updatedEnd() },
                                onDragCancel = { updatedCancel() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    updatedDelta(amount.y)
                                }
                            )
                        } else Modifier
                    ),
                onAdd = onAdd,
                onEdit = onEdit,
                onFavorite = onFavorite,
                onVariant = onVariant
            )
        }
    }
}

@Composable
private fun RowScope.DrinkLibraryRowContent(
    row: DrinkRowUi,
    enabled: Boolean,
    dragging: Boolean,
    contentModifier: Modifier,
    onAdd: (Drink) -> Unit,
    onEdit: (Drink) -> Unit,
    onFavorite: (Drink) -> Unit,
    onVariant: (Drink) -> Unit
) {
    val strings = LocalStrings.current
    val drink = row.drink
    var menu by remember { mutableStateOf(false) }
    Row(
        contentModifier
            .clip(drinkRowInnerShape)
            .clickable(enabled = enabled && !dragging, onClickLabel = strings.selectDrink) { onAdd(drink) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrinkThumbnail(kind = row.imageKind, modifier = Modifier.size(44.dp), showContainer = false)
        Column(Modifier.weight(1f)) {
            Text(row.shownName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
    IconButton(enabled = enabled && !dragging, onClick = { onFavorite(drink.copy(favorite = !drink.favorite)) }) {
        TrackerGlyph(
            TrackerIcon.Favorite,
            description = (if (drink.favorite) strings.removeFavoritePrefix else strings.favoritePrefix) + row.shownName,
            tint = if (drink.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    }
    Box {
        IconButton(enabled = enabled && !dragging, onClick = { menu = true }) {
            TrackerGlyph(TrackerIcon.More, description = strings.optionsPrefix + row.shownName)
        }
        if (menu) {
            DropdownMenu(expanded = true, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    modifier = Modifier.clip(drinkMenuItemShape),
                    text = { Text(strings.edit) },
                    onClick = { menu = false; onEdit(drink) },
                    leadingIcon = { TrackerGlyph(TrackerIcon.Edit) }
                )
                DropdownMenuItem(
                    modifier = Modifier.clip(drinkMenuItemShape),
                    text = { Text(strings.createOtherSize) },
                    onClick = { menu = false; onVariant(drink) },
                    leadingIcon = { TrackerGlyph(TrackerIcon.Plus) }
                )
            }
        }
    }
}
