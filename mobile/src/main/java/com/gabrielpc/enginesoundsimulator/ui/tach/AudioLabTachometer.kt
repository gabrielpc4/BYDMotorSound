package com.gabrielpc.enginesoundsimulator.ui.tach

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielpc.enginesoundsimulator.rememberRedlineShakeMotion
import com.gabrielpc.enginesoundsimulator.redlineShakeIntensity
import com.gabrielpc.enginesoundsimulator.simulation.AutomaticTransmissionMode
import com.gabrielpc.enginesoundsimulator.simulation.DrivetrainState
import com.gabrielpc.enginesoundsimulator.simulation.ShiftLightThresholds
import com.gabrielpc.enginesoundsimulator.simulation.TransmissionPosition
import com.gabrielpc.enginesoundsimulator.ui.theme.AutomaticTransmissionModeCaption
import com.gabrielpc.enginesoundsimulator.ui.theme.CONDENSED_FONT_FAMILY_NAME
import com.gabrielpc.enginesoundsimulator.ui.theme.LocalDashboardSkin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Tachometer ported from the Assetto Corsa Audio Lab desktop tool.
 *
 * Everything is authored in the reference SVG's 600x520 coordinate space (see [AudioLabDial]) and
 * multiplied by a single scale factor, so the dial keeps the reference proportions at any size
 * instead of drifting as the dashboard is resized.
 */
@Composable
fun AudioLabTachometer(
    drivetrain: DrivetrainState,
    transmissionPosition: TransmissionPosition,
    manualShiftModeEnabled: Boolean,
    cruisingLogicEnabled: Boolean,
    cruisingShiftRangeOverlayEnabled: Boolean,
    maxRpm: Double,
    redlineRpm: Double,
    modifier: Modifier = Modifier,
) {
    val shakeIntensity = redlineShakeIntensity(
        rpm = drivetrain.rpm,
        redlineRpm = redlineRpm,
        maxRpm = maxRpm,
        limiterActive = drivetrain.limiterActive,
    )
    val redlineShake = rememberRedlineShakeMotion(shakeIntensity)
    val showAutomaticTransmissionMode = transmissionPosition == TransmissionPosition.DRIVE &&
        !manualShiftModeEnabled
    val tachMaxRpm = maxRpm.coerceAtLeast(1_000.0)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // One scale factor drives the whole port, so the dial cannot drift out of proportion.
        val scale = AudioLabScale(
            dpPerUnit = min(
                maxWidth.value / AudioLabDial.VIEWBOX_WIDTH,
                maxHeight.value / (
                    AudioLabDial.SHIFT_ARRAY_HEIGHT +
                        AudioLabDial.VIEWBOX_HEIGHT +
                        DigitalBarStyle.HEIGHT
                    ),
            ),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ShiftLightArray(
                rpm = drivetrain.rpm,
                thresholds = drivetrain.shiftLightsRpm,
                blinkRpm = ShiftLightThresholds.blinkRpm(drivetrain.limiterRpm),
                scale = scale,
                modifier = Modifier.width(scale.dp(AudioLabDial.VIEWBOX_WIDTH)),
            )

            Box(
                modifier = Modifier.size(
                    width = scale.dp(AudioLabDial.VIEWBOX_WIDTH),
                    height = scale.dp(AudioLabDial.VIEWBOX_HEIGHT),
                ),
            ) {
                AudioLabDialCanvas(
                    drivetrain = drivetrain,
                    tachMaxRpm = tachMaxRpm,
                    limiterRpm = redlineRpm,
                    needleAngleJitterDegrees = redlineShake.needleTipAngleJitterDegrees,
                    showCruisingShiftRange = showAutomaticTransmissionMode &&
                        cruisingShiftRangeOverlayEnabled &&
                        cruisingLogicEnabled &&
                        drivetrain.automaticTransmissionMode == AutomaticTransmissionMode.CRUISING &&
                        drivetrain.gear > 1,
                )
            }

            DigitalReadoutBar(
                drivetrain = drivetrain,
                transmissionPosition = transmissionPosition,
                manualShiftModeEnabled = manualShiftModeEnabled,
                showAutomaticTransmissionMode = showAutomaticTransmissionMode,
                scale = scale,
            )
        }
    }
}

