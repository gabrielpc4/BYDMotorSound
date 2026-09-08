package com.gabrielpc.enginesoundsimulator

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielpc.enginesoundsimulator.drive.RpmGainCurvePoint
import com.gabrielpc.enginesoundsimulator.drive.RpmGainCurveEvaluator
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioGain
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioGainResolver
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioSettings
import com.gabrielpc.enginesoundsimulator.ui.theme.AccentSoft
import com.gabrielpc.enginesoundsimulator.ui.theme.LocalDashboardSkin
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun RpmGainCurveChart(
    curvePoints: List<RpmGainCurvePoint>,
    speedGainCoefficient: Float,
    liveRpm: Double,
    liveSpeedKmh: Double,
    onPointsChange: (List<RpmGainCurvePoint>) -> Unit,
    onRestoreDefaults: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val normalizedPoints = remember(curvePoints) {
        SpeedAudioGain.normalizeCurvePoints(curvePoints)
    }
    val evaluator = remember(normalizedPoints) {
        RpmGainCurveEvaluator.fromPoints(normalizedPoints)
    }
    val curveSamples = remember(normalizedPoints) {
        sampleCurve(normalizedPoints, evaluator)
    }
    var draggedPointIndex by remember { mutableIntStateOf(-1) }
    val activeDraggedPointIndex = draggedPointIndex
    val skin = LocalDashboardSkin.current
    val panelShape = skin.panelShape
    val liveCombinedGain = remember(normalizedPoints, speedGainCoefficient, liveRpm, liveSpeedKmh) {
        SpeedAudioGainResolver.combinedGainOffset(
            rpm = liveRpm,
            speedKmh = liveSpeedKmh,
            settings = SpeedAudioSettings(
                curvePoints = normalizedPoints,
                speedGainCoefficient = speedGainCoefficient,
            ),
        )
    }
    val liveRpmMarker = liveRpm.coerceIn(0.0, SpeedAudioGain.RPM_MAX.toDouble())
    val description = remember(normalizedPoints, liveCombinedGain) {
        normalizedPoints.joinToString(". ") { point ->
            "${point.rpm} RPM at ${SpeedAudioGain.formatGainOffset(point.gainOffset)}"
        }
    }

    Column(
        modifier = modifier
            .background(skin.chartSurface, panelShape)
            .border(1.dp, skin.chartOutline, panelShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "RPM GAIN CURVE",
                color = skin.chartTitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = SpeedAudioGain.formatGainOffset(liveCombinedGain),
                    color = skin.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
                OutlinedButton(onClick = onRestoreDefaults) {
                    Text("RESTORE", color = AccentSoft, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }

        Text(
            text = "Drag the five points. Endpoints stay at 0 and max RPM.",
            color = skin.chartFooter,
            fontSize = 10.sp,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .semantics { contentDescription = description }
                .pointerInput(normalizedPoints) {
                    val leftPx = 42.dp.toPx()
                    val topPx = 12.dp.toPx()
                    val bottomPx = 28.dp.toPx()
                    val rightPx = 12.dp.toPx()
                    val plotWidth = (size.width - leftPx - rightPx).coerceAtLeast(1f)
                    val plotHeight = (size.height - topPx - bottomPx).coerceAtLeast(1f)
                    val hitRadiusPx = 20.dp.toPx()
                    val rpmMax = SpeedAudioGain.RPM_MAX.toFloat()
                    val gainMin = SpeedAudioGain.GAIN_MIN
                    val gainMax = SpeedAudioGain.GAIN_MAX
                    val gainSpan = gainMax - gainMin

                    fun rpmAtX(x: Float): Int {
                        val normalizedX = ((x - leftPx) / plotWidth).coerceIn(0f, 1f)
                        return SpeedAudioGain.normalizeRpm((normalizedX * rpmMax).roundToInt())
                    }

                    fun gainAtY(y: Float): Float {
                        val normalizedY = (1f - ((y - topPx) / plotHeight)).coerceIn(0f, 1f)
                        return SpeedAudioGain.normalizeGain(gainMin + normalizedY * gainSpan)
                    }

                    fun pointCenter(index: Int): Offset {
                        val point = normalizedPoints[index]
                        val x = leftPx + plotWidth * (point.rpm / rpmMax)
                        val y = topPx + plotHeight * (1f - ((point.gainOffset - gainMin) / gainSpan))
                        return Offset(x, y)
                    }

                    fun pointIndexAt(position: Offset): Int {
                        var closestIndex = -1
                        var closestDistance = Float.MAX_VALUE
                        normalizedPoints.forEachIndexed { index, _ ->
                            val center = pointCenter(index)
                            val distance = abs(position.x - center.x) + abs(position.y - center.y)
                            if (distance <= hitRadiusPx && distance < closestDistance) {
                                closestDistance = distance
                                closestIndex = index
                            }
                        }
                        return closestIndex
                    }

                    detectDragGestures(
                        onDragStart = { offset ->
                            draggedPointIndex = pointIndexAt(offset)
                        },
                        onDrag = { change, _ ->
                            val index = draggedPointIndex
                            if (index in normalizedPoints.indices) {
                                val updated = normalizedPoints.toMutableList()
                                val rpm = when (index) {
                                    0 -> SpeedAudioGain.RPM_MIN
                                    normalizedPoints.lastIndex -> SpeedAudioGain.RPM_MAX
                                    else -> rpmAtX(change.position.x)
                                }
                                updated[index] = RpmGainCurvePoint(
                                    rpm = rpm,
                                    gainOffset = gainAtY(change.position.y),
                                )
                                onPointsChange(SpeedAudioGain.normalizeCurvePoints(updated))
                            }
                        },
                        onDragEnd = {
                            draggedPointIndex = -1
                        },
                        onDragCancel = {
                            draggedPointIndex = -1
                        },
                    )
                }
                .drawWithCache {
                    val leftPx = 42.dp.toPx()
                    val topPx = 12.dp.toPx()
                    val bottomPx = 28.dp.toPx()
                    val rightPx = 12.dp.toPx()
                    val plotWidth = (size.width - leftPx - rightPx).coerceAtLeast(1f)
                    val plotHeight = (size.height - topPx - bottomPx).coerceAtLeast(1f)
                    val rpmMax = SpeedAudioGain.RPM_MAX.toFloat()
                    val gainMin = SpeedAudioGain.GAIN_MIN
                    val gainMax = SpeedAudioGain.GAIN_MAX
                    val gainSpan = gainMax - gainMin
                    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 10.sp.toPx()
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                        color = skin.chartLabel.toArgb()
                    }
                    val labelPaint = Paint(axisPaint)
                    val pointColor = skin.accentSoft
                    val activePointColor = skin.warning
                    val curvePath = Path().apply {
                        curveSamples.forEachIndexed { index, sample ->
                            val x = leftPx + plotWidth * (sample.rpm / rpmMax)
                            val y = topPx + plotHeight * (1f - ((sample.gain - gainMin) / gainSpan))
                            if (index == 0) {
                                moveTo(x, y)
                            } else {
                                lineTo(x, y)
                            }
                        }
                    }

                    onDrawBehind {
                        val gridGainValues = floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f)
                        gridGainValues.forEach { gain ->
                            val y = topPx + plotHeight * (1f - ((gain - gainMin) / gainSpan))
                            drawLine(
                                skin.chartAxis.copy(alpha = 0.18f),
                                Offset(leftPx, y),
                                Offset(leftPx + plotWidth, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        val gridRpmValues = intArrayOf(0, 3_000, 6_000, 9_000, 12_000)
                        gridRpmValues.forEach { rpm ->
                            val x = leftPx + plotWidth * (rpm / rpmMax)
                            drawLine(
                                skin.chartAxis.copy(alpha = 0.18f),
                                Offset(x, topPx),
                                Offset(x, topPx + plotHeight),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        drawLine(
                            skin.chartAxis,
                            Offset(leftPx, topPx + plotHeight),
                            Offset(leftPx + plotWidth, topPx + plotHeight),
                            strokeWidth = 1.dp.toPx(),
                        )
                        drawLine(
                            skin.chartAxis,
                            Offset(leftPx, topPx),
                            Offset(leftPx, topPx + plotHeight),
                            strokeWidth = 1.dp.toPx(),
                        )

                        drawPath(
                            path = curvePath,
                            color = skin.warning.copy(alpha = 0.85f),
                            style = Stroke(width = 2.5.dp.toPx()),
                        )

                        val liveMarkerX = leftPx + plotWidth * (liveRpmMarker / rpmMax).toFloat()
                        drawLine(
                            color = Color.White.copy(alpha = 0.85f),
                            start = Offset(liveMarkerX, topPx),
                            end = Offset(liveMarkerX, topPx + plotHeight),
                            strokeWidth = 2.dp.toPx(),
                        )

                        normalizedPoints.forEachIndexed { index, point ->
                            val x = leftPx + plotWidth * (point.rpm / rpmMax)
                            val y = topPx + plotHeight * (1f - ((point.gainOffset - gainMin) / gainSpan))
                            val active = index == activeDraggedPointIndex
                            val radius = if (active) {
                                8.dp.toPx()
                            } else {
                                6.dp.toPx()
                            }
                            drawCircle(
                                color = if (active) {
                                    activePointColor
                                } else {
                                    pointColor
                                },
                                radius = radius,
                                center = Offset(x, y),
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.9f),
                                radius = radius * 0.35f,
                                center = Offset(x, y),
                            )
                        }

                        val canvas = drawContext.canvas.nativeCanvas
                        gridGainValues.forEach { gain ->
                            val label = SpeedAudioGain.formatGainOffset(gain)
                            val y = topPx + plotHeight * (1f - ((gain - gainMin) / gainSpan))
                            canvas.drawText(label, 0f, y - 2.dp.toPx(), labelPaint)
                        }
                        gridRpmValues.forEach { rpm ->
                            val label = if (rpm >= 1_000) {
                                "${rpm / 1_000}k"
                            } else {
                                "0"
                            }
                            val x = leftPx + plotWidth * (rpm / rpmMax)
                            canvas.drawText(
                                label,
                                x - labelPaint.measureText(label) / 2f,
                                topPx + plotHeight + 16.dp.toPx(),
                                labelPaint,
                            )
                        }

                        if (activeDraggedPointIndex in normalizedPoints.indices) {
                            val point = normalizedPoints[activeDraggedPointIndex]
                            val detail = "${String.format(java.util.Locale.US, "%,d", point.rpm)} RPM  " +
                                SpeedAudioGain.formatGainOffset(point.gainOffset)
                            canvas.drawText(
                                detail,
                                leftPx,
                                topPx - 2.dp.toPx(),
                                axisPaint.apply { textSize = 11.sp.toPx() },
                            )
                        }
                    }
                },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GAIN", color = skin.chartFooter, fontSize = 9.sp)
            Text("RPM", color = skin.chartFooter, fontSize = 9.sp)
        }
    }
}

private data class CurveSample(
    val rpm: Float,
    val gain: Float,
)

private fun sampleCurve(
    points: List<RpmGainCurvePoint>,
    evaluator: RpmGainCurveEvaluator,
    sampleCount: Int = 48,
): List<CurveSample> {
    val startRpm = points.first().rpm.toDouble()
    val endRpm = points.last().rpm.toDouble()
    val span = (endRpm - startRpm).coerceAtLeast(1.0)

    return List(sampleCount) { index ->
        val rpm = startRpm + span * index / (sampleCount - 1).coerceAtLeast(1)
        CurveSample(
            rpm = rpm.toFloat(),
            gain = evaluator.eval(rpm),
        )
    }
}
