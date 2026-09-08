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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielpc.enginesoundsimulator.drive.VirtualGearSpeedBoundaries
import com.gabrielpc.enginesoundsimulator.ui.theme.AccentSoft
import com.gabrielpc.enginesoundsimulator.ui.theme.LocalDashboardSkin
import kotlin.math.abs

@Composable
internal fun VirtualGearDistributionChart(
    gearCount: Int,
    boundariesKmh: List<Int>,
    onBoundaryChange: ((boundaryIndex: Int, speedKmh: Int) -> Unit)? = null,
    onRestoreDefaults: (() -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val normalizedBoundaries = remember(gearCount, boundariesKmh) {
        VirtualGearSpeedBoundaries.normalizeBoundaries(boundariesKmh, gearCount)
    }
    val boundaryLabels = remember(normalizedBoundaries) {
        normalizedBoundaries.map { speed -> speed.toString() }
    }
    val ranges = remember(normalizedBoundaries) {
        boundaryLabels.zipWithNext { low, high -> "$low – $high" }
    }
    val axisHeight = with(LocalDensity.current) { 70.sp.toDp() + 16.dp }
    val editable = onBoundaryChange != null
    val description = remember(gearCount, ranges) {
        ranges.mapIndexed { index, range -> "Gear ${index + 1}: $range km/h" }
            .joinToString(". ")
    }
    var draggedBoundaryIndex by remember(gearCount) { mutableIntStateOf(-1) }
    val activeDraggedBoundaryIndex = draggedBoundaryIndex
    val skin = LocalDashboardSkin.current
    val panelShape = skin.panelShape
    val labelColor = skin.chartLabel
    val axisColor = skin.chartAxis
    val gearLabelColor = skin.onSurface
    val baselineColor = skin.chartAxis
    val barTrackColor = skin.onSurface.copy(alpha = 0.025f)
    val hueStart = skin.chartHueStart
    val topSpeed = VirtualGearSpeedBoundaries.TOP_SPEED_KMH.toFloat()

    Column(
        modifier = modifier
            .background(skin.chartSurface, panelShape)
            .border(1.dp, skin.chartOutline, panelShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "$gearCount GEARS",
                color = skin.chartTitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("0–$topSpeed km/h", color = skin.onSurface, fontSize = 11.sp)
                if (onRestoreDefaults != null) {
                    OutlinedButton(onClick = onRestoreDefaults) {
                        Text("RESTORE", color = AccentSoft, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        }

        if (editable) {
            Text(
                text = "Drag the vertical dividers. Speed snaps to 10 km/h steps.",
                color = skin.chartFooter,
                fontSize = 10.sp,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height((gearCount * 34).dp + axisHeight)
                .semantics { contentDescription = description }
                .then(
                    if (editable) {
                        Modifier.pointerInput(normalizedBoundaries, gearCount) {
                            val leftPx = 30.dp.toPx()
                            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = 10.sp.toPx()
                            }
                            val rangeWidth = ranges.maxOf { labelPaint.measureText(it) } + 14.dp.toPx()
                            val plotWidth = (size.width - leftPx - rangeWidth).coerceAtLeast(1f)
                            val hitRadiusPx = 18.dp.toPx()

                            fun speedAtX(x: Float): Int {
                                val normalizedX = ((x - leftPx) / plotWidth).coerceIn(0f, 1f)
                                return VirtualGearSpeedBoundaries.snapSpeedKmh((normalizedX * topSpeed).toDouble())
                            }

                            fun boundaryIndexAtX(x: Float): Int {
                                var closestIndex = -1
                                var closestDistance = Float.MAX_VALUE
                                for (index in 1 until gearCount) {
                                    val boundaryX = leftPx + plotWidth * (normalizedBoundaries[index] / topSpeed)
                                    val distance = abs(x - boundaryX)
                                    if (distance <= hitRadiusPx && distance < closestDistance) {
                                        closestDistance = distance
                                        closestIndex = index
                                    }
                                }
                                return closestIndex
                            }

                            detectDragGestures(
                                onDragStart = { offset ->
                                    draggedBoundaryIndex = boundaryIndexAtX(offset.x)
                                },
                                onDrag = { change, _ ->
                                    val index = draggedBoundaryIndex
                                    if (index in 1 until gearCount) {
                                        onBoundaryChange?.invoke(index, speedAtX(change.position.x))
                                    }
                                },
                                onDragEnd = {
                                    draggedBoundaryIndex = -1
                                },
                                onDragCancel = {
                                    draggedBoundaryIndex = -1
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .drawWithCache {
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 10.sp.toPx()
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                        color = labelColor.toArgb()
                    }
                    val axisPaint = Paint(labelPaint).apply {
                        textSize = 12.sp.toPx()
                    }
                    val gearPaint = Paint(labelPaint).apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        color = gearLabelColor.toArgb()
                    }
                    val rowHeight = 34.dp.toPx()
                    val barHeight = 18.dp.toPx()
                    val left = 30.dp.toPx()
                    val rangeWidth = ranges.maxOf { labelPaint.measureText(it) } + 14.dp.toPx()
                    val plotWidth = (size.width - left - rangeWidth).coerceAtLeast(1f)
                    val plotBottom = gearCount * rowHeight
                    val colors = List(gearCount) { index ->
                        Color.hsv((hueStart + index * 10f) % 360f, 0.62f, 0.95f)
                    }
                    val fills = colors.map { color ->
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.45f), color),
                            startX = left,
                            endX = left + plotWidth,
                        )
                    }

                    onDrawBehind {
                        drawLine(
                            baselineColor,
                            Offset(left, plotBottom),
                            Offset(left + plotWidth, plotBottom),
                            strokeWidth = 1.dp.toPx(),
                        )
                        normalizedBoundaries.forEachIndexed { index, speed ->
                            val x = left + plotWidth * (speed / topSpeed)
                            val color = if (index == 0) {
                                axisColor
                            } else {
                                colors[index - 1]
                            }
                            val dividerAlpha = if (index in 1 until gearCount && index == activeDraggedBoundaryIndex) {
                                0.85f
                            } else {
                                0.22f
                            }
                            val dividerWidth = if (index == activeDraggedBoundaryIndex) {
                                2.5.dp.toPx()
                            } else {
                                1.dp.toPx()
                            }
                            drawLine(
                                color.copy(alpha = dividerAlpha),
                                Offset(x, 0f),
                                Offset(x, plotBottom),
                                strokeWidth = dividerWidth,
                            )
                            drawLine(
                                color,
                                Offset(x, plotBottom),
                                Offset(x, plotBottom + 5.dp.toPx()),
                                strokeWidth = 1.dp.toPx(),
                            )
                            val label = boundaryLabels[index]
                            axisPaint.color = color.toArgb()
                            val canvas = drawContext.canvas.nativeCanvas
                            canvas.save()
                            canvas.translate(x, plotBottom + 10.dp.toPx())
                            canvas.rotate(-90f)
                            canvas.drawText(
                                label,
                                -axisPaint.measureText(label),
                                -(axisPaint.ascent() + axisPaint.descent()) / 2,
                                axisPaint,
                            )
                            canvas.restore()
                        }
                        ranges.forEachIndexed { index, range ->
                            val centerY = index * rowHeight + rowHeight / 2
                            val baseline = centerY - (labelPaint.ascent() + labelPaint.descent()) / 2
                            val startX = left + plotWidth * (normalizedBoundaries[index] / topSpeed)
                            val endX = left + plotWidth * (normalizedBoundaries[index + 1] / topSpeed)
                            drawRoundRect(
                                barTrackColor,
                                Offset(left, centerY - barHeight / 2),
                                Size(plotWidth, barHeight),
                                CornerRadius(4.dp.toPx()),
                            )
                            drawRoundRect(
                                fills[index],
                                Offset(startX, centerY - barHeight / 2),
                                Size(endX - startX, barHeight),
                                CornerRadius(4.dp.toPx()),
                            )
                            drawLine(
                                colors[index],
                                Offset(endX, centerY - barHeight / 2),
                                Offset(endX, centerY + barHeight / 2),
                                strokeWidth = 2.dp.toPx(),
                            )
                            drawContext.canvas.nativeCanvas.drawText("${index + 1}", 0f, baseline, gearPaint)
                            drawContext.canvas.nativeCanvas.drawText(
                                range,
                                left + plotWidth + 14.dp.toPx(),
                                baseline,
                                labelPaint,
                            )
                        }
                    }
                },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GEAR", color = skin.chartFooter, fontSize = 9.sp)
            Text("ROAD SPEED / km/h", color = skin.chartFooter, fontSize = 9.sp)
        }
    }
}