/**
 * Reference geometry, straight from the Audio Lab SVG and stylesheet.
 *
 * Keeping the raw numbers together makes it obvious when a value is a faithful port rather than a
 * tuning choice, and lets the drawing code read like the original markup.
 */
private object AudioLabDial {
    const val VIEWBOX_WIDTH = 600f
    const val VIEWBOX_HEIGHT = 520f

    /**
     * Exactly the lamp row plus its own padding, with no slack.
     *
     * The reference tool reserves more room here because its lamps sit under a page header; on the
     * dashboard the row is the topmost element, so the leftover space just read as a dead gap.
     */
    const val SHIFT_ARRAY_HEIGHT = ShiftLightStyle.ROW_TOP_PADDING +
        ShiftLightStyle.HEIGHT +
        ShiftLightStyle.ROW_BOTTOM_PADDING

    const val CENTER_X = 300f
    const val CENTER_Y = 278f

    /** The sweep runs from -130 degrees to +130 degrees, with zero pointing straight up. */
    const val SWEEP_START_DEGREES = -130f
    const val SWEEP_SPAN_DEGREES = 260f

    const val BEZEL_RADIUS = 238f
    const val BEZEL_STROKE = 3f
    const val FACE_RADIUS = 222f
    const val FACE_STROKE = 2f

    const val REDLINE_ARC_RADIUS = 216f
    const val REDLINE_ARC_STROKE = 7f

    /** Cruising shift band sits just inside the redline arc so the two never overlap. */
    const val SHIFT_RANGE_ARC_RADIUS = 204f
    const val SHIFT_RANGE_ARC_STROKE = 6f

    const val TICK_STEP_RPM = 100
    const val TICK_OUTER_RADIUS = 213f
    const val TICK_MAJOR_INNER_RADIUS = 187f
    const val TICK_MEDIUM_INNER_RADIUS = 195f
    const val TICK_MINOR_INNER_RADIUS = 202f
    const val TICK_MAJOR_STROKE = 4f
    const val TICK_MEDIUM_STROKE = 2f
    const val TICK_MINOR_STROKE = 1.3f
    const val TICK_MAJOR_ALPHA = 0.96f
    const val TICK_MEDIUM_ALPHA = 0.78f
    const val TICK_MINOR_ALPHA = 0.65f

    const val LABEL_RADIUS = 161f
    const val LABEL_BASELINE_OFFSET = 10f
    const val LABEL_SIZE = 30f

    const val CAPTION_Y = 345f
    const val CAPTION_SIZE = 13f
    const val CAPTION_LETTER_SPACING_EM = 0.18f
    const val CAPTION_TEXT = "RPM × 1000"

    const val HUB_OUTER_RADIUS = 28f
    const val HUB_OUTER_STROKE = 3f
    const val HUB_INNER_RADIUS = 13f
    const val HUB_INNER_STROKE = 2f

    const val NEEDLE_SHADOW_OFFSET_X = 4f
    const val NEEDLE_SHADOW_OFFSET_Y = 5f

    val bezelFill = Color(0xFF070A0C)
    val bezelStroke = Color(0xFF35404A)
    val faceFill = Color(0xFF0A0E11)
    val faceStroke = Color(0xFF151C21)
    val tickMinor = Color(0xFF7D888F)
    val tickMedium = Color(0xFFC1C9CD)
    val tickMajor = Color(0xFFFFFFFF)
    val label = Color(0xFFE9EDEF)
    val needleStroke = Color(0xFFFF756D)
    val needleShadow = Color(0xB3000000)
    val hubOuterFill = Color(0xFF181E23)
    val hubOuterStroke = Color(0xFF4E5B64)
    val hubInnerStroke = Color(0xFFFF6B63)

