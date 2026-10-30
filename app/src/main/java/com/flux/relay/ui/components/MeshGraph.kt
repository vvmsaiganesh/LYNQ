package com.flux.relay.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.flux.relay.mesh.Topology
import com.flux.relay.transport.RadioState
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.RelayBlue
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The live mesh view: this device in the middle, neighbours on the first ring, and
 * everything they can reach on the second ring. Pulses travel along links in the
 * direction packets actually flow, which makes relaying legible at a glance.
 */
@Composable
fun MeshGraph(
    topology: Topology,
    radio: RadioState,
    modifier: Modifier = Modifier,
    tint: Color = MeshGreen,
) {
    val transition = rememberInfiniteTransition(label = "mesh")
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "flow",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
    val selfStyle = MaterialTheme.typography.labelSmall.copy(color = tint)

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val unit = minOf(size.width, size.height) / 2f
            val innerRadius = unit * 0.46f
            val outerRadius = unit * 0.82f

            drawRadar(center, unit, sweep, tint)

            val selfId = topology.selfId
            val hop1 = topology.nodes.filter { it.hops == 1 }
            val hop2 = topology.nodes.filter { it.hops == 2 }

            val positions = mutableMapOf<String, Offset>()
            if (selfId.isNotEmpty()) positions[selfId] = center
            hop1.forEachIndexed { index, node ->
                positions[node.id] = ringPoint(center, innerRadius, index, hop1.size)
            }
            hop2.forEachIndexed { index, node ->
                positions[node.id] = ringPoint(center, outerRadius, index, hop2.size)
            }

            // Links behind the nodes.
            topology.links.forEachIndexed { index, link ->
                val from = positions[link.from] ?: return@forEachIndexed
                val to = positions[link.to] ?: return@forEachIndexed
                val toDirect = topology.nodes.firstOrNull { it.id == link.to }?.hops == 1
                val base = if (toDirect) RelayBlue else TextMuted
                drawLine(
                    color = base.copy(alpha = if (toDirect) 0.45f else 0.22f),
                    start = from,
                    end = to,
                    strokeWidth = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 9f)),
                )
                if (radio != RadioState.OFF && radio != RadioState.UNAVAILABLE) {
                    val progress = ((flow + index * 0.19f) % 1f)
                    val point = lerp(from, to, progress)
                    val alpha = (sin(progress * PI).toFloat()).coerceIn(0f, 1f)
                    drawCircle(
                        color = tint.copy(alpha = 0.10f * alpha),
                        radius = 7.dp.toPx(),
                        center = point,
                    )
                    drawCircle(
                        color = tint.copy(alpha = 0.9f * alpha),
                        radius = 2.2.dp.toPx(),
                        center = point,
                    )
                }
            }

            // Nodes.
            topology.nodes.forEach { node ->
                val position = positions[node.id] ?: return@forEach
                val isSelf = node.id == topology.selfId
                val radius = when {
                    isSelf -> 9.dp.toPx()
                    node.hops == 1 -> 6.5.dp.toPx()
                    else -> 4.5.dp.toPx()
                }
                val color = when {
                    isSelf -> tint
                    node.hops == 1 -> RelayBlue
                    node.online -> TextSecondary
                    else -> TextMuted
                }
                val glow = if (isSelf) radius * 3.4f else radius * 2.6f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = if (isSelf) 0.30f else 0.20f), Color.Transparent),
                        center = position,
                        radius = glow,
                    ),
                    radius = glow,
                    center = position,
                )
                drawCircle(color = color, radius = radius, center = position)
                if (isSelf) {
                    drawCircle(color = Color.Black.copy(alpha = 0.55f), radius = radius * 0.42f, center = position)
                }

                val label = if (isSelf) "YOU" else node.label.take(9)
                val layout = measurer.measure(label, if (isSelf) selfStyle else labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        position.x - layout.size.width / 2f,
                        position.y + radius + 7.dp.toPx(),
                    ),
                )
            }

            if (hop1.isEmpty()) {
                val layout = measurer.measure(
                    if (radio == RadioState.SLEEPING) "LOW POWER · NODES ASLEEP" else "SEARCHING FOR NODES",
                    labelStyle.copy(color = TextMuted),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        center.x - layout.size.width / 2f,
                        center.y - layout.size.height - 26.dp.toPx(),
                    ),
                )
            }
        }
    }
}

private fun ringPoint(center: Offset, radius: Float, index: Int, count: Int): Offset {
    if (count <= 0) return center
    val angle = (-PI / 2) + (2 * PI * index / count)
    return Offset(
        x = center.x + (cos(angle) * radius).toFloat(),
        y = center.y + (sin(angle) * radius).toFloat(),
    )
}

private fun DrawScope.drawRadar(center: Offset, unit: Float, sweep: Float, tint: Color) {
    listOf(0.34f, 0.62f, 0.9f).forEach { factor ->
        drawCircle(
            color = tint.copy(alpha = 0.05f),
            radius = unit * factor,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }

    val angle = sweep * 360f
    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(Color.Transparent, tint.copy(alpha = 0.10f), Color.Transparent),
            center = center,
        ),
        startAngle = angle - 40f,
        sweepAngle = 40f,
        useCenter = true,
        topLeft = Offset(center.x - unit * 0.9f, center.y - unit * 0.9f),
        size = Size(unit * 1.8f, unit * 1.8f),
    )
}
