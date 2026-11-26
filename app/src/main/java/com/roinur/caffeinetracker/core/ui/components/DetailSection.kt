package com.roinur.caffeinetracker.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings

@Composable
fun DetailSection(title: String, summary: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val strings = LocalStrings.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val angle by animateFloatAsState(if(expanded) 90f else 0f, label = "disclosure")
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .semantics { stateDescription = if(expanded) strings.openState else strings.closedState }
            .clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if(summary != null) Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TrackerGlyph(TrackerIcon.Chevron, Modifier.graphicsLayer(rotationZ = angle))
        }
        AnimatedVisibility(expanded) { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}
