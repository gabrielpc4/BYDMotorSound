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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
import com.gabrielpc.enginesoundsimulator.audio.FmodBankDiagnostics
import com.gabrielpc.enginesoundsimulator.audio.FmodBankProfile
import com.gabrielpc.enginesoundsimulator.audio.FmodBankProfiles
import com.gabrielpc.enginesoundsimulator.audio.FmodBankResolver
import com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective
import com.gabrielpc.enginesoundsimulator.audio.FmodEventSection
import com.gabrielpc.enginesoundsimulator.audio.MixerCarSpecificGains
import com.gabrielpc.enginesoundsimulator.audio.MixerEventCategory
import com.gabrielpc.enginesoundsimulator.audio.MixerGainScope
import com.gabrielpc.enginesoundsimulator.audio.MixerGainScopeRepository
import com.gabrielpc.enginesoundsimulator.audio.MixerGlobalGains
import com.gabrielpc.enginesoundsimulator.audio.AppVolumeSettings
import com.gabrielpc.enginesoundsimulator.audio.AcousticDiagnosticProgress
import com.gabrielpc.enginesoundsimulator.audio.AcousticDiagnosticStatus
import com.gabrielpc.enginesoundsimulator.audio.AcousticDiagnosticSummary
import com.gabrielpc.enginesoundsimulator.audio.AcousticAdjustmentState
import com.gabrielpc.enginesoundsimulator.audio.AcousticAdjustmentValidity
import com.gabrielpc.enginesoundsimulator.audio.LoudnessCalibrationProgress
import com.gabrielpc.enginesoundsimulator.audio.LoudnessCalibrationStatus
import com.gabrielpc.enginesoundsimulator.audio.LoudnessNormalizationSummary
import com.gabrielpc.enginesoundsimulator.audio.LoudnessNormalizationState
import com.gabrielpc.enginesoundsimulator.audio.LoudnessNormalizationValidity
import com.gabrielpc.enginesoundsimulator.audio.CatalogGainEntryStatus
import com.gabrielpc.enginesoundsimulator.audio.CatalogGainSettings
import com.gabrielpc.enginesoundsimulator.audio.CatalogGainTableEntry
import com.gabrielpc.enginesoundsimulator.audio.ManualLoudnessRepository
import com.gabrielpc.enginesoundsimulator.audio.ManualLoudnessTableEntry
import com.gabrielpc.enginesoundsimulator.audio.FmodSourceState
import com.gabrielpc.enginesoundsimulator.audio.FmodUpdateRate
import com.gabrielpc.enginesoundsimulator.drive.GearProfileSelection
import com.gabrielpc.enginesoundsimulator.drive.CruisingShiftOffsetByTachMaxRpm
import com.gabrielpc.enginesoundsimulator.drive.DriveSnapshot
import com.gabrielpc.enginesoundsimulator.drive.BackfireSettings
import com.gabrielpc.enginesoundsimulator.drive.VirtualGearSpeedBoundaries
import com.gabrielpc.enginesoundsimulator.drive.VirtualGearSpeedBoundariesSettings
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioGain
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioGainResolver
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioSettings
import com.gabrielpc.enginesoundsimulator.drive.MinimumAudioThrottle
import com.gabrielpc.enginesoundsimulator.drive.AutomaticDownshiftMilliseconds
import com.gabrielpc.enginesoundsimulator.drive.AutomaticUpshiftMilliseconds
import com.gabrielpc.enginesoundsimulator.drive.ManualAutodownshiftRpm
import com.gabrielpc.enginesoundsimulator.drive.ManualRedlineHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingEnterDelayMilliseconds
import com.gabrielpc.enginesoundsimulator.drive.RacingEnterMinThrottlePercent
import com.gabrielpc.enginesoundsimulator.drive.KickdownStompDeltaPercent
import com.gabrielpc.enginesoundsimulator.drive.KickdownStompMinThrottlePercent
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnThrottlePercent
import com.gabrielpc.enginesoundsimulator.drive.PedalAudioThrottleRampMilliseconds
import com.gabrielpc.enginesoundsimulator.simulation.VirtualGearProfile
import com.gabrielpc.enginesoundsimulator.drive.AlfaBackfireSources
import com.gabrielpc.enginesoundsimulator.simulation.DrivetrainState
import com.gabrielpc.enginesoundsimulator.ui.theme.Accent
import com.gabrielpc.enginesoundsimulator.ui.theme.AccentSoft
import com.gabrielpc.enginesoundsimulator.ui.theme.Background
import com.gabrielpc.enginesoundsimulator.ui.theme.ControlShape
import com.gabrielpc.enginesoundsimulator.ui.theme.Danger
import com.gabrielpc.enginesoundsimulator.ui.theme.DashboardSkin
import com.gabrielpc.enginesoundsimulator.ui.theme.DisplayFamily
import com.gabrielpc.enginesoundsimulator.ui.theme.Favorite
import com.gabrielpc.enginesoundsimulator.ui.theme.LocalDashboardSkin
import com.gabrielpc.enginesoundsimulator.ui.theme.Master
import com.gabrielpc.enginesoundsimulator.ui.theme.MeterTrack
import com.gabrielpc.enginesoundsimulator.ui.theme.Muted
import com.gabrielpc.enginesoundsimulator.ui.theme.OnSurface
import com.gabrielpc.enginesoundsimulator.ui.theme.Outline
import com.gabrielpc.enginesoundsimulator.ui.theme.PanelShape
import com.gabrielpc.enginesoundsimulator.ui.theme.Success
import com.gabrielpc.enginesoundsimulator.ui.theme.StadiumShape
import com.gabrielpc.enginesoundsimulator.ui.theme.softFillShape
import com.gabrielpc.enginesoundsimulator.ui.theme.skinPillShape
import com.gabrielpc.enginesoundsimulator.ui.theme.skinShape
import com.gabrielpc.enginesoundsimulator.ui.theme.Surface
import com.gabrielpc.enginesoundsimulator.ui.theme.SurfaceRaised
import com.gabrielpc.enginesoundsimulator.ui.theme.Warning
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
        tint = Accent,
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    )
}

internal const val MIXER_SCREEN_HORIZONTAL_PADDING = 20

/** Reserve scroll space so mixer sliders can scroll clear of the floating pedals row. */
private val MIXER_PEDALS_OVERLAY_HEIGHT = 240.dp

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
    onAppVolumePercentChange: (Int) -> Unit,
    onMixerCarSpecificGainsChange: (MixerCarSpecificGains) -> Unit,
    onResetMixerCarSpecificGains: () -> Unit,
    onEventMute: (String, Boolean) -> Unit,
    onEventSolo: (String, Boolean) -> Unit,
    soundPerspective: EngineSoundPerspective,
    onSoundPerspectiveChange: (EngineSoundPerspective) -> Unit,
    exteriorPureAudio: Boolean,
    onExteriorPureAudioChange: (Boolean) -> Unit,
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
                exteriorPureAudio = exteriorPureAudio,
                onExteriorPureAudioChange = onExteriorPureAudioChange,
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
                                color = AccentSoft,
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
                carBackfireOverrideGain = state.backfireOverrideGain,
                carShiftOverrideGain = state.shiftOverrideGain,
                hasTurbo = state.hasTurbo,
                hasSupercharger = state.hasSupercharger,
                mixerGains = mixerGains,
                mixerSpecificGains = mixerSpecificGains,
                appVolumeSettings = state.appVolumeSettings,
                loudnessNormalization = state.currentLoudnessNormalization,
                acousticAdjustment = state.currentAcousticAdjustment,
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
                onAppVolumePercentChange = onAppVolumePercentChange,
                onMixerSpecificGainsChange = { updated ->
                    mixerSpecificGains = updated
                    onMixerCarSpecificGainsChange(updated)
                },
                onResetCarSpecificGains = {
                    onResetMixerCarSpecificGains()
                    mixerSpecificGains = MixerCarSpecificGains()
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
    exteriorPureAudio: Boolean,
    onExteriorPureAudioChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(skinShape(8.dp))
            .background(Surface.copy(alpha = 0.92f))
            .border(1.dp, Outline.copy(alpha = 0.65f), skinShape(8.dp))
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
                color = if (active) Accent else Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(softFillShape(5.dp))
                    .background(if (active) Accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onPerspectiveSelected(option) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (perspective == EngineSoundPerspective.EXTERIOR) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "PURE",
                color = if (exteriorPureAudio) {
                    Accent
                } else {
                    Muted
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = exteriorPureAudio,
                onCheckedChange = onExteriorPureAudioChange,
            )
        }
    }
}

