package com.roinur.caffeinetracker.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.ui.theme.animatePaletteColorAsState

/** Reuses Material's editor, outline cutout, label layout and focus interactions.
 * Material 1.2 animates theme colors in its default container/label. Resolve those
 * paints separately so a palette swap cannot trail the rest of the screen.
 * The editor/decoration are deliberately NOT keyed by the palette: focus,
 * selection, composing text and label position survive theme changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaletteOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animatePaletteColorAsState(
        targetValue = when {
            !enabled -> colors.disabledIndicatorColor
            isError -> colors.errorIndicatorColor
            focused -> colors.focusedIndicatorColor
            else -> colors.unfocusedIndicatorColor
        },
        animationSpec = tween(150), label = "outlinedFieldBorder"
    )
    val labelColor by animatePaletteColorAsState(
        targetValue = when {
            !enabled -> colors.disabledLabelColor
            isError -> colors.errorLabelColor
            focused -> colors.focusedLabelColor
            else -> colors.unfocusedLabelColor
        },
        animationSpec = tween(150), label = "outlinedFieldLabel"
    )
    val borderWidth by animateDpAsState(
        if (enabled && focused) OutlinedTextFieldDefaults.FocusedBorderThickness
        else OutlinedTextFieldDefaults.UnfocusedBorderThickness,
        animationSpec = tween(150), label = "outlinedFieldBorderWidth"
    )
    val containerColor = when {
        !enabled -> colors.disabledContainerColor
        isError -> colors.errorContainerColor
        focused -> colors.focusedContainerColor
        else -> colors.unfocusedContainerColor
    }
    val textColor = when {
        !enabled -> colors.disabledTextColor
        isError -> colors.errorTextColor
        focused -> colors.focusedTextColor
        else -> colors.unfocusedTextColor
    }
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = modifier
                .then(if (label != null) Modifier.semantics(mergeDescendants = true) {}.padding(top = 8.dp) else Modifier)
                .semantics { if (isError) error("Invalid input") }
                .defaultMinSize(OutlinedTextFieldDefaults.MinWidth, OutlinedTextFieldDefaults.MinHeight),
            enabled = enabled, readOnly = readOnly,
            textStyle = textStyle.merge(TextStyle(color = if (textStyle.color.isSpecified) textStyle.color else textColor)),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            visualTransformation = visualTransformation, interactionSource = interactionSource,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            decorationBox = { editor ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value, innerTextField = editor, enabled = enabled, singleLine = singleLine,
                    visualTransformation = visualTransformation, interactionSource = interactionSource,
                    isError = isError,
                    label = label?.let { content ->
                        {
                            // Override only the paint; Material still animates the floating label position/size.
                            CompositionLocalProvider(LocalContentColor provides labelColor) {
                                ProvideTextStyle(LocalTextStyle.current.copy(color = labelColor)) { content() }
                            }
                        }
                    },
                    placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    prefix = prefix, suffix = suffix, supportingText = supportingText, colors = colors,
                    container = {
                        // DecorationBox applies Material's label notch to this existing outline.
                        Box(Modifier.background(containerColor, shape).border(borderWidth, borderColor, shape))
                    }
                )
            }
        )
    }
}
