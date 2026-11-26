package com.roinur.caffeinetracker.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared action language for Settings only; selection chips and editor gestures stay intact. */
@Composable
internal fun SettingsActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    primary: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val palette = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (destructive) palette.error.copy(alpha = 0.5f) else palette.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (primary) palette.primaryContainer else palette.surfaceContainer,
            contentColor = when { destructive -> palette.error; primary -> palette.onPrimaryContainer; else -> palette.onSurface }
        ),
        content = content
    )
}
