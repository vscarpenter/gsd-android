package dev.vinny.gsd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import dev.vinny.gsd.theme.LocalGsdColors

/**
 * A text field's decoration box: its [hint], while the field is [empty], under the field's text.
 * The field merges what its decoration box holds, so TalkBack reads the hint as the field's name,
 * the way Material's own fields name themselves. Beside the field, the hint was read as loose text
 * and the field as an unnamed edit box (review, Accessibility).
 */
@Composable
fun HintedField(hint: String, empty: Boolean, style: TextStyle, innerTextField: @Composable () -> Unit) {
    Box {
        if (empty) Text(hint, style = style, color = LocalGsdColors.current.ink3)
        innerTextField()
    }
}