    /** `M 291 288 L 301 82 L 309 288 Z` from the reference needle path. */
    fun needlePath(): Path {
        return Path().apply {
            moveTo(291f, 288f)
            lineTo(301f, 82f)
            lineTo(309f, 288f)
            close()
        }
    }

    /** `M 288 288 L 301 76 L 312 288 Z`, the slightly wider drop shadow behind the needle. */
    fun needleShadowPath(): Path {
        return Path().apply {
            moveTo(288f, 288f)
            lineTo(301f, 76f)
            lineTo(312f, 288f)
            close()
        }
    }

    fun angleFor(rpm: Double, tachMaxRpm: Double): Float {
        val fraction = (rpm / tachMaxRpm).coerceIn(0.0, 1.0).toFloat()
        return SWEEP_START_DEGREES + fraction * SWEEP_SPAN_DEGREES
    }

    /** The reference `polarPoint`: zero degrees points up and angles grow clockwise. */
    fun polar(radius: Float, angleDegrees: Float): Offset {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return Offset(
            CENTER_X + radius * sin(radians).toFloat(),
            CENTER_Y - radius * cos(radians).toFloat(),
        )
    }
}

@Composable
private fun AudioLabDialCanvas(
    drivetrain: DrivetrainState,
    tachMaxRpm: Double,
    limiterRpm: Double,
    needleAngleJitterDegrees: Float,
    showCruisingShiftRange: Boolean,
) {
    // Canvas draw lambdas are not composable, so the skin colors are resolved up front.
    val skin = LocalDashboardSkin.current
    val accent = skin.accent
    val accentHot = skin.accentHot
    val success = skin.success
    val captionColor = skin.muted
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(CONDENSED_FONT_FAMILY_NAME, Typeface.BOLD)
        }
    }
    val captionPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(CONDENSED_FONT_FAMILY_NAME, Typeface.BOLD)
            letterSpacing = AudioLabDial.CAPTION_LETTER_SPACING_EM
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val unit = size.width / AudioLabDial.VIEWBOX_WIDTH
        val center = scaled(AudioLabDial.CENTER_X, AudioLabDial.CENTER_Y, unit)

        drawCircle(AudioLabDial.bezelFill, AudioLabDial.BEZEL_RADIUS * unit, center)
        drawCircle(
            color = AudioLabDial.bezelStroke,
            radius = AudioLabDial.BEZEL_RADIUS * unit,
            center = center,
            style = Stroke(AudioLabDial.BEZEL_STROKE * unit),
        )
        drawCircle(AudioLabDial.faceFill, AudioLabDial.FACE_RADIUS * unit, center)
        drawCircle(
            color = AudioLabDial.faceStroke,
            radius = AudioLabDial.FACE_RADIUS * unit,
            center = center,
            style = Stroke(AudioLabDial.FACE_STROKE * unit),
        )

        drawDialArc(
            radius = AudioLabDial.REDLINE_ARC_RADIUS,
            strokeWidth = AudioLabDial.REDLINE_ARC_STROKE,
            color = accent,
            fromRpm = limiterRpm,
            toRpm = tachMaxRpm,
            tachMaxRpm = tachMaxRpm,
            unit = unit,
            cap = StrokeCap.Square,
        )

        if (showCruisingShiftRange) {
            drawDialArc(
                radius = AudioLabDial.SHIFT_RANGE_ARC_RADIUS,
                strokeWidth = AudioLabDial.SHIFT_RANGE_ARC_STROKE,
                color = success.copy(alpha = 0.62f),
                fromRpm = drivetrain.effectiveAutomaticDownshiftRpm,
                toRpm = drivetrain.effectiveAutomaticUpshiftRpm,
                tachMaxRpm = tachMaxRpm,
                unit = unit,
                cap = StrokeCap.Butt,
            )
        }

        drawTicks(
            tachMaxRpm = tachMaxRpm,
            limiterRpm = limiterRpm,
            redlineColor = accentHot,
            unit = unit,
        )

        drawIntoCanvas { canvas ->
            labelPaint.textSize = AudioLabDial.LABEL_SIZE * unit
            var rpm = 0
            while (rpm <= tachMaxRpm.roundToInt()) {
                val point = AudioLabDial.polar(
                    AudioLabDial.LABEL_RADIUS,
                    AudioLabDial.angleFor(rpm.toDouble(), tachMaxRpm),
                )
                labelPaint.color = if (rpm >= limiterRpm) {
                    accentHot.toArgb()
                } else {
                    AudioLabDial.label.toArgb()
                }
                canvas.nativeCanvas.drawText(
                    (rpm / 1_000).toString(),
                    point.x * unit,
                    (point.y + AudioLabDial.LABEL_BASELINE_OFFSET) * unit,
                    labelPaint,
                )
                rpm += 1_000
            }

            captionPaint.textSize = AudioLabDial.CAPTION_SIZE * unit
            captionPaint.color = captionColor.toArgb()
            canvas.nativeCanvas.drawText(
                AudioLabDial.CAPTION_TEXT,
                AudioLabDial.CENTER_X * unit,
                AudioLabDial.CAPTION_Y * unit,
                captionPaint,
            )
        }

        drawNeedle(
            angleDegrees = AudioLabDial.angleFor(drivetrain.rpm, tachMaxRpm) + needleAngleJitterDegrees,
            bodyColor = accentHot,
            hubInnerFill = accent,
            unit = unit,
        )
    }
}

