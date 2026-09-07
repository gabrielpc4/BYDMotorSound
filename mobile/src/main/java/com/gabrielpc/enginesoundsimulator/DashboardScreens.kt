package com.gabrielpc.enginesoundsimulator

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gabrielpc.enginesoundsimulator.RuntimeFeatureFlags
import com.gabrielpc.enginesoundsimulator.audio.FmodBankProfile
import com.gabrielpc.enginesoundsimulator.audio.FmodBankProfiles
import com.gabrielpc.enginesoundsimulator.audio.FmodBankResolver
import com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective
import com.gabrielpc.enginesoundsimulator.audio.FmodEventSection
import com.gabrielpc.enginesoundsimulator.audio.MixerCarSpecificGains
import com.gabrielpc.enginesoundsimulator.audio.MixerEventCategory
import com.gabrielpc.enginesoundsimulator.audio.MixerGlobalGains
import com.gabrielpc.enginesoundsimulator.audio.FmodSourceState
import com.gabrielpc.enginesoundsimulator.audio.FmodUpdateRate
import com.gabrielpc.enginesoundsimulator.drive.CruisingShiftOffsetByTachMaxRpm
import com.gabrielpc.enginesoundsimulator.drive.DriveSnapshot
import com.gabrielpc.enginesoundsimulator.drive.BackfireSettings
import com.gabrielpc.enginesoundsimulator.drive.MinimumAudioThrottle
import com.gabrielpc.enginesoundsimulator.drive.ManualAutodownshiftRpm
import com.gabrielpc.enginesoundsimulator.drive.ManualRedlineHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnThrottlePercent
import com.gabrielpc.enginesoundsimulator.drive.PedalAudioThrottleRampMilliseconds
import com.gabrielpc.enginesoundsimulator.simulation.VirtualGearProfile
import com.gabrielpc.enginesoundsimulator.drive.AlfaBackfireSources
import com.gabrielpc.enginesoundsimulator.simulation.DrivetrainState
import com.gabrielpc.enginesoundsimulator.simulation.TransmissionPosition
import java.util.Locale
import kotlin.math.roundToInt

enum class DashboardMainScreen(val title: String, val subtitle: String) {
    CLASSIC("CLASSIC", "CIRCULAR TACH"),
    MIXER("MIXER", "HUD + LAYERS"),
    SETTINGS("SETTINGS", "PREFERENCES"),
}

@Composable
internal fun DashboardMixerLauncherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.Default.Tune,
        contentDescription = "Mixer",
        tint = Cyan,
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    )
}

internal const val MIXER_SCREEN_HORIZONTAL_PADDING = 20

/** Reserve scroll space so mixer sliders can scroll clear of the floating pedals row. */
private val MIXER_PEDALS_OVERLAY_HEIGHT = 240.dp

/** Which mixer layer the category sliders edit in the mixer panel. */
private enum class MixerGainScope(val displayName: String) {
    GLOBAL("GLOBAL"),
    SPECIFIC("SPECIFIC"),
}

