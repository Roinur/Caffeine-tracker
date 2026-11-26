package com.roinur.caffeinetracker.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import com.roinur.caffeinetracker.core.ui.i18n.AppStrings
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings

internal enum class SettingsCategory(val path: String) {
    MODEL("M4,13h4v8H4z M10,3h4v18h-4z M16,8h4v13h-4z"),
    APPEARANCE("M12,3a9,9 0,1 0,0 18h1a2,2 0,0 0,1.4 -3.4c-.5,-.5 -.1,-1.6 .7,-1.6H17a4,4 0,0 0,4 -4c0,-5 -4,-9 -9,-9z M7,8h2v2H7z M11,6h2v2h-2z M15,8h2v2h-2z M6,12h2v2H6z"),
    BACKUP("M12,3a9,9 0,1 1,-8 5H1l4,-5 4,5H6a7,7 0,1 0,6 -3z M11,8h2v5l4,2 -1,2 -5,-3z"),
    ABOUT("M12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20z M11,6h2v2h-2z M11,10h2v7h-2z");

    fun title(strings: AppStrings): String = when (this) {
        MODEL -> strings.catModelTitle
        APPEARANCE -> strings.catAppearanceTitle
        BACKUP -> strings.catBackupTitle
        ABOUT -> strings.catAboutTitle
    }

    fun summary(strings: AppStrings): String = when (this) {
        MODEL -> strings.catModelSummary
        APPEARANCE -> strings.catAppearanceSummary
        BACKUP -> strings.catBackupSummary
        ABOUT -> strings.catAboutSummary
    }
}

private fun settingsVector(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()
private val backIcon = settingsVector("SettingsBack", "M20,11H7l5,-5 -1.5,-1.5L3,12l7.5,7.5L12,18l-5,-5h13z")
private val searchIcon = settingsVector("SettingsSearch", "M10,3a7,7 0,1 0,0 14a7,7 0,0 0,0 -14z M10,5a5,5 0,1 1,0 10a5,5 0,0 1,0 -10z M15,14l7,7 -1.5,1.5 -7,-7z")
private val categoryIcons = SettingsCategory.entries.associateWith { settingsVector(it.name, it.path) }

internal fun settingsSearchMatches(query: String, text: String): Boolean {
    val haystack = text.lowercase()
    return query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank).all { it in haystack }
}

@Composable
internal fun SettingsSearchBar(query: String, onQuery: (String) -> Unit, onBack: () -> Unit) {
    val strings = LocalStrings.current
    // The editor owns its immediate value/cursor. Rebuilding Dashboard's result list
    // must not round-trip every key through a slower parent composition.
    var editor by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(query)) }
    var published by remember { mutableStateOf(query) }
    val publish by rememberUpdatedState(onQuery)
    LaunchedEffect(query) {
        if (query != published) {
            published = query
            editor = TextFieldValue(query, TextRange(query.length))
        }
    }
    LaunchedEffect(editor.text) {
        if (editor.text != published) {
            delay(150)
            published = editor.text
            publish(editor.text)
        }
    }
    fun clear() { editor = TextFieldValue(""); published = ""; onQuery("") }
    OutlinedTextField(value = editor, onValueChange = { editor = it }, singleLine = true,
        placeholder = { Text(strings.searchSettings) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp),
        leadingIcon = { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (editor.text.isNotBlank()) clear() else onBack() }) { Icon(backIcon, strings.back) }
            Icon(searchIcon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        trailingIcon = if (editor.text.isNotBlank()) ({ TextButton(onClick = { clear() }) { Text(strings.clear) } }) else null)
}

@Composable
internal fun SettingsCategoryRow(category: SettingsCategory, onClick: () -> Unit) {
    val strings = LocalStrings.current
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(categoryIcons.getValue(category), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(category.title(strings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(category.summary(strings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SettingsOptionSection(category: SettingsCategory, title: String, terms: String,
    active: String?, query: String, content: @Composable ColumnScope.() -> Unit) {
    val strings = LocalStrings.current
    val catTitle = category.title(strings)
    val catSummary = category.summary(strings)
    val visible = if (query.isNotBlank()) settingsSearchMatches(query, "$catTitle $catSummary $title $terms") else active == category.name
    if (!visible) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(categoryIcons.getValue(category), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (query.isNotBlank()) "$catTitle · $title" else title,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}
