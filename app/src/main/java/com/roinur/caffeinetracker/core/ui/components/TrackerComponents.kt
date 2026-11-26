package com.roinur.caffeinetracker.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.model.AppLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class TrackerIcon(val svg: String) {
    Coffee("M2,3h16v3h2a3,3 0,0 1,0 6h-2v2a5,5 0,0 1,-5 5H7a5,5 0,0 1,-5 -5zM18,8v2h2a1,1 0,0 0,0 -2zM2,21h18v-2H2z"),
    Home("M3,3h8v8H3zM13,3h8v5h-8zM13,10h8v11h-8zM3,13h8v8H3z"),
    History("M13,3a9,9 0,1 1,-8.4 12.2l1.9,-.7A7,7 0,1 0,7 7H10v2H3V2h2v3.8A9,9 0,0 1,13 3zM12,7h2v5.2l3.5,2.1l-1,1.7L12,13z"),
    Drinks("M6,2h12v2H6zM5,5h2v15h10V5h2v17H5zM10,7h5l-3,5h3l-6,7l2,-6H8z"),
    Chart("M3,3h2v16h16v2H3zM7,11h3v6H7zM12,6h3v11h-3zM17,9h3v8h-3z"),
    Settings("M10,2h4l.6,3a8,8 0,0 1,1.5 .9l2.9,-1l2,3.4l-2.3,2a8,8 0,0 1,0 1.7l2.3,2l-2,3.4l-2.9,-1a8,8 0,0 1,-1.5 .9L14,21h-4l-.6,-3.1a8,8 0,0 1,-1.5 -.9l-2.9,1l-2,-3.4l2.3,-2a8,8 0,0 1,0 -1.7L3,8.9l2,-3.4l2.9,1a8,8 0,0 1,1.5 -.9zM12,8a4,4 0,1 0,0 8a4,4 0,0 0,0 -8z"),
    Plus("M11,4h2v7h7v2h-7v7h-2v-7H4v-2h7z"),
    Edit("M3,17.3V21h3.7L17.8,9.9l-3.7,-3.7zM20.7,7a1,1 0,0 0,0 -1.4L18.4,3.3a1,1 0,0 0,-1.4 0L15.2,5.1l3.7,3.7z"),
    Delete("M6,7h12l-1,14H7zM9,2h6l1,2h5v2H3V4h5z"),
    Import("M11,2h2v10l3.5,-3.5L18,10l-6,6l-6,-6l1.5,-1.5L11,12zM4,17h2v3h12v-3h2v5H4z"),
    Export("M11,16h2V6l3.5,3.5L18,8l-6,-6l-6,6l1.5,1.5L11,6zM4,17h2v3h12v-3h2v5H4z"),
    Moon("M20.5,14a9,9 0,0 1,-10.5 -10.5A9,9 0,1 0,20.5 14z"),
    Favorite("M12,21l-1.45,-1.32C5.4,15 2,11.9 2,8.1A5.4,5.4 0,0 1,7.5 2.6A6,6 0,0 1,12 4.7A6,6 0,0 1,16.5 2.6A5.4,5.4 0,0 1,22 8.1c0,3.8 -3.4,6.9 -8.55,11.6z"),
    Close("M6,4.6L12,10.6l6,-6L19.4,6l-6,6l6,6L18,19.4l-6,-6l-6,6L4.6,18l6,-6l-6,-6z"),
    Check("M9,16.2L4.8,12l-1.4,1.4L9,19L21,7l-1.4,-1.4z"),
    Folder("M2,4h8l2,2h10v14H2z"),
    Info("M11,10h2v7h-2zM11,6h2v2h-2zM12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20zM12,4a8,8 0,1 1,0 16a8,8 0,0 1,0 -16z"),
    Chevron("M9,5l7,7l-7,7l-1.4,-1.4L13.2,12L7.6,6.4z"),
    Calendar("M5,2h2v2h10V2h2v2h3v18H2V4h3zM4,9v11h16V9zM6,12h4v3H6z"),
    Clock("M12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20zM12,4a8,8 0,1 1,0 16a8,8 0,0 1,0 -16zM11,6h2v6l4,2l-1,1.7L11,13z"),
    Search("M10,2a8,8 0,1 0,5 14.2L20.8,22L22,20.8l-5.8,-5.8A8,8 0,0 0,10 2zM10,4a6,6 0,1 1,0 12a6,6 0,0 1,0 -12z"),
    Reorder("M4,7h16v2H4zM4,15h16v2H4z"),
    More("M12,3a2,2 0,1 0,0 4a2,2 0,0 0,0 -4zM12,10a2,2 0,1 0,0 4a2,2 0,0 0,0 -4zM12,17a2,2 0,1 0,0 4a2,2 0,0 0,0 -4z"),
    Back("M20,11H7.8l5.6,-5.6L12,4l-8,8l8,8l1.4,-1.4L7.8,13H20z")
}

private val vectors = TrackerIcon.entries.associateWith {
    if(it == TrackerIcon.Drinks) ImageVector.Builder("DrinkCan", 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString("M8,2.5h8 M8,5h8a2,2 0,0 1,2 2v12a2,2 0,0 1,-2 2H8a2,2 0,0 1,-2 -2V7a2,2 0,0 1,2 -2z M8,7.5h8 M8,18.5h8").toNodes(),
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round)
        .addPath(PathParser().parsePathString("M12.8,9L9.5,13.4h2.6l-.8,3.6l3.5,-5h-2.7z").toNodes(), fill = SolidColor(Color.Black)).build()
    else ImageVector.Builder(it.name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(it.svg).toNodes(), fill = SolidColor(Color.Black)).build()
}

@Composable
fun TrackerGlyph(icon: TrackerIcon, modifier: Modifier = Modifier, description: String? = null, tint: Color = LocalContentColor.current) {
    Icon(vectors.getValue(icon), description, modifier.size(24.dp), tint)
}

@Composable
fun TrackerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
fun EmptyState(title: String, detail: String, icon: TrackerIcon = TrackerIcon.Coffee, action: (@Composable () -> Unit)? = null) {
    TrackerCard {
        Box(Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), RoundedCornerShape(16.dp)).padding(14.dp)) {
            TrackerGlyph(icon, tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

private val svDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", AppLanguage.SV.locale)
private val enDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", AppLanguage.EN.locale)
private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun mg(value: Double): String {
    val rounded = value.roundToInt()
    return (if (rounded == 0) 0 else rounded).toString() + " mg"
}
fun decimal(value: Double, language: AppLanguage = AppLanguage.SV): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else if (language == AppLanguage.SV) value.toString().replace(".", ",")
    else value.toString()
fun clock(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(clockFormatter)
fun dateLabel(timestamp: Long, language: AppLanguage = AppLanguage.SV): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(if (language == AppLanguage.EN) enDateFormatter else svDateFormatter)
fun dayOf(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
