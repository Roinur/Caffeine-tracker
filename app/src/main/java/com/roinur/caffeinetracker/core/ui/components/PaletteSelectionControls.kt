package com.roinur.caffeinetracker.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.roinur.caffeinetracker.core.ui.theme.currentPaletteIdentity

// Material 1.2 also animates checkbox/radio paints on palette changes. Restart
// only these small stateless visuals; their selected value and interaction source
// remain owned by the caller, with no settings/library reload.
@Composable
internal fun PaletteCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) = key(currentPaletteIdentity()) {
    Checkbox(checked, onCheckedChange, modifier, enabled, colors, interactionSource)
}

@Composable
internal fun PaletteRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) = key(currentPaletteIdentity()) {
    RadioButton(selected, onClick, modifier, enabled, colors, interactionSource)
}
