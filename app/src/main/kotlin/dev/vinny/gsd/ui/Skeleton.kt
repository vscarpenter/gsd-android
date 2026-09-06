package dev.vinny.gsd.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.LocalGsdColors

/** A placeholder block in the real layout's silhouette: sunken fill, the given corner shape (S§4.2). */
@Composable
fun SkeletonBlock(height: Dp, width: Dp? = null, shape: Shape = GsdShapes.small, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    Box(
        modifier = modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .clip(shape)
            .background(gsd.sunken),
    )
}

/**
 * One light band sweeping the whole composition, masked to its own shapes so it never
 * streaks across gaps. Static when animators are disabled (S§4.2). Apply once, to the
 * outermost skeleton container.
 */
fun Modifier.skeletonShimmer(): Modifier = composed {
    if (!ValueAnimator.areAnimatorsEnabled()) return@composed this
    val band = LocalGsdColors.current.surface.copy(alpha = 0.75f)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = -0.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val bandWidth = size.width * 0.5f
            val start = progress * size.width
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.5f to band, 1f to Color.Transparent,
                    startX = start, endX = start + bandWidth,
                ),
                blendMode = BlendMode.SrcIn,
            )
        }
}

/** Collapse a whole skeleton into one "Loading" node for TalkBack. */
fun Modifier.loadingSemantics(label: String): Modifier = clearAndSetSemantics { contentDescription = label }