@Composable
internal fun MixerDashboardScreen(
    state: DriveSnapshot,
    onThrottle: (Double) -> Unit,
    onBrake: (Double) -> Unit,
    onSimulatedRegen: (Double) -> Unit,
    onToggleSimulatedPedalLatch: () -> Unit,
    onSelectCar: (String) -> Unit,
    onToggleCarFavorite: (String) -> Unit,
    onTransmissionPositionChange: (TransmissionPosition) -> Unit,
    onManualUpshift: () -> Unit,
    onManualDownshift: () -> Unit,
    onMixerGlobalGainsChange: (MixerGlobalGains) -> Unit,
    onMixerCarSpecificGainsChange: (MixerCarSpecificGains) -> Unit,
    onEventMute: (String, Boolean) -> Unit,
    onEventSolo: (String, Boolean) -> Unit,
    soundPerspective: EngineSoundPerspective,
    onSoundPerspectiveChange: (EngineSoundPerspective) -> Unit,
    modifier: Modifier = Modifier,
) {
    var knownSources by remember(soundPerspective, state.selectedCarId) { mutableStateOf(emptyMap<String, FmodSourceState>()) }
    var previousActive by remember(soundPerspective, state.selectedCarId) { mutableStateOf(emptySet<String>()) }
    var initialized by remember(soundPerspective, state.selectedCarId) { mutableStateOf(false) }
    var highlightedIds by remember(soundPerspective, state.selectedCarId) { mutableStateOf(emptySet<String>()) }
    // FMOD swaps sound names inside one authored event as RPM changes. Keep
    // M/S on that event identity so a control never disappears with a source.
    var mutedEvents by remember(soundPerspective, state.selectedCarId) { mutableStateOf(emptyMap<String, Boolean>()) }
    var soloedEvents by remember(soundPerspective, state.selectedCarId) { mutableStateOf(emptyMap<String, Boolean>()) }
    val activeIds = state.fmodSources.filter { it.isActive }.mapTo(mutableSetOf(), FmodSourceState::id)
    val enteredIds = activeIds - previousActive

    LaunchedEffect(state.fmodSources) {
        val currentSources = state.fmodSources
            .filter(FmodSourceState::isActive)
            .associateBy(FmodSourceState::id)
        val inactiveKnownSources = knownSources.mapValues { (_, source) ->
            source.copy(
                audibility = 0.0,
                voiceCount = 0,
                isVirtual = false,
                isActive = false,
            )
        }
        // Once FMOD has exposed a source, keep its diagnostic card so its
        // disappearance is visible as SILENT instead of looking like a reset.
        knownSources = inactiveKnownSources + currentSources
        val shouldHighlight = initialized
        previousActive = activeIds
        initialized = true
        if (shouldHighlight && enteredIds.isNotEmpty()) {
            val highlightable = enteredIds.filter { id ->
                state.fmodSources.firstOrNull { it.id == id }?.let { it.eventName != "engine_int" && it.eventName != "engine_ext" } == true
            }.toSet()
            highlightedIds = highlightedIds + highlightable
        }
    }

    LaunchedEffect(highlightedIds) {
        if (highlightedIds.isNotEmpty()) {
            delay(1000)
            highlightedIds = emptySet()
        }
    }

    val sections = remember(knownSources) {
        // Dormant sources that FMOD has never exposed are omitted. Sources that
        // were previously live remain as SILENT diagnostics instead of READY.
        knownSources.values
            .groupBy(FmodSourceState::section)
            .toSortedMap(compareBy<FmodEventSection> {
                // Keep the many engine-region cards out of the way of the
                // shorter effect sections by presenting ENGINE last.
                if (it == FmodEventSection.ENGINE) Int.MAX_VALUE else it.order
            })
            .mapValues { (_, sources) ->
                sources.sortedWith(
                    // Keep each source in a deterministic slot. Activity and audibility are
                    // diagnostic values only; sorting by them made a newly audible voice appear
                    // to replace another card even though both FMOD voices were still alive.
                    compareBy(FmodSourceState::id),
                )
            }
    }

    var mixerGains by remember(state.mixerGlobalGains) { mutableStateOf(state.mixerGlobalGains) }
    var mixerSpecificGains by remember(state.selectedCarId, soundPerspective, state.mixerCarSpecificGains) {
        mutableStateOf(state.mixerCarSpecificGains)
    }
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MIXER_SCREEN_HORIZONTAL_PADDING.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
        ) {
            MixerHeaderRow(
                drivetrain = state.drivetrain,
                transmissionPosition = state.transmissionPosition,
                maxRpm = state.drivetrain.tachometerMaximumRpm,
                redlineRpm = state.drivetrain.redlineRpm,
                selectedCarId = state.selectedCarId,
                selectedCarName = state.selectedCarName,
                selectedCarPreviewAsset = state.selectedCarPreviewAsset,
                favoriteCarIds = state.favoriteCarIds,
                onSelectCar = onSelectCar,
                onToggleCarFavorite = onToggleCarFavorite,
            )
            Spacer(Modifier.height(8.dp))
            MixerListeningPerspectiveSelector(
                perspective = soundPerspective,
                onPerspectiveSelected = onSoundPerspectiveChange,
            )
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val columnCount = (maxWidth.value / 390f).toInt().coerceIn(1, 2)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sections.forEach { (section, sources) ->
                        item(key = "section-${section.name}") {
                            Text(
                                text = section.displayName,
                                color = CyanSoft,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 2.dp, start = 2.dp),
                            )
                        }
                        items(sources.chunked(columnCount), key = { row -> "${state.selectedCarId}-${soundPerspective.name}-" + row.joinToString { it.id } }) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { source ->
                                    FmodSourceMeter(
                                        source = source,
                                        highlight = source.id in highlightedIds,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(columnCount - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
        ) {
            MixerControlsPanel(
                soundPerspective = soundPerspective,
                carEngineHostGain = state.engineHostGain,
                carEffectsHostGain = state.effectsHostGain,
                carTransmissionGain = state.transmissionGain,
                carGearShiftGain = state.gearShiftGain,
                carTurboGain = state.turboGain,
                carBackfireGain = state.backfireGain,
                carLimiterGain = state.limiterGain,
                carSuperchargerGain = state.superchargerGain,
                hasTurbo = state.hasTurbo,
                hasSupercharger = state.hasSupercharger,
                mixerGains = mixerGains,
                mixerSpecificGains = mixerSpecificGains,
                mutedEvents = mutedEvents,
                soloedEvents = soloedEvents,
                onToggleCategoryMute = { category, muted ->
                    var updated = mutedEvents
                    category.eventNames.forEach { eventName ->
                        updated = if (muted) {
                            updated + (eventName to true)
                        } else {
                            updated - eventName
                        }
                        onEventMute(eventName, muted)
                    }
                    mutedEvents = updated
                },
                onToggleCategorySolo = { category, solo ->
                    var updated = soloedEvents
                    category.eventNames.forEach { eventName ->
                        updated = if (solo) {
                            updated + (eventName to true)
                        } else {
                            updated - eventName
                        }
                        onEventSolo(eventName, solo)
                    }
                    soloedEvents = updated
                },
                onMixerGainsChange = { updated ->
                    mixerGains = updated
                    onMixerGlobalGainsChange(updated)
                },
                onMixerSpecificGainsChange = { updated ->
                    mixerSpecificGains = updated
                    onMixerCarSpecificGainsChange(updated)
                },
                modifier = Modifier.fillMaxSize(),
            )
            MixerDriveControls(
                state = state,
                onThrottle = onThrottle,
                onBrake = onBrake,
                onSimulatedRegen = onSimulatedRegen,
                onToggleSimulatedPedalLatch = onToggleSimulatedPedalLatch,
                onTransmissionPositionChange = onTransmissionPositionChange,
                onManualUpshift = onManualUpshift,
                onManualDownshift = onManualDownshift,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun MixerListeningPerspectiveSelector(
    perspective: EngineSoundPerspective,
    onPerspectiveSelected: (EngineSoundPerspective) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Panel.copy(alpha = 0.92f))
            .border(1.dp, Line.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "LISTENING",
            color = Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(10.dp))
        EngineSoundPerspective.entries.forEach { option ->
            val active = option == perspective
            Text(
                text = option.displayName,
                color = if (active) Cyan else Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (active) Cyan.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onPerspectiveSelected(option) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MixerControlsPanel(
    soundPerspective: EngineSoundPerspective,
    carEngineHostGain: Float,
    carEffectsHostGain: Float,
    carTransmissionGain: Float,
    carGearShiftGain: Float,
    carTurboGain: Float,
    carBackfireGain: Float,
    carLimiterGain: Float,
    carSuperchargerGain: Float,
    hasTurbo: Boolean,
    hasSupercharger: Boolean,
    mixerGains: MixerGlobalGains,
    mixerSpecificGains: MixerCarSpecificGains,
    mutedEvents: Map<String, Boolean>,
    soloedEvents: Map<String, Boolean>,
    onToggleCategoryMute: (MixerEventCategory, Boolean) -> Unit,
    onToggleCategorySolo: (MixerEventCategory, Boolean) -> Unit,
    onMixerGainsChange: (MixerGlobalGains) -> Unit,
    onMixerSpecificGainsChange: (MixerCarSpecificGains) -> Unit,
    modifier: Modifier = Modifier,
) {
    var gainScope by remember { mutableStateOf(MixerGainScope.GLOBAL) }
    val cardShape = RoundedCornerShape(8.dp)
    val cardModifier = modifier
        .fillMaxHeight()
        .clip(cardShape)
        .then(
            if (gainScope == MixerGainScope.GLOBAL) {
                Modifier.border(1.dp, Line, cardShape)
            } else {
                Modifier
                    .background(Panel)
                    .border(1.dp, Line, cardShape)
            },
        )
        .padding(horizontal = 12.dp, vertical = 10.dp)

    Column(modifier = cardModifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = MIXER_PEDALS_OVERLAY_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MixerGainScopeSelector(
                scope = gainScope,
                listeningPerspective = soundPerspective,
                onScopeSelected = { gainScope = it },
            )
            if (gainScope == MixerGainScope.SPECIFIC) {
                MixerLayerGainSlider(
                    label = "OVERALL",
                    layerValue = mixerSpecificGains.overall,
                    dashboardValue = carEngineHostGain,
                    globalValue = mixerGains.engineInterior,
                    specificValue = mixerSpecificGains.engineInterior,
                    overall = mixerSpecificGains.overall,
                    accentColor = Amber,
                    onValueChange = {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(overall = it))
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = Amber.copy(alpha = 0.45f),
                )
            }
            MixerLayerGainSlider(
                label = "ENGINE INTERIOR",
                eventCategory = MixerEventCategory.ENGINE_INTERIOR,
                mutedEvents = mutedEvents,
                soloedEvents = soloedEvents,
                onToggleCategoryMute = onToggleCategoryMute,
                onToggleCategorySolo = onToggleCategorySolo,
                layerValue = layerValueForScope(gainScope, mixerGains.engineInterior, mixerSpecificGains.engineInterior),
                dashboardValue = carEngineHostGain,
                globalValue = mixerGains.engineInterior,
                specificValue = mixerSpecificGains.engineInterior,
                overall = mixerSpecificGains.overall,
                onValueChange = { value ->
                    if (gainScope == MixerGainScope.GLOBAL) {
                        onMixerGainsChange(mixerGains.copy(engineInterior = value))
                    } else {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(engineInterior = value))
                    }
                },
            )
            MixerLayerGainSlider(
                label = "ENGINE EXTERIOR",
                eventCategory = MixerEventCategory.ENGINE_EXTERIOR,
                mutedEvents = mutedEvents,
                soloedEvents = soloedEvents,
                onToggleCategoryMute = onToggleCategoryMute,
                onToggleCategorySolo = onToggleCategorySolo,
                layerValue = layerValueForScope(gainScope, mixerGains.engineExterior, mixerSpecificGains.engineExterior),
                dashboardValue = carEngineHostGain,
                globalValue = mixerGains.engineExterior,
                specificValue = mixerSpecificGains.engineExterior,
                overall = mixerSpecificGains.overall,
                onValueChange = { value ->
                    if (gainScope == MixerGainScope.GLOBAL) {
                        onMixerGainsChange(mixerGains.copy(engineExterior = value))
                    } else {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(engineExterior = value))
                    }
                },
            )
            MixerLayerGainSlider(
                label = "EFFECTS",
                layerValue = layerValueForScope(gainScope, mixerGains.effectsHost, mixerSpecificGains.effectsHost),
                dashboardValue = carEffectsHostGain,
                globalValue = mixerGains.effectsHost,
                specificValue = mixerSpecificGains.effectsHost,
                overall = mixerSpecificGains.overall,
                onValueChange = { value ->
                    if (gainScope == MixerGainScope.GLOBAL) {
                        onMixerGainsChange(mixerGains.copy(effectsHost = value))
                    } else {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(effectsHost = value))
                    }
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Line,
            )
            MixerLayerGainSlider(
                label = "TRANSMISSION",
                eventCategory = MixerEventCategory.TRANSMISSION,
                mutedEvents = mutedEvents,
                soloedEvents = soloedEvents,
                onToggleCategoryMute = onToggleCategoryMute,
                onToggleCategorySolo = onToggleCategorySolo,
                layerValue = layerValueForScope(gainScope, mixerGains.transmission, mixerSpecificGains.transmission),
                dashboardValue = carTransmissionGain,
                globalValue = mixerGains.transmission,
                specificValue = mixerSpecificGains.transmission,
                overall = mixerSpecificGains.overall,
                onValueChange = { value ->
                    if (gainScope == MixerGainScope.GLOBAL) {
                        onMixerGainsChange(mixerGains.copy(transmission = value))
                    } else {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(transmission = value))
                    }
                },
            )
            MixerLayerGainSlider(
                label = "SHIFT",
                eventCategory = MixerEventCategory.SHIFT,
                mutedEvents = mutedEvents,
                soloedEvents = soloedEvents,
                onToggleCategoryMute = onToggleCategoryMute,
                onToggleCategorySolo = onToggleCategorySolo,
                layerValue = layerValueForScope(gainScope, mixerGains.gearShift, mixerSpecificGains.gearShift),
                dashboardValue = carGearShiftGain,
                globalValue = mixerGains.gearShift,
                specificValue = mixerSpecificGains.gearShift,
                overall = mixerSpecificGains.overall,
                onValueChange = { value ->
                    if (gainScope == MixerGainScope.GLOBAL) {
                        onMixerGainsChange(mixerGains.copy(gearShift = value))
                    } else {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(gearShift = value))
                    }
                },
            )
            if (hasTurbo) {
                MixerLayerGainSlider(
                    label = "TURBO",
                    eventCategory = MixerEventCategory.TURBO,
                    mutedEvents = mutedEvents,
                    soloedEvents = soloedEvents,
                    onToggleCategoryMute = onToggleCategoryMute,
                    onToggleCategorySolo = onToggleCategorySolo,
                    layerValue = layerValueForScope(gainScope, mixerGains.turbo, mixerSpecificGains.turbo),
                    dashboardValue = carTurboGain,
                    globalValue = mixerGains.turbo,
                    specificValue = mixerSpecificGains.turbo,
                    overall = mixerSpecificGains.overall,
                    onValueChange = { value ->
                        if (gainScope == MixerGainScope.GLOBAL) {
                            onMixerGainsChange(mixerGains.copy(turbo = value))
                        } else {
                            onMixerSpecificGainsChange(mixerSpecificGains.copy(turbo = value))
                        }
                    },
                )
            }
            if (hasSupercharger && RuntimeFeatureFlags.MIX_SUPERCHARGER) {
                MixerLayerGainSlider(
                    label = "SUPERCHARGER",
                    eventCategory = MixerEventCategory.SUPERCHARGER,
                    mutedEvents = mutedEvents,
                    soloedEvents = soloedEvents,
                    onToggleCategoryMute = onToggleCategoryMute,
                    onToggleCategorySolo = onToggleCategorySolo,
                    layerValue = layerValueForScope(gainScope, mixerGains.supercharger, mixerSpecificGains.supercharger),
                    dashboardValue = carSuperchargerGain,
                    globalValue = mixerGains.supercharger,
                    specificValue = mixerSpecificGains.supercharger,
                    overall = mixerSpecificGains.overall,
                    onValueChange = { value ->
                        if (gainScope == MixerGainScope.GLOBAL) {
                            onMixerGainsChange(mixerGains.copy(supercharger = value))
                        } else {
                            onMixerSpecificGainsChange(mixerSpecificGains.copy(supercharger = value))
                        }
                    },
                )
            }
            MixerLayerGainSlider(
                label = "POPS & BANGS",
                eventCategory = MixerEventCategory.BACKFIRE,
                mutedEvents = mutedEvents,
                soloedEvents = soloedEvents,
                onToggleCategoryMute = onToggleCategoryMute,
                onToggleCategorySolo = onToggleCategorySolo,
                layerValue = layerValueForScope(gainScope, mixerGains.backfire, mixerSpecificGains.backfire),
                dashboardValue = carBackfireGain,
                globalValue = mixerGains.backfire,
                specificValue = mixerSpecificGains.backfire,
                overall = mixerSpecificGains.overall,
                onValueChange = { value ->
                    if (gainScope == MixerGainScope.GLOBAL) {
                        onMixerGainsChange(mixerGains.copy(backfire = value))
                    } else {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(backfire = value))
                    }
                },
            )
            MixerLayerGainSlider(
                label = "LIMITER",
                eventCategory = MixerEventCategory.LIMITER,
                mutedEvents = mutedEvents,
                soloedEvents = soloedEvents,
                onToggleCategoryMute = onToggleCategoryMute,
                onToggleCategorySolo = onToggleCategorySolo,
                layerValue = layerValueForScope(gainScope, mixerGains.limiter, mixerSpecificGains.limiter),
                dashboardValue = carLimiterGain,
                globalValue = mixerGains.limiter,
                specificValue = mixerSpecificGains.limiter,
                overall = mixerSpecificGains.overall,
                onValueChange = { value ->
                    if (gainScope == MixerGainScope.GLOBAL) {
                        onMixerGainsChange(mixerGains.copy(limiter = value))
                    } else {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(limiter = value))
                    }
                },
            )
        }
    }
}

