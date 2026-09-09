package com.gabrielpc.enginesoundsimulator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Selectable dashboard looks. Classic is the original BYD skin; AudioLab mirrors the desktop lab. */
enum class DashboardTheme {
    Classic,
    AudioLab,
}

/**
 * Every themeable value the dashboard draws with.
 *
 * Screens never hardcode a color again: they read the semantic slot they mean, so swapping the
 * skin restyles the whole app without touching layout code.
 */
data class DashboardSkin(
    val theme: DashboardTheme,
    // Structure
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    // Accents
    val accent: Color,
    val accentSoft: Color,
    val accentHot: Color,
    // Text
    val onSurface: Color,
    val muted: Color,
    val dim: Color,
    // Semantic states
    val success: Color,
    val danger: Color,
    val warning: Color,
    val yellow: Color,
    val info: Color,
    val realPedalsAccent: Color,
    val favorite: Color,
    /** Tint for the gearbox mode caption when the car is not in its aggressive shift map. */
    val modeCaptionCalm: Color,
    /** Marks a final-stage control, such as APP VOLUME or a car-specific OVERALL trim. */
    val master: Color,
    /**
     * The lamp beside the header title, lit while the sound engine is running.
     *
     * Classic reads it as a status dot (green good, red bad). Audio Lab reads it as a real
     * dashboard warning lamp instead: energised in the skin's red, and simply dark when dead.
     */
    val statusHealthy: Color,
    val statusFault: Color,
    // Banners
    val errorBannerBody: Color,
    val infoBannerBody: Color,
    /** Molded plastic look shared by pedals, shifter and the manual shift buttons. */
    val hardwareGradient: List<Color>,
    val hardwareBorder: Color,
    val hardwareSlotBorder: Color,
    val hardwareBackdrop: Color,
    // Gauges and meters
    val meterTrack: Color,
    val gaugeFaceOuter: Color,
    val gaugeFaceInner: Color,
    val gaugeTrack: Color,
    val gaugeHub: Color,
    // Gear distribution chart
    val chartSurface: Color,
    val chartOutline: Color,
    val chartTitle: Color,
    val chartLabel: Color,
    val chartAxis: Color,
    val chartFooter: Color,
    /** Starting hue for the per-gear band colors, so the chart follows the skin's accent family. */
    val chartHueStart: Float,
    // Form
    val panelShape: Shape,
    val controlShape: Shape,
    /** When false every authored corner radius collapses to a hard edge. */
    val roundedCorners: Boolean,
    val displayFamily: FontFamily,
)

/** The reference tool sets `font-family: "Roboto Condensed"`; on Android that is a system family. */
internal const val CONDENSED_FONT_FAMILY_NAME = "sans-serif-condensed"

private val CondensedFamily = FontFamily(
    Font(DeviceFontFamilyName(CONDENSED_FONT_FAMILY_NAME), FontWeight.Normal),
    Font(DeviceFontFamilyName(CONDENSED_FONT_FAMILY_NAME), FontWeight.Medium),
    Font(DeviceFontFamilyName(CONDENSED_FONT_FAMILY_NAME), FontWeight.SemiBold),
    Font(DeviceFontFamilyName(CONDENSED_FONT_FAMILY_NAME), FontWeight.Bold),
)