private fun DrawScope.drawTicks(
    tachMaxRpm: Double,
    limiterRpm: Double,
    redlineColor: Color,
    unit: Float,
) {
    val maxTickRpm = tachMaxRpm.roundToInt()
    var rpm = 0
    while (rpm <= maxTickRpm) {
        val major = rpm % 1_000 == 0
        val medium = !major && rpm % 500 == 0
        val innerRadius = when {
            major -> AudioLabDial.TICK_MAJOR_INNER_RADIUS
            medium -> AudioLabDial.TICK_MEDIUM_INNER_RADIUS
            else -> AudioLabDial.TICK_MINOR_INNER_RADIUS
        }
        val baseColor = when {
            major -> AudioLabDial.tickMajor
            medium -> AudioLabDial.tickMedium
            else -> AudioLabDial.tickMinor
        }
        val alpha = when {
            major -> AudioLabDial.TICK_MAJOR_ALPHA
            medium -> AudioLabDial.TICK_MEDIUM_ALPHA
            else -> AudioLabDial.TICK_MINOR_ALPHA
        }
        val strokeWidth = when {
            major -> AudioLabDial.TICK_MAJOR_STROKE
            medium -> AudioLabDial.TICK_MEDIUM_STROKE
            else -> AudioLabDial.TICK_MINOR_STROKE
        }
        val angle = AudioLabDial.angleFor(rpm.toDouble(), tachMaxRpm)
        val outer = AudioLabDial.polar(AudioLabDial.TICK_OUTER_RADIUS, angle)
        val inner = AudioLabDial.polar(innerRadius, angle)

        drawLine(
            color = if (rpm >= limiterRpm) redlineColor else baseColor,
            start = Offset(inner.x * unit, inner.y * unit),
            end = Offset(outer.x * unit, outer.y * unit),
            strokeWidth = strokeWidth * unit,
            alpha = alpha,
        )
        rpm += AudioLabDial.TICK_STEP_RPM
    }
}

