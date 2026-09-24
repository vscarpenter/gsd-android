package dev.vinny.gsd.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

/**
 * A section's caption, set in capitals in `ink3`. TalkBack announces it as a heading, so a reader can
 * move from section to section, and reads the [label] as written, since a short word in capitals can
 * be spelled out letter by letter (review, Accessibility: the app had no headings).
 */
@Composable
fun SectionHeading(label: String, modifier: Modifier = Modifier) {
    Text(
        label.uppercase(),
        style = GsdType.caption,
        color = LocalGsdColors.current.ink3,
        modifier = modifier.clearAndSetSemantics {
            heading()
            text = AnnotatedString(label)
        },
    )
}
