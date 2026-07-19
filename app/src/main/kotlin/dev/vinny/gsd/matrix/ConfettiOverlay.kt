package dev.vinny.gsd.matrix

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.theme.LocalGsdColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The one celebration (spec §5.8): 160 particles, 1.6 s, gravity 700,
 * launch 150–460 upward-biased, size 5–10dp, fade 1→0. Colors are the four
 * quadrant pigments + success green — never generic rainbow. Pure math here;
 * rendering reads it so the parameters stay JVM-testable.
 */
object ConfettiSpec {
    const val COUNT = 160
    const val DURATION_MS = 1600
    const val GRAVITY = 700f
    const val UPWARD_BIAS = -220f

    data class Particle(
        val xNorm: Float,
        val speed: Float,
        val angleRad: Float,
        val sizeDp: Float,
        val colorIndex: Int,
    )

    fun particles(random: Random): List<Particle> = List(COUNT) {
        Particle(
            xNorm = 0.3f + random.nextFloat() * 0.4f,
            speed = 150f + random.nextFloat() * 310f,
            angleRad = (random.nextFloat() * 2f * PI).toFloat(),
            sizeDp = 5f + random.nextFloat() * 5f,
            colorIndex = random.nextInt(5),
        )
    }
}

/** Increments of [trigger] replay the celebration; skipped when animators are off. */
@Composable
fun ConfettiOverlay(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0 || !ValueAnimator.areAnimatorsEnabled()) return
    val gsd = LocalGsdColors.current
    val palette = listOf(gsd.rust, gsd.tide, gsd.ochre, gsd.slate, gsd.success)
    val particles = remember(trigger) { ConfettiSpec.particles(Random(trigger)) }
    var progress by remember(trigger) { mutableFloatStateOf(0f) }
    LaunchedEffect(trigger) {
        val startedAt = withFrameNanos { it }
        while (progress < 1f) {
            val now = withFrameNanos { it }
            progress = ((now - startedAt) / 1_000_000f / ConfettiSpec.DURATION_MS).coerceAtMost(1f)
        }
    }
    if (progress >= 1f) return
    val density = LocalDensity.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress * (ConfettiSpec.DURATION_MS / 1000f)
        particles.forEach { p ->
            val vx = p.speed * cos(p.angleRad)
            val vy = p.speed * sin(p.angleRad) + ConfettiSpec.UPWARD_BIAS
            val px = size.width * p.xNorm + with(density) { (vx * t).dp.toPx() }
            val py = size.height * 0.4f +
                with(density) { (vy * t + 0.5f * ConfettiSpec.GRAVITY * t * t).dp.toPx() }
            drawCircle(
                color = palette[p.colorIndex].copy(alpha = 1f - progress),
                radius = with(density) { (p.sizeDp / 2f).dp.toPx() },
                center = Offset(px, py),
            )
        }
    }
}
