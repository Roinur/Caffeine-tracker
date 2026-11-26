@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.roinur.caffeinetracker.core.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val pickerOptions = listOf(
    DrinkImage.MONSTER, DrinkImage.MONSTER_ULTRA,
    DrinkImage.NOCCO, DrinkImage.CELSIUS,
    DrinkImage.RED_BULL, DrinkImage.LATITUDE,
    DrinkImage.POWERKING, DrinkImage.ROCKSTAR,
    DrinkImage.REIGN, DrinkImage.PRIME,
    DrinkImage.ENERGY, DrinkImage.COCA_COLA,
    DrinkImage.PEPSI, DrinkImage.DRIP,
    DrinkImage.INSTANT, DrinkImage.LATTE,
    DrinkImage.ICED_LATTE, DrinkImage.ESPRESSO,
    DrinkImage.BLACK_TEA, DrinkImage.GREEN_TEA,
    DrinkImage.NONE
)
private val pickerTileShape = RoundedCornerShape(14.dp)

@Composable
fun DrinkImagePicker(name: String, category: String, imageKey: String?, onSelection: (String?) -> Unit) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var customKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    val selected = remember(name, category, imageKey) { drinkImage(name, category, imageKey) }
    val isCustomSelected = isCustomDrinkImageKey(imageKey)
    val subtitle = if (isCustomSelected) {
        strings.customImageLabel
    } else {
        selected.localizedLabel(language) + if (imageKey == null) strings.imageAutoSuffix else strings.imageCustomSuffix
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val savedKey = importCustomDrinkImage(context, uri)
            if (savedKey != null) {
                importError = null
                customKeys = withContext(Dispatchers.IO) { listCustomDrinkImageKeys(context) }
                onSelection(savedKey)
                expanded = false
            } else {
                importError = strings.customImageLoadError
            }
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            importError = null
            customKeys = withContext(Dispatchers.IO) { listCustomDrinkImageKeys(context) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.clip(pickerTileShape).clickable(onClickLabel = strings.chooseImage) { expanded = true }) {
                DrinkThumbnail(name = name, category = category, modifier = Modifier.size(48.dp), imageKey = imageKey)
            }
            Column(Modifier.weight(1f)) {
                Text(strings.imageIconLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { expanded = !expanded }) { Text(strings.chooseImage) }
        }
        if (expanded) {
            // Skip half-expanded state so vertical grid scrolling never fights sheet expansion.
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val gridState = rememberLazyGridState()
            val primary = MaterialTheme.colorScheme.primary
            val primaryContainer = MaterialTheme.colorScheme.primaryContainer
            val tileContainer = MaterialTheme.colorScheme.surfaceContainer
            val inactiveBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
            ModalBottomSheet(
                onDismissRequest = { expanded = false },
                sheetState = sheetState
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.chooseImage, style = MaterialTheme.typography.titleLarge)
                    Text(
                        strings.imageReuseHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = imageKey == null,
                            onClick = { onSelection(null); expanded = false },
                            label = { Text(strings.autoFromName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                        FilledTonalButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(strings.chooseCustomImage, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (importError != null) {
                        Text(
                            importError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(customKeys, key = { it }, contentType = { "custom_drink_image_tile" }) { key ->
                            val active = imageKey == key
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(pickerTileShape)
                                    .background(if (active) primaryContainer else tileContainer)
                                    .border(1.dp, if (active) primary else inactiveBorder, pickerTileShape)
                                    .selectable(
                                        selected = active,
                                        role = Role.RadioButton,
                                        onClick = { onSelection(key); expanded = false }
                                    )
                                    .semantics { contentDescription = strings.imageDescPrefix + strings.customImageLabel }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CustomDrinkThumbnail(imageKey = key, modifier = Modifier.size(48.dp))
                                Text(
                                    strings.customImageLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        items(pickerOptions, key = { it.name }, contentType = { "drink_image_tile" }) { option ->
                            val active = imageKey == option.name
                            val optionLabel = option.localizedLabel(language)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(pickerTileShape)
                                    .background(if (active) primaryContainer else tileContainer)
                                    .border(1.dp, if (active) primary else inactiveBorder, pickerTileShape)
                                    .selectable(
                                        selected = active,
                                        role = Role.RadioButton,
                                        onClick = { onSelection(option.name); expanded = false }
                                    )
                                    .semantics { contentDescription = strings.imageDescPrefix + optionLabel }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DrinkThumbnail(kind = option, modifier = Modifier.size(48.dp))
                                Text(
                                    optionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