@Composable
private fun MixerControlsPanel(
    soundPerspective: EngineSoundPerspective,
    carBackfireOverrideGain: Float,
    carShiftOverrideGain: Float,
    hasTurbo: Boolean,
    hasSupercharger: Boolean,
    mixerGains: MixerGlobalGains,
    mixerSpecificGains: MixerCarSpecificGains,
    appVolumeSettings: AppVolumeSettings,
    loudnessNormalization: LoudnessNormalizationState,
    acousticAdjustment: AcousticAdjustmentState,
    mutedEvents: Map<String, Boolean>,
    soloedEvents: Map<String, Boolean>,
    onToggleCategoryMute: (MixerEventCategory, Boolean) -> Unit,
    onToggleCategorySolo: (MixerEventCategory, Boolean) -> Unit,
    onMixerGainsChange: (MixerGlobalGains) -> Unit,
    onAppVolumePercentChange: (Int) -> Unit,
    onMixerSpecificGainsChange: (MixerCarSpecificGains) -> Unit,
    onResetCarSpecificGains: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val gainScopeRepository = remember(context) {
        MixerGainScopeRepository(context.applicationContext)
    }
    var gainScope by remember {
        mutableStateOf(gainScopeRepository.load())
    }
    val cardShape = skinShape(8.dp)
    val cardModifier = modifier
        .fillMaxHeight()
        .clip(cardShape)
        .then(
            if (gainScope == MixerGainScope.GLOBAL) {
                Modifier.border(1.dp, Outline, cardShape)
            } else {
                Modifier
                    .background(Surface)
                    .border(1.dp, Outline, cardShape)
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
                onScopeSelected = { selected ->
                    gainScope = selected
                    gainScopeRepository.save(selected)
                },
                onResetSpecificGains = onResetCarSpecificGains,
            )
            if (gainScope == MixerGainScope.GLOBAL) {
                AppVolumeSlider(
                    settings = appVolumeSettings,
                    loudnessNormalization = loudnessNormalization,
                    acousticAdjustment = acousticAdjustment,
                    onPercentChange = onAppVolumePercentChange,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = Master.copy(alpha = 0.45f),
                )
            }
            if (gainScope == MixerGainScope.SPECIFIC) {
                MixerLayerGainSlider(
                    label = "OVERALL",
                    layerValue = mixerSpecificGains.overall,
                    globalValue = 1f,
                    specificValue = mixerSpecificGains.overall,
                    accentColor = Master,
                    onValueChange = {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(overall = it))
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = Master.copy(alpha = 0.45f),
                )
                MixerLayerGainSlider(
                    label = "ENGINE IDLE",
                    layerValue = mixerSpecificGains.engineIdle,
                    globalValue = 1f,
                    specificValue = mixerSpecificGains.engineIdle,
                    accentColor = Master,
                    onValueChange = { value ->
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(engineIdle = value))
                    },
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
                globalValue = mixerGains.engineInterior,
                specificValue = mixerSpecificGains.engineInterior,
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
                globalValue = mixerGains.engineExterior,
                specificValue = mixerSpecificGains.engineExterior,
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
                globalValue = mixerGains.effectsHost,
                specificValue = mixerSpecificGains.effectsHost,
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
                color = Outline,
            )
            MixerLayerGainSlider(
                label = "TRANSMISSION",
                eventCategory = MixerEventCategory.TRANSMISSION,
                mutedEvents = mutedEvents,
                soloedEvents = soloedEvents,
                onToggleCategoryMute = onToggleCategoryMute,
                onToggleCategorySolo = onToggleCategorySolo,
                layerValue = layerValueForScope(gainScope, mixerGains.transmission, mixerSpecificGains.transmission),
                globalValue = mixerGains.transmission,
                specificValue = mixerSpecificGains.transmission,
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
                globalValue = mixerGains.gearShift,
                specificValue = mixerSpecificGains.gearShift,
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
                    globalValue = mixerGains.turbo,
                    specificValue = mixerSpecificGains.turbo,
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
                    globalValue = mixerGains.supercharger,
                    specificValue = mixerSpecificGains.supercharger,
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
                globalValue = mixerGains.backfire,
                specificValue = mixerSpecificGains.backfire,
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
                globalValue = mixerGains.limiter,
                specificValue = mixerSpecificGains.limiter,
                onValueChange = { value ->
                    if (gainScope == MixerGainScope.GLOBAL) {
                        onMixerGainsChange(mixerGains.copy(limiter = value))
                    } else {
                        onMixerSpecificGainsChange(mixerSpecificGains.copy(limiter = value))
                    }
                },
            )
            if (gainScope == MixerGainScope.GLOBAL) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = Outline,
                )
                MixerLayerGainSlider(
                    label = "POPS & BANGS OVERRIDE",
                    layerValue = mixerGains.backfireOverrideGain,
                    globalValue = mixerGains.backfireOverrideGain,
                    specificValue = carBackfireOverrideGain,
                    onValueChange = { value ->
                        onMixerGainsChange(mixerGains.copy(backfireOverrideGain = value))
                    },
                )
                MixerLayerGainSlider(
                    label = "SHIFT SOUNDS OVERRIDE",
                    layerValue = mixerGains.shiftOverrideGain,
                    globalValue = mixerGains.shiftOverrideGain,
                    specificValue = carShiftOverrideGain,
                    onValueChange = { value ->
                        onMixerGainsChange(mixerGains.copy(shiftOverrideGain = value))
                    },
                )
            }
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
    onResetSpecificGains: () -> Unit,
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
                    color = if (active) Accent else Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(softFillShape(5.dp))
                        .background(if (active) Accent.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onScopeSelected(option) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        if (scope == MixerGainScope.SPECIFIC) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Per-car profile for ${listeningPerspective.displayName}",
                    color = Warning,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = "RESET",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier
                        .clip(softFillShape(5.dp))
                        .border(1.dp, Outline, softFillShape(5.dp))
                        .clickable(onClick = onResetSpecificGains)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AppVolumeSlider(
    settings: AppVolumeSettings,
    loudnessNormalization: LoudnessNormalizationState,
    acousticAdjustment: AcousticAdjustmentState,
    onPercentChange: (Int) -> Unit,
) {
    val percent = settings.normalized().percent
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "APP VOLUME",
                color = Master,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = "$percent%",
                color = OnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onPercentChange(it.roundToInt().coerceIn(0, 200)) },
            valueRange = AppVolumeSettings.MIN_PERCENT.toFloat()..AppVolumeSettings.MAX_PERCENT.toFloat(),
            steps = AppVolumeSettings.sliderSteps(),
            colors = SliderDefaults.colors(
                thumbColor = Master,
                activeTrackColor = Master,
                inactiveTrackColor = Outline,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0%", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("100%", color = AccentSoft, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("200%", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = when (loudnessNormalization.validity) {
                LoudnessNormalizationValidity.VALID -> {
                    "LOUDNESS NORMALIZATION ${String.format(Locale.US, "%.1f dB", loudnessNormalization.normalizationDb)}"
                }
                LoudnessNormalizationValidity.STALE -> "LOUDNESS NORMALIZATION STALE • UNITY"
                LoudnessNormalizationValidity.MISSING -> "ENGINE LOUDNESS UNCALIBRATED • UNITY"
                LoudnessNormalizationValidity.EXCLUDED -> "ORIGINAL CAR • AUTOMATIC NORMALIZATION DISABLED"
            },
            color = when (loudnessNormalization.validity) {
                LoudnessNormalizationValidity.VALID -> AccentSoft
                LoudnessNormalizationValidity.STALE -> Warning
                LoudnessNormalizationValidity.MISSING -> Muted
                LoudnessNormalizationValidity.EXCLUDED -> Muted
            },
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (acousticAdjustment.validity != AcousticAdjustmentValidity.EXCLUDED) {
            Text(
                text = when (acousticAdjustment.validity) {
                    AcousticAdjustmentValidity.VALID -> {
                        "IPHONE ACOUSTIC ADJUSTMENT ${String.format(Locale.US, "%+.1f dB", acousticAdjustment.adjustmentDb)}"
                    }
                    AcousticAdjustmentValidity.STALE -> "IPHONE ACOUSTIC ADJUSTMENT STALE • UNITY"
                    AcousticAdjustmentValidity.FAILED -> "IPHONE ACOUSTIC MEASUREMENT FAILED • UNITY"
                    AcousticAdjustmentValidity.MISSING -> "IPHONE ACOUSTIC ADJUSTMENT MISSING • UNITY"
                    AcousticAdjustmentValidity.EXCLUDED -> ""
                },
                color = when (acousticAdjustment.validity) {
                    AcousticAdjustmentValidity.VALID -> AccentSoft
                    AcousticAdjustmentValidity.STALE, AcousticAdjustmentValidity.FAILED -> Warning
                    AcousticAdjustmentValidity.MISSING, AcousticAdjustmentValidity.EXCLUDED -> Muted
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MixerLayerGainSlider(
    label: String,
    layerValue: Float,
    globalValue: Float,
    specificValue: Float,
    accentColor: Color = AccentSoft,
    eventCategory: MixerEventCategory? = null,
    mutedEvents: Map<String, Boolean> = emptyMap(),
    soloedEvents: Map<String, Boolean> = emptyMap(),
    onToggleCategoryMute: (MixerEventCategory, Boolean) -> Unit = { _, _ -> },
    onToggleCategorySolo: (MixerEventCategory, Boolean) -> Unit = { _, _ -> },
    onValueChange: (Float) -> Unit,
) {
    val clampedLayerValue = MixerGlobalGains.snapToStep(layerValue)
    val effectiveValue = globalValue * specificValue
    val sliderColors = SliderDefaults.colors(
        thumbColor = accentColor,
        activeTrackColor = accentColor,
        inactiveTrackColor = Outline,
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
                        color = if (muted) Accent else Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable {
                            onToggleCategoryMute(eventCategory, !muted)
                        },
                    )
                    Text(
                        text = "S",
                        color = if (soloed) Accent else Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable {
                            onToggleCategorySolo(eventCategory, !soloed)
                        },
                    )
                }
            }
            Text(
                text = "${MixerGlobalGains.formatMultiplier(clampedLayerValue)} → ${MixerGlobalGains.formatMultiplier(effectiveValue)}",
                color = if (accentColor == Warning) Warning else OnSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = clampedLayerValue,
            onValueChange = { value ->
                onValueChange(MixerGlobalGains.snapToStep(value))
            },
            valueRange = MixerGlobalGains.MIN..MixerGlobalGains.MAX,
            steps = MixerGlobalGains.sliderSteps(),
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SettingsScreen(
    onExportSettings: () -> Unit,
    onResetAll: () -> Unit,
    onRescanBanks: () -> Unit,
    loudnessNormalizationSummary: LoudnessNormalizationSummary,
    loudnessCalibrationProgress: LoudnessCalibrationProgress,
    onCalibrateAllCars: () -> Unit,
    onResumeLoudnessCalibration: () -> Unit,
    onCancelLoudnessCalibration: () -> Unit,
    acousticDiagnosticSummary: AcousticDiagnosticSummary,
    acousticDiagnosticProgress: AcousticDiagnosticProgress,
    iphoneMeterLinked: Boolean,
    iphoneMeterSelected: Boolean,
    iphoneCalibrationLogLines: List<String>,
    onAssociateIphoneMeter: () -> Unit,
    onForgetIphoneMeter: () -> Unit,
    onClearIphoneCalibration: () -> Unit,
    onRecoverLenientAcousticMeasurements: () -> Unit,
    onClearIphoneCalibrationLog: () -> Unit,
    catalogGainSettings: CatalogGainSettings,
    catalogGainTable: List<CatalogGainTableEntry>,
    onCatalogGainSettingsChange: (CatalogGainSettings) -> Unit,
    manualLoudnessTable: List<ManualLoudnessTableEntry>,
    onManualLoudnessEnabledChange: (Boolean) -> Unit,
    onManualLoudnessDbChange: (String, EngineSoundPerspective, Double) -> Unit,
    onManualLoudnessPreviewChange: (String, Boolean) -> Unit,
    onManualLoudnessPreviewExteriorChange: (String, Boolean) -> Unit,
    onStopManualLoudnessPreviews: () -> Unit,
    onSaveManualLoudnessAsDefault: () -> Unit,
    onExportManualLoudnessPreset: () -> Unit,
    clubReferenceMediaPlaying: Boolean,
    onToggleClubReferenceMedia: () -> Unit,
    onStartAcousticDiagnostic: (String?) -> Unit,
    onResumeAcousticDiagnostic: () -> Unit,
    onCancelAcousticDiagnostic: () -> Unit,
    fmodUpdateRateHz: Int,
    onFmodUpdateRateChange: (Int) -> Unit,
    backfireSettings: BackfireSettings,
    onBackfireSettingsChange: (BackfireSettings) -> Unit,
    virtualGearSpeedBoundaries: VirtualGearSpeedBoundariesSettings,
    gearProfileSelection: GearProfileSelection,
    onVirtualGearSpeedBoundaryChange: (Int, Int, Int) -> Unit,
    onRestoreVirtualGearSpeedBoundaries: (Int) -> Unit,
    allowManualOnLaunchEnabled: Boolean,
    onAllowManualOnLaunchEnabledChange: (Boolean) -> Unit,
    manualTransmissionKickdownEnabled: Boolean,
    onManualTransmissionKickdownEnabledChange: (Boolean) -> Unit,
    minimumAudioThrottle: Float,
    onMinimumAudioThrottleChange: (Float) -> Unit,
    racingEnterMinThrottlePercent: Int,
    onRacingEnterMinThrottlePercentChange: (Int) -> Unit,
    racingReturnThrottlePercent: Int,
    onRacingReturnThrottlePercentChange: (Int) -> Unit,
    racingReturnHoldSeconds: Int,
    onRacingReturnHoldSecondsChange: (Int) -> Unit,
    kickdownStompDeltaPercent: Int,
    onKickdownStompDeltaPercentChange: (Int) -> Unit,
    kickdownStompMinThrottlePercent: Int,
    onKickdownStompMinThrottlePercentChange: (Int) -> Unit,
    racingEnterDelayMilliseconds: Int,
    onRacingEnterDelayMillisecondsChange: (Int) -> Unit,
    automaticUpshiftMilliseconds: Int,
    onAutomaticUpshiftMillisecondsChange: (Int) -> Unit,
    automaticDownshiftMilliseconds: Int,
    onAutomaticDownshiftMillisecondsChange: (Int) -> Unit,
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
    lowSpeedCrawlRpmHoldEnabled: Boolean,
    onLowSpeedCrawlRpmHoldEnabledChange: (Boolean) -> Unit,
    cruisingShiftOffsetsByTachMaxRpm: Map<Int, Int>,
    onCruisingShiftOffsetForTachMaxRpmChange: (Int, Int) -> Unit,
    onPreviewBackfireSample: (Int) -> Unit,
    speedAudioSettings: SpeedAudioSettings,
    onSpeedAudioSettingsChange: (SpeedAudioSettings) -> Unit,
    liveUsesRacingGain: () -> Boolean,
    livePreparingCruising: () -> Boolean,
    liveSpeedKmh: () -> Double,
) {
    var selectedTab by remember { mutableStateOf(SettingsSection.SPEED_AUDIO) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showCalibrationConfirmation by remember { mutableStateOf(false) }
    var showAcousticConfirmation by remember { mutableStateOf(false) }
    var iphonePairingCode by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SETTINGS", color = OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Row(
            modifier = Modifier.fillMaxWidth().border(1.dp, Outline, skinShape(8.dp)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsTab("SPEED AUDIO", selectedTab == SettingsSection.SPEED_AUDIO) {
                selectedTab = SettingsSection.SPEED_AUDIO
            }
            SettingsTab("GENERAL", selectedTab == SettingsSection.GENERAL) {
                selectedTab = SettingsSection.GENERAL
            }
            SettingsTab("BACKFIRE", selectedTab == SettingsSection.BACKFIRE) {
                selectedTab = SettingsSection.BACKFIRE
            }
            SettingsTab("LOUDNESS", selectedTab == SettingsSection.LOUDNESS) {
                selectedTab = SettingsSection.LOUDNESS
            }
            SettingsTab("BANK IMPORT", selectedTab == SettingsSection.BANK_IMPORT) {
                selectedTab = SettingsSection.BANK_IMPORT
            }
        }
        when (selectedTab) {
            SettingsSection.SPEED_AUDIO -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                SpeedAudioSettingsPanel(
                    settings = speedAudioSettings,
                    liveUsesRacingGain = liveUsesRacingGain(),
                    livePreparingCruising = livePreparingCruising(),
                    liveSpeedKmh = liveSpeedKmh(),
                    onChange = onSpeedAudioSettingsChange,
                )
            }
            SettingsSection.GENERAL -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
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
            VirtualGearSpeedBoundariesSettingsControl(
                settings = virtualGearSpeedBoundaries,
                gearProfileSelection = gearProfileSelection,
                onBoundaryChange = onVirtualGearSpeedBoundaryChange,
                onRestorePreset = onRestoreVirtualGearSpeedBoundaries,
            )
            CruisingShiftOffsetsByTachMaxRpmControl(
                offsets = cruisingShiftOffsetsByTachMaxRpm,
                onOffsetChange = onCruisingShiftOffsetForTachMaxRpmChange,
            )
            AutomaticTransmissionSettingsControl(
                minimumAudioThrottle = minimumAudioThrottle,
                onMinimumAudioThrottleChange = onMinimumAudioThrottleChange,
                racingEnterMinThrottlePercent = racingEnterMinThrottlePercent,
                onRacingEnterMinThrottlePercentChange = onRacingEnterMinThrottlePercentChange,
                racingReturnThrottlePercent = racingReturnThrottlePercent,
                onRacingReturnThrottlePercentChange = onRacingReturnThrottlePercentChange,
                racingReturnHoldSeconds = racingReturnHoldSeconds,
                onRacingReturnHoldSecondsChange = onRacingReturnHoldSecondsChange,
                kickdownStompDeltaPercent = kickdownStompDeltaPercent,
                onKickdownStompDeltaPercentChange = onKickdownStompDeltaPercentChange,
                kickdownStompMinThrottlePercent = kickdownStompMinThrottlePercent,
                onKickdownStompMinThrottlePercentChange = onKickdownStompMinThrottlePercentChange,
                racingEnterDelayMilliseconds = racingEnterDelayMilliseconds,
                onRacingEnterDelayMillisecondsChange = onRacingEnterDelayMillisecondsChange,
                automaticUpshiftMilliseconds = automaticUpshiftMilliseconds,
                onAutomaticUpshiftMillisecondsChange = onAutomaticUpshiftMillisecondsChange,
                automaticDownshiftMilliseconds = automaticDownshiftMilliseconds,
                onAutomaticDownshiftMillisecondsChange = onAutomaticDownshiftMillisecondsChange,
                manualRedlineHoldSeconds = manualRedlineHoldSeconds,
                onManualRedlineHoldSecondsChange = onManualRedlineHoldSecondsChange,
                manualAutodownshiftRpm = manualAutodownshiftRpm,
                onManualAutodownshiftRpmChange = onManualAutodownshiftRpmChange,
                manualTransmissionKickdownEnabled = manualTransmissionKickdownEnabled,
                onManualTransmissionKickdownEnabledChange = onManualTransmissionKickdownEnabledChange,
                allowManualOnLaunchEnabled = allowManualOnLaunchEnabled,
                onAllowManualOnLaunchEnabledChange = onAllowManualOnLaunchEnabledChange,
                tachometerCruisingShiftRangeOverlayEnabled = tachometerCruisingShiftRangeOverlayEnabled,
                onTachometerCruisingShiftRangeOverlayEnabledChange = onTachometerCruisingShiftRangeOverlayEnabledChange,
                lowSpeedCrawlRpmHoldEnabled = lowSpeedCrawlRpmHoldEnabled,
                onLowSpeedCrawlRpmHoldEnabledChange = onLowSpeedCrawlRpmHoldEnabledChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onExportSettings,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Outline),
                ) {
                    Text("EXPORT SETTINGS", color = AccentSoft, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = { showResetConfirmation = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger.copy(alpha = 0.85f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("RESET ALL", color = OnSurface, fontWeight = FontWeight.Black)
                }
            }
            }
            SettingsSection.BACKFIRE -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                BackfireSettingsPanel(
                    settings = backfireSettings,
                    onChange = onBackfireSettingsChange,
                    onPreview = onPreviewBackfireSample,
                )
            }
            SettingsSection.LOUDNESS -> ManualLoudnessSettingsTab(
                settings = catalogGainSettings,
                tableEntries = manualLoudnessTable,
                onManualEnabledChange = onManualLoudnessEnabledChange,
                onAdjustmentDbChange = onManualLoudnessDbChange,
                onPreviewChange = onManualLoudnessPreviewChange,
                onPreviewExteriorChange = onManualLoudnessPreviewExteriorChange,
                onSaveManualLoudnessAsDefault = onSaveManualLoudnessAsDefault,
                onExportManualLoudnessPreset = onExportManualLoudnessPreset,
                onStopManualLoudnessPreviews = onStopManualLoudnessPreviews,
                clubReferenceMediaPlaying = clubReferenceMediaPlaying,
                onToggleClubReferenceMedia = onToggleClubReferenceMedia,
                modifier = Modifier.weight(1f),
            )
            SettingsSection.BANK_IMPORT -> BankImportDiagnosticsPanel(
                onRescanBanks = onRescanBanks,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = {
                Text("Reset all settings?", color = OnSurface, fontWeight = FontWeight.Black)
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
                    Text("RESET", color = Danger, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("CANCEL", color = AccentSoft, fontWeight = FontWeight.Black)
                }
            },
            containerColor = Surface,
        )
    }

    if (showCalibrationConfirmation) {
        val minimumSeconds = loudnessNormalizationSummary.totalCount * 6
        val minutes = minimumSeconds / 60
        val seconds = minimumSeconds % 60
        AlertDialog(
            onDismissRequest = { showCalibrationConfirmation = false },
            title = {
                Text("Calibrate all modded cars?", color = OnSurface, fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    "This will silently measure ${loudnessNormalizationSummary.totalCount} modded car/perspective pairs. " +
                        "Allow at least ${minutes}m ${seconds}s plus bank loading time.",
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalibrationConfirmation = false
                        onCalibrateAllCars()
                    },
                ) {
                    Text("CALIBRATE", color = Accent, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalibrationConfirmation = false }) {
                    Text("CANCEL", color = Muted, fontWeight = FontWeight.Black)
                }
            },
            containerColor = Surface,
        )
    }

    if (showAcousticConfirmation) {
        val eligiblePairs = loudnessNormalizationSummary.validCount
        val estimatedSeconds = eligiblePairs * 10
        AlertDialog(
            onDismissRequest = { showAcousticConfirmation = false },
            title = { Text("Calibrate modded cars with iPhone?", color = OnSurface, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Only modded cars will be measured. The resulting per-car adjustment is applied automatically. " +
                        "Place the iPhone at driver head height. Keep the car stopped, close doors and windows, " +
                        "and turn climate control off. The engine sweep will be audible for about " +
                        "${estimatedSeconds / 60}m ${estimatedSeconds % 60}s plus bank loading.",
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAcousticConfirmation = false
                        onStartAcousticDiagnostic(iphonePairingCode.takeIf { it.length == 6 })
                    },
                ) {
                    Text("CALIBRATE", color = Accent, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAcousticConfirmation = false }) {
                    Text("CANCEL", color = Muted, fontWeight = FontWeight.Black)
                }
            },
            containerColor = Surface,
        )
    }
}

private enum class SettingsSection {
    SPEED_AUDIO,
    GENERAL,
    BACKFIRE,
    LOUDNESS,
    BANK_IMPORT,
}

@Composable
private fun ManualLoudnessSettingsTab(
    settings: CatalogGainSettings,
    tableEntries: List<ManualLoudnessTableEntry>,
    onManualEnabledChange: (Boolean) -> Unit,
    onAdjustmentDbChange: (String, EngineSoundPerspective, Double) -> Unit,
    onPreviewChange: (String, Boolean) -> Unit,
    onPreviewExteriorChange: (String, Boolean) -> Unit,
    onSaveManualLoudnessAsDefault: () -> Unit,
    onExportManualLoudnessPreset: () -> Unit,
    onStopManualLoudnessPreviews: () -> Unit,
    clubReferenceMediaPlaying: Boolean,
    onToggleClubReferenceMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onDispose {
            onStopManualLoudnessPreviews()
            if (clubReferenceMediaPlaying) {
                onToggleClubReferenceMedia()
            }
        }
    }

    ManualLoudnessAdjustmentPanel(
        settings = settings,
        tableEntries = tableEntries,
        onManualEnabledChange = onManualEnabledChange,
        onAdjustmentDbChange = onAdjustmentDbChange,
        onPreviewChange = onPreviewChange,
        onPreviewExteriorChange = onPreviewExteriorChange,
        onSaveManualLoudnessAsDefault = onSaveManualLoudnessAsDefault,
        onExportManualLoudnessPreset = onExportManualLoudnessPreset,
        clubReferenceMediaPlaying = clubReferenceMediaPlaying,
        onToggleClubReferenceMedia = onToggleClubReferenceMedia,
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    )
}