val ClassicSkin = DashboardSkin(
    theme = DashboardTheme.Classic,
    background = Color(0xFF060606),
    surface = Color(0xFF0B1925),
    surfaceRaised = Color(0xFF112837),
    outline = Color(0xFF1A3C4A),
    accent = Color(0xFF35E8F2),
    accentSoft = Color(0xFF5FBAC7),
    accentHot = Color(0xFF35E8F2),
    onSurface = Color(0xFFF5FAFD),
    muted = Color(0xFF88A2B2),
    dim = Color(0xFF88A2B2),
    success = Color(0xFF38E58C),
    danger = Color(0xFFFF394F),
    warning = Color(0xFFFFC456),
    yellow = Color(0xFFE8DD3C),
    info = Color(0xFF55B8FF),
    realPedalsAccent = Color(0xFF43BD84),
    favorite = Color(0xFFFFD54F),
    modeCaptionCalm = Color(0xFF5FBAC7),
    master = Color(0xFFFFC456),
    statusHealthy = Color(0xFF38E58C),
    statusFault = Color(0xFFFF394F),
    errorBannerBody = Color(0xFF6E1018),
    infoBannerBody = Color(0xFF0B4545),
    hardwareGradient = listOf(Color(0xFF5B6670), Color(0xFF232D35), Color(0xFF11181E)),
    hardwareBorder = Color(0xFF60717D),
    hardwareSlotBorder = Color(0xFF4A5A66),
    hardwareBackdrop = Color(0xFF111111),
    meterTrack = Color(0xFF061018),
    gaugeFaceOuter = Color(0xFF0B2535),
    gaugeFaceInner = Color(0xFF06111A),
    gaugeTrack = Color(0xFF123F4E),
    gaugeHub = Color(0xFF07141F),
    chartSurface = Color(0xFF09151F),
    chartOutline = Color(0xFF243846),
    chartTitle = Color(0xFF9FEAF1),
    chartLabel = Color(0xFFB8CBD8),
    chartAxis = Color(0xFF526776),
    chartFooter = Color(0xFF8DA5B6),
    chartHueStart = 180f,
    panelShape = RoundedCornerShape(12.dp),
    controlShape = RoundedCornerShape(8.dp),
    roundedCorners = true,
    displayFamily = FontFamily.Default,
)

/**
 * Ported from the Assetto Corsa Audio Lab desktop tool: near-black panels, a single red accent
 * and square edges instead of the Classic skin's cyan-on-navy rounded cards.
 */
val AudioLabSkin = DashboardSkin(
    theme = DashboardTheme.AudioLab,
    background = Color(0xFF080B0E),
    surface = Color(0xFF0D1217),
    surfaceRaised = Color(0xFF121920),
    outline = Color(0xFF28333C),
    accent = Color(0xFFE73632),
    accentSoft = Color(0xFF8D9AA4),
    accentHot = Color(0xFFFF4A3F),
    onSurface = Color(0xFFF1F4F5),
    muted = Color(0xFF8D9AA4),
    dim = Color(0xFF5F6C75),
    success = Color(0xFF58D693),
    danger = Color(0xFFFF4A3F),
    warning = Color(0xFFFFB020),
    yellow = Color(0xFFE8DD3C),
    info = Color(0xFF55B8FF),
    realPedalsAccent = Color(0xFF58D693),
    favorite = Color(0xFFFFB020),
    modeCaptionCalm = Color(0xFF58D693),
    master = Color(0xFFE73632),
    statusHealthy = Color(0xFFE73632),
    // Unlit lamp: dark enough to read as black, light enough to still locate on the header.
    statusFault = Color(0xFF12181D),
    errorBannerBody = Color(0xFF1D1113),
    infoBannerBody = Color(0xFF172129),
    hardwareGradient = listOf(Color(0xFF212A31), Color(0xFF151C22), Color(0xFF0F1418)),
    hardwareBorder = Color(0xFF424D55),
    hardwareSlotBorder = Color(0xFF3B4852),
    hardwareBackdrop = Color(0xFF090D10),
    meterTrack = Color(0xFF11171C),
    gaugeFaceOuter = Color(0xFF0B1217),
    gaugeFaceInner = Color(0xFF070A0C),
    gaugeTrack = Color(0xFF28333C),
    gaugeHub = Color(0xFF11171C),
    chartSurface = Color(0xFF0D1217),
    chartOutline = Color(0xFF28333C),
    chartTitle = Color(0xFFF1F4F5),
    chartLabel = Color(0xFF8D9AA4),
    chartAxis = Color(0xFF5F6C75),
    chartFooter = Color(0xFF8D9AA4),
    chartHueStart = 6f,
    panelShape = RectangleShape,
    controlShape = RectangleShape,
    roundedCorners = false,
    displayFamily = CondensedFamily,
)

