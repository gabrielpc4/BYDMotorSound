package com.gabrielpc.enginesoundsimulator

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioGain
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioGainResolver
import com.gabrielpc.enginesoundsimulator.ui.theme.LocalDashboardSkin
import kotlin.math.roundToInt

@Composable
internal fun SpeedGainCurveChart(
    speedGainCoefficient: Float,
    liveSpeedKmh: Double,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val normalizedCoefficient = remember(speedGainCoefficient) {
        SpeedAudioGain.normalizeSpeedCoefficient(speedGainCoefficient)
    }
    val samples = remember(normalizedCoefficient) {
        sampleSpeedGainCurve(normalizedCoefficient)
    }
    val gainAxisMax = remember(normalizedCoefficient) {
        (normalizedCoefficient * 1.15f).coerceAtLeast(0.25f)
            .coerceAtMost(SpeedAudioGain.SPEED_COEFFICIENT_MAX)
    }
    val skin = LocalDashboardSkin.current
    val panelShape = skin.panelShape
    val referenceKmh = SpeedAudioGain.SPEED_REFERENCE_KMH
    val chartMaxKmh = (referenceKmh * 1.15).roundToInt().toFloat()
    val liveSpeedMarker = liveSpeedKmh.coerceAtLeast(0.0)
    val description = remember(normalizedCoefficient, liveSpeedMarker) {
        "Speed gain from 0 to ${referenceKmh.toInt()} km/h at " +
            SpeedAudioGain.formatSpeedCoefficient(normalizedCoefficient)
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
                text = "SPEED GAIN PREVIEW",
                color = skin.chartTitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "0 … ${SpeedAudioGain.formatGainOffset(gainAxisMax)}",
                color = skin.onSurface,
                fontSize = 11.sp,
            )
        }

        Text(
            text = "Linear bonus from standstill to ${referenceKmh.toInt()} km/h. Above that, gain stays at the maximum.",
            color = skin.chartFooter,
            fontSize = 10.sp,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = description }
                .drawWithCache {
                    val leftPx = 42.dp.toPx()
                    val topPx = 12.dp.toPx()
                    val bottomPx = 28.dp.toPx()
                    val rightPx = 12.dp.toPx()
                    val plotWidth = (size.width - leftPx - rightPx).coerceAtLeast(1f)
                    val plotHeight = (size.height - topPx - bottomPx).coerceAtLeast(1f)
                    val gainMin = 0f
                    val gainMax = gainAxisMax
                    val gainSpan = (gainMax - gainMin).coerceAtLeast(0.01f)
                    val referenceLineX = leftPx + plotWidth * (referenceKmh / chartMaxKmh).toFloat()
                    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 10.sp.toPx()
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                        color = skin.chartLabel.toArgb()
                    }
                    val labelPaint = Paint(axisPaint)
                    val curvePath = Path().apply {
                        samples.forEachIndexed { index, sample ->
                            val x = leftPx + plotWidth * (sample.speedKmh / chartMaxKmh)
                            val y = topPx + plotHeight * (1f - ((sample.gainBonus - gainMin) / gainSpan))
                            if (index == 0) {
                                moveTo(x, y)
                            } else {
                                lineTo(x, y)
                            }
                        }
                    }

                    onDrawBehind {
                        val gridGainValues = buildGainGridValues(gainMax)
                        gridGainValues.forEach { gain ->
                            val y = topPx + plotHeight * (1f - ((gain - gainMin) / gainSpan))
                            drawLine(
                                skin.chartAxis.copy(alpha = 0.18f),
                                Offset(leftPx, y),
                                Offset(leftPx + plotWidth, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        val gridSpeedValues = intArrayOf(0, 50, 100, referenceKmh.roundToInt(), chartMaxKmh.roundToInt())
                        gridSpeedValues.forEach { speedKmh ->
                            val x = leftPx + plotWidth * (speedKmh / chartMaxKmh)
                            drawLine(
                                skin.chartAxis.copy(alpha = 0.18f),
                                Offset(x, topPx),
                                Offset(x, topPx + plotHeight),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        drawLine(
                            skin.chartAxis.copy(alpha = 0.35f),
                            Offset(referenceLineX, topPx),
                            Offset(referenceLineX, topPx + plotHeight),
                            strokeWidth = 1.dp.toPx(),
                        )

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
                            color = skin.accentSoft.copy(alpha = 0.9f),
                            style = Stroke(width = 2.5.dp.toPx()),
                        )

                        val liveMarkerX = leftPx + plotWidth * (liveSpeedMarker / chartMaxKmh).toFloat().coerceIn(0f, 1f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.85f),
                            start = Offset(liveMarkerX, topPx),
                            end = Offset(liveMarkerX, topPx + plotHeight),
                            strokeWidth = 2.dp.toPx(),
                        )

                        val canvas = drawContext.canvas.nativeCanvas
                        gridGainValues.forEach { gain ->
                            val label = SpeedAudioGain.formatGainOffset(gain)
                            val y = topPx + plotHeight * (1f - ((gain - gainMin) / gainSpan))
                            canvas.drawText(label, 0f, y - 2.dp.toPx(), labelPaint)
                        }
                        gridSpeedValues.forEach { speedKmh ->
                            val label = if (speedKmh >= 1_000) {
                                "${speedKmh / 1_000}k"
                            } else {
                                "$speedKmh"
                            }
                            val x = leftPx + plotWidth * (speedKmh / chartMaxKmh)
                            canvas.drawText(
                                label,
                                x - labelPaint.measureText(label) / 2f,
                                topPx + plotHeight + 16.dp.toPx(),
                                labelPaint,
                            )
                        }
                    }
                },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GAIN", color = skin.chartFooter, fontSize = 9.sp)
            Text("SPEED (km/h)", color = skin.chartFooter, fontSize = 9.sp)
        }
    }
}

private data class SpeedGainSample(
    val speedKmh: Float,
    val gainBonus: Float,
)

private fun sampleSpeedGainCurve(
    coefficient: Float,
    sampleCount: Int = 32,
): List<SpeedGainSample> {
    val chartMaxKmh = (SpeedAudioGain.SPEED_REFERENCE_KMH * 1.15).toFloat()

    return List(sampleCount) { index ->
        val speedKmh = chartMaxKmh * index / (sampleCount - 1).coerceAtLeast(1)
        SpeedGainSample(
            speedKmh = speedKmh,
            gainBonus = SpeedAudioGainResolver.speedGainBonus(
                speedKmh = speedKmh.toDouble(),
                coefficient = coefficient,
            ),
        )
    }
}

private fun buildGainGridValues(gainMax: Float): FloatArray {
    if (gainMax <= 0.25f) {
        return floatArrayOf(0f, gainMax)
    }

    if (gainMax <= 0.75f) {
        return floatArrayOf(0f, gainMax / 2f, gainMax)
    }

    return floatArrayOf(0f, gainMax / 2f, gainMax)
}