private fun DrawScope.drawDialArc(
    radius: Float,
    strokeWidth: Float,
    color: Color,
    fromRpm: Double,
    toRpm: Double,
    tachMaxRpm: Double,
    unit: Float,
    cap: StrokeCap,
) {
    if (fromRpm <= 0.0 || toRpm <= fromRpm) {
        return
    }

    // Compose measures arcs from three o'clock; the reference measures from twelve.
    val startAngle = AudioLabDial.angleFor(fromRpm, tachMaxRpm) - 90f
    val sweepAngle = AudioLabDial.angleFor(toRpm, tachMaxRpm) -
        AudioLabDial.angleFor(fromRpm, tachMaxRpm)
    if (sweepAngle <= 0f) {
        return
    }

    val scaledRadius = radius * unit
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(
            AudioLabDial.CENTER_X * unit - scaledRadius,
            AudioLabDial.CENTER_Y * unit - scaledRadius,
        ),
        size = Size(scaledRadius * 2f, scaledRadius * 2f),
        style = Stroke(strokeWidth * unit, cap = cap),
    )
}

private fun DrawScope.drawNeedle(
    angleDegrees: Float,
    bodyColor: Color,
    hubInnerFill: Color,
    unit: Float,
) {
    val pivot = scaled(AudioLabDial.CENTER_X, AudioLabDial.CENTER_Y, unit)

    rotate(degrees = angleDegrees, pivot = pivot) {
        // The needle paths keep the reference's literal coordinates, drawn under a uniform scale.
        scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
            translate(
                left = AudioLabDial.NEEDLE_SHADOW_OFFSET_X,
                top = AudioLabDial.NEEDLE_SHADOW_OFFSET_Y,
            ) {
                drawPath(AudioLabDial.needleShadowPath(), AudioLabDial.needleShadow)
            }
            drawPath(AudioLabDial.needlePath(), bodyColor)
            drawPath(
                path = AudioLabDial.needlePath(),
                color = AudioLabDial.needleStroke,
                style = Stroke(1f),
            )
        }
    }

    drawCircle(AudioLabDial.hubOuterFill, AudioLabDial.HUB_OUTER_RADIUS * unit, pivot)
    drawCircle(
        color = AudioLabDial.hubOuterStroke,
        radius = AudioLabDial.HUB_OUTER_RADIUS * unit,
        center = pivot,
        style = Stroke(AudioLabDial.HUB_OUTER_STROKE * unit),
    )
    drawCircle(hubInnerFill, AudioLabDial.HUB_INNER_RADIUS * unit, pivot)
    drawCircle(
        color = AudioLabDial.hubInnerStroke,
        radius = AudioLabDial.HUB_INNER_RADIUS * unit,
        center = pivot,
        style = Stroke(AudioLabDial.HUB_INNER_STROKE * unit),
    )
}

private fun DrawScope.scaled(x: Float, y: Float, unit: Float): Offset {
    return Offset(x * unit, y * unit)
}

private object ShiftLightStyle {
    const val WIDTH = 40f
    const val HEIGHT = 14f
    const val GAP = 16f
    const val OUTER_RADIUS = 9f
    const val LAMP_RADIUS = 6f
    const val LAMP_INSET = 3f
    const val ROW_TOP_PADDING = 4f
    const val ROW_BOTTOM_PADDING = 6f

    val border = Color(0xFF2D383F)
    val well = Color(0xFF0A0E11)
    val rowDivider = Color(0xFF151D23)

    /** Unlit lamps keep a dark tint of the color they will turn, matching the reference. */
    val offTints = listOf(
        Color(0xFF313924),
        Color(0xFF313924),
        Color(0xFF3D2D18),
        Color(0xFF3D2D18),
        Color(0xFF3B1819),
    )
}

