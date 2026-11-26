package com.roinur.caffeinetracker.core.ui.components.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Source-API adapter for Sauce's Text(..., cunnyExempt). Both private mode flags
 * are fixed false by Caffeine's call site; normal Sauce Text delegates to Material3. */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun SauceThemeText(text: String, style: TextStyle, fontWeight: FontWeight? = null,
    cunnyExempt: Boolean = false) {
    androidx.compose.material3.Text(text = text, style = style, fontWeight = fontWeight)
}