@Composable
private fun CatalogGainControlsPanel(
    settings: CatalogGainSettings,
    tableEntries: List<CatalogGainTableEntry>,
    tablePerspective: EngineSoundPerspective,
    onTablePerspectiveChange: (EngineSoundPerspective) -> Unit,
    onSettingsChange: (CatalogGainSettings) -> Unit,
) {
    val filteredRows = tableEntries.filter { it.perspective == tablePerspective }
    val catalogTogglesEnabled = !settings.manualLoudnessEnabled

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CALIBRATED GAIN CONTROLS", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "Turn catalog-wide LUFS normalization and iPhone acoustic adjustments on or off without deleting stored measurements.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
        CatalogGainToggleRow(
            label = "APPLY LUFS NORMALIZATION",
            checked = settings.applyLufsNormalization,
            enabled = catalogTogglesEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange(settings.copy(applyLufsNormalization = enabled))
            },
        )
        CatalogGainToggleRow(
            label = "APPLY IPHONE ACOUSTIC ADJUSTMENT",
            checked = settings.applyIphoneAcousticAdjustment,
            enabled = catalogTogglesEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange(settings.copy(applyIphoneAcousticAdjustment = enabled))
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EngineSoundPerspective.entries.forEach { perspective ->
                val selected = tablePerspective == perspective
                OutlinedButton(
                    onClick = { onTablePerspectiveChange(perspective) },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (selected) Accent else Outline),
                ) {
                    Text(
                        perspective.name,
                        color = if (selected) Accent else Muted,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        CatalogGainTableSection(
            title = "LUFS NORMALIZATION BY CAR",
            rows = filteredRows,
            valueDb = { it.lufsNormalizationDb },
            status = { it.lufsStatus },
        )
        CatalogGainTableSection(
            title = "IPHONE ACOUSTIC ADJUSTMENT BY CAR",
            rows = filteredRows,
            valueDb = { it.iphoneAcousticAdjustmentDb },
            status = { it.iphoneStatus },
        )
    }
}

@Composable
private fun CatalogGainToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Outline, skinShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (enabled) {
                OnSurface
            } else {
                Muted
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ManualLoudnessAdjustmentPanel(
    settings: CatalogGainSettings,
    tableEntries: List<ManualLoudnessTableEntry>,
    onManualEnabledChange: (Boolean) -> Unit,
    onAdjustmentDbChange: (String, EngineSoundPerspective, Double) -> Unit,
    onPreviewChange: (String, Boolean) -> Unit,
    onPreviewExteriorChange: (String, Boolean) -> Unit,
    onSaveManualLoudnessAsDefault: () -> Unit,
    onExportManualLoudnessPreset: () -> Unit,
    clubReferenceMediaPlaying: Boolean,
    onToggleClubReferenceMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingProfileId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("MANUAL LOUDNESS ADJUSTMENT", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "Per-car volume trim from -20 dB to +20 dB. When enabled, LUFS and iPhone catalog adjustments are bypassed and their previous on/off state is restored when you turn manual mode off.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Outline, skinShape(8.dp))
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ENABLE MANUAL LOUDNESS ADJUSTMENT",
                    color = OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.manualLoudnessEnabled,
                    onCheckedChange = onManualEnabledChange,
                )
            }
            OutlinedButton(
                onClick = onToggleClubReferenceMedia,
                border = BorderStroke(
                    1.dp,
                    if (clubReferenceMediaPlaying) {
                        Accent
                    } else {
                        Outline
                    },
                ),
            ) {
                Text(
                    if (clubReferenceMediaPlaying) {
                        "STOP CLUB"
                    } else {
                        "IN THE CLUB"
                    },
                    color = if (clubReferenceMediaPlaying) {
                        Accent
                    } else {
                        AccentSoft
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onSaveManualLoudnessAsDefault,
                modifier = Modifier.weight(1f),
            ) {
                Text("SAVE AS DEFAULT", color = AccentSoft, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onExportManualLoudnessPreset,
                modifier = Modifier.weight(1f),
            ) {
                Text("EXPORT PRESET", color = AccentSoft, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
        Text(
            "Export writes manual_loudness_preset.json to the app files folder. Share that file to bake factory defaults into a future build.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        if (settings.manualLoudnessEnabled) {
            Text(
                "Tap EDIT to unlock a car slider. PLAY runs one RPM sweep and stops. INT/EXT keep separate saved values.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Outline, skinShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (tableEntries.isEmpty()) {
                Text("No modded cars installed.", color = Muted, fontSize = 12.sp)
            } else {
                tableEntries.forEach { row ->
                    ManualLoudnessCarRow(
                        entry = row,
                        enabled = settings.manualLoudnessEnabled,
                        sliderEnabled = settings.manualLoudnessEnabled &&
                            (editingProfileId == row.profileId || row.previewActive),
                        isEditing = editingProfileId == row.profileId,
                        onToggleEdit = {
                            editingProfileId = if (editingProfileId == row.profileId) {
                                null
                            } else {
                                row.profileId
                            }
                        },
                        onAdjustmentDbChange = { db ->
                            onAdjustmentDbChange(row.profileId, row.activePerspective, db)
                        },
                        onPreviewChange = { active ->
                            onPreviewChange(row.profileId, active)
                        },
                        onPreviewExteriorChange = { exterior ->
                            onPreviewExteriorChange(row.profileId, exterior)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualLoudnessCarRow(
    entry: ManualLoudnessTableEntry,
    enabled: Boolean,
    sliderEnabled: Boolean,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onAdjustmentDbChange: (Double) -> Unit,
    onPreviewChange: (Boolean) -> Unit,
    onPreviewExteriorChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val bankResolver = remember(context) { FmodBankResolver(context.applicationContext) }
    val profile = remember(entry.profileId) {
        FmodBankProfiles.all.firstOrNull { profile -> profile.id == entry.profileId }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (profile != null) {
                CarPreviewThumbnail(
                    profile = profile,
                    audioAssetResolver = bankResolver,
                    contentDescription = entry.carName,
                    modifier = Modifier
                        .width(72.dp)
                        .height(48.dp),
                )
            }
            Text(
                entry.carName,
                color = if (enabled) {
                    OnSurface
                } else {
                    Muted
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            Text(
                String.format(
                    Locale.US,
                    "%s %+.1f dB",
                    if (entry.previewExterior) {
                        "EXT"
                    } else {
                        "INT"
                    },
                    entry.activeAdjustmentDb,
                ),
                color = if (enabled) {
                    AccentSoft
                } else {
                    Muted
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onPreviewExteriorChange(!entry.previewExterior) },
                enabled = enabled,
                border = BorderStroke(
                    1.dp,
                    if (entry.previewExterior) {
                        Accent
                    } else {
                        Outline
                    },
                ),
            ) {
                Text(
                    "EXT",
                    color = if (entry.previewExterior) {
                        Accent
                    } else {
                        AccentSoft
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                )
            }
            OutlinedButton(
                onClick = onToggleEdit,
                enabled = enabled,
                border = BorderStroke(
                    1.dp,
                    if (isEditing) {
                        Accent
                    } else {
                        Outline
                    },
                ),
            ) {
                Text(
                    if (isEditing) {
                        "DONE"
                    } else {
                        "EDIT"
                    },
                    color = if (isEditing) {
                        Accent
                    } else {
                        AccentSoft
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                )
            }
            OutlinedButton(
                onClick = { onPreviewChange(!entry.previewActive) },
                enabled = enabled,
                border = BorderStroke(
                    1.dp,
                    if (entry.previewActive) {
                        Accent
                    } else {
                        Outline
                    },
                ),
            ) {
                Text(
                    if (entry.previewActive) {
                        "PLAYING"
                    } else {
                        "PLAY"
                    },
                    color = if (entry.previewActive) {
                        Accent
                    } else {
                        AccentSoft
                    },
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Slider(
            value = ManualLoudnessRepository.sliderPercentFromDb(entry.activeAdjustmentDb),
            onValueChange = { percent ->
                onAdjustmentDbChange(ManualLoudnessRepository.sliderDbFromPercent(percent))
            },
            enabled = sliderEnabled,
            valueRange = 0f..ManualLoudnessTableEntry.SLIDER_STEPS.toFloat(),
            steps = ManualLoudnessTableEntry.SLIDER_STEPS - 1,
            colors = SliderDefaults.colors(
                thumbColor = if (sliderEnabled) Accent else Muted,
                activeTrackColor = if (sliderEnabled) Accent else Outline,
                inactiveTrackColor = Outline,
                disabledThumbColor = Muted,
                disabledActiveTrackColor = Outline,
                disabledInactiveTrackColor = Outline,
            ),
        )
    }
}

@Composable
private fun CatalogGainTableSection(
    title: String,
    rows: List<CatalogGainTableEntry>,
    valueDb: (CatalogGainTableEntry) -> Double?,
    status: (CatalogGainTableEntry) -> CatalogGainEntryStatus,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Outline, skinShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Black)
        if (rows.isEmpty()) {
            Text("No modded cars installed.", color = Muted, fontSize = 12.sp)
        } else {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.carName,
                        color = OnSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    val rowStatus = status(row)
                    Text(
                        formatCatalogGainDb(valueDb(row), rowStatus),
                        color = when (rowStatus) {
                            CatalogGainEntryStatus.ACTIVE -> Success
                            CatalogGainEntryStatus.STALE, CatalogGainEntryStatus.FAILED -> Warning
                            CatalogGainEntryStatus.MISSING, CatalogGainEntryStatus.SKIPPED -> Muted
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

private fun formatCatalogGainDb(valueDb: Double?, entryStatus: CatalogGainEntryStatus): String {
    if (entryStatus == CatalogGainEntryStatus.ACTIVE && valueDb != null) {
        return String.format(Locale.US, "%+.1f dB", valueDb)
    }

    return catalogGainStatusLabel(entryStatus)
}

private fun catalogGainStatusLabel(status: CatalogGainEntryStatus): String {
    return when (status) {
        CatalogGainEntryStatus.ACTIVE -> "ACTIVE"
        CatalogGainEntryStatus.MISSING -> "MISSING"
        CatalogGainEntryStatus.STALE -> "STALE"
        CatalogGainEntryStatus.FAILED -> "FAILED"
        CatalogGainEntryStatus.SKIPPED -> "SKIPPED"
    }
}

@Composable
private fun AcousticDiagnosticSettingsPanel(
    summary: AcousticDiagnosticSummary,
    progress: AcousticDiagnosticProgress,
    iphoneMeterLinked: Boolean,
    iphoneMeterSelected: Boolean,
    calibrationLogLines: List<String>,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onAssociate: () -> Unit,
    onForget: () -> Unit,
    onClearCalibration: () -> Unit,
    onRecoverLenient: () -> Unit,
    onClearLog: () -> Unit,
    onMeasure: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Text("IPHONE ACOUSTIC CALIBRATION", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
    Text(
        "Measures only already-normalized modded cars through the vehicle speakers, then applies a bounded per-car acoustic adjustment. Bluetooth carries timing and metrics only; no audio is transferred or stored.",
        color = Muted,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Outline, skinShape(8.dp))
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LoudnessCount("COVERAGE", "${summary.validCount} / ${summary.totalCount}", Success)
        LoudnessCount("FAILED", summary.failedCount.toString(), Warning)
        LoudnessCount("STALE / MISSING", "${summary.staleCount} / ${summary.missingCount}", Muted)
        LoudnessCount("WITHIN ±1.5 dB", summary.withinToleranceCount.toString(), AccentSoft)
        LoudnessCount(
            "ADJUSTED SPREAD",
            summary.spreadDb?.let { String.format(Locale.US, "%.1f dB", it) } ?: "—",
            AccentSoft,
        )
    }
    if (summary.validCount > 0) {
        Text(
            listOfNotNull(
                "Median: 0.0 dB(A) RELATIVE",
                summary.spreadDb?.let { "Adjusted spread: ${String.format(Locale.US, "%.1f dB", it)}" },
                summary.medianPresenceBalanceDb?.let {
                    "Presence balance: ${String.format(Locale.US, "%.1f dB", it)}"
                },
                summary.loudestCar?.let {
                    "Loudest measured: $it (${String.format(Locale.US, "%+.1f dB(A) RELATIVE", requireNotNull(summary.loudestDeltaDb))})"
                },
                summary.quietestCar?.let {
                    "Quietest measured: $it (${String.format(Locale.US, "%+.1f dB(A) RELATIVE", requireNotNull(summary.quietestDeltaDb))})"
                },
                summary.mostMuffledCar?.let {
                    "Most muffled: $it (${String.format(Locale.US, "%.1f dB presence", requireNotNull(summary.mostMuffledPresenceBalanceDb))})"
                },
            ).joinToString("  •  "),
            color = OnSurface,
            fontSize = 12.sp,
        )
    }
    if (progress.status != AcousticDiagnosticStatus.IDLE) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Outline, skinShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                when (progress.status) {
                    AcousticDiagnosticStatus.CONNECTING -> "CONNECTING TO IPHONE"
                    AcousticDiagnosticStatus.PAIRING -> "PAIRING IPHONE"
                    AcousticDiagnosticStatus.PREPARING -> "MEASURING ACOUSTIC LATENCY"
                    AcousticDiagnosticStatus.RUNNING -> "ACOUSTIC MEASUREMENT"
                    AcousticDiagnosticStatus.INTERRUPTED -> "INTERRUPTED — READY TO RESUME"
                    AcousticDiagnosticStatus.COMPLETED -> "ACOUSTIC CALIBRATION COMPLETE"
                    AcousticDiagnosticStatus.CANCELLED -> "ACOUSTIC CALIBRATION CANCELLED"
                    AcousticDiagnosticStatus.FAILED -> "ACOUSTIC CALIBRATION FAILED"
                    AcousticDiagnosticStatus.IDLE -> ""
                },
                color = if (progress.status == AcousticDiagnosticStatus.FAILED) Danger else Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            if (progress.totalCount > 0) {
                Text(
                    "${progress.completedCount} / ${progress.totalCount} processed · " +
                        "${progress.skippedCount} skipped · ${progress.failedCount} failed",
                    color = OnSurface,
                    fontSize = 13.sp,
                )
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = MeterTrack,
                )
            }
            progress.lastError?.let { Text(it, color = Warning, fontSize = 12.sp) }
        }
    }
    if (!iphoneMeterLinked) {
        Text("Enter the six-digit code shown on the iPhone.", color = Muted, fontSize = 12.sp)
        BasicTextField(
            value = pairingCode,
            onValueChange = onPairingCodeChange,
            singleLine = true,
            textStyle = TextStyle(
                color = OnSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp,
            ),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Outline, skinShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { field ->
                if (pairingCode.isEmpty()) Text("000000", color = Muted, fontSize = 22.sp, letterSpacing = 5.sp)
                field()
            },
        )
    }
    var showForgetIphoneConfirmation by remember { mutableStateOf(false) }
    var showClearCalibrationConfirmation by remember { mutableStateOf(false) }
    var showRecoverLenientConfirmation by remember { mutableStateOf(false) }
    val hasIphoneCalibrationData = progress.canResume ||
        summary.validCount > 0 ||
        summary.failedCount > 0 ||
        progress.status != AcousticDiagnosticStatus.IDLE
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onAssociate,
            enabled = !progress.isRunning,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (iphoneMeterLinked) "CHOOSE IPHONE" else "PAIR IPHONE", color = AccentSoft)
        }
        Button(
            onClick = onMeasure,
            enabled = summary.totalCount > 0 && !progress.isRunning &&
                (iphoneMeterLinked || (iphoneMeterSelected && pairingCode.length == 6)),
            modifier = Modifier.weight(1f),
        ) {
            Text("CALIBRATE WITH IPHONE", fontWeight = FontWeight.Black)
        }
        if (progress.canResume) {
            OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) {
                Text("RESUME", color = Accent, fontWeight = FontWeight.Black)
            }
        }
        if (progress.isRunning) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("CANCEL", color = Danger, fontWeight = FontWeight.Black)
            }
        }
    }
    if (iphoneMeterLinked || iphoneMeterSelected || hasIphoneCalibrationData) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (iphoneMeterLinked || iphoneMeterSelected) {
                OutlinedButton(
                    onClick = { showForgetIphoneConfirmation = true },
                    enabled = !progress.isRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("FORGET IPHONE", color = Danger, fontWeight = FontWeight.Black)
                }
            }
            if (hasIphoneCalibrationData) {
                OutlinedButton(
                    onClick = { showClearCalibrationConfirmation = true },
                    enabled = !progress.isRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("CLEAR CALIBRATION", color = Warning, fontWeight = FontWeight.Black)
                }
            }
        }
    }
    if (summary.lenientRecoverableCount > 0 && !progress.isRunning) {
        OutlinedButton(
            onClick = { showRecoverLenientConfirmation = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "RECOVER ${summary.lenientRecoverableCount} MEASUREMENTS",
                color = AccentSoft,
                fontWeight = FontWeight.Black,
            )
        }
    }
    if (showForgetIphoneConfirmation) {
        AlertDialog(
            onDismissRequest = { showForgetIphoneConfirmation = false },
            title = { Text("Forget iPhone?", color = OnSurface, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Clears the saved iPhone link and companion Bluetooth association in this app. " +
                        "Use this after unpairing the phone in the car settings.",
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForgetIphoneConfirmation = false
                        onForget()
                    },
                ) {
                    Text("FORGET", color = Danger, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgetIphoneConfirmation = false }) {
                    Text("CANCEL", color = AccentSoft)
                }
            },
        )
    }
    if (showClearCalibrationConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearCalibrationConfirmation = false },
            title = { Text("Clear iPhone calibration?", color = OnSurface, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Deletes every iPhone volume measurement, acoustic adjustment, and interrupted session. " +
                        "The Bluetooth link to the iPhone is kept.",
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCalibrationConfirmation = false
                        onClearCalibration()
                    },
                ) {
                    Text("CLEAR", color = Danger, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCalibrationConfirmation = false }) {
                    Text("CANCEL", color = AccentSoft)
                }
            },
        )
    }
    if (showRecoverLenientConfirmation) {
        AlertDialog(
            onDismissRequest = { showRecoverLenientConfirmation = false },
            title = { Text("Recover stored measurements?", color = OnSurface, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Uses measurements already saved on this head unit. Accepts ${summary.lenientRecoverableCount} " +
                        "pairs that failed only because ambient noise drifted between the before/after silence windows, " +
                        "as long as SNR and peak were still healthy. No iPhone replay is needed.",
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRecoverLenientConfirmation = false
                        onRecoverLenient()
                    },
                ) {
                    Text("RECOVER", color = AccentSoft, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoverLenientConfirmation = false }) {
                    Text("CANCEL", color = AccentSoft)
                }
            },
        )
    }
    IphoneVolumeCalibrationConsolePanel(
        logLines = calibrationLogLines,
        onClear = onClearLog,
    )
}

@Composable
private fun IphoneVolumeCalibrationConsolePanel(
    logLines: List<String>,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "IPHONE VOLUME CALIBRATION CONSOLE",
            color = Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "CLEAR",
            color = AccentSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clip(softFillShape(5.dp))
                .border(1.dp, Outline, softFillShape(5.dp))
                .clickable(onClick = onClear)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
    MaterialSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 260.dp)
            .border(1.dp, Outline, skinShape(8.dp)),
        color = Color.Black.copy(alpha = 0.35f),
        shape = skinShape(8.dp),
    ) {
        val logScrollState = rememberScrollState()
        LaunchedEffect(logLines.size, logLines.lastOrNull()) {
            logScrollState.scrollTo(logScrollState.maxValue)
        }
        Text(
            text = if (logLines.isEmpty()) {
                "No calibration events yet. Pair the iPhone, then tap CALIBRATE WITH IPHONE."
            } else {
                logLines.joinToString("\n")
            },
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(logScrollState)
                .padding(14.dp),
            color = if (logLines.any { it.contains(" ERR") }) Warning else AccentSoft,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
internal fun LoudnessCalibrationModal(
    progress: LoudnessCalibrationProgress,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSurface(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .sizeIn(maxWidth = 760.dp),
                shape = skinShape(14.dp),
                color = Surface,
                border = BorderStroke(1.dp, Outline),
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "CALIBRATING ENGINE LOUDNESS",
                        color = Accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Output remains silent while the complete catalog is measured.",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    Text(
                        progress.activeCarName ?: "Preparing FMOD…",
                        color = OnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    progress.perspective?.let { perspective ->
                        Text(
                            if (perspective == EngineSoundPerspective.EXTERIOR) "EXTERIOR • PURE" else "CABIN",
                            color = AccentSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Text(
                        "${progress.completedCount} / ${progress.totalCount} processed" +
                            if (progress.failedCount > 0) " • ${progress.failedCount} failed" else "",
                        color = OnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = MeterTrack,
                    )
                    progress.lastError?.let { error ->
                        Text(error, color = Warning, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.align(Alignment.End),
                        border = BorderStroke(1.dp, Danger.copy(alpha = 0.8f)),
                    ) {
                        Text("CANCEL", color = Danger, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoudnessCount(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun AcousticDiagnosticModal(
    progress: AcousticDiagnosticProgress,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSurface(
                modifier = Modifier.fillMaxWidth(0.68f).sizeIn(maxWidth = 760.dp),
                shape = skinShape(14.dp),
                color = Surface,
                border = BorderStroke(1.dp, Outline),
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("CALIBRATING WITH IPHONE", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Audible engine sweeps are playing through the vehicle speakers. Keep the iPhone at driver head height and the cabin unchanged.",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    Text(
                        progress.activeCarName ?: progress.meterName ?: "Connecting…",
                        color = OnSurface,
                        fontSize = 18.sp,
                    )
                    progress.perspective?.let {
                        Text(
                            if (it == EngineSoundPerspective.EXTERIOR) "EXTERIOR • PURE" else "CABIN",
                            color = AccentSoft,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Text(
                        "${progress.completedCount} / ${progress.totalCount} processed · ${progress.skippedCount} skipped",
                        color = OnSurface,
                        fontSize = 13.sp,
                    )
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = MeterTrack,
                    )
                    OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                        Text("EMERGENCY STOP", color = Danger, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedAudioSettingsPanel(
    settings: SpeedAudioSettings,
    liveUsesRacingGain: Boolean,
    livePreparingCruising: Boolean,
    liveSpeedKmh: Double,
    onChange: (SpeedAudioSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = settings.normalized()
    val modeGainSteps = SpeedAudioGain.modeGainSliderSteps()
    val modeBlendSteps = SpeedAudioGain.modeBlendSliderSteps()
    val speedCoefficientSteps = SpeedAudioGain.speedCoefficientSliderSteps()
    val liveModeLabel = when {
        liveUsesRacingGain -> "RACING / MANUAL"
        livePreparingCruising -> "P-CRUISING"
        else -> "CRUISING"
    }
    val liveModeGain = remember(normalized, liveUsesRacingGain) {
        SpeedAudioGainResolver.combinedGainOffset(
            usesRacingGain = liveUsesRacingGain,
            settings = normalized,
        )
    }
    val liveSpeedBonus = remember(normalized, liveSpeedKmh, liveUsesRacingGain) {
        if (!liveUsesRacingGain) {
            0f
        } else {
            SpeedAudioGainResolver.speedGainBonus(
                speedKmh = liveSpeedKmh,
                coefficient = normalized.speedGainCoefficient,
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Outline, skinShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SPEED AUDIO",
                color = Accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = liveModeLabel,
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = SpeedAudioGain.formatGainOffset(liveModeGain),
                    color = OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
                if (liveSpeedBonus > 0f) {
                    Text(
                        text = "+ speed ${SpeedAudioGain.formatGainOffset(liveSpeedBonus)}",
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Text(
            text = "Two fixed host-gain offsets: one while cruising, one while racing or in manual shift. Smooth transition cross-fades between them when the drivetrain mode changes.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

        SpeedAudioModeGainControl(
            title = "CRUISING",
            value = normalized.cruisingGain,
            onValueChange = { value ->
                onChange(normalized.copy(cruisingGain = value))
            },
            steps = modeGainSteps,
        )

        SpeedAudioModeGainControl(
            title = "RACING / MANUAL",
            value = normalized.racingGain,
            onValueChange = { value ->
                onChange(normalized.copy(racingGain = value))
            },
            steps = modeGainSteps,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SMOOTH TRANSITION",
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = SpeedAudioGain.formatModeBlendSeconds(normalized.modeBlendSeconds),
                    color = OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = "Cross-fade time when switching between cruising and racing/manual. 0s = instant.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Slider(
                value = normalized.modeBlendSeconds,
                onValueChange = { value ->
                    onChange(normalized.copy(modeBlendSeconds = value))
                },
                valueRange = SpeedAudioGain.MODE_BLEND_SECONDS_MIN..SpeedAudioGain.MODE_BLEND_SECONDS_MAX,
                steps = modeBlendSteps.coerceAtLeast(0),
            )
        }

        OutlinedButton(
            onClick = {
                onChange(
                    normalized.copy(
                        cruisingGain = SpeedAudioGain.DEFAULT_CRUISING_GAIN,
                        racingGain = SpeedAudioGain.DEFAULT_RACING_GAIN,
                        modeBlendSeconds = SpeedAudioGain.DEFAULT_MODE_BLEND_SECONDS,
                    ),
                )
            },
            border = BorderStroke(1.dp, Outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("RESTORE MODE DEFAULTS", color = AccentSoft, fontWeight = FontWeight.Black)
        }

        HorizontalDivider(color = Outline)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SPEED COEFFICIENT",
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = SpeedAudioGain.formatSpeedCoefficient(normalized.speedGainCoefficient),
                    color = OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = "Adds positive gain from road speed only while racing or in manual shift, scaled to ${SpeedAudioGain.SPEED_REFERENCE_KMH.toInt()} km/h. Never applies in cruising or P-CRUISING.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            SpeedGainCurveChart(
                speedGainCoefficient = normalized.speedGainCoefficient,
                liveSpeedKmh = liveSpeedKmh,
            )
            Slider(
                value = normalized.speedGainCoefficient,
                onValueChange = { value ->
                    onChange(normalized.copy(speedGainCoefficient = value))
                },
                valueRange = SpeedAudioGain.SPEED_COEFFICIENT_MIN..SpeedAudioGain.SPEED_COEFFICIENT_MAX,
                steps = speedCoefficientSteps.coerceAtLeast(0),
            )
        }
    }
}

@Composable
private fun SpeedAudioModeGainControl(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = SpeedAudioGain.formatGainOffset(value),
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = SpeedAudioGain.GAIN_MIN..SpeedAudioGain.GAIN_MAX,
            steps = steps.coerceAtLeast(0),
        )
    }
}

@Composable
private fun BankImportDiagnosticsPanel(
    onRescanBanks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolver = remember(context) {
        FmodBankResolver(context.applicationContext)
    }
    var refreshSerial by remember { mutableStateOf(0) }
    var diagnostics by remember { mutableStateOf<FmodBankDiagnostics?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshSerial) {
        loading = true
        diagnostics = withContext(Dispatchers.IO) {
            resolver.diagnose()
        }
        loading = false
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FMOD BANK DISCOVERY",
                    color = Accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = "Checks both private files and Android/data direct-copy storage.",
                    color = Muted,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                )
            }
            Button(
                enabled = !loading,
                onClick = {
                    onRescanBanks()
                    refreshSerial += 1
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent.copy(alpha = 0.85f)),
            ) {
                Text(
                    text = if (loading) "SCANNING…" else "RESCAN BANKS",
                    color = Background,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        diagnostics?.let { report ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DiagnosticStatusTag("${report.recognizedCarCount} CARS", AccentSoft)
                DiagnosticStatusTag("${report.validPackCount} VALID PACKS", Success)
                DiagnosticStatusTag(
                    text = "${report.issueCount} ISSUES",
                    color = if (report.issueCount == 0) Success else Danger,
                )
            }
        }

        MaterialSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Outline, skinShape(8.dp)),
            color = Color.Black.copy(alpha = 0.35f),
            shape = skinShape(8.dp),
        ) {
            val logScrollState = rememberScrollState()
            LaunchedEffect(diagnostics?.generatedAtEpochMillis) {
                logScrollState.scrollTo(logScrollState.maxValue)
            }
            Text(
                text = diagnostics?.logLines?.joinToString("\n")
                    ?: "Scanning bank folders…",
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(logScrollState)
                    .padding(14.dp),
                color = if (diagnostics?.issueCount == 0) AccentSoft else OnSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun DiagnosticStatusTag(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.65f), skinShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
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
private fun VirtualGearSpeedBoundariesSettingsControl(
    settings: VirtualGearSpeedBoundariesSettings,
    gearProfileSelection: GearProfileSelection,
    onBoundaryChange: (preset: Int, boundaryIndex: Int, speedKmh: Int) -> Unit,
    onRestorePreset: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val activePreset = gearProfileSelection.virtualCountOrNull()

    Column(
        modifier = modifier
            .border(1.dp, Outline, skinShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "GEAR SPEED BANDS",
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = when {
                    gearProfileSelection.isAdaptive() ->
                        "Adaptive 6/10 mirrors the saved 6-gear profile in cruising and the 10-gear profile in racing or manual shift, including custom speed bands. Select 6 or 10 on the dashboard to edit those presets."
                    activePreset != null ->
                        "Drag dividers to set how each gear maps to road speed for the $activePreset-gear preset selected on the dashboard."
                    else ->
                        "Select 6, 10, or 15 on the main dashboard to tune virtual gear speed bands. ORIGINAL uses the bank ratios and cannot be edited here."
                },
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }

        if (activePreset != null) {
            VirtualGearDistributionChart(
                gearCount = activePreset,
                boundariesKmh = settings.boundariesFor(activePreset),
                onBoundaryChange = { boundaryIndex, speedKmh ->
                    onBoundaryChange(activePreset, boundaryIndex, speedKmh)
                },
                onRestoreDefaults = {
                    onRestorePreset(activePreset)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
                    .border(1.dp, Outline, skinShape(8.dp))
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
            Text(title, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
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
private fun CruisingShiftOffsetsByTachMaxRpmControl(
    offsets: Map<Int, Int>,
    onOffsetChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val offsetSteps = CruisingShiftOffsetByTachMaxRpm.sliderSteps

    Column(
        modifier = modifier
            .border(1.dp, Outline, skinShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("CRUISING OFFSET BY TACH MAX", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
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
                                color = AccentSoft,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                text = CruisingShiftOffsetByTachMaxRpm.formatOffsetLabel(offset),
                                color = OnSurface,
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
    racingEnterMinThrottlePercent: Int,
    onRacingEnterMinThrottlePercentChange: (Int) -> Unit,
    racingReturnThrottlePercent: Int,
    onRacingReturnThrottlePercentChange: (Int) -> Unit,
    racingReturnHoldSeconds: Int,
    onRacingReturnHoldSecondsChange: (Int) -> Unit,
    kickdownStompDeltaPercent: Int,
    onKickdownStompDeltaPercentChange: (Int) -> Unit,
    kickdownStompMinThrottlePercent: Int,
    onKickdownStompMinThrottlePercentChange: (Int) -> Unit,
    racingEnterDelayMilliseconds: Int,
    onRacingEnterDelayMillisecondsChange: (Int) -> Unit,
    automaticUpshiftMilliseconds: Int,
    onAutomaticUpshiftMillisecondsChange: (Int) -> Unit,
    automaticDownshiftMilliseconds: Int,
    onAutomaticDownshiftMillisecondsChange: (Int) -> Unit,
    manualRedlineHoldSeconds: Int,
    onManualRedlineHoldSecondsChange: (Int) -> Unit,
    manualAutodownshiftRpm: Int,
    onManualAutodownshiftRpmChange: (Int) -> Unit,
    manualTransmissionKickdownEnabled: Boolean,
    onManualTransmissionKickdownEnabledChange: (Boolean) -> Unit,
    allowManualOnLaunchEnabled: Boolean,
    onAllowManualOnLaunchEnabledChange: (Boolean) -> Unit,
    tachometerCruisingShiftRangeOverlayEnabled: Boolean,
    onTachometerCruisingShiftRangeOverlayEnabledChange: (Boolean) -> Unit,
    lowSpeedCrawlRpmHoldEnabled: Boolean,
    onLowSpeedCrawlRpmHoldEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(
        modifier = modifier
            .border(1.dp, Outline, skinShape(8.dp))
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
            Text("MIN THROTTLE", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = String.format(Locale.US, "%.2f", minimumAudioThrottle),
                color = OnSurface,
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
        TachometerShiftOverlayToggle(
            title = "LOW SPEED CRAWL RPM HOLD",
            description = "While normal RPM would stay below 3,000, throttle above 1% glides the tach to 3,000 RPM. At or above 3,000 RPM naturally, normal logic takes over. Lifting the pedal or braking glides back to idle.",
            enabled = lowSpeedCrawlRpmHoldEnabled,
            onEnabledChange = onLowSpeedCrawlRpmHoldEnabledChange,
            modifier = Modifier.fillMaxWidth(),
            embedded = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RACING ENTER MIN THROTTLE", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = "$racingEnterMinThrottlePercent%",
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = "While cruising, pressing the accelerator above this level switches to racing mode.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Slider(
            value = racingEnterMinThrottlePercent.toFloat(),
            onValueChange = { value ->
                val selectedPercent = RacingEnterMinThrottlePercent.normalize(value.roundToInt())
                if (selectedPercent != racingEnterMinThrottlePercent) {
                    onRacingEnterMinThrottlePercentChange(selectedPercent)
                }
            },
            valueRange = RacingEnterMinThrottlePercent.MIN.toFloat()..RacingEnterMinThrottlePercent.MAX.toFloat(),
            steps = (RacingEnterMinThrottlePercent.MAX - RacingEnterMinThrottlePercent.MIN) / RacingEnterMinThrottlePercent.STEP - 1,
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
                    Text("RACING RETURN THROTTLE", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = "$racingReturnThrottlePercent%",
                        color = OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = "After P-CRUISING is armed, re-acceleration at or below this level completes the return to cruising. Above it, or a kickdown stomp, stays in racing.",
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

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("LIGHT BRAKE RETURN HOLD", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = RacingReturnHoldSeconds.format(racingReturnHoldSeconds),
                        color = OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = "In racing, any brake input prepares P-CRUISING. Holding the brake lightly (below 35%) for this long completes the return to cruising.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Slider(
                    value = racingReturnHoldSeconds.toFloat(),
                    onValueChange = { value ->
                        val selectedSeconds = RacingReturnHoldSeconds.normalize(value.roundToInt())
                        if (selectedSeconds != racingReturnHoldSeconds) {
                            onRacingReturnHoldSecondsChange(selectedSeconds)
                        }
                    },
                    valueRange = RacingReturnHoldSeconds.MIN.toFloat()..RacingReturnHoldSeconds.MAX.toFloat(),
                    steps = (RacingReturnHoldSeconds.MAX - RacingReturnHoldSeconds.MIN) / RacingReturnHoldSeconds.STEP - 1,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TachometerShiftOverlayToggle(
                title = "CRUISING RPM RANGE OVERLAY",
                description = "Semi-transparent wedge on the tachometer showing min/max automatic shift RPM while cruising.",
                enabled = tachometerCruisingShiftRangeOverlayEnabled,
                onEnabledChange = onTachometerCruisingShiftRangeOverlayEnabledChange,
                modifier = Modifier.weight(1f),
                embedded = true,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RACING KICKDOWN SMOOTHNESS", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = RacingEnterDelayMilliseconds.format(racingEnterDelayMilliseconds),
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = "When cruising switches to racing, kickdown starts immediately. Each downshift blends RPM over this duration — higher is smoother.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Slider(
            value = racingEnterDelayMilliseconds.toFloat(),
            onValueChange = { value ->
                val selectedDelay = RacingEnterDelayMilliseconds.normalize(value.roundToInt())
                if (selectedDelay != racingEnterDelayMilliseconds) {
                    onRacingEnterDelayMillisecondsChange(selectedDelay)
                }
            },
            valueRange = RacingEnterDelayMilliseconds.MIN.toFloat()..RacingEnterDelayMilliseconds.MAX.toFloat(),
            steps = (RacingEnterDelayMilliseconds.MAX - RacingEnterDelayMilliseconds.MIN) / RacingEnterDelayMilliseconds.STEP,
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
                    Text("MANUAL KICKDOWN STOMP DELTA", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = "$kickdownStompDeltaPercent%",
                        color = OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = "Manual mode only: minimum pedal increase in one frame to count as a kickdown stomp.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Slider(
                    value = kickdownStompDeltaPercent.toFloat(),
                    onValueChange = { value ->
                        val selectedPercent = KickdownStompDeltaPercent.normalize(value.roundToInt())
                        if (selectedPercent != kickdownStompDeltaPercent) {
                            onKickdownStompDeltaPercentChange(selectedPercent)
                        }
                    },
                    valueRange = KickdownStompDeltaPercent.MIN.toFloat()..KickdownStompDeltaPercent.MAX.toFloat(),
                    steps = (KickdownStompDeltaPercent.MAX - KickdownStompDeltaPercent.MIN) / KickdownStompDeltaPercent.STEP - 1,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("MANUAL KICKDOWN MIN THROTTLE", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = "$kickdownStompMinThrottlePercent%",
                        color = OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = "Manual mode only: pedal level a kickdown stomp must reach after the delta above.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Slider(
                    value = kickdownStompMinThrottlePercent.toFloat(),
                    onValueChange = { value ->
                        val selectedPercent = KickdownStompMinThrottlePercent.normalize(value.roundToInt())
                        if (selectedPercent != kickdownStompMinThrottlePercent) {
                            onKickdownStompMinThrottlePercentChange(selectedPercent)
                        }
                    },
                    valueRange = KickdownStompMinThrottlePercent.MIN.toFloat()..KickdownStompMinThrottlePercent.MAX.toFloat(),
                    steps = (KickdownStompMinThrottlePercent.MAX - KickdownStompMinThrottlePercent.MIN) / KickdownStompMinThrottlePercent.STEP - 1,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AutomaticShiftTimingColumn(
                title = "UPSHIFT BLEND",
                description = "How long each automatic upshift blends RPM into the next gear.",
                valueLabel = AutomaticUpshiftMilliseconds.format(automaticUpshiftMilliseconds),
                value = automaticUpshiftMilliseconds.toFloat(),
                valueRange = AutomaticUpshiftMilliseconds.MIN.toFloat()..AutomaticUpshiftMilliseconds.MAX.toFloat(),
                steps = (AutomaticUpshiftMilliseconds.MAX - AutomaticUpshiftMilliseconds.MIN) / AutomaticUpshiftMilliseconds.STEP - 1,
                onValueChange = { value ->
                    val selected = AutomaticUpshiftMilliseconds.normalize(value.roundToInt())
                    if (selected != automaticUpshiftMilliseconds) {
                        onAutomaticUpshiftMillisecondsChange(selected)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            AutomaticShiftTimingColumn(
                title = "DOWNSHIFT BLEND",
                description = "How long each automatic downshift blends RPM into the next gear.",
                valueLabel = AutomaticDownshiftMilliseconds.format(automaticDownshiftMilliseconds),
                value = automaticDownshiftMilliseconds.toFloat(),
                valueRange = AutomaticDownshiftMilliseconds.MIN.toFloat()..AutomaticDownshiftMilliseconds.MAX.toFloat(),
                steps = (AutomaticDownshiftMilliseconds.MAX - AutomaticDownshiftMilliseconds.MIN) / AutomaticDownshiftMilliseconds.STEP - 1,
                onValueChange = { value ->
                    val selected = AutomaticDownshiftMilliseconds.normalize(value.roundToInt())
                    if (selected != automaticDownshiftMilliseconds) {
                        onAutomaticDownshiftMillisecondsChange(selected)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        val manualRedlineStopIndex = ManualRedlineHoldSeconds.stopIndex(manualRedlineHoldSeconds).toFloat()
        val manualRedlineLastStopIndex = (ManualRedlineHoldSeconds.STOPS.size - 1).toFloat()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TachometerShiftOverlayToggle(
                title = "MANUAL TRANSMISSION KICKDOWN",
                description = "Manual mode: a sharp throttle stomp downshifts toward the best gear for acceleration, then the next upshift alone uses automatic timing.",
                enabled = manualTransmissionKickdownEnabled,
                onEnabledChange = onManualTransmissionKickdownEnabledChange,
                modifier = Modifier.weight(1f),
                embedded = true,
            )
            TachometerShiftOverlayToggle(
                title = "ALLOW MANUAL ON LAUNCH",
                description = "When off, arming launch control switches to automatic shift mode.",
                enabled = allowManualOnLaunchEnabled,
                onEnabledChange = onAllowManualOnLaunchEnabledChange,
                modifier = Modifier.weight(1f),
                embedded = true,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MANUAL REDLINE HOLD", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = ManualRedlineHoldSeconds.format(manualRedlineHoldSeconds),
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = "Manual mode returns to automatic racing after staying at or above redline for this long.",
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
            Text("MANUAL AUTODOWNSHIFT RPM", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = "$manualAutodownshiftRpm RPM",
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = "Manual mode: falling below this RPM downshifts one gear automatically. Kickdown stomps use a sharp pedal increase and this value as an offset below redline when choosing a target gear.",
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
private fun AutomaticShiftTimingColumn(
    title: String,
    description: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
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
            Text(title, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = valueLabel,
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = description,
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps.coerceAtLeast(0),
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
            .border(1.dp, Outline, skinShape(8.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("EXTERIOR PURE AUDIO", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
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
        modifier = Modifier.fillMaxWidth().border(1.dp, Outline, skinShape(8.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("SHIFT SOUND OVERRIDE", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
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
            .border(1.dp, Outline, skinShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = PedalAudioThrottleRampMilliseconds.format(valueMilliseconds),
                color = OnSurface,
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
            .border(1.dp, Outline, skinShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("FMOD CONTROL RATE", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(
                    "Physics and FMOD share this cadence. 60 Hz is recommended; 30 Hz is economy mode.",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
            Text(
                "$rateHz Hz",
                color = OnSurface,
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
                        containerColor = if (rateHz == optionHz) Accent else SurfaceRaised,
                    ),
                ) {
                    Text(
                        text = "$optionHz Hz",
                        color = if (rateHz == optionHz) Background else OnSurface,
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
        color = if (selected) Accent else Muted,
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(if (selected) Accent.copy(alpha = 0.14f) else Color.Transparent)
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
        Text("GLOBAL BACKFIRE POLICY", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
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
        Text("ALFA ROMEO BACKFIRE SAMPLES", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
        AlfaBackfireSources.indices.forEach { sample ->
            val allowed = sample in value.allowedSamples
            Row(
                modifier = Modifier.fillMaxWidth().clip(skinShape(6.dp)).background(Surface)
                    .border(1.dp, Outline, skinShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(AlfaBackfireSources.names[sample - 1], color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("PLAY ▶", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Black,
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
        Text(label, color = if (enabled) Accent else Muted, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.width(58.dp).height(28.dp).clip(StadiumShape)
                .background(if (enabled) Accent else Outline).padding(4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).offset(x = if (enabled) 30.dp else 0.dp).clip(CircleShape).background(OnSurface))
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
            Text(label, color = AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text("$shown$suffix", color = OnSurface, fontSize = 12.sp)
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
            .clip(skinShape(14.dp))
            .background(Surface.copy(alpha = 0.92f))
            .border(1.dp, Outline.copy(alpha = 0.65f), skinShape(14.dp))
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
                Text(drivetrain.rpm.toInt().toString(), color = OnSurface, fontSize = 42.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(" RPM", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                MixerTelemetryReadout("SPEED", drivetrain.realOrDocumentedRawSpeedKmh.toInt().toString(), "km/h")
                Spacer(Modifier.width(12.dp))
                MixerTelemetryReadout("PRED SPEED", String.format(Locale.US, "%.2f", drivetrain.presentationSpeedKmh), "km/h")
            }
            Text(gear, color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        // Canvas draw lambdas are not composable, so the skin colors are read up here first.
        val barGradient = listOf(Accent.copy(alpha = 0.35f), Warning, Danger)
        val redlineColor = Danger
        val barTrack = LocalDashboardSkin.current.meterTrack

        val barShape = skinShape(4.dp)

        Box(modifier = Modifier.fillMaxWidth().height(22.dp).clip(barShape).background(barTrack).border(1.dp, Outline, barShape)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(brush = Brush.horizontalGradient(barGradient), size = androidx.compose.ui.geometry.Size(size.width * rpmFraction, size.height))
                drawRect(color = redlineColor.copy(alpha = 0.18f), topLeft = Offset(size.width * redlineFraction, 0f), size = androidx.compose.ui.geometry.Size(size.width * (1f - redlineFraction), size.height))
                drawLine(redlineColor, Offset(size.width * redlineFraction, 0f), Offset(size.width * redlineFraction, size.height), 2f)
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
                color = Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            unit?.let {
                Text(
                    text = it,
                    color = AccentSoft,
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
                .clip(skinShape(10.dp))
                .background(SurfaceRaised)
                .border(1.dp, Outline, skinShape(10.dp)),
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
                        color = OnSurface,
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
                Favorite
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
                .padding(top = 24.dp, bottom = 48.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceRaised),
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
                        color = AccentSoft,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                    if (showGroupFilters) {
                        CarPickerGroupChip(
                            label = "MODDED",
                            selected = selectedGroup == FmodBankProfiles.moddedCarsPackId,
                            onClick = {
                                selectedGroup = FmodBankProfiles.moddedCarsPackId
                                pickerPreferences.edit()
                                    .putString("selected", FmodBankProfiles.moddedCarsPackId)
                                    .apply()
                            },
                        )
                        CarPickerGroupChip(
                            label = "ORIGINAL",
                            selected = selectedGroup == FmodBankProfiles.originalCarsPackId,
                            onClick = {
                                selectedGroup = FmodBankProfiles.originalCarsPackId
                                pickerPreferences.edit()
                                    .putString("selected", FmodBankProfiles.originalCarsPackId)
                                    .apply()
                            },
                        )
                    }
                    CarPickerSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close car picker",
                            tint = OnSurface,
                        )
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
                                    .clip(skinShape(10.dp))
                                    .background(if (profile.id == selectedCarId) Accent.copy(alpha = 0.18f) else Surface)
                                    .border(1.dp, if (profile.id == selectedCarId) Accent else Outline, skinShape(10.dp))
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
                                        .clip(skinShape(6.dp)),
                                )
                                Text(
                                    text = CarDisplayNameFormatter.format(profile.displayName),
                                    color = OnSurface,
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
                            CircularProgressIndicator(color = Accent)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun CarPickerGroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    MaterialSurface(
        color = if (selected) Accent.copy(alpha = 0.24f) else Surface,
        shape = skinShape(6.dp),
        border = BorderStroke(1.dp, if (selected) Accent else Outline),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (selected) Accent else Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
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
            .clip(skinShape(6.dp))
            .background(Surface)
            .border(1.dp, Outline, skinShape(6.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            enabled = editingEnabled,
            textStyle = TextStyle(
                color = OnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(Accent),
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
    val fillColor = outputMeterFillColor(level, LocalDashboardSkin.current)
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
            .clip(skinShape(10.dp))
            .background(Surface.copy(alpha = 0.88f))
            .border(2.dp, if (highlight) Accent else Outline.copy(alpha = 0.55f), skinShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = source.soundName,
                color = OnSurface,
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
                    color = if (source.isActive) Accent else Muted,
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
            color = AccentSoft,
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
                    .clip(skinShape(4.dp))
                    .background(MeterTrack)
                    .border(1.dp, Outline.copy(alpha = 0.5f), skinShape(4.dp)),
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

/** Green → accent → warning → danger as the live output meter fills. */
private fun outputMeterFillColor(level: Float, skin: DashboardSkin): Color {
    return when {
        level <= 0.01f -> skin.muted.copy(alpha = 0.35f)
        level < 0.30f -> blendColors(skin.success.copy(alpha = 0.65f), skin.accent, level / 0.30f)
        level < 0.60f -> blendColors(skin.accent, skin.warning, (level - 0.30f) / 0.30f)
        level < 0.85f -> blendColors(skin.warning, skin.accentHot, (level - 0.60f) / 0.25f)
        else -> blendColors(skin.accentHot, skin.danger, (level - 0.85f) / 0.15f)
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