@Composable
private fun ShiftLightArray(
    rpm: Double,
    thresholds: List<Double>,
    blinkRpm: Double,
    scale: AudioLabScale,
    modifier: Modifier = Modifier,
) {
    if (thresholds.isEmpty()) {
        return
    }

    val skin = LocalDashboardSkin.current
    val litTints = remember(skin) {
        listOf(skin.yellow, skin.yellow, skin.warning, skin.warning, skin.accentHot)
    }
    val blinkVisible = rememberShiftLightBlink(active = rpm >= blinkRpm && blinkRpm > 0.0)

    Row(
        modifier = modifier
            .height(scale.dp(AudioLabDial.SHIFT_ARRAY_HEIGHT))
            .padding(
                PaddingValues(
                    top = scale.dp(ShiftLightStyle.ROW_TOP_PADDING),
                    bottom = scale.dp(ShiftLightStyle.ROW_BOTTOM_PADDING),
                ),
            ),
        horizontalArrangement = Arrangement.spacedBy(
            space = scale.dp(ShiftLightStyle.GAP),
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        thresholds.forEachIndexed { index, threshold ->
            val lit = rpm >= threshold && blinkVisible
            val tintIndex = index.coerceAtMost(ShiftLightStyle.offTints.lastIndex)

            Box(
                modifier = Modifier
                    .size(
                        width = scale.dp(ShiftLightStyle.WIDTH),
                        height = scale.dp(ShiftLightStyle.HEIGHT),
                    )
                    .background(
                        color = ShiftLightStyle.well,
                        shape = RoundedCornerShape(scale.dp(ShiftLightStyle.OUTER_RADIUS)),
                    )
                    .border(
                        width = scale.dp(1f),
                        color = ShiftLightStyle.border,
                        shape = RoundedCornerShape(scale.dp(ShiftLightStyle.OUTER_RADIUS)),
                    )
                    .padding(scale.dp(ShiftLightStyle.LAMP_INSET)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = if (lit) {
                                litTints[tintIndex]
                            } else {
                                ShiftLightStyle.offTints[tintIndex]
                            },
                            shape = RoundedCornerShape(scale.dp(ShiftLightStyle.LAMP_RADIUS)),
                        ),
                )
            }
        }
    }
}

/**
 * Square wave at the reference blink rate.
 *
 * The lights are only animated while the engine is above the blink threshold, so the frame loop
 * stays idle for the rest of the rev range.
 */
@Composable
private fun rememberShiftLightBlink(active: Boolean): Boolean {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(active) {
        if (!active) {
            visible = true
            return@LaunchedEffect
        }

        val periodMillis = (1_000.0 / ShiftLightThresholds.BLINK_HZ).toLong()
        while (true) {
            withFrameMillis { frameTimeMillis ->
                visible = frameTimeMillis % periodMillis < periodMillis / 2
            }
        }
    }

    return if (active) {
        visible
    } else {
        true
    }
}

private object DigitalBarStyle {
    const val WIDTH_FRACTION = 0.642f
    const val HEIGHT = 75f
    const val GEAR_COLUMN_WIDTH = 78f
    const val COLUMN_PADDING_HORIZONTAL = 11f
    const val COLUMN_PADDING_VERTICAL = 8f
    const val GEAR_COLUMN_PADDING_HORIZONTAL = 7f

    const val LABEL_SIZE = 8.5f
    const val GEAR_SIZE = 40f
    const val SPEED_SIZE = 26f
    const val SPEED_UNIT_SIZE = 8f
    const val PHASE_SIZE = 9f
    const val RPM_SIZE = 19f
    const val RPM_UNIT_SIZE = 9f

    /**
     * Line heights from the reference stylesheet.
     *
     * They matter: with Compose's default leading the three stacked lines in the speed column
     * overflow the 75-unit bar and the mode caption gets clipped.
     */
    const val LABEL_LINE_HEIGHT = LABEL_SIZE
    const val GEAR_LINE_HEIGHT = GEAR_SIZE * 0.95f
    const val SPEED_LINE_HEIGHT = SPEED_SIZE * 1.1f
    const val PHASE_LINE_HEIGHT = PHASE_SIZE * 1.15f
    const val RPM_LINE_HEIGHT = RPM_SIZE

