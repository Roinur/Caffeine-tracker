@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.roinur.caffeinetracker.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
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
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = remember(name, category, imageKey) { drinkImage(name, category, imageKey) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.clip(pickerTileShape).clickable(onClickLabel = strings.chooseImage) { expanded = true }) {
                DrinkThumbnail(kind = selected, modifier = Modifier.size(48.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(strings.imageIconLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(selected.localizedLabel(language) + if(imageKey == null) strings.imageAutoSuffix else strings.imageCustomSuffix,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { expanded = !expanded }) { Text(strings.chooseImage) }
        }
        if(expanded) ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(strings.chooseImage, style = MaterialTheme.typography.titleLarge)
                Text(strings.imageReuseHint,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(selected = imageKey == null, onClick = { onSelection(null); expanded = false },
                    label = { Text(strings.autoFromName) })
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pickerOptions, key = { it.name }, contentType = { "drink_image_tile" }) { option ->
                        val active = imageKey == option.name
                        val optionLabel = option.localizedLabel(language)
                        Surface(
                            shape = pickerTileShape,
                            color = if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().clip(pickerTileShape).selectable(
                                    selected = active,
                                    role = Role.RadioButton,
                                    onClick = { onSelection(option.name); expanded = false }
                                ).semantics { contentDescription = strings.imageDescPrefix + optionLabel }
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