fun skinFor(theme: DashboardTheme): DashboardSkin {
    return when (theme) {
        DashboardTheme.Classic -> ClassicSkin
        DashboardTheme.AudioLab -> AudioLabSkin
    }
}

val LocalDashboardSkin = staticCompositionLocalOf { ClassicSkin }

/**
 * Semantic color accessors used directly by the dashboard composables.
 *
 * These keep call sites terse (`color = Accent`) while still resolving through the active skin,
 * which is why they are properties with composable getters rather than plain constants.
 */
internal val Background: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.background

internal val Surface: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.surface

internal val SurfaceRaised: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.surfaceRaised

internal val Outline: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.outline

internal val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.accent

internal val AccentSoft: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.accentSoft

internal val OnSurface: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.onSurface

internal val Muted: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.muted

internal val Dim: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.dim

internal val Success: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.success

internal val Danger: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.danger

internal val Warning: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.warning

internal val RealPedalsAccent: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.realPedalsAccent

internal val Favorite: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.favorite

internal val HardwareGradient: List<Color>
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.hardwareGradient

internal val HardwareBorder: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.hardwareBorder

internal val HardwareSlotBorder: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.hardwareSlotBorder

internal val HardwareBackdrop: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.hardwareBackdrop

internal val MeterTrack: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.meterTrack

internal val Master: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.master

internal val ErrorBannerBody: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.errorBannerBody

internal val InfoBannerBody: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.infoBannerBody

internal val StatusHealthy: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.statusHealthy

internal val StatusFault: Color
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.statusFault

/**
 * Corner treatment for an element the Classic skin authored with [radius].
 *
 * Keeping the original radius as the argument means Classic renders exactly as before while
 * Audio Lab flattens every corner, without duplicating shape decisions at each call site.
 */
@Composable
@ReadOnlyComposable
internal fun skinShape(radius: Dp): Shape {
    if (!LocalDashboardSkin.current.roundedCorners) {
        return RectangleShape
    }

    return RoundedCornerShape(radius)
}

/**
 * Corner treatment for the on-screen replicas of physical hardware: the pedals, the gear selector
 * and the shift paddles.
 *
 * These are meant to read as molded plastic parts rather than app chrome, so they keep their
 * rounded corners even under a skin that squares off every panel.
 */
internal fun hardwareShape(radius: Dp): Shape {
    return RoundedCornerShape(radius)
}

/**
 * Corner treatment for chips defined only by their fill, with no outline around them.
 *
 * Squaring off a bordered panel reads as a deliberate frame, but doing it to a bare block of flat
 * color just reads as an unfinished rectangle, so these keep a soft corner under every skin.
 */
internal fun softFillShape(radius: Dp): Shape {
    return RoundedCornerShape(radius)
}

/**
 * Fully rounded shape that deliberately ignores the skin's corner treatment.
 *
 * Used where a hard edge would look broken rather than styled: switch tracks, whose thumb is a
 * circle, and the molded ribs across the pedal faces.
 */
internal val StadiumShape: Shape = RoundedCornerShape(percent = 50)

/** Same idea as [skinShape] for fully rounded pills. */
@Composable
@ReadOnlyComposable
internal fun skinPillShape(): Shape {
    if (!LocalDashboardSkin.current.roundedCorners) {
        return RectangleShape
    }

    return RoundedCornerShape(50)
}

internal val PanelShape: Shape
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.panelShape

internal val ControlShape: Shape
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.controlShape

internal val DisplayFamily: FontFamily
    @Composable @ReadOnlyComposable get() = LocalDashboardSkin.current.displayFamily