    val background = Color(0xFF070A0C)
    val divider = Color(0xFF263139)
    val gearColumnBackground = Color(0xFF11171C)
    val phaseDivider = Color(0xFF213039)
}

/**
 * The bar under the dial. Gear, speed and RPM live here rather than on the dial face, which is
 * where the reference tool puts them.
 */
@Composable
private fun DigitalReadoutBar(
    drivetrain: DrivetrainState,
    transmissionPosition: TransmissionPosition,
    manualShiftModeEnabled: Boolean,
    showAutomaticTransmissionMode: Boolean,
    scale: AudioLabScale,
    modifier: Modifier = Modifier,
) {
    val skin = LocalDashboardSkin.current
    val gearLabel = if (transmissionPosition == TransmissionPosition.DRIVE) {
        drivetrain.gear.toString()
    } else {
        transmissionPosition.displayName
    }

    Row(
        modifier = modifier
            .width(scale.dp(AudioLabDial.VIEWBOX_WIDTH * DigitalBarStyle.WIDTH_FRACTION))
            .height(scale.dp(DigitalBarStyle.HEIGHT))
            .background(DigitalBarStyle.background)
            .border(scale.dp(1f), DigitalBarStyle.divider),
    ) {
        DigitalColumn(
            scale = scale,
            horizontalAlignment = Alignment.CenterHorizontally,
            horizontalPadding = DigitalBarStyle.GEAR_COLUMN_PADDING_HORIZONTAL,
            modifier = Modifier
                .width(scale.dp(DigitalBarStyle.GEAR_COLUMN_WIDTH))
                .background(DigitalBarStyle.gearColumnBackground)
                .rightDivider(scale),
        ) {
            DriveLabel("Gear", scale)
            Text(
                text = gearLabel,
                color = if (drivetrain.isShifting) skin.warning else skin.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = scale.sp(DigitalBarStyle.GEAR_SIZE),
                lineHeight = scale.sp(DigitalBarStyle.GEAR_LINE_HEIGHT),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = scale.sp(DigitalBarStyle.GEAR_SIZE * -0.1f),
                maxLines = 1,
            )
        }

        DigitalColumn(
            scale = scale,
            horizontalAlignment = Alignment.Start,
            horizontalPadding = DigitalBarStyle.COLUMN_PADDING_HORIZONTAL,
            modifier = Modifier
                .weight(1f)
                .rightDivider(scale),
        ) {
            DriveLabel("Vehicle speed", scale)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = drivetrain.realOrDocumentedRawSpeedKmh.roundToInt().toString(),
                    color = skin.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = scale.sp(DigitalBarStyle.SPEED_SIZE),
                    lineHeight = scale.sp(DigitalBarStyle.SPEED_LINE_HEIGHT),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = "km/h",
                    color = skin.muted,
                    fontSize = scale.sp(DigitalBarStyle.SPEED_UNIT_SIZE),
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = scale.dp(5f)),
                )
            }
            ShiftPhaseCaption(
                drivetrain = drivetrain,
                manualShiftModeEnabled = manualShiftModeEnabled,
                showAutomaticTransmissionMode = showAutomaticTransmissionMode,
                scale = scale,
            )
        }

        DigitalColumn(
            scale = scale,
            horizontalAlignment = Alignment.End,
            horizontalPadding = DigitalBarStyle.COLUMN_PADDING_HORIZONTAL,
            modifier = Modifier.weight(1f),
        ) {
            DriveLabel("Engine", scale)
            Text(
                text = drivetrain.rpm.roundToInt().toString(),
                color = skin.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = scale.sp(DigitalBarStyle.RPM_SIZE),
                lineHeight = scale.sp(DigitalBarStyle.RPM_LINE_HEIGHT),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "RPM",
                color = skin.muted,
                fontSize = scale.sp(DigitalBarStyle.RPM_UNIT_SIZE),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = scale.sp(DigitalBarStyle.RPM_UNIT_SIZE * 0.1f),
            )
        }
    }
}