private fun layerValueForScope(
    scope: MixerGainScope,
    globalValue: Float,
    specificValue: Float,
): Float {
    if (scope == MixerGainScope.GLOBAL) {
        return globalValue
    }

    return specificValue
}

@Composable
private fun MixerGainScopeSelector(
    scope: MixerGainScope,
    listeningPerspective: EngineSoundPerspective,
    onScopeSelected: (MixerGainScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MIX",
                color = Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(10.dp))
            MixerGainScope.entries.forEach { option ->
                val active = option == scope
                Text(
                    text = option.displayName,
                    color = if (active) Cyan else Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (active) Cyan.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onScopeSelected(option) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        if (scope == MixerGainScope.SPECIFIC) {
            Text(
                text = "Per-car profile for ${listeningPerspective.displayName}",
                color = Amber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
private fun MixerLayerGainSlider(
    label: String,
    layerValue: Float,
    dashboardValue: Float,
    globalValue: Float,
    specificValue: Float,
    overall: Float,
    accentColor: Color = CyanSoft,
    eventCategory: MixerEventCategory? = null,
    mutedEvents: Map<String, Boolean> = emptyMap(),
    soloedEvents: Map<String, Boolean> = emptyMap(),
    onToggleCategoryMute: (MixerEventCategory, Boolean) -> Unit = { _, _ -> },
    onToggleCategorySolo: (MixerEventCategory, Boolean) -> Unit = { _, _ -> },
    onValueChange: (Float) -> Unit,
) {
    val snappedLayerValue = MixerGlobalGains.snap(layerValue)
    val effectiveValue = dashboardValue * globalValue * specificValue * overall
    val stopIndex = MixerGlobalGains.stopIndex(snappedLayerValue).toFloat()
    val lastStopIndex = (MixerGlobalGains.STOPS.size - 1).toFloat()
    val sliderColors = SliderDefaults.colors(
        thumbColor = accentColor,
        activeTrackColor = accentColor,
        inactiveTrackColor = Line,
        activeTickColor = accentColor.copy(alpha = 0.55f),
        inactiveTickColor = Line.copy(alpha = 0.85f),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                )
                if (eventCategory != null) {
                    val muted = eventCategory.isMuted(mutedEvents)
                    val soloed = eventCategory.isSoloed(soloedEvents)
                    Text(
                        text = "M",
                        color = if (muted) Cyan else Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable {
                            onToggleCategoryMute(eventCategory, !muted)
                        },
                    )
                    Text(
                        text = "S",
                        color = if (soloed) Cyan else Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable {
                            onToggleCategorySolo(eventCategory, !soloed)
                        },
                    )
                }
            }
            Text(
                text = "${MixerGlobalGains.formatMultiplier(snappedLayerValue)} → ${MixerGlobalGains.formatMultiplier(effectiveValue)}",
                color = if (accentColor == Amber) Amber else White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = stopIndex,
            onValueChange = { rawIndex ->
                val index = rawIndex.roundToInt().coerceIn(0, MixerGlobalGains.STOPS.lastIndex)
                onValueChange(MixerGlobalGains.stopValue(index))
            },
            valueRange = 0f..lastStopIndex,
            steps = MixerGlobalGains.STOPS.size - 2,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SettingsScreen(
    onExportSettings: () -> Unit,
    onResetAll: () -> Unit,
    fmodUpdateRateHz: Int,
    onFmodUpdateRateChange: (Int) -> Unit,
    backfireSettings: BackfireSettings,
    onBackfireSettingsChange: (BackfireSettings) -> Unit,
    virtualForwardGearCount: Int,
    onVirtualForwardGearCountChange: (Int) -> Unit,
    sixGearOnLaunchEnabled: Boolean,
    onSixGearOnLaunchEnabledChange: (Boolean) -> Unit,
    minimumAudioThrottle: Float,
    onMinimumAudioThrottleChange: (Float) -> Unit,
    racingReturnThrottlePercent: Int,
    onRacingReturnThrottlePercentChange: (Int) -> Unit,
    racingReturnHoldSeconds: Int,
    onRacingReturnHoldSecondsChange: (Int) -> Unit,
    manualRedlineHoldSeconds: Int,
    onManualRedlineHoldSecondsChange: (Int) -> Unit,
    manualAutodownshiftRpm: Int,
    onManualAutodownshiftRpmChange: (Int) -> Unit,
    pedalAudioThrottleRampUpMilliseconds: Int,
    onPedalAudioThrottleRampUpMillisecondsChange: (Int) -> Unit,
    pedalAudioThrottleRampDownMilliseconds: Int,
    onPedalAudioThrottleRampDownMillisecondsChange: (Int) -> Unit,
    tachometerCruisingShiftRangeOverlayEnabled: Boolean,
    onTachometerCruisingShiftRangeOverlayEnabledChange: (Boolean) -> Unit,
    cruisingShiftOffsetsByTachMaxRpm: Map<Int, Int>,
    onCruisingShiftOffsetForTachMaxRpmChange: (Int, Int) -> Unit,
    onPreviewBackfireSample: (Int) -> Unit,
) {
    var backfireTab by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SETTINGS", color = White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Row(
            modifier = Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsTab("GENERAL", !backfireTab) { backfireTab = false }
            SettingsTab("BACKFIRE", backfireTab) { backfireTab = true }
        }
        if (!backfireTab) {
            SettingsGridRow {
                FmodUpdateRateControl(
                    rateHz = fmodUpdateRateHz,
                    onRateChange = onFmodUpdateRateChange,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            SettingsGridRow {
                PedalAudioThrottleRampSettingCard(
                    title = "RAMP UP",
                    valueMilliseconds = pedalAudioThrottleRampUpMilliseconds,
                    onValueChange = onPedalAudioThrottleRampUpMillisecondsChange,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                PedalAudioThrottleRampSettingCard(
                    title = "RAMP DOWN",
                    valueMilliseconds = pedalAudioThrottleRampDownMilliseconds,
                    onValueChange = onPedalAudioThrottleRampDownMillisecondsChange,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            VirtualForwardGearCountControl(
                gearCount = virtualForwardGearCount,
                onGearCountChange = onVirtualForwardGearCountChange,
                sixGearOnLaunchEnabled = sixGearOnLaunchEnabled,
                onSixGearOnLaunchEnabledChange = onSixGearOnLaunchEnabledChange,
            )
            CruisingShiftOffsetsByTachMaxRpmControl(
                offsets = cruisingShiftOffsetsByTachMaxRpm,
                onOffsetChange = onCruisingShiftOffsetForTachMaxRpmChange,
            )
            AutomaticTransmissionSettingsControl(
                minimumAudioThrottle = minimumAudioThrottle,
                onMinimumAudioThrottleChange = onMinimumAudioThrottleChange,
                racingReturnThrottlePercent = racingReturnThrottlePercent,
                onRacingReturnThrottlePercentChange = onRacingReturnThrottlePercentChange,
                manualRedlineHoldSeconds = manualRedlineHoldSeconds,
                onManualRedlineHoldSecondsChange = onManualRedlineHoldSecondsChange,
                manualAutodownshiftRpm = manualAutodownshiftRpm,
                onManualAutodownshiftRpmChange = onManualAutodownshiftRpmChange,
                tachometerCruisingShiftRangeOverlayEnabled = tachometerCruisingShiftRangeOverlayEnabled,
                onTachometerCruisingShiftRangeOverlayEnabledChange = onTachometerCruisingShiftRangeOverlayEnabledChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onExportSettings,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Line),
                ) {
                    Text("EXPORT SETTINGS", color = CyanSoft, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = { showResetConfirmation = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Red.copy(alpha = 0.85f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("RESET ALL", color = White, fontWeight = FontWeight.Black)
                }
            }
        } else {
            BackfireSettingsPanel(
                settings = backfireSettings,
                onChange = onBackfireSettingsChange,
                onPreview = onPreviewBackfireSample,
            )
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = {
                Text("Reset all settings?", color = White, fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    "This clears every saved preference, per-car mix, and dashboard setting. It cannot be undone.",
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onResetAll()
                    },
                ) {
                    Text("RESET", color = Red, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("CANCEL", color = CyanSoft, fontWeight = FontWeight.Black)
                }
            },
            containerColor = Panel,
        )
    }
}

@Composable
private fun SettingsGridRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
private fun VirtualForwardGearCountControl(
    gearCount: Int,
    onGearCountChange: (Int) -> Unit,
    sixGearOnLaunchEnabled: Boolean,
    onSixGearOnLaunchEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(
        modifier = modifier
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("VIRTUAL FORWARD GEARS", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = "$gearCount gears",
                        color = White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                Slider(
                    value = gearCount.toFloat(),
                    onValueChange = { value ->
                        val selectedCount = value.roundToInt()

                        if (selectedCount != gearCount) {
                            onGearCountChange(selectedCount)
                        }
                    },
                    valueRange = VirtualGearProfile.MIN_VIRTUAL_GEARS.toFloat()..VirtualGearProfile.MAX_VIRTUAL_GEARS.toFloat(),
                    steps = VirtualGearProfile.MAX_VIRTUAL_GEARS - VirtualGearProfile.MIN_VIRTUAL_GEARS - 1,
                )
            }

            SixGearOnLaunchSetting(
                enabled = sixGearOnLaunchEnabled,
                onEnabledChange = onSixGearOnLaunchEnabledChange,
                modifier = Modifier.width(280.dp),
            )
        }

        VirtualGearDistributionChart(
            gearCount = gearCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TachometerShiftOverlayToggle(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    Column(
        modifier = modifier.then(
            if (embedded) {
                Modifier
            } else {
                Modifier
                    .border(1.dp, Line, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            },
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }

        Text(
            text = description,
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun SixGearOnLaunchSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("6-GEAR ON LAUNCH", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }

        Text(
            text = "Launch control temporarily uses the 6-gear ratio profile until throttle lift or brake.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun CruisingShiftOffsetsByTachMaxRpmControl(
    offsets: Map<Int, Int>,
    onOffsetChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val offsetSteps = (CruisingShiftOffsetByTachMaxRpm.MAX - CruisingShiftOffsetByTachMaxRpm.MIN) /
        CruisingShiftOffsetByTachMaxRpm.STEP - 1

    Column(
        modifier = modifier
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("CRUISING OFFSET BY TACH MAX", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Text(
            text = "Cruising shift RPM offset stored per tachometer maximum. Cars that share the same max RPM use the same value.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

        CruisingShiftOffsetByTachMaxRpm.TIERS.chunked(2).forEach { rowTiers ->
            SettingsGridRow {
                rowTiers.forEach { tier ->
                    val offset = offsets[tier] ?: CruisingShiftOffsetByTachMaxRpm.defaultOffsets().getValue(tier)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${tier / 1_000}K RPM",
                                color = CyanSoft,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                text = CruisingShiftOffsetByTachMaxRpm.formatOffsetLabel(offset),
                                color = White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Slider(
                            value = CruisingShiftOffsetByTachMaxRpm.sliderValueFromOffset(offset),
                            onValueChange = { value ->
                                val normalized = CruisingShiftOffsetByTachMaxRpm.offsetFromSliderValue(value)
                                if (normalized != offset) {
                                    onOffsetChange(tier, normalized)
                                }
                            },
                            valueRange = CruisingShiftOffsetByTachMaxRpm.MIN.toFloat()..CruisingShiftOffsetByTachMaxRpm.MAX.toFloat(),
                            steps = offsetSteps.coerceAtLeast(0),
                        )
                    }
                }

                if (rowTiers.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AutomaticTransmissionSettingsControl(
    minimumAudioThrottle: Float,
    onMinimumAudioThrottleChange: (Float) -> Unit,
    racingReturnThrottlePercent: Int,
    onRacingReturnThrottlePercentChange: (Int) -> Unit,
    manualRedlineHoldSeconds: Int,
    onManualRedlineHoldSecondsChange: (Int) -> Unit,
    manualAutodownshiftRpm: Int,
    onManualAutodownshiftRpmChange: (Int) -> Unit,
    tachometerCruisingShiftRangeOverlayEnabled: Boolean,
    onTachometerCruisingShiftRangeOverlayEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(
        modifier = modifier
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val throttleSteps = ((MinimumAudioThrottle.MAX - MinimumAudioThrottle.MIN) / MinimumAudioThrottle.STEP)
            .roundToInt() - 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MIN THROTTLE", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = String.format(Locale.US, "%.2f", minimumAudioThrottle),
                color = White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = "Lower bound applied to the FMOD engine throttle parameter.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Slider(
            value = minimumAudioThrottle,
            onValueChange = { value ->
                onMinimumAudioThrottleChange(MinimumAudioThrottle.normalize(value))
            },
            valueRange = MinimumAudioThrottle.MIN..MinimumAudioThrottle.MAX,
            steps = throttleSteps.coerceAtLeast(0),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("RACING RETURN THROTTLE", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = "$racingReturnThrottlePercent%",
                        color = White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = "After braking in racing mode, the next acceleration decides the mode: at or below this pedal level returns to cruising; above it stays in racing.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Slider(
                    value = racingReturnThrottlePercent.toFloat(),
                    onValueChange = { value ->
                        val selectedPercent = RacingReturnThrottlePercent.normalize(value.roundToInt())
                        if (selectedPercent != racingReturnThrottlePercent) {
                            onRacingReturnThrottlePercentChange(selectedPercent)
                        }
                    },
                    valueRange = RacingReturnThrottlePercent.MIN.toFloat()..RacingReturnThrottlePercent.MAX.toFloat(),
                    steps = (RacingReturnThrottlePercent.MAX - RacingReturnThrottlePercent.MIN) / RacingReturnThrottlePercent.STEP - 1,
                )
            }

            TachometerShiftOverlayToggle(
                title = "CRUISING RPM RANGE OVERLAY",
                description = "Semi-transparent wedge on the tachometer showing min/max automatic shift RPM while cruising.",
                enabled = tachometerCruisingShiftRangeOverlayEnabled,
                onEnabledChange = onTachometerCruisingShiftRangeOverlayEnabledChange,
                modifier = Modifier.weight(1f),
                embedded = true,
            )
        }
        val manualRedlineStopIndex = ManualRedlineHoldSeconds.stopIndex(manualRedlineHoldSeconds).toFloat()
        val manualRedlineLastStopIndex = (ManualRedlineHoldSeconds.STOPS.size - 1).toFloat()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MANUAL REDLINE HOLD", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = ManualRedlineHoldSeconds.format(manualRedlineHoldSeconds),
                color = White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = "Manual mode returns to automatic racing after staying at or above redline for this long. Braking in racing mode then uses the return throttle above to decide cruising on the next acceleration.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Slider(
            value = manualRedlineStopIndex,
            onValueChange = { rawIndex ->
                val index = rawIndex.roundToInt().coerceIn(0, ManualRedlineHoldSeconds.STOPS.lastIndex)
                val selectedSeconds = ManualRedlineHoldSeconds.stopValue(index)
                if (selectedSeconds != manualRedlineHoldSeconds) {
                    onManualRedlineHoldSecondsChange(selectedSeconds)
                }
            },
            valueRange = 0f..manualRedlineLastStopIndex,
            steps = (ManualRedlineHoldSeconds.STOPS.size - 2).coerceAtLeast(0),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MANUAL AUTODOWNSHIFT RPM", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = "$manualAutodownshiftRpm RPM",
                color = White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = "Manual mode: falling below this RPM downshifts one gear automatically. Racing kickdown uses the same value as an offset below redline when choosing a target gear.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Slider(
            value = manualAutodownshiftRpm.toFloat(),
            onValueChange = { value ->
                val selectedRpm = ManualAutodownshiftRpm.normalize(value.roundToInt())
                if (selectedRpm != manualAutodownshiftRpm) {
                    onManualAutodownshiftRpmChange(selectedRpm)
                }
            },
            valueRange = ManualAutodownshiftRpm.MIN.toFloat()..ManualAutodownshiftRpm.MAX.toFloat(),
            steps = (ManualAutodownshiftRpm.MAX - ManualAutodownshiftRpm.MIN) / ManualAutodownshiftRpm.STEP - 1,
        )
    }
}

@Composable
private fun ExteriorPureAudioControl(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("EXTERIOR PURE AUDIO", color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(
                "Neutralizes exterior 3D distance and pan. FMOD events, pitch, gain, fades and authored DSP remain active.",
                color = Muted,
                fontSize = 12.sp,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun ShiftSoundOverrideControl(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(8.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("SHIFT SOUND OVERRIDE", color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(
                "Uses the bundled upshift/downshift samples instead of the car's authored gear sounds.",
                color = Muted,
                fontSize = 12.sp,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun PedalAudioThrottleRampSettingCard(
    title: String,
    valueMilliseconds: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val stopIndex = PedalAudioThrottleRampMilliseconds.stopIndex(valueMilliseconds).toFloat()
    val lastStopIndex = (PedalAudioThrottleRampMilliseconds.STOPS.size - 1).toFloat()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = PedalAudioThrottleRampMilliseconds.format(valueMilliseconds),
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = "Pedal audio throttle smoothing time when the pedal moves in this direction.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Slider(
            value = stopIndex,
            onValueChange = { rawIndex ->
                val index = rawIndex.roundToInt().coerceIn(0, PedalAudioThrottleRampMilliseconds.STOPS.lastIndex)
                onValueChange(PedalAudioThrottleRampMilliseconds.stopValue(index))
            },
            valueRange = 0f..lastStopIndex,
            steps = (PedalAudioThrottleRampMilliseconds.STOPS.size - 2).coerceAtLeast(0),
        )
    }
}

@Composable
private fun FmodUpdateRateControl(
    rateHz: Int,
    onRateChange: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("FMOD CONTROL RATE", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(
                    "Physics and FMOD share this cadence. 60 Hz is recommended; 30 Hz is economy mode.",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
            Text(
                "$rateHz Hz",
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(FmodUpdateRate.ECONOMY_HZ, FmodUpdateRate.STANDARD_HZ).forEach { optionHz ->
                Button(
                    onClick = { onRateChange(optionHz) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (rateHz == optionHz) Cyan else PanelBright,
                    ),
                ) {
                    Text(
                        text = "$optionHz Hz",
                        color = if (rateHz == optionHz) Night else White,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.SettingsTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Cyan else Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(if (selected) Cyan.copy(alpha = 0.14f) else Color.Transparent)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BackfireSettingsPanel(
    settings: BackfireSettings,
    onChange: (BackfireSettings) -> Unit,
    onPreview: (Int) -> Unit,
) {
    val value = settings.normalized()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("GLOBAL BACKFIRE POLICY", color = Cyan, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "These rules apply to every car. A backfire arms after a clear throttle run, then fires only after the pedal is released for the selected delay.",
            color = Muted,
            fontSize = 14.sp,
        )
        SettingsToggle("OVERRIDE SOUNDS ONLY", value.soundOnlyOverrideEnabled) {
            onChange(value.copy(soundOnlyOverrideEnabled = !value.soundOnlyOverrideEnabled))
        }
        SettingsToggle("ALLOW BACKFIRE IN P / N", value.allowParkNeutralOverride) {
            onChange(value.copy(allowParkNeutralOverride = !value.allowParkNeutralOverride))
        }
        BackfireSlider("ARM THROTTLE", value.armThrottle, 0.05f..1.0f, steps = 17) {
            onChange(value.copy(armThrottle = it.toDouble()))
        }
        BackfireSlider("RELEASE THROTTLE", value.releaseThrottle, 0.0f..0.9f, steps = 17) {
            onChange(value.copy(releaseThrottle = it.toDouble()))
        }
        BackfireSlider("RELEASE DELAY", value.releaseDelaySeconds, 0.0f..5.0f, suffix = "s", steps = 49) {
            onChange(value.copy(releaseDelaySeconds = it.toDouble()))
        }
        BackfireSlider("MINIMUM RPM", value.minimumRpm, 0.0f..16000.0f, integer = true, steps = 31) {
            onChange(value.copy(minimumRpm = it.toDouble()))
        }
        BackfireSlider("MAXIMUM RPM", value.maximumRpm, 500.0f..12000.0f, integer = true, steps = 23) {
            onChange(value.copy(maximumRpm = it.toDouble()))
        }
        Text("ALFA ROMEO BACKFIRE SAMPLES", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Black)
        AlfaBackfireSources.indices.forEach { sample ->
            val allowed = sample in value.allowedSamples
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Panel)
                    .border(1.dp, Line, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(AlfaBackfireSources.names[sample - 1], color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("PLAY ▶", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable { onPreview(sample) }.padding(8.dp))
                SettingsToggle("ALLOW", allowed, compact = true) {
                    val next = if (allowed) value.allowedSamples - sample else value.allowedSamples + sample
                    onChange(value.copy(allowedSamples = next))
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(label: String, enabled: Boolean, compact: Boolean = false, onToggle: () -> Unit) {
    Row(
        modifier = (if (compact) Modifier.width(110.dp) else Modifier.fillMaxWidth()).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = if (enabled) Cyan else Muted, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.width(58.dp).height(28.dp).clip(RoundedCornerShape(50))
                .background(if (enabled) Cyan else Line).padding(4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).offset(x = if (enabled) 30.dp else 0.dp).clip(CircleShape).background(White))
        }
    }
}

@Composable
private fun BackfireSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    integer: Boolean = false,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val shown = if (integer) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.2f", value)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = CyanSoft, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text("$shown$suffix", color = White, fontSize = 12.sp)
        }
        Slider(value = value.toFloat().coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun MixerHeaderRow(
    drivetrain: DrivetrainState,
    transmissionPosition: TransmissionPosition,
    maxRpm: Double,
    redlineRpm: Double,
    selectedCarId: String,
    selectedCarName: String,
    selectedCarPreviewAsset: String,
    favoriteCarIds: Set<String>,
    onSelectCar: (String) -> Unit,
    onToggleCarFavorite: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel.copy(alpha = 0.92f))
            .border(1.dp, Line.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarTachometerHud(
            drivetrain = drivetrain,
            transmissionPosition = transmissionPosition,
            maxRpm = maxRpm,
            redlineRpm = redlineRpm,
            modifier = Modifier.weight(0.58f).fillMaxHeight(),
        )
        CarDropdownSelector(
            selectedCarId = selectedCarId,
            selectedCarName = selectedCarName,
            selectedCarPreviewAsset = selectedCarPreviewAsset,
            favoriteCarIds = favoriteCarIds,
            onSelectCar = onSelectCar,
            onToggleCarFavorite = onToggleCarFavorite,
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
        )
    }
}

@Composable
private fun BarTachometerHud(
    drivetrain: DrivetrainState,
    transmissionPosition: TransmissionPosition,
    maxRpm: Double,
    redlineRpm: Double,
    modifier: Modifier = Modifier,
) {
    val rpmFraction = (drivetrain.rpm / maxRpm.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val redlineFraction = (redlineRpm / maxRpm.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val gear = if (transmissionPosition == TransmissionPosition.DRIVE) drivetrain.gear.toString() else transmissionPosition.displayName
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(drivetrain.rpm.toInt().toString(), color = White, fontSize = 42.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(" RPM", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                MixerTelemetryReadout("SPEED", drivetrain.realOrDocumentedRawSpeedKmh.toInt().toString(), "km/h")
                Spacer(Modifier.width(12.dp))
                MixerTelemetryReadout("PRED SPEED", String.format(Locale.US, "%.2f", drivetrain.presentationSpeedKmh), "km/h")
            }
            Text(gear, color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        Box(modifier = Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF061018)).border(1.dp, Line, RoundedCornerShape(4.dp))) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(brush = Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.35f), Amber, Red)), size = androidx.compose.ui.geometry.Size(size.width * rpmFraction, size.height))
                drawRect(color = Red.copy(alpha = 0.18f), topLeft = Offset(size.width * redlineFraction, 0f), size = androidx.compose.ui.geometry.Size(size.width * (1f - redlineFraction), size.height))
                drawLine(Red, Offset(size.width * redlineFraction, 0f), Offset(size.width * redlineFraction, size.height), 2f)
            }
        }
    }
}


@Composable
private fun MixerTelemetryReadout(
    label: String,
    value: String,
    unit: String? = null,
) {
    Column {
        Text(
            text = label,
            color = Muted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = Cyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            unit?.let {
                Text(
                    text = it,
                    color = CyanSoft,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CarDropdownSelector(
    selectedCarId: String,
    selectedCarName: String,
    selectedCarPreviewAsset: String,
    favoriteCarIds: Set<String>,
    onSelectCar: (String) -> Unit,
    onToggleCarFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val audioAssetResolver = remember(context) {
        FmodBankResolver(context.applicationContext)
    }
    Box(
        modifier = modifier.padding(start = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(PanelBright)
                .border(1.dp, Line, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarPreviewThumbnail(
                profile = FmodBankProfiles.find(selectedCarId),
                audioAssetResolver = audioAssetResolver,
                contentDescription = CarDisplayNameFormatter.format(selectedCarName),
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { expanded = true },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                ) {
                    Text("SIMULATED CAR", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        text = CarDisplayNameFormatter.format(selectedCarName),
                        color = White,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

            }
        }
        if (expanded) {
            // Reuse the dashboard picker verbatim so Mixer and Classic expose the same
            // installed-car groups, adaptive grid, previews, and selection behavior.
            CarGridSelectionDialog(
                selectedCarId = selectedCarId,
                favoriteCarIds = favoriteCarIds,
                onSelectCar = onSelectCar,
                onToggleFavorite = onToggleCarFavorite,
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
internal fun CarFavoriteStarButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val buttonSize = (36f * scale).dp
    val iconSize = (22f * scale).dp

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavorite) {
                Icons.Filled.Star
            } else {
                Icons.Filled.StarBorder
            },
            contentDescription = if (isFavorite) {
                "Remove favorite"
            } else {
                "Add favorite"
            },
            tint = if (isFavorite) {
                Color(0xFFFFD54F)
            } else {
                Color.White.copy(alpha = 0.82f)
            },
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Positions the selected car in the center of the grid without animation. */
private suspend fun LazyGridState.scrollItemToCenterInstant(index: Int) {
    if (index < 0) {
        return
    }

    snapshotFlow { layoutInfo.viewportSize.height }
        .first { height -> height > 0 }

    snapshotFlow { layoutInfo.totalItemsCount }
        .first { count -> count > index }

    scrollToItem(index)

    repeat(12) {
        val itemInfo = layoutInfo.visibleItemsInfo.find { visibleItem -> visibleItem.index == index }
        if (itemInfo != null) {
            val viewportCenter = layoutInfo.viewportSize.height / 2f
            val itemCenter = itemInfo.offset.y + itemInfo.size.height / 2f
            val delta = itemCenter - viewportCenter
            if (kotlin.math.abs(delta) > 1f) {
                scroll {
                    scrollBy(delta)
                }
            }
            return
        }

        delay(16)
        scrollToItem(index)
    }
}

@Composable
internal fun CarGridSelectionDialog(
    selectedCarId: String,
    favoriteCarIds: Set<String>,
    onSelectCar: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resolver = remember(context) { FmodBankResolver(context.applicationContext) }
    val pickerPreferences = remember(context) {
        context.getSharedPreferences(AppPreferenceStores.CAR_PICKER_GROUP, android.content.Context.MODE_PRIVATE)
    }
    val installedProfiles = remember(resolver) { FmodBankProfiles.all.filter(resolver::isInstalled) }
    var selectedGroup by remember(selectedCarId) {
        mutableStateOf(
            FmodBankProfiles.catalogGroup ?: run {
                val selectedPackGroup = FmodBankProfiles.find(selectedCarId).packGroup
                if (
                    selectedPackGroup == FmodBankProfiles.moddedCarsPackId ||
                    selectedPackGroup == FmodBankProfiles.originalCarsPackId
                ) {
                    selectedPackGroup
                } else {
                    pickerPreferences.getString(
                        "selected",
                        FmodBankProfiles.moddedCarsPackId,
                    )?.takeIf {
                        it == FmodBankProfiles.moddedCarsPackId || it == FmodBankProfiles.originalCarsPackId
                    } ?: FmodBankProfiles.moddedCarsPackId
                }
            },
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    val showGroupFilters = FmodBankProfiles.catalogGroup == null
    val groupProfiles = remember(installedProfiles, selectedGroup) {
        installedProfiles.filter { it.packGroup == selectedGroup }
    }
    val visibleProfiles = remember(groupProfiles, searchQuery) {
        groupProfiles.filter { profile -> carPickerProfileMatchesSearch(profile, searchQuery) }
    }
    val gridState = rememberLazyGridState()
    var isGridReady by remember { mutableStateOf(false) }
    var didInitialScroll by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        delay(1)
        focusManager.clearFocus()
    }
    LaunchedEffect(selectedCarId, visibleProfiles) {
        if (didInitialScroll) {
            return@LaunchedEffect
        }

        val selectedIndex = visibleProfiles.indexOfFirst { profile -> profile.id == selectedCarId }
        if (selectedIndex < 0) {
            didInitialScroll = true
            isGridReady = true
            return@LaunchedEffect
        }

        isGridReady = false
        gridState.scrollItemToCenterInstant(selectedIndex)
        didInitialScroll = true
        isGridReady = true
    }
    // Disable the platform's narrow default dialog width so the picker can span the display.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PanelBright),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "SELECT CAR",
                        color = CyanSoft,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                    CarPickerSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close car picker",
                            tint = White,
                        )
                    }
                }
                if (showGroupFilters) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(top = 10.dp),
                    ) {
                        listOf(
                            FmodBankProfiles.moddedCarsPackId to "MODDED CARS",
                            FmodBankProfiles.originalCarsPackId to "ORIGINAL CARS",
                        ).forEach { (group, label) ->
                            Surface(
                                color = if (selectedGroup == group) Cyan.copy(alpha = 0.24f) else Panel,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (selectedGroup == group) Cyan else Line),
                                modifier = Modifier.clickable {
                                    selectedGroup = group
                                    pickerPreferences.edit().putString("selected", group).apply()
                                },
                            ) {
                                Text(
                                    label,
                                    color = if (selectedGroup == group) Cyan else Muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        // Choose as many cards as fit at runtime; this remains usable on both the
                        // 1920x1080 emulator and narrower vehicle displays.
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isGridReady) 1f else 0f),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(visibleProfiles, key = { it.id }) { profile ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (profile.id == selectedCarId) Cyan.copy(alpha = 0.18f) else Panel)
                                    .border(1.dp, if (profile.id == selectedCarId) Cyan else Line, RoundedCornerShape(10.dp))
                                    .clickable {
                                        onDismiss()
                                        onSelectCar(profile.id)
                                    }
                                    .padding(10.dp),
                            ) {
                                CarPreviewThumbnail(
                                    profile = profile,
                                    audioAssetResolver = resolver,
                                    contentDescription = CarDisplayNameFormatter.format(profile.displayName),
                                    isFavorite = profile.id in favoriteCarIds,
                                    onToggleFavorite = { onToggleFavorite(profile.id) },
                                    favoriteStarPadding = 2.dp,
                                    showFavoriteStarOnlyWhenFavorited = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 128.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                                Text(
                                    text = CarDisplayNameFormatter.format(profile.displayName),
                                    color = White,
                                    fontSize = 15.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = if (profile.id == selectedCarId) FontWeight.Black else FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 9.dp),
                                )
                            }
                        }
                    }

                    if (!isGridReady) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {},
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Cyan)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarPickerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingEnabled by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(editingEnabled) {
        if (editingEnabled) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            enabled = editingEnabled,
            textStyle = TextStyle(
                color = White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(Cyan),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "SEARCH CARS",
                            color = Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    innerTextField()
                }
            },
        )

        if (!editingEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        editingEnabled = true
                    },
            )
        }
    }
}

private fun carPickerProfileMatchesSearch(profile: FmodBankProfile, query: String): Boolean {
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    if (normalizedQuery.isEmpty()) {
        return true
    }

    val formattedName = CarDisplayNameFormatter.format(profile.displayName).lowercase(Locale.getDefault())
    val rawName = profile.displayName.lowercase(Locale.getDefault())
    val profileId = profile.id.lowercase(Locale.getDefault())

    return formattedName.contains(normalizedQuery)
        || rawName.contains(normalizedQuery)
        || profileId.contains(normalizedQuery)
}

@Composable
private fun CarPreviewThumbnail(
    profile: FmodBankProfile,
    audioAssetResolver: FmodBankResolver,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    favoriteStarPadding: Dp = 6.dp,
    showFavoriteStarOnlyWhenFavorited: Boolean = false,
) {
    val installedPreviewPath = audioAssetResolver.previewFile(profile)?.path
    val preview = remember(profile.id, installedPreviewPath) {
        runCatching {
            audioAssetResolver.openCarPreviewInput(profile)?.use { input ->
                val bitmap = requireNotNull(BitmapFactory.decodeStream(input))
                LoadedCarPreview(
                    image = bitmap.asImageBitmap(),
                    aspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat(),
                )
            }
        }.getOrNull()
    }

    val aspectRatio = preview?.aspectRatio ?: (16f / 9f)

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(
                bitmap = preview.image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.apex_v10_car),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val shouldShowFavoriteStar = onToggleFavorite != null &&
            (!showFavoriteStarOnlyWhenFavorited || isFavorite)

        if (shouldShowFavoriteStar) {
            CarFavoriteStarButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(favoriteStarPadding),
            )
        }
    }
}

private data class LoadedCarPreview(
    val image: ImageBitmap,
    val aspectRatio: Float,
)

@Composable
private fun FmodSourceMeter(
    source: FmodSourceState,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val level = source.audibility.toFloat().coerceIn(0f, 1f)
    val fillColor = outputMeterFillColor(level)
    val meterLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.RIGHT
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Panel.copy(alpha = 0.88f))
            .border(2.dp, if (highlight) Cyan else Line.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = source.soundName,
                color = White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        source.isVirtual -> "VIRTUAL"
                        source.isActive && source.audibility <= 0.002 ->
                            "SILENT • ${source.voiceCount} VOICE${if (source.voiceCount == 1) "" else "S"}"
                        source.isActive ->
                            "${source.voiceCount} VOICE${if (source.voiceCount == 1) "" else "S"}"
                        else -> "SILENT"
                    },
                    color = if (source.isActive) Cyan else Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = String.format(Locale.US, "ROUTE %.2fx", source.routeGain),
                    color = Muted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Text(
            text = source.eventName.uppercase().replace('_', ' '),
            color = CyanSoft,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF061018))
                    .border(1.dp, Line.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
            ) {
                if (level > 0.002f) {
                    drawRect(
                        color = fillColor,
                        size = androidx.compose.ui.geometry.Size(
                            width = size.width * level,
                            height = size.height,
                        ),
                        alpha = 0.88f,
                    )
                }
                meterLabelPaint.textSize = size.height * 0.55f
                meterLabelPaint.color = fillColor.toArgb()
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        "${source.audibilityPercent}%",
                        size.width - 8f,
                        size.height * 0.70f,
                        meterLabelPaint,
                    )
                }
            }
        }
    }
}

/** Green → cyan → amber → red as the live output meter fills. */
private fun outputMeterFillColor(level: Float): Color {
    return when {
        level <= 0.01f -> Muted.copy(alpha = 0.35f)
        level < 0.30f -> blendColors(Green.copy(alpha = 0.65f), Cyan, level / 0.30f)
        level < 0.60f -> blendColors(Cyan, Amber, (level - 0.30f) / 0.30f)
        level < 0.85f -> blendColors(Amber, Color(0xFFFF7040), (level - 0.60f) / 0.25f)
        else -> blendColors(Color(0xFFFF7040), Red, (level - 0.85f) / 0.15f)
    }
}

private fun blendColors(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t,
    )
}

private val Night = Color(0xFF060606)
private val Panel = Color(0xFF0B1925)
private val PanelBright = Color(0xFF112837)
private val Line = Color(0xFF1A3C4A)
private val Cyan = Color(0xFF35E8F2)
private val CyanSoft = Color(0xFF5FBAC7)
private val Green = Color(0xFF38E58C)
private val Red = Color(0xFFFF394F)
private val Amber = Color(0xFFFFC456)
private val White = Color(0xFFF5FAFD)
private val Muted = Color(0xFF88A2B2)