@Composable
private fun DigitalColumn(
    scale: AudioLabScale,
    horizontalAlignment: Alignment.Horizontal,
    horizontalPadding: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = scale.dp(horizontalPadding),
                vertical = scale.dp(DigitalBarStyle.COLUMN_PADDING_VERTICAL),
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = horizontalAlignment,
    ) {
        content()
    }
}

@Composable
private fun DriveLabel(text: String, scale: AudioLabScale) {
    Text(
        text = text.uppercase(),
        color = LocalDashboardSkin.current.dim,
        fontSize = scale.sp(DigitalBarStyle.LABEL_SIZE),
        lineHeight = scale.sp(DigitalBarStyle.LABEL_LINE_HEIGHT),
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = scale.sp(DigitalBarStyle.LABEL_SIZE * 0.12f),
        maxLines = 1,
    )
}

/**
 * Sub-line under the speed readout.
 *
 * The reference shows a shift phase here; the app shows the automatic gearbox mode when it is
 * meaningful, so nothing that used to sit beside the classic tach is lost.
 */
@Composable
private fun ShiftPhaseCaption(
    drivetrain: DrivetrainState,
    manualShiftModeEnabled: Boolean,
    showAutomaticTransmissionMode: Boolean,
    scale: AudioLabScale,
) {
    val skin = LocalDashboardSkin.current
    val caption = when {
        drivetrain.isShifting -> "SHIFTING" to skin.warning
        showAutomaticTransmissionMode -> {
            AutomaticTransmissionModeCaption.label(
                mode = drivetrain.automaticTransmissionMode,
                preparingCruising = drivetrain.racingReturnArmed,
            ) to AutomaticTransmissionModeCaption.color(
                mode = drivetrain.automaticTransmissionMode,
                preparingCruising = drivetrain.racingReturnArmed,
                skin = skin,
            )
        }
        manualShiftModeEnabled -> "MANUAL" to skin.accentHot
        else -> "READY" to skin.success
    }

    Text(
        text = caption.first,
        color = caption.second,
        fontSize = scale.sp(DigitalBarStyle.PHASE_SIZE),
        lineHeight = scale.sp(DigitalBarStyle.PHASE_LINE_HEIGHT),
        fontWeight = FontWeight.Bold,
        letterSpacing = scale.sp(DigitalBarStyle.PHASE_SIZE * 0.09f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = scale.dp(3f))
            .topDivider(scale)
            .padding(top = scale.dp(3f)),
    )
}

/** Columns in the reference bar are separated by a right-hand rule, with none on the last one. */
private fun Modifier.rightDivider(scale: AudioLabScale): Modifier {
    val lineWidth = scale.dp(1f)

    return drawWithContent {
        drawContent()
        val strokeWidth = lineWidth.toPx()
        val x = size.width - strokeWidth / 2f
        drawLine(
            color = DigitalBarStyle.divider,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokeWidth,
        )
    }
}

private fun Modifier.topDivider(scale: AudioLabScale): Modifier {
    val lineWidth = scale.dp(1f)

    return drawWithContent {
        drawContent()
        val strokeWidth = lineWidth.toPx()
        val y = strokeWidth / 2f
        drawLine(
            color = DigitalBarStyle.phaseDivider,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
        )
    }
}

/** Converts the reference SVG's coordinate units into layout dp and text sp. */
@JvmInline
private value class AudioLabScale(private val dpPerUnit: Float) {
    fun dp(units: Float): Dp {
        return (dpPerUnit * units).dp
    }

    fun sp(units: Float): TextUnit {
        return (dpPerUnit * units).sp
    }
}
