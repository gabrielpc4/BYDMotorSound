package com.gabrielpc.enginesoundsimulator

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.AssociationInfo
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.Choreographer
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.gabrielpc.enginesoundsimulator.drive.DriveController
import com.gabrielpc.enginesoundsimulator.drive.DriveSnapshot
import com.gabrielpc.enginesoundsimulator.drive.BackfireSettings
import com.gabrielpc.enginesoundsimulator.drive.EffectSoundKind
import com.gabrielpc.enginesoundsimulator.drive.GearProfileSelection
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioGainResolver
import com.gabrielpc.enginesoundsimulator.drive.SpeedAudioSettings
import com.gabrielpc.enginesoundsimulator.drive.UserVisibleMessage
import com.gabrielpc.enginesoundsimulator.drive.UserVisibleMessageSeverity
import com.gabrielpc.enginesoundsimulator.drive.InputMode
import com.gabrielpc.enginesoundsimulator.drive.CruisingShiftOffsetByTachMaxRpm
import com.gabrielpc.enginesoundsimulator.audio.MasterOutputLevel
import com.gabrielpc.enginesoundsimulator.audio.FmodBankProfiles
import com.gabrielpc.enginesoundsimulator.audio.CarSubtitleCatalog
import com.gabrielpc.enginesoundsimulator.audio.FmodBankResolver
import com.gabrielpc.enginesoundsimulator.audio.MediaShiftButtonCoordinator
import com.gabrielpc.enginesoundsimulator.audio.IphoneAcousticMeterProtocol
import com.gabrielpc.enginesoundsimulator.audio.IphoneCalibrationLogLevel
import com.gabrielpc.enginesoundsimulator.audio.BackfirePreviewPlayer
import com.gabrielpc.enginesoundsimulator.simulation.AutomaticTransmissionMode
import com.gabrielpc.enginesoundsimulator.simulation.DrivetrainState
import com.gabrielpc.enginesoundsimulator.simulation.TransmissionPosition
import com.gabrielpc.enginesoundsimulator.ui.tach.AudioLabTachometer
import com.gabrielpc.enginesoundsimulator.ui.theme.Accent
import com.gabrielpc.enginesoundsimulator.ui.theme.AccentSoft
import com.gabrielpc.enginesoundsimulator.ui.theme.AutomaticTransmissionModeCaption
import com.gabrielpc.enginesoundsimulator.ui.theme.CONDENSED_FONT_FAMILY_NAME
import com.gabrielpc.enginesoundsimulator.ui.theme.Background
import com.gabrielpc.enginesoundsimulator.ui.theme.ControlShape
import com.gabrielpc.enginesoundsimulator.ui.theme.Danger
import com.gabrielpc.enginesoundsimulator.ui.theme.DashboardTheme
import com.gabrielpc.enginesoundsimulator.ui.theme.DisplayFamily
import com.gabrielpc.enginesoundsimulator.ui.theme.EngineSoundsSimulatorTheme
import com.gabrielpc.enginesoundsimulator.ui.theme.ErrorBannerBody
import com.gabrielpc.enginesoundsimulator.ui.theme.HardwareBackdrop
import com.gabrielpc.enginesoundsimulator.ui.theme.HardwareBorder
import com.gabrielpc.enginesoundsimulator.ui.theme.HardwareGradient
import com.gabrielpc.enginesoundsimulator.ui.theme.HardwareSlotBorder
import com.gabrielpc.enginesoundsimulator.ui.theme.InfoBannerBody
import com.gabrielpc.enginesoundsimulator.ui.theme.LocalDashboardSkin
import com.gabrielpc.enginesoundsimulator.ui.theme.Muted
import com.gabrielpc.enginesoundsimulator.ui.theme.OnSurface
import com.gabrielpc.enginesoundsimulator.ui.theme.Outline
import com.gabrielpc.enginesoundsimulator.ui.theme.PanelShape
import com.gabrielpc.enginesoundsimulator.ui.theme.RealPedalsAccent
import com.gabrielpc.enginesoundsimulator.ui.theme.StatusFault
import com.gabrielpc.enginesoundsimulator.ui.theme.StatusHealthy
import com.gabrielpc.enginesoundsimulator.ui.theme.Success
import com.gabrielpc.enginesoundsimulator.ui.theme.StadiumShape
import com.gabrielpc.enginesoundsimulator.ui.theme.softFillShape
import com.gabrielpc.enginesoundsimulator.ui.theme.hardwareShape
import com.gabrielpc.enginesoundsimulator.ui.theme.skinPillShape
import com.gabrielpc.enginesoundsimulator.ui.theme.skinShape
import com.gabrielpc.enginesoundsimulator.ui.theme.Surface
import com.gabrielpc.enginesoundsimulator.ui.theme.SurfaceRaised
import com.gabrielpc.enginesoundsimulator.ui.theme.Warning
import com.gabrielpc.enginesoundsimulator.ui.theme.rememberDashboardThemeController
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.Locale

/** Fixed dashboard layout values for the BYD multimedia safe area. */
private object DashboardLayoutDefaults {
    const val UI_SCALE = 0.8f
    const val CANVAS_WIDTH_PX = 1920
    const val CANVAS_HEIGHT_PX = 942
    const val CANVAS_ASPECT_RATIO = CANVAS_WIDTH_PX.toFloat() / CANVAS_HEIGHT_PX

    /** Emulator AVD panel size: 70% of [CANVAS_WIDTH_PX]×[CANVAS_HEIGHT_PX] (multimedia auto-scales ~30%). */
    const val EMULATOR_PANEL_WIDTH_PX = 1344
    const val EMULATOR_PANEL_HEIGHT_PX = 659
    /** Classic layout keeps the tach as a right-side overlay sized like the old 0.88 row weight. */
    const val TACHOMETER_OVERLAY_WIDTH_FRACTION = 0.88f / (1.12f + 0.88f)
    /** Car preview/header slot matches the old 1.12 row beside the tach. */
    const val CLASSIC_CAR_STAGE_WIDTH_FRACTION = 1.12f / (1.12f + 0.88f)
    val classicContentStartPadding = 8.dp
    val classicContentEndPadding = 8.dp
}

private object CarStageTapDefaults {
    val sideStripWidth = 116.dp
    val navigationButtonHeight = 232.dp
    val navigationIconSize = 48.dp
    val navigationArrowFontSize = 84.sp
    val favoriteCornerHeight = 88.dp
}

class MainActivity : ComponentActivity() {
    private val controller: DriveController
        get() = (application as EngineSoundsApplication).driveController

    private val choreographer by lazy(LazyThreadSafetyMode.NONE) { Choreographer.getInstance() }
    private var driveState by mutableStateOf<DriveSnapshot?>(null)
    private var uiMonitoringActive by mutableStateOf(false)
    private val backfirePreviewPlayer by lazy(LazyThreadSafetyMode.NONE) { BackfirePreviewPlayer(this) }
    private var legacyIphoneScanRequested = false
    private val companionChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            logIphoneCalibration(
                IphoneCalibrationLogLevel.WARN,
                "Companion chooser closed without a device (result=${result.resultCode}).",
            )
            return@registerForActivityResult
        }
        val address = if (Build.VERSION.SDK_INT >= 33) {
            result.data
                ?.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
                ?.deviceMacAddress
                ?.toString()
        } else {
            @Suppress("DEPRECATION")
            when (val device = result.data?.getParcelableExtra<android.os.Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)) {
                is ScanResult -> device.device.address
                is BluetoothDevice -> device.address
                else -> null
            }
        }
        if (address == null) {
            logIphoneCalibration(
                IphoneCalibrationLogLevel.ERROR,
                "Companion chooser returned OK but no Bluetooth address was found.",
            )
        } else {
            logIphoneCalibration(IphoneCalibrationLogLevel.OK, "Companion chooser selected $address.")
            controller.selectIphoneAcousticMeter(address)
        }
    }
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        grants.forEach { (permission, granted) ->
            val label = permission.substringAfterLast('.')
            if (granted) {
                logIphoneCalibration(IphoneCalibrationLogLevel.OK, "$label permission granted.")
            } else {
                logIphoneCalibration(IphoneCalibrationLogLevel.ERROR, "$label permission denied.")
            }
        }
        if (grants.values.all { it }) {
            if (legacyIphoneScanRequested) {
                legacyIphoneScanRequested = false
                logIphoneCalibration(
                    IphoneCalibrationLogLevel.INFO,
                    "Legacy BLE scan path selected after permission grant.",
                )
                controller.selectIphoneAcousticMeter("")
            } else {
                beginIphoneMeterAssociation()
            }
        } else {
            logIphoneCalibration(
                IphoneCalibrationLogLevel.ERROR,
                "Pairing stopped because required permissions were denied.",
            )
        }
    }

    private val refreshUi = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!uiMonitoringActive) {
                return
            }

            driveState = controller.snapshot()
            choreographer.postFrameCallback(this)
        }
    }

    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && controller.handleShiftKey(event.keyCode)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        driveState = controller.snapshot()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.statusBars())
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        setContent {
            val themeController = rememberDashboardThemeController(this)

            EngineSoundsSimulatorTheme(skin = themeController.skin) {
                driveState?.let { state ->
                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        // One density multiplier scales every dp/sp dimension in every screen.
                        LocalDensity provides Density(
                            density = baseDensity.density * DashboardLayoutDefaults.UI_SCALE,
                            fontScale = baseDensity.fontScale * DashboardLayoutDefaults.UI_SCALE,
                        ),
                        LocalDashboardSkin provides themeController.skin,
                        // Text call sites never set fontFamily, so providing it here restyles every
                        // label at once. Readouts that ask for Monospace explicitly keep it.
                        LocalTextStyle provides LocalTextStyle.current.copy(
                            fontFamily = themeController.skin.displayFamily,
                        ),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MotorSoundDashboard(
                                state = state,
                                uiMonitoringActive = uiMonitoringActive,
                                onToggleDashboardTheme = themeController::toggle,
                                onThrottle = controller::setSimulatedPedalThrottle,
                                onBrake = controller::setSimulatedPedalBrake,
                                onSimulatedRegen = controller::setSimulatedRegen,
                                onToggleSimulatedPedalLatch = controller::setSimulatedPedalsLatched,
                                onTransmissionPositionChange = controller::setTransmissionPosition,
                                onSelectSimulatedPedals = controller::selectSimulatedPedals,
                                onSelectRealPedals = controller::selectRealPedals,
                                onToggleInputSource = controller::toggleInputSource,
                                onToggleAudioMute = controller::toggleAudioMute,
                                onEffectOverrideChange = controller::setEffectOverride,
                                onEngineExternalChange = { enabled ->
                                    controller.setSoundPerspective(
                                        if (enabled) {
                                            com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective.EXTERIOR
                                        } else {
                                            com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective.CABIN
                                        },
                                    )
                                },
                                onEnginePureChange = controller::setExteriorPureAudio,
                                onCruisingLogicChange = controller::setCruisingLogicEnabled,
                                onResetAllPreferences = {
                                    controller.resetAllPreferences()
                                    clearIphoneCompanionAssociations()
                                    themeController.resetToDefault()
                                },
                                onExportSettings = controller::exportAllPreferences,
                                onRescanBanks = controller::rescanBanks,
                                onToggleManualShiftMode = controller::toggleManualShiftMode,
                                onMediaShiftButton = controller::handleMediaShiftButton,
                                onGearProfileSelectionChange = controller::setGearProfileSelection,
                                onVirtualGearSpeedBoundaryChange = controller::setVirtualGearSpeedBoundary,
                                onRestoreVirtualGearSpeedBoundaries = controller::restoreVirtualGearSpeedBoundaries,
                                onAllowManualOnLaunchEnabledChange = controller::setAllowManualOnLaunchEnabled,
                                onManualTransmissionKickdownEnabledChange =
                                    controller::setManualTransmissionKickdownEnabled,
                                onTachometerCruisingShiftRangeOverlayEnabledChange =
                                    controller::setTachometerCruisingShiftRangeOverlayEnabled,
                                onLowSpeedCrawlRpmHoldEnabledChange =
                                    controller::setLowSpeedCrawlRpmHoldEnabled,
                                onCruisingShiftOffsetForTachMaxRpmChange =
                                    controller::setCruisingShiftOffsetForTachMaxRpm,
                                onRacingReturnThrottlePercentChange = controller::setRacingReturnThrottlePercent,
                                onRacingEnterMinThrottlePercentChange =
                                    controller::setRacingEnterMinThrottlePercent,
                                onRacingReturnHoldSecondsChange = controller::setRacingReturnHoldSeconds,
                                onKickdownStompDeltaPercentChange = controller::setKickdownStompDeltaPercent,
                                onKickdownStompMinThrottlePercentChange =
                                    controller::setKickdownStompMinThrottlePercent,
                                onRacingEnterDelayMillisecondsChange = controller::setRacingEnterDelayMilliseconds,
                                onAutomaticUpshiftMillisecondsChange = controller::setAutomaticUpshiftMilliseconds,
                                onAutomaticDownshiftMillisecondsChange = controller::setAutomaticDownshiftMilliseconds,
                                onManualRedlineHoldSecondsChange = controller::setManualRedlineHoldSeconds,
                                onManualAutodownshiftRpmChange = controller::setManualAutodownshiftRpm,
                                onMinimumAudioThrottleChange = controller::setMinimumAudioThrottle,
                                onPedalAudioThrottleRampUpMillisecondsChange =
                                    controller::setPedalAudioThrottleRampUpMilliseconds,
                                onPedalAudioThrottleRampDownMillisecondsChange =
                                    controller::setPedalAudioThrottleRampDownMilliseconds,
                                onManualUpshift = controller::requestManualUpshift,
                                onManualDownshift = controller::requestManualDownshift,
                                onMixerGlobalGainsChange = controller::setMixerGlobalGains,
                                onAppVolumePercentChange = controller::setAppVolumePercent,
                                onMixerCarSpecificGainsChange = controller::setMixerCarSpecificGains,
                                onResetMixerCarSpecificGains =
                                    controller::resetMixerCarSpecificGainsForCurrentSelection,
                                onFmodUpdateRateChange = controller::setFmodUpdateRateHz,
                                onExteriorPureAudioChange = controller::setExteriorPureAudio,
                                onMixerDiagnosticsActive = controller::setMixerDiagnosticsActive,
                                onOverrideGainChange = controller::setEffectSoundOverrideGain,
                                onBackfireSettingsChange = controller::setBackfireSettings,
                                onPreviewBackfireSample = backfirePreviewPlayer::play,
                                onSpeedAudioSettingsChange = controller::setSpeedAudioSettings,
                                onEventMute = controller::setFmodEventMute,
                                onEventSolo = controller::setFmodEventSolo,
                                onPreviousCar = controller::selectPreviousCar,
                                onNextCar = controller::selectNextCar,
                                onShuffleCar = controller::selectShuffleCar,
                                onSelectCar = controller::selectCar,
                                onToggleCarFavorite = controller::toggleCarFavorite,
                                onSoundPerspectiveChange = controller::setSoundPerspective,
                                onCalibrateAllCars = {
                                    backfirePreviewPlayer.release()
                                    controller.stopManualLoudnessPreviews()
                                    controller.startLoudnessCalibration(resume = false)
                                },
                                onResumeLoudnessCalibration = {
                                    backfirePreviewPlayer.release()
                                    controller.stopManualLoudnessPreviews()
                                    controller.startLoudnessCalibration(resume = true)
                                },
                                onCancelLoudnessCalibration = controller::cancelLoudnessCalibration,
                                onAssociateIphoneMeter = ::associateIphoneMeter,
                                onForgetIphoneMeter = ::forgetIphoneMeter,
                                onClearIphoneCalibration = controller::clearIphoneCalibration,
                                onRecoverLenientAcousticMeasurements = {
                                    controller.recoverLenientAcousticMeasurements()
                                },
                                onClearIphoneCalibrationLog = controller::clearIphoneCalibrationLog,
                                onCatalogGainSettingsChange = controller::setCatalogGainSettings,
                                manualLoudnessTable = state.manualLoudnessTable,
                                onManualLoudnessDbChange = controller::setManualLoudnessDb,
                                onManualLoudnessPreviewChange = controller::setManualLoudnessPreviewActive,
                                onManualLoudnessPreviewExteriorChange = controller::setManualLoudnessPreviewExterior,
                                onStopManualLoudnessPreviews = controller::stopManualLoudnessPreviews,
                                onAdjustAllModdedManualLoudnessDb = controller::adjustAllModdedManualLoudnessDb,
                                onRestoreManualLoudnessDefaults = controller::restoreManualLoudnessDefaults,
                                onExportManualLoudnessPreset = controller::exportManualLoudnessPreset,
                                onStartAcousticDiagnostic = { code ->
                                    backfirePreviewPlayer.release()
                                    controller.stopManualLoudnessPreviews()
                                    controller.startAcousticDiagnostic(code, resume = false)
                                },
                                onResumeAcousticDiagnostic = {
                                    backfirePreviewPlayer.release()
                                    controller.stopManualLoudnessPreviews()
                                    controller.startAcousticDiagnostic(null, resume = true)
                                },
                                onCancelAcousticDiagnostic = controller::cancelAcousticDiagnostic,
                                onDismissUserMessage = controller::dismissUserMessage,
                            )
                            if (state.loudnessCalibrationProgress.isRunning) {
                                LoudnessCalibrationModal(
                                    progress = state.loudnessCalibrationProgress,
                                    onCancel = controller::cancelLoudnessCalibration,
                                )
                            }
                            if (state.acousticDiagnosticProgress.isRunning) {
                                AcousticDiagnosticModal(
                                    progress = state.acousticDiagnosticProgress,
                                    onCancel = controller::cancelAcousticDiagnostic,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        stopService(EngineRuntimeService.stopIntent(this))
        controller.setUiActive(true)
        uiMonitoringActive = true
        // start() is intentionally idempotent. Calling it after the activity returns from the
        // BYD file manager lets DriveController notice staged FMOD packages even when the
        // background engine service kept the simulation alive while this screen was closed.
        controller.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        choreographer.removeFrameCallback(refreshUi)
        choreographer.postFrameCallback(refreshUi)
        driveState = controller.snapshot()
    }

    override fun onStop() {
        releaseManualControls()
        uiMonitoringActive = false
        controller.setUiActive(false)
        controller.setMixerDiagnosticsActive(false)
        choreographer.removeFrameCallback(refreshUi)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startService(EngineRuntimeService.startIntent(this))
        super.onStop()
    }

    override fun onDestroy() {
        backfirePreviewPlayer.release()
        if (isFinishing) {
            (application as EngineSoundsApplication).shutdownEngine()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) releaseManualControls()
    }

    private fun logIphoneCalibration(level: IphoneCalibrationLogLevel, message: String) {
        controller.logIphoneCalibration(level, message)
    }

    private fun associateIphoneMeter() {
        logIphoneCalibration(IphoneCalibrationLogLevel.INFO, "PAIR IPHONE tapped.")
        logIphoneCalibration(
            IphoneCalibrationLogLevel.INFO,
            "Android API ${Build.VERSION.SDK_INT}; linked=${controller.isIphoneMeterLinked()}; " +
                "selected=${controller.isIphoneMeterSelected()}.",
        )
        if (needsLegacyBleLocationPermission() && !hasFineLocationPermission()) {
            legacyIphoneScanRequested = Build.VERSION.SDK_INT < 26
            logIphoneCalibration(
                IphoneCalibrationLogLevel.INFO,
                "Requesting ACCESS_FINE_LOCATION for BLE discovery.",
            )
            bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
                logIphoneCalibration(
                    IphoneCalibrationLogLevel.INFO,
                    "Requesting Bluetooth scan/connect permissions.",
                )
                bluetoothPermissionLauncher.launch(permissions)
                return
            }
        }
        beginIphoneMeterAssociation()
    }

    private fun needsLegacyBleLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT <= 30
    }

    private fun hasFineLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldSkipCompanionDeviceAssociation(): Boolean {
        // DiLink and other automotive builds through Android 11 accept associate() but never
        // invoke onDeviceFound/onFailure, leaving pairing stuck after "scan requested".
        return Build.VERSION.SDK_INT <= 30
    }

    private fun beginIphoneMeterAssociation() {
        if (Build.VERSION.SDK_INT < 26) {
            logIphoneCalibration(
                IphoneCalibrationLogLevel.INFO,
                "API < 26: using legacy BLE scan path.",
            )
            controller.selectIphoneAcousticMeter("")
            return
        }
        if (shouldSkipCompanionDeviceAssociation()) {
            logIphoneCalibration(
                IphoneCalibrationLogLevel.INFO,
                "Skipping companion chooser on API ${Build.VERSION.SDK_INT}; using direct BLE scan.",
            )
            selectIphoneMeterFallbackScan()
            return
        }
        val manager = getSystemService(CompanionDeviceManager::class.java)
        if (manager == null) {
            logIphoneCalibration(
                IphoneCalibrationLogLevel.WARN,
                "CompanionDeviceManager unavailable on this head unit.",
            )
            selectIphoneMeterFallbackScan()
            return
        }
        logIphoneCalibration(
            IphoneCalibrationLogLevel.INFO,
            "Starting companion association for service ${IphoneAcousticMeterProtocol.SERVICE_UUID}.",
        )
        val filter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(IphoneAcousticMeterProtocol.SERVICE_UUID))
                    .build(),
            )
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(true)
            .build()
        runCatching {
            manager.associate(
                request,
                object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: android.content.IntentSender) {
                        logIphoneCalibration(
                            IphoneCalibrationLogLevel.OK,
                            "Companion scan found a device; opening system chooser.",
                        )
                        companionChooserLauncher.launch(IntentSenderRequest.Builder(chooserLauncher).build())
                    }

                    override fun onFailure(error: CharSequence?) {
                        logIphoneCalibration(
                            IphoneCalibrationLogLevel.WARN,
                            "Companion association failed: ${error ?: "unknown error"}.",
                        )
                        selectIphoneMeterFallbackScan()
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            logIphoneCalibration(IphoneCalibrationLogLevel.INFO, "Companion association scan requested.")
        }.onFailure { error ->
            logIphoneCalibration(
                IphoneCalibrationLogLevel.ERROR,
                "Companion association threw ${error::class.java.simpleName}: ${error.message ?: "no message"}.",
            )
            selectIphoneMeterFallbackScan()
        }
    }

    private fun selectIphoneMeterFallbackScan() {
        // Some BYD builds omit the companion chooser. An empty address explicitly selects the
        // permission-backed service UUID scan used by the worker.
        logIphoneCalibration(IphoneCalibrationLogLevel.INFO, "Falling back to direct service-UUID BLE scan.")
        if (needsLegacyBleLocationPermission() && !hasFineLocationPermission()) {
            legacyIphoneScanRequested = true
            logIphoneCalibration(
                IphoneCalibrationLogLevel.INFO,
                "Fallback still needs ACCESS_FINE_LOCATION; requesting it now.",
            )
            bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            controller.selectIphoneAcousticMeter("")
            logIphoneCalibration(
                IphoneCalibrationLogLevel.OK,
                "Ready to pair. Enter the six-digit iPhone code, then tap CALIBRATE WITH IPHONE.",
            )
        }
    }

    private fun forgetIphoneMeter() {
        logIphoneCalibration(IphoneCalibrationLogLevel.INFO, "FORGET IPHONE tapped.")
        controller.forgetIphoneMeter()
        clearIphoneCompanionAssociations()
        logIphoneCalibration(
            IphoneCalibrationLogLevel.OK,
            "Bluetooth pairing reset in the app. Tap PAIR IPHONE to link again.",
        )
    }

    private fun clearIphoneCompanionAssociations() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val manager = getSystemService(CompanionDeviceManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                manager.myAssociations.forEach { association -> manager.disassociate(association.id) }
            } else {
                @Suppress("DEPRECATION")
                manager.associations.forEach { address -> manager.disassociate(address) }
            }
        }
    }

    private fun releaseManualControls() {
        controller.setSimulatedPedalThrottle(0.0)
        controller.setSimulatedPedalBrake(0.0)
    }

}

@Composable
private fun MotorSoundDashboard(
    state: DriveSnapshot,
    uiMonitoringActive: Boolean,
    onToggleDashboardTheme: () -> Unit,
    onThrottle: (Double) -> Unit,
    onBrake: (Double) -> Unit,
    onSimulatedRegen: (Double) -> Unit,
    onToggleSimulatedPedalLatch: (Boolean) -> Unit,
    onTransmissionPositionChange: (TransmissionPosition) -> Unit,
    onSelectSimulatedPedals: () -> Unit,
    onSelectRealPedals: () -> Unit,
    onToggleInputSource: () -> Unit,
    onToggleAudioMute: () -> Boolean,
    onEffectOverrideChange: (EffectSoundKind, Boolean) -> Unit,
    onEngineExternalChange: (Boolean) -> Unit,
    onEnginePureChange: (Boolean) -> Unit,
    onCruisingLogicChange: (Boolean) -> Unit,
    onResetAllPreferences: () -> Unit,
    onExportSettings: () -> Unit,
    onRescanBanks: () -> Unit,
    onToggleManualShiftMode: () -> Unit,
    onMediaShiftButton: (Int) -> Boolean,
    onGearProfileSelectionChange: (GearProfileSelection) -> Unit,
    onVirtualGearSpeedBoundaryChange: (Int, Int, Int) -> Unit,
    onRestoreVirtualGearSpeedBoundaries: (Int) -> Unit,
    onAllowManualOnLaunchEnabledChange: (Boolean) -> Unit,
    onManualTransmissionKickdownEnabledChange: (Boolean) -> Unit,
    onTachometerCruisingShiftRangeOverlayEnabledChange: (Boolean) -> Unit,
    onLowSpeedCrawlRpmHoldEnabledChange: (Boolean) -> Unit,
    onCruisingShiftOffsetForTachMaxRpmChange: (Int, Int) -> Unit,
    onRacingReturnThrottlePercentChange: (Int) -> Unit,
    onRacingEnterMinThrottlePercentChange: (Int) -> Unit,
    onRacingReturnHoldSecondsChange: (Int) -> Unit,
    onKickdownStompDeltaPercentChange: (Int) -> Unit,
    onKickdownStompMinThrottlePercentChange: (Int) -> Unit,
    onRacingEnterDelayMillisecondsChange: (Int) -> Unit,
    onAutomaticUpshiftMillisecondsChange: (Int) -> Unit,
    onAutomaticDownshiftMillisecondsChange: (Int) -> Unit,
    onManualRedlineHoldSecondsChange: (Int) -> Unit,
    onManualAutodownshiftRpmChange: (Int) -> Unit,
    onMinimumAudioThrottleChange: (Float) -> Unit,
    onPedalAudioThrottleRampUpMillisecondsChange: (Int) -> Unit,
    onPedalAudioThrottleRampDownMillisecondsChange: (Int) -> Unit,
    onManualUpshift: () -> Unit,
    onManualDownshift: () -> Unit,
    onMixerGlobalGainsChange: (com.gabrielpc.enginesoundsimulator.audio.MixerGlobalGains) -> Unit,
    onAppVolumePercentChange: (Int) -> Unit,
    onMixerCarSpecificGainsChange: (com.gabrielpc.enginesoundsimulator.audio.MixerCarSpecificGains) -> Unit,
    onResetMixerCarSpecificGains: () -> Unit,
    onFmodUpdateRateChange: (Int) -> Unit,
    onExteriorPureAudioChange: (Boolean) -> Unit,
    onMixerDiagnosticsActive: (Boolean) -> Unit,
    onOverrideGainChange: (EffectSoundKind, Float) -> Unit,
    onBackfireSettingsChange: (BackfireSettings) -> Unit,
    onPreviewBackfireSample: (Int) -> Unit,
    onSpeedAudioSettingsChange: (SpeedAudioSettings) -> Unit,
    onEventMute: (String, Boolean) -> Unit,
    onEventSolo: (String, Boolean) -> Unit,
    onPreviousCar: () -> Unit,
    onNextCar: () -> Unit,
    onShuffleCar: () -> Unit,
    onSelectCar: (String) -> Unit,
    onToggleCarFavorite: (String) -> Unit,
    onSoundPerspectiveChange: (com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective) -> Unit,
    onCalibrateAllCars: () -> Unit,
    onResumeLoudnessCalibration: () -> Unit,
    onCancelLoudnessCalibration: () -> Unit,
    onAssociateIphoneMeter: () -> Unit,
    onForgetIphoneMeter: () -> Unit,
    onClearIphoneCalibration: () -> Unit,
    onRecoverLenientAcousticMeasurements: () -> Unit,
    onClearIphoneCalibrationLog: () -> Unit,
    onCatalogGainSettingsChange: (com.gabrielpc.enginesoundsimulator.audio.CatalogGainSettings) -> Unit,
    manualLoudnessTable: List<com.gabrielpc.enginesoundsimulator.audio.ManualLoudnessTableEntry>,
    onManualLoudnessDbChange: (String, com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective, Double) -> Unit,
    onManualLoudnessPreviewChange: (String, Boolean) -> Unit,
    onManualLoudnessPreviewExteriorChange: (String, Boolean) -> Unit,
    onStopManualLoudnessPreviews: () -> Unit,
    onAdjustAllModdedManualLoudnessDb: (Double) -> Unit,
    onRestoreManualLoudnessDefaults: () -> Unit,
    onExportManualLoudnessPreset: () -> Unit,
    onStartAcousticDiagnostic: (String?) -> Unit,
    onResumeAcousticDiagnostic: () -> Unit,
    onCancelAcousticDiagnostic: () -> Unit,
    onDismissUserMessage: () -> Unit,
) {
    var mainScreen by remember {
        mutableStateOf(
            if (RuntimeFeatureFlags.START_ON_MIXER) {
                DashboardMainScreen.MIXER
            } else {
                DashboardMainScreen.CLASSIC
            },
        )
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(mainScreen) {
        onMixerDiagnosticsActive(mainScreen == DashboardMainScreen.MIXER)
    }
    MaterialSurface(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val pressed = event.type == KeyEventType.KeyDown
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        onThrottle(if (pressed) 1.0 else 0.0)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_S,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_SPACE,
                    -> {
                        onBrake(if (pressed) 1.0 else 0.0)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                    -> {
                        if (pressed) {
                            onMediaShiftButton(event.nativeKeyEvent.keyCode)
                        }
                        MediaShiftButtonCoordinator.isMediaShiftKeyCode(event.nativeKeyEvent.keyCode)
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    -> {
                        if (pressed) {
                            onMediaShiftButton(event.nativeKeyEvent.keyCode)
                        }
                        MediaShiftButtonCoordinator.isMediaShiftKeyCode(event.nativeKeyEvent.keyCode)
                    }
                    else -> false
                }
            },
        color = Background,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            val canvasAspectRatio = DashboardLayoutDefaults.CANVAS_ASPECT_RATIO
            val heightForFullWidth = maxWidth / canvasAspectRatio
            val (dashboardWidth, dashboardHeight) = if (heightForFullWidth <= maxHeight) {
                maxWidth to heightForFullWidth
            } else {
                (maxHeight * canvasAspectRatio) to maxHeight
            }

            Box(
                modifier = Modifier.width(dashboardWidth).height(dashboardHeight),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    DashboardHeader(
                        state = state,
                        uiMonitoringActive = uiMonitoringActive,
                        mainScreen = mainScreen,
                        onMainScreenChange = { screen ->
                            mainScreen = screen
                        },
                        onSelectSimulatedPedals = onSelectSimulatedPedals,
                        onSelectRealPedals = onSelectRealPedals,
                        onToggleInputSource = onToggleInputSource,
                        onToggleAudioMute = onToggleAudioMute,
                        onToggleManualShiftMode = onToggleManualShiftMode,
                        onToggleDashboardTheme = onToggleDashboardTheme,
                        onOpenSettings = { mainScreen = DashboardMainScreen.SETTINGS },
                    )

                    state.userMessage?.let { message ->
                        DismissableUserMessageBanner(
                            message = message,
                            onDismiss = onDismissUserMessage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 34.dp, vertical = 8.dp),
                        )
                    }

                    when (mainScreen) {
                        DashboardMainScreen.CLASSIC -> BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 34.dp, vertical = 6.dp),
                        ) {
                            val carStageWidth = maxWidth * DashboardLayoutDefaults.CLASSIC_CAR_STAGE_WIDTH_FRACTION
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CarStage(
                                    state = state,
                                    onPreviousCar = onPreviousCar,
                                    onNextCar = onNextCar,
                                    onShuffleCar = onShuffleCar,
                                    onSelectCar = onSelectCar,
                                    onToggleCarFavorite = onToggleCarFavorite,
                                    modifier = Modifier
                                        .align(Alignment.Start)
                                        .width(carStageWidth)
                                        .weight(1f),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    DashboardClassicAudioControlsStack(
                                        state = state,
                                        onManualLoudnessDbChange = onManualLoudnessDbChange,
                                        onEffectOverrideChange = onEffectOverrideChange,
                                        onOverrideGainChange = onOverrideGainChange,
                                        modifier = Modifier.padding(
                                            start = DashboardLayoutDefaults.classicContentStartPadding,
                                            bottom = 2.dp,
                                        ),
                                    )
                                    ClassicDriveControls(
                                        state = state,
                                        onThrottle = onThrottle,
                                        onBrake = onBrake,
                                        onSimulatedRegen = onSimulatedRegen,
                                        onToggleSimulatedPedalLatch = { onToggleSimulatedPedalLatch(!state.simulatedPedalsLatched) },
                                        onTransmissionPositionChange = onTransmissionPositionChange,
                                        onManualUpshift = onManualUpshift,
                                        onManualDownshift = onManualDownshift,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 28.dp),
                                    )
                                    DashboardMixerLauncherButton(
                                        onClick = { mainScreen = DashboardMainScreen.MIXER },
                                        modifier = Modifier.padding(
                                            end = DashboardLayoutDefaults.classicContentEndPadding,
                                            bottom = 12.dp,
                                        ),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(maxWidth * DashboardLayoutDefaults.TACHOMETER_OVERLAY_WIDTH_FRACTION)
                                    .fillMaxHeight()
                                    .padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Tachometer(
                                    drivetrain = state.drivetrain,
                                    transmissionPosition = state.transmissionPosition,
                                    manualShiftModeEnabled = state.manualShiftModeEnabled,
                                    cruisingLogicEnabled = state.cruisingLogicEnabled,
                                    cruisingShiftRangeOverlayEnabled = state.tachometerCruisingShiftRangeOverlayEnabled,
                                    maxRpm = state.drivetrain.tachometerMaximumRpm,
                                    redlineRpm = state.drivetrain.redlineRpm,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                )
                                DashboardTachometerAccessoryControls(
                                    state = state,
                                    gearProfileSelection = state.gearProfileSelection,
                                    onCruisingShiftOffsetForTachMaxRpmChange = onCruisingShiftOffsetForTachMaxRpmChange,
                                    onCruisingLogicChange = onCruisingLogicChange,
                                    onGearProfileSelectionChange = onGearProfileSelectionChange,
                                    onEngineExternalChange = onEngineExternalChange,
                                    onEnginePureChange = onEnginePureChange,
                                )
                            }
                        }
                        DashboardMainScreen.MIXER -> MixerDashboardScreen(
                            state = state,
                            onThrottle = onThrottle,
                            onBrake = onBrake,
                            onSimulatedRegen = onSimulatedRegen,
                            onToggleSimulatedPedalLatch = { onToggleSimulatedPedalLatch(!state.simulatedPedalsLatched) },
                            onTransmissionPositionChange = onTransmissionPositionChange,
                            onSelectCar = onSelectCar,
                            onToggleCarFavorite = onToggleCarFavorite,
                            soundPerspective = state.soundPerspective,
                            onSoundPerspectiveChange = onSoundPerspectiveChange,
                            onManualUpshift = onManualUpshift,
                            onManualDownshift = onManualDownshift,
                            onMixerGlobalGainsChange = onMixerGlobalGainsChange,
                            onAppVolumePercentChange = onAppVolumePercentChange,
                            onMixerCarSpecificGainsChange = onMixerCarSpecificGainsChange,
                            onResetMixerCarSpecificGains = onResetMixerCarSpecificGains,
                            onEventMute = onEventMute,
                            onEventSolo = onEventSolo,
                            exteriorPureAudio = state.exteriorPureAudio,
                            onExteriorPureAudioChange = onExteriorPureAudioChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                        DashboardMainScreen.SETTINGS -> SettingsScreen(
                            onExportSettings = onExportSettings,
                            onResetAll = onResetAllPreferences,
                            onRescanBanks = onRescanBanks,
                            loudnessNormalizationSummary = state.loudnessNormalizationSummary,
                            loudnessCalibrationProgress = state.loudnessCalibrationProgress,
                            onCalibrateAllCars = onCalibrateAllCars,
                            onResumeLoudnessCalibration = onResumeLoudnessCalibration,
                            onCancelLoudnessCalibration = onCancelLoudnessCalibration,
                            acousticDiagnosticSummary = state.acousticDiagnosticSummary,
                            acousticDiagnosticProgress = state.acousticDiagnosticProgress,
                            iphoneMeterLinked = state.iphoneMeterLinked,
                            iphoneMeterSelected = state.iphoneMeterSelected,
                            iphoneCalibrationLogLines = state.iphoneCalibrationLogLines,
                            onAssociateIphoneMeter = onAssociateIphoneMeter,
                            onForgetIphoneMeter = onForgetIphoneMeter,
                            onClearIphoneCalibration = onClearIphoneCalibration,
                            onRecoverLenientAcousticMeasurements = onRecoverLenientAcousticMeasurements,
                            onClearIphoneCalibrationLog = onClearIphoneCalibrationLog,
                            catalogGainSettings = state.catalogGainSettings,
                            catalogGainTable = state.catalogGainTable,
                            onCatalogGainSettingsChange = onCatalogGainSettingsChange,
                            manualLoudnessTable = manualLoudnessTable,
                            onManualLoudnessDbChange = onManualLoudnessDbChange,
                            onManualLoudnessPreviewChange = onManualLoudnessPreviewChange,
                            onManualLoudnessPreviewExteriorChange = onManualLoudnessPreviewExteriorChange,
                            onStopManualLoudnessPreviews = onStopManualLoudnessPreviews,
                            onAdjustAllModdedManualLoudnessDb = onAdjustAllModdedManualLoudnessDb,
                            onRestoreManualLoudnessDefaults = onRestoreManualLoudnessDefaults,
                            onExportManualLoudnessPreset = onExportManualLoudnessPreset,
                            onStartAcousticDiagnostic = onStartAcousticDiagnostic,
                            onResumeAcousticDiagnostic = onResumeAcousticDiagnostic,
                            onCancelAcousticDiagnostic = onCancelAcousticDiagnostic,
                            fmodUpdateRateHz = state.fmodUpdateRateHz,
                            onFmodUpdateRateChange = onFmodUpdateRateChange,
                            backfireSettings = state.backfireSettings,
                            onBackfireSettingsChange = onBackfireSettingsChange,
                            virtualGearSpeedBoundaries = state.virtualGearSpeedBoundaries,
                            gearProfileSelection = state.gearProfileSelection,
                            onGearProfileSelectionChange = onGearProfileSelectionChange,
                            onVirtualGearSpeedBoundaryChange = onVirtualGearSpeedBoundaryChange,
                            onRestoreVirtualGearSpeedBoundaries = onRestoreVirtualGearSpeedBoundaries,
                            allowManualOnLaunchEnabled = state.allowManualOnLaunchEnabled,
                            onAllowManualOnLaunchEnabledChange = onAllowManualOnLaunchEnabledChange,
                            manualTransmissionKickdownEnabled = state.manualTransmissionKickdownEnabled,
                            onManualTransmissionKickdownEnabledChange =
                                onManualTransmissionKickdownEnabledChange,
                            minimumAudioThrottle = state.minimumAudioThrottle,
                            onMinimumAudioThrottleChange = onMinimumAudioThrottleChange,
                            racingEnterMinThrottlePercent = state.racingEnterMinThrottlePercent,
                            onRacingEnterMinThrottlePercentChange = onRacingEnterMinThrottlePercentChange,
                            racingReturnThrottlePercent = state.racingReturnThrottlePercent,
                            onRacingReturnThrottlePercentChange = onRacingReturnThrottlePercentChange,
                            racingReturnHoldSeconds = state.racingReturnHoldSeconds,
                            onRacingReturnHoldSecondsChange = onRacingReturnHoldSecondsChange,
                            kickdownStompDeltaPercent = state.kickdownStompDeltaPercent,
                            onKickdownStompDeltaPercentChange = onKickdownStompDeltaPercentChange,
                            kickdownStompMinThrottlePercent = state.kickdownStompMinThrottlePercent,
                            onKickdownStompMinThrottlePercentChange = onKickdownStompMinThrottlePercentChange,
                            racingEnterDelayMilliseconds = state.racingEnterDelayMilliseconds,
                            onRacingEnterDelayMillisecondsChange = onRacingEnterDelayMillisecondsChange,
                            automaticUpshiftMilliseconds = state.automaticUpshiftMilliseconds,
                            onAutomaticUpshiftMillisecondsChange = onAutomaticUpshiftMillisecondsChange,
                            automaticDownshiftMilliseconds = state.automaticDownshiftMilliseconds,
                            onAutomaticDownshiftMillisecondsChange = onAutomaticDownshiftMillisecondsChange,
                            manualRedlineHoldSeconds = state.manualRedlineHoldSeconds,
                            onManualRedlineHoldSecondsChange = onManualRedlineHoldSecondsChange,
                            manualAutodownshiftRpm = state.manualAutodownshiftRpm,
                            onManualAutodownshiftRpmChange = onManualAutodownshiftRpmChange,
                            pedalAudioThrottleRampUpMilliseconds = state.pedalAudioThrottleRampUpMilliseconds,
                            onPedalAudioThrottleRampUpMillisecondsChange = onPedalAudioThrottleRampUpMillisecondsChange,
                            pedalAudioThrottleRampDownMilliseconds = state.pedalAudioThrottleRampDownMilliseconds,
                            onPedalAudioThrottleRampDownMillisecondsChange = onPedalAudioThrottleRampDownMillisecondsChange,
                            tachometerCruisingShiftRangeOverlayEnabled = state.tachometerCruisingShiftRangeOverlayEnabled,
                            onTachometerCruisingShiftRangeOverlayEnabledChange =
                                onTachometerCruisingShiftRangeOverlayEnabledChange,
                            lowSpeedCrawlRpmHoldEnabled = state.lowSpeedCrawlRpmHoldEnabled,
                            onLowSpeedCrawlRpmHoldEnabledChange = onLowSpeedCrawlRpmHoldEnabledChange,
                            cruisingShiftOffsetsByTachMaxRpm = state.cruisingShiftOffsetsByTachMaxRpm,
                            onCruisingShiftOffsetForTachMaxRpmChange = onCruisingShiftOffsetForTachMaxRpmChange,
                            onPreviewBackfireSample = onPreviewBackfireSample,
                            speedAudioSettings = state.speedAudioSettings,
                            onSpeedAudioSettingsChange = onSpeedAudioSettingsChange,
                            liveUsesRacingGain = {
                                SpeedAudioGainResolver.usesRacingGain(
                                    manualShiftEnabled = state.manualShiftModeEnabled,
                                    automaticTransmissionMode = state.drivetrain.automaticTransmissionMode,
                                    racingReturnArmed = state.drivetrain.racingReturnArmed,
                                )
                            },
                            livePreparingCruising = {
                                state.drivetrain.racingReturnArmed
                            },
                            liveSpeedKmh = { state.drivetrain.presentationSpeedKmh },
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun DashboardHeader(
    state: DriveSnapshot,
    uiMonitoringActive: Boolean,
    mainScreen: DashboardMainScreen,
    onMainScreenChange: (DashboardMainScreen) -> Unit,
    onSelectSimulatedPedals: () -> Unit,
    onSelectRealPedals: () -> Unit,
    onToggleInputSource: () -> Unit,
    onToggleAudioMute: () -> Boolean,
    onToggleManualShiftMode: () -> Unit,
    onToggleDashboardTheme: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var memoryLabels by remember {
        mutableStateOf(MemoryHeaderLabels(usageLabel = "— MB", availableLabel = "— MB left"))
    }
    var cpuLabel by remember { mutableStateOf("—% CPU") }
    var smoothedOutputLinear by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current

    LaunchedEffect(state.selectedCarId, state.audioMuted, state.engineSoundEnabled) {
        smoothedOutputLinear = 0f
    }

    val outputMeterActive = state.engineSoundEnabled &&
        !state.audioMuted &&
        state.carAudioReady

    SideEffect {
        if (!outputMeterActive) {
            smoothedOutputLinear = 0f
            return@SideEffect
        }

        val targetLinear = state.masterOutputLinear
        val smoothing = if (targetLinear > smoothedOutputLinear) {
            HEADER_OUTPUT_METER_ATTACK
        } else {
            HEADER_OUTPUT_METER_RELEASE
        }

        smoothedOutputLinear += (targetLinear - smoothedOutputLinear) * smoothing
    }

    val outputLevelLabel = if (outputMeterActive) {
        MasterOutputLevel.formatLinear(smoothedOutputLinear)
    } else {
        "— dB"
    }

    LaunchedEffect(uiMonitoringActive, Unit) {
        if (!uiMonitoringActive) {
            return@LaunchedEffect
        }

        val startupBurstEndsAtMs = System.currentTimeMillis() + HEADER_MEMORY_STARTUP_BURST_MS
        while (uiMonitoringActive) {
            memoryLabels = AppMemoryUsage.readHeaderLabels(context)
            val refreshMs = if (System.currentTimeMillis() < startupBurstEndsAtMs) {
                HEADER_MEMORY_STARTUP_REFRESH_MS
            } else {
                HEADER_MEMORY_REFRESH_MS
            }
            delay(refreshMs)
        }
    }

    LaunchedEffect(uiMonitoringActive, Unit) {
        if (!uiMonitoringActive) {
            return@LaunchedEffect
        }

        AppCpuUsage.primeSample()
        while (uiMonitoringActive) {
            delay(HEADER_CPU_REFRESH_MS)
            cpuLabel = AppCpuUsage.sampleLabel()
        }
    }

    LaunchedEffect(uiMonitoringActive, state.selectedCarId, state.carAudioReady) {
        if (!uiMonitoringActive) {
            return@LaunchedEffect
        }

        if (!state.carAudioReady) {
            return@LaunchedEffect
        }

        withFrameNanos { }
        memoryLabels = AppMemoryUsage.readHeaderLabels(context)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(Color.Black.copy(alpha = 0.38f))
            .border(width = 1.dp, color = Outline.copy(alpha = 0.55f))
            .padding(horizontal = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (mainScreen) {
                DashboardMainScreen.MIXER,
                DashboardMainScreen.SETTINGS -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to dashboard",
                        tint = Accent,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                onMainScreenChange(DashboardMainScreen.CLASSIC)
                            },
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(if (state.engineSoundEnabled) StatusHealthy else StatusFault),
                    )
                }
            }
            Text(
                text = "ENGINE",
                color = OnSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.0.sp,
            )
            Text(
                text = "// SIMULATOR",
                color = Accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.0.sp,
            )
            StatusTag("BUILD ${AppBuildInfo.buildNumber}", AccentSoft)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusTag(memoryLabels.usageLabel, Muted)
                Text(
                    text = memoryLabels.availableLabel,
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                )
                Text(
                    text = cpuLabel,
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    lineHeight = 12.sp,
                )
                Text(
                    text = outputLevelLabel,
                    color = if (outputMeterActive) AccentSoft else Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    lineHeight = 12.sp,
                )
            }
            if (state.manualShiftModeEnabled) {
                StatusTag("MANUAL", AccentSoft)
            }
        }

        PedalsInputHeaderControl(
            state = state,
            onSelectSimulated = onSelectSimulatedPedals,
            onSelectReal = onSelectRealPedals,
            onToggle = onToggleInputSource,
        )
        ManualShiftHeaderControl(
            manualEnabled = state.manualShiftModeEnabled,
            onToggle = onToggleManualShiftMode,
        )
        MasterMuteHeaderControl(
            muted = state.audioMuted,
            onToggle = onToggleAudioMute,
        )
        ThemeToggleHeaderControl(onToggle = onToggleDashboardTheme)
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = Accent,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenSettings),
        )
    }
}

/** Cycles the dashboard skin. Lit in the accent color while the Audio Lab theme is active. */
@Composable
private fun ThemeToggleHeaderControl(onToggle: () -> Unit) {
    val skin = LocalDashboardSkin.current
    val audioLabActive = skin.theme == DashboardTheme.AudioLab
    val tint = if (audioLabActive) {
        skin.accent
    } else {
        skin.muted
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(ControlShape)
            .background(if (audioLabActive) skin.accent.copy(alpha = 0.16f) else Surface)
            .border(1.dp, if (audioLabActive) skin.accent.copy(alpha = 0.65f) else Outline, ControlShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "T",
            color = tint,
            fontFamily = DisplayFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.0.sp,
        )
    }
}

@Composable
private fun MasterMuteHeaderControl(
    muted: Boolean,
    onToggle: () -> Boolean,
) {
    Row(
        modifier = Modifier
            .height(52.dp)
            .clip(skinShape(12.dp))
            .background(if (muted) Danger.copy(alpha = 0.18f) else Surface)
            .border(1.dp, if (muted) Danger.copy(alpha = 0.65f) else Outline, skinShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeDown else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (muted) "Unmute and reset audio engine" else "Mute audio",
            tint = if (muted) Danger else Accent,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = if (muted) "UNMUTE" else "MUTE",
            color = if (muted) Danger else OnSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp,
        )
    }
}
private const val HEADER_MEMORY_STARTUP_BURST_MS = 10_000L
private const val HEADER_MEMORY_STARTUP_REFRESH_MS = 250L
private const val HEADER_MEMORY_REFRESH_MS = 15_000L
private const val HEADER_CPU_REFRESH_MS = 1_000L
private const val HEADER_OUTPUT_METER_ATTACK = 0.35f
private const val HEADER_OUTPUT_METER_RELEASE = 0.12f

@Composable
private fun ManualShiftHeaderControl(
    manualEnabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(52.dp)
            .clip(softFillShape(12.dp))
            .background(Surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "SHIFT:",
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
        PedalsInputToggle(
            realSelected = manualEnabled,
            realPedalsActive = manualEnabled,
            enabled = true,
            onToggle = onToggle,
        )
        Text(
            text = "MANUAL",
            color = if (manualEnabled) {
                AccentSoft
            } else {
                Muted
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            modifier = Modifier.clickable {
                if (!manualEnabled) {
                    onToggle()
                }
            },
        )
    }
}

@Composable
private fun PedalsInputHeaderControl(
    state: DriveSnapshot,
    onSelectSimulated: () -> Unit,
    onSelectReal: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(52.dp)
            .clip(softFillShape(12.dp))
            .background(Surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "${InputMode.SimulatedPedals.secondaryLabel}:",
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
        Text(
            text = InputMode.SimulatedPedals.primaryLabel,
            color = Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            modifier = Modifier.clickable(onClick = onSelectSimulated),
        )
        PedalsInputToggle(
            realSelected = state.inputSourceIsRealPedals,
            realPedalsActive = state.inputSourceIsRealPedals,
            enabled = !state.inputSourceFaded,
            onToggle = onToggle,
        )
        Text(
            text = InputMode.RealPedals.primaryLabel,
            color = RealPedalsAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .alpha(if (state.inputSourceFaded) {
                    0.42f
                } else {
                    1f
                })
                .clickable(
                    enabled = !state.inputSourceFaded,
                    onClick = onSelectReal,
                ),
        )
    }
}

@Composable
private fun PedalsInputToggle(
    realSelected: Boolean,
    realPedalsActive: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val thumbProgress by animateFloatAsState(
        targetValue = if (realSelected) {
            1f
        } else {
            0f
        },
        animationSpec = tween(durationMillis = 180),
        label = "pedalsInputToggle",
    )
    val trackWidth = 46.dp
    val trackHeight = 24.dp
    val thumbSize = 18.dp
    val trackInset = 3.dp
    val trackColor = if (realPedalsActive) {
        RealPedalsAccent
    } else {
        Outline
    }

    BoxWithConstraints(
        modifier = Modifier
            .width(trackWidth)
            .height(trackHeight)
            .alpha(if (enabled) {
                1f
            } else {
                0.42f
            })
            .clip(StadiumShape)
            .background(trackColor)
            .clickable(
                enabled = enabled,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = maxWidth - thumbSize - trackInset * 2
        Box(
            modifier = Modifier
                .padding(start = trackInset)
                .offset(x = travel * thumbProgress)
                .size(thumbSize)
                .clip(CircleShape)
                .background(OnSurface),
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    accent: Color = Accent,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = softFillShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = accent),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(52.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Keeps simulated pedal percentages after a pointer release. This is intentionally a runtime
 * control: REAL PEDALS remain telemetry-authoritative, and disabling it immediately clears both
 * virtual pedals so an old test value cannot silently remain applied.
 */
@Composable
private fun SimulatedPedalLatchToggle(
    enabled: Boolean,
    onToggle: () -> Unit,
    scale: Float,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(scale.scaledDp(5)),
        modifier = Modifier.padding(bottom = scale.scaledDp(6)),
    ) {
        Text(
            text = "HOLD PEDALS",
            color = if (enabled) Accent else Muted,
            fontSize = (11f * scale).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (0.8f * scale).sp,
        )
        Box(
            modifier = Modifier
                .width(scale.scaledDp(62))
                .height(scale.scaledDp(28))
                .clip(StadiumShape)
                .background(if (enabled) Accent else Outline)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = scale.scaledDp(4))
                    .offset(x = if (enabled) scale.scaledDp(30) else 0.dp)
                    .size(scale.scaledDp(20))
                    .clip(CircleShape)
                    .background(OnSurface),
            )
        }
    }
}

@Composable
private fun SimulatedRegenControl(
    value: Double,
    onValue: (Double) -> Unit,
    scale: Float,
) {
    Column(
        modifier = Modifier.width(scale.scaledDp(118)).padding(bottom = scale.scaledDp(4)),
        verticalArrangement = Arrangement.spacedBy(scale.scaledDp(2)),
    ) {
        Text(
            text = "REGEN ${"%.0f".format(Locale.US, value * 100.0)}%",
            color = AccentSoft,
            fontSize = (10f * scale).sp,
            fontWeight = FontWeight.Black,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toDouble()) },
            valueRange = 0f..1f,
            modifier = Modifier.height(scale.scaledDp(30)),
        )
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier
            .clip(skinPillShape())
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.42f), skinPillShape())
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private data class DashboardEffectGainPreset(val label: String, val gain: Float)

private val DASHBOARD_EFFECT_GAIN_PRESETS = listOf(
    DashboardEffectGainPreset("LOWER", 0.5f),
    DashboardEffectGainPreset("NORMAL", 1.0f),
    DashboardEffectGainPreset("LOUD", 2.0f),
    DashboardEffectGainPreset("LOUDER", 3.0f),
)

private object DashboardEngineControlsLayout {
    val columnGap = 8.dp
    val columnPadding = 3.dp
    val rowHeight = 42.dp
    val cruisingOffsetSliderWidth = 168.dp
}

private object DashboardClassicEffectLayout {
    val effectLabelColumnWidth = 94.dp
    val presetColumnWidth = 76.dp
    val columnGap = 8.dp
    val columnPadding = 3.dp
    val switchColumnWidth = 64.dp + columnPadding * 2
    val effectControlsTrailingWidth =
        columnGap + switchColumnWidth + columnGap + (presetColumnWidth * 4) + (columnGap * 3)
}

private object DashboardManualVolumeLayout {
    val rowHeight = 42.dp
    val buttonHeight = 38.dp
    val columnGap = DashboardClassicEffectLayout.columnGap
    val valueWidth = 90.dp
    val buttonWidth = 70.dp
    val valueFontSize = 15.sp
    val buttonFontSize = 15.sp
}

@Composable
private fun DashboardClassicAudioControlsStack(
    state: DriveSnapshot,
    onManualLoudnessDbChange: (String, com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective, Double) -> Unit,
    onEffectOverrideChange: (EffectSoundKind, Boolean) -> Unit,
    onOverrideGainChange: (EffectSoundKind, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DashboardManualVolumeControl(
            state = state,
            onManualLoudnessDbChange = onManualLoudnessDbChange,
        )
        DashboardEffectControls(
            state = state,
            onOverrideChange = onEffectOverrideChange,
            onOverrideGainChange = onOverrideGainChange,
        )
    }
}

@Composable
private fun DashboardTachometerAccessoryControls(
    state: DriveSnapshot,
    gearProfileSelection: GearProfileSelection,
    onCruisingShiftOffsetForTachMaxRpmChange: (Int, Int) -> Unit,
    onCruisingLogicChange: (Boolean) -> Unit,
    onGearProfileSelectionChange: (GearProfileSelection) -> Unit,
    onEngineExternalChange: (Boolean) -> Unit,
    onEnginePureChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The stock rows are wider than the tachometer column, so they are laid out unbounded and
    // anchored to its right edge instead of being reflowed into a narrower variant.
    Column(
        modifier = modifier
            .wrapContentWidth(align = Alignment.End, unbounded = true)
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DashboardEngineControls(
            state = state,
            onCruisingShiftOffsetForTachMaxRpmChange = onCruisingShiftOffsetForTachMaxRpmChange,
            onEngineExternalChange = onEngineExternalChange,
            onEnginePureChange = onEnginePureChange,
            onCruisingLogicChange = onCruisingLogicChange,
        )
        DashboardGearProfileControls(
            selection = gearProfileSelection,
            onSelectionChange = onGearProfileSelectionChange,
        )
    }
}

@Composable
private fun DashboardEngineControls(
    state: DriveSnapshot,
    onCruisingShiftOffsetForTachMaxRpmChange: (Int, Int) -> Unit,
    onEngineExternalChange: (Boolean) -> Unit,
    onEnginePureChange: (Boolean) -> Unit,
    onCruisingLogicChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val external = state.soundPerspective == com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective.EXTERIOR
    val layout = DashboardEngineControlsLayout
    val rowHeight = layout.rowHeight

    Row(
        modifier = modifier.wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(layout.columnGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .height(rowHeight)
                .wrapContentWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text("ENGINE", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .wrapContentWidth()
                .padding(layout.columnPadding),
        ) {
            DashboardColumnTextCell("EXTERNAL", external, rowHeight)
        }
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .wrapContentWidth()
                .padding(layout.columnPadding),
        ) {
            DashboardSwitchCell(rowHeight, external, Outline) {
                onEngineExternalChange(!external)
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .wrapContentWidth()
                .padding(layout.columnPadding),
        ) {
            DashboardColumnTextCell("PURE", state.exteriorPureAudio, rowHeight)
        }
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .wrapContentWidth()
                .padding(layout.columnPadding),
        ) {
            DashboardSwitchCell(rowHeight, state.exteriorPureAudio, Outline) {
                onEnginePureChange(!state.exteriorPureAudio)
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .wrapContentWidth()
                .padding(layout.columnPadding),
        ) {
            DashboardColumnTextCell("CRUISING", state.cruisingLogicEnabled, rowHeight)
        }
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .wrapContentWidth()
                .padding(layout.columnPadding),
        ) {
            DashboardSwitchCell(rowHeight, state.cruisingLogicEnabled, Outline) {
                onCruisingLogicChange(!state.cruisingLogicEnabled)
            }
        }
        if (state.cruisingLogicEnabled) {
            DashboardCruisingRpmOffsetSlider(
                offsetRpm = state.cruisingShiftOffsetRpm,
                onOffsetChange = { selectedOffset ->
                    if (selectedOffset != state.cruisingShiftOffsetRpm) {
                        onCruisingShiftOffsetForTachMaxRpmChange(
                            state.cruisingShiftOffsetTachMaxRpm,
                            selectedOffset,
                        )
                    }
                },
                modifier = Modifier.width(layout.cruisingOffsetSliderWidth),
            )
        }
    }
}

@Composable
private fun DashboardManualVolumeControl(
    state: DriveSnapshot,
    onManualLoudnessDbChange: (String, com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = DashboardClassicEffectLayout
    val volumeLayout = DashboardManualVolumeLayout
    val rowHeight = volumeLayout.rowHeight
    val adjustmentDb = state.manualLoudnessAdjustmentDb
    val perspective = state.soundPerspective
    val minDb = com.gabrielpc.enginesoundsimulator.audio.ManualLoudnessTableEntry.MIN_DB
    val maxDb = com.gabrielpc.enginesoundsimulator.audio.ManualLoudnessTableEntry.MAX_DB

    fun canAdjustBy(deltaSteps: Int): Boolean {
        val nextDb = manualVolumeDbAfterStep(adjustmentDb, deltaSteps)
        return nextDb >= minDb && nextDb <= maxDb
    }

    fun adjustBy(deltaSteps: Int) {
        val nextDb = manualVolumeDbAfterStep(adjustmentDb, deltaSteps).coerceIn(minDb, maxDb)
        onManualLoudnessDbChange(state.selectedCarId, perspective, nextDb)
    }

    Row(
        modifier = modifier.wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(volumeLayout.columnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(layout.effectLabelColumnWidth)
                .height(rowHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "VOLUME",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        DashboardVolumeStepButton(
            label = "-5",
            enabled = canAdjustBy(-5),
            onClick = { adjustBy(-5) },
            layout = volumeLayout,
        )
        DashboardVolumeStepButton(
            label = "-1",
            enabled = canAdjustBy(-1),
            onClick = { adjustBy(-1) },
            layout = volumeLayout,
        )
        Box(
            modifier = Modifier
                .width(volumeLayout.valueWidth)
                .height(rowHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatDashboardManualVolumeDb(adjustmentDb),
                color = OnSurface,
                fontSize = volumeLayout.valueFontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        DashboardVolumeStepButton(
            label = "+1",
            enabled = canAdjustBy(1),
            onClick = { adjustBy(1) },
            layout = volumeLayout,
        )
        DashboardVolumeStepButton(
            label = "+5",
            enabled = canAdjustBy(5),
            onClick = { adjustBy(5) },
            layout = volumeLayout,
        )
    }
}

private fun isManualVolumeHalfStepDb(db: Double): Boolean {
    val fractional = kotlin.math.abs(db - kotlin.math.truncate(db))
    return kotlin.math.abs(fractional - 0.5) < 0.01
}

private fun manualVolumeDbAfterStep(currentDb: Double, deltaSteps: Int): Double {
    val halfStep = isManualVolumeHalfStepDb(currentDb)
    val anchor = if (halfStep) {
        if (deltaSteps > 0) {
            kotlin.math.ceil(currentDb)
        } else {
            kotlin.math.floor(currentDb)
        }
    } else {
        currentDb
    }
    val consumedSteps = if (halfStep) {
        1
    } else {
        0
    }
    val remainingSteps = kotlin.math.abs(deltaSteps) - consumedSteps
    val direction = if (deltaSteps > 0) {
        1
    } else {
        -1
    }

    return anchor + direction * remainingSteps
}

private fun formatDashboardManualVolumeDb(db: Double): String {
    val roundedTenth = kotlin.math.round(db * 10.0) / 10.0
    val isWhole = kotlin.math.abs(roundedTenth - kotlin.math.round(roundedTenth)) < 0.001

    return if (isWhole) {
        String.format(Locale.US, "%+.0f dB", roundedTenth)
    } else {
        String.format(Locale.US, "%+.1f dB", roundedTenth)
    }
}

@Composable
private fun DashboardVolumeStepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    layout: DashboardManualVolumeLayout,
    modifier: Modifier = Modifier,
) {
    val textColor = if (enabled) {
        Accent
    } else {
        Muted
    }
    val borderColor = if (enabled) {
        Outline
    } else {
        Outline.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .width(layout.buttonWidth)
            .height(layout.rowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.buttonHeight)
                .clip(skinShape(6.dp))
                .background(SurfaceRaised)
                .border(1.dp, borderColor, skinShape(6.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = layout.buttonFontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DashboardCruisingRpmOffsetSlider(
    offsetRpm: Int,
    onOffsetChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stopCount = CruisingShiftOffsetByTachMaxRpm.stopCount
    val lastStopIndex = CruisingShiftOffsetByTachMaxRpm.lastStopIndex
    val normalizedOffset = CruisingShiftOffsetByTachMaxRpm.normalize(offsetRpm)
    val stopIndex = ((CruisingShiftOffsetByTachMaxRpm.MAX - normalizedOffset) / CruisingShiftOffsetByTachMaxRpm.STEP)
        .toFloat()
        .coerceIn(0f, lastStopIndex)
    val sliderColors = SliderDefaults.colors(
        thumbColor = Accent,
        activeTrackColor = Accent,
        inactiveTrackColor = Outline,
        activeTickColor = Background,
        inactiveTickColor = Accent,
        disabledThumbColor = Accent,
        disabledActiveTrackColor = Accent,
        disabledInactiveTrackColor = Outline,
        disabledActiveTickColor = Background,
        disabledInactiveTickColor = Accent,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CRUISING RPM OFFSET",
                color = Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = CruisingShiftOffsetByTachMaxRpm.formatOffsetLabel(normalizedOffset),
                color = OnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = stopIndex,
            onValueChange = { rawIndex ->
                val index = rawIndex.roundToInt().coerceIn(0, stopCount - 1)
                val selectedOffset = CruisingShiftOffsetByTachMaxRpm.MAX - (index * CruisingShiftOffsetByTachMaxRpm.STEP)
                val normalized = CruisingShiftOffsetByTachMaxRpm.normalize(selectedOffset)
                if (normalized != normalizedOffset) {
                    onOffsetChange(normalized)
                }
            },
            valueRange = 0f..lastStopIndex,
            steps = CruisingShiftOffsetByTachMaxRpm.sliderSteps.coerceAtLeast(0),
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DashboardGearProfileControls(
    selection: GearProfileSelection,
    onSelectionChange: (GearProfileSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = DashboardClassicEffectLayout
    val rowHeight = 42.dp
    val presets = listOf(
        DashboardGearPreset(
            label = "ORIGINAL",
            selection = GearProfileSelection.Original,
            active = selection.isOriginal(),
        ),
        DashboardGearPreset(
            label = "6",
            selection = GearProfileSelection.virtual(6),
            active = selection.matchesMixerPreset(6),
        ),
        DashboardGearPreset(
            label = "10",
            selection = GearProfileSelection.virtual(10),
            active = selection.matchesMixerPreset(10),
        ),
        DashboardGearPreset(
            label = "6/10",
            selection = GearProfileSelection.AdaptiveCruising6Racing10,
            active = selection.isAdaptive(),
        ),
        DashboardGearPreset(
            label = "15",
            selection = GearProfileSelection.virtual(15),
            active = selection.matchesMixerPreset(15),
        ),
    )

    Row(
        modifier = modifier.wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(layout.columnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(layout.effectLabelColumnWidth)
                .height(rowHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "GEARS",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(layout.columnPadding),
        ) {
            presets.forEach { preset ->
                DashboardGearPresetButton(
                    label = preset.label,
                    selected = preset.active,
                    onClick = {
                        onSelectionChange(preset.selection)
                    },
                )
            }
        }
    }
}

private data class DashboardGearPreset(
    val label: String,
    val selection: GearProfileSelection,
    val active: Boolean,
)

@Composable
private fun DashboardGearPresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        color = if (selected) {
            Background
        } else {
            Accent
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .width(72.dp)
            .height(38.dp)
            .clip(skinShape(6.dp))
            .background(if (selected) Accent else SurfaceRaised)
            .border(1.dp, if (selected) Accent else Outline, skinShape(6.dp))
            .clickable(onClick = onClick)
            .padding(top = 10.dp),
    )
}

@Composable
private fun DashboardEffectControls(
    state: DriveSnapshot,
    onOverrideChange: (EffectSoundKind, Boolean) -> Unit,
    onOverrideGainChange: (EffectSoundKind, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = DashboardClassicEffectLayout
    val rows = listOf(
        "POPS & BANGS" to EffectSoundKind.POPS_AND_BANGS,
        "SHIFT SOUNDS" to EffectSoundKind.SHIFT,
    )
    val rowHeight = 42.dp
    val rowGap = 7.dp

    fun overrideEnabled(kind: EffectSoundKind): Boolean {
        return when (kind) {
            EffectSoundKind.POPS_AND_BANGS -> state.popsAndBangsOverride
            EffectSoundKind.SHIFT -> state.shiftSoundsOverride
            EffectSoundKind.TRANSMISSION, EffectSoundKind.TURBO -> false
        }
    }

    fun overrideGain(kind: EffectSoundKind): Float {
        return when (kind) {
            EffectSoundKind.POPS_AND_BANGS -> state.backfireOverrideGain
            EffectSoundKind.SHIFT -> state.shiftOverrideGain
            EffectSoundKind.TRANSMISSION, EffectSoundKind.TURBO -> 1.0f
        }
    }

    Row(
        modifier = modifier.wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(layout.columnGap),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(rowGap),
            modifier = Modifier.width(layout.effectLabelColumnWidth),
        ) {
            rows.forEach { (label, _) ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = label,
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(rowGap),
            modifier = Modifier
                .wrapContentWidth()
                .padding(layout.columnPadding),
        ) {
            rows.forEach { (_, kind) ->
                val override = overrideEnabled(kind)
                DashboardSwitchCell(rowHeight, override, Outline) {
                    onOverrideChange(kind, !override)
                }
            }
        }
        DASHBOARD_EFFECT_GAIN_PRESETS.forEach { preset ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rowGap),
                modifier = Modifier
                    .width(layout.presetColumnWidth)
                    .padding(layout.columnPadding),
            ) {
                rows.forEach { (_, kind) ->
                    if (overrideEnabled(kind)) {
                        val rowGain = overrideGain(kind)
                        val selected = kotlin.math.abs(rowGain - preset.gain) < 0.001f
                        DashboardGainButton(preset, selected, if (selected) Background else Accent, Outline) {
                            onOverrideGainChange(kind, preset.gain)
                        }
                    } else {
                        DashboardEmptyControlCell(rowHeight)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardEmptyControlCell(height: Dp) {
    Spacer(Modifier.height(height))
}

@Composable
private fun DashboardColumnTextCell(label: String, active: Boolean, height: Dp) {
    Box(Modifier.height(height), contentAlignment = Alignment.CenterEnd) {
        Text(label, color = if (active) Accent else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DashboardSwitchCell(height: Dp, checked: Boolean, borderColor: Color, onToggle: () -> Unit) {
    Box(Modifier.width(64.dp).height(height), contentAlignment = Alignment.Center) {
        DashboardEffectSwitch(checked, onToggle, borderColor)
    }
}

@Composable
private fun DashboardGainButton(
    preset: DashboardEffectGainPreset,
    selected: Boolean,
    textColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Box(Modifier.width(DashboardClassicEffectLayout.presetColumnWidth).height(42.dp), contentAlignment = Alignment.Center) {
        Text(
            text = preset.label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(skinShape(6.dp))
                .background(if (selected) Accent else SurfaceRaised)
                .border(1.dp, if (selected) Accent else borderColor, skinShape(6.dp))
                .clickable(onClick = onClick)
                .padding(top = 10.dp),
        )
    }
}

@Composable
private fun DashboardEffectSwitch(enabled: Boolean, onToggle: () -> Unit, borderColor: Color = Outline) {
    Box(
        modifier = Modifier.width(64.dp).height(32.dp).clip(StadiumShape)
            .background(if (enabled) Accent else Outline)
            .clickable(onClick = onToggle),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(4.dp).size(24.dp).clip(CircleShape).background(OnSurface))
    }
}

private const val CLASSIC_DRIVE_CONTROL_SCALE = 0.7f
private const val CAR_ENGINE_AUDIO_LOAD_TIMEOUT_MS = 15_000L

private fun Float.scaledDp(base: Int): Dp = (base * this).dp

@Composable
private fun ClassicDriveControls(
    state: DriveSnapshot,
    onThrottle: (Double) -> Unit,
    onBrake: (Double) -> Unit,
    onSimulatedRegen: (Double) -> Unit,
    onToggleSimulatedPedalLatch: () -> Unit,
    onTransmissionPositionChange: (TransmissionPosition) -> Unit,
    onManualUpshift: () -> Unit,
    onManualDownshift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CLASSIC_DRIVE_CONTROL_SCALE.scaledDp(18)),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (state.manualShiftModeEnabled && !state.inputSourceIsRealPedals) {
            ManualShiftButtons(
                onUpshift = onManualUpshift,
                onDownshift = onManualDownshift,
                scale = CLASSIC_DRIVE_CONTROL_SCALE,
            )
        }
        if (!state.inputSourceIsRealPedals) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CLASSIC_DRIVE_CONTROL_SCALE.scaledDp(6)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SimulatedPedalLatchToggle(
                    enabled = state.simulatedPedalsLatched,
                    onToggle = onToggleSimulatedPedalLatch,
                    scale = CLASSIC_DRIVE_CONTROL_SCALE,
                )
                SimulatedRegenControl(state.simulatedRegen, onSimulatedRegen, CLASSIC_DRIVE_CONTROL_SCALE)
            }
        }
        PedalControl(
            label = "BRAKE",
            value = state.brake,
            accent = Danger,
            width = CLASSIC_DRIVE_CONTROL_SCALE.scaledDp(92),
            height = CLASSIC_DRIVE_CONTROL_SCALE.scaledDp(154),
            contentScale = CLASSIC_DRIVE_CONTROL_SCALE,
            onValue = onBrake,
        )
        PedalControl(
            label = "THROTTLE",
            value = state.throttle,
            accent = Success,
            width = CLASSIC_DRIVE_CONTROL_SCALE.scaledDp(84),
            height = CLASSIC_DRIVE_CONTROL_SCALE.scaledDp(202),
            contentScale = CLASSIC_DRIVE_CONTROL_SCALE,
            onValue = onThrottle,
        )
        TransmissionShifter(
            position = state.transmissionPosition,
            lockedToVehicle = state.transmissionLockedToVehicle,
            scale = CLASSIC_DRIVE_CONTROL_SCALE,
            onPositionSelected = onTransmissionPositionChange,
        )
    }
}
@Composable
private fun ManualShiftButtons(
    onUpshift: () -> Unit,
    onDownshift: () -> Unit,
    scale: Float,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(scale.scaledDp(8)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = scale.scaledDp(6)),
    ) {
        ManualShiftButton(
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Upshift",
            accent = Success,
            size = scale.scaledDp(56),
            contentScale = scale,
            onClick = onUpshift,
        )
        ManualShiftButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Downshift",
            accent = Danger,
            size = scale.scaledDp(56),
            contentScale = scale,
            onClick = onDownshift,
        )
    }
}

@Composable
private fun ManualShiftButton(
    icon: ImageVector,
    contentDescription: String,
    accent: Color,
    size: Dp,
    contentScale: Float,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = pressed

    Box(
        modifier = Modifier
            .size(size)
            .clip(hardwareShape((16f * contentScale).dp))
            .background(
                Brush.verticalGradient(HardwareGradient),
            )
            .border(
                (2f * contentScale).dp,
                if (active) {
                    accent
                } else {
                    HardwareBorder
                },
                hardwareShape((16f * contentScale).dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0.45f)),
                        ),
                    ),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) {
                accent
            } else {
                Muted
            },
            modifier = Modifier.size((28f * contentScale).dp),
        )
    }
}

@Composable
internal fun MixerDriveControls(
    state: DriveSnapshot,
    onThrottle: (Double) -> Unit,
    onBrake: (Double) -> Unit,
    onSimulatedRegen: (Double) -> Unit,
    onToggleSimulatedPedalLatch: () -> Unit,
    onTransmissionPositionChange: (TransmissionPosition) -> Unit,
    onManualUpshift: () -> Unit,
    onManualDownshift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClassicDriveControls(
        state = state,
        onThrottle = onThrottle,
        onBrake = onBrake,
        onSimulatedRegen = onSimulatedRegen,
        onToggleSimulatedPedalLatch = onToggleSimulatedPedalLatch,
        onTransmissionPositionChange = onTransmissionPositionChange,
        onManualUpshift = onManualUpshift,
        onManualDownshift = onManualDownshift,
        modifier = modifier,
    )
}

@Composable
private fun CarPreviewLoadingOverlay(
    visible: Boolean,
    onOpenCarPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) {
        return
    }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = onOpenCarPicker),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = "LOADING ENGINE",
                color = AccentSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
            )
        }
    }
}

private object CarStageTypography {
    const val nameFontSizeSp = 48f
    const val subtitleFontSizeSp = 18f
}

@Composable
private fun CarStage(
    state: DriveSnapshot,
    onPreviousCar: () -> Unit,
    onNextCar: () -> Unit,
    onShuffleCar: () -> Unit,
    onSelectCar: (String) -> Unit,
    onToggleCarFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var carPickerExpanded by remember { mutableStateOf(false) }
    val nameFontSizeSp = CarStageTypography.nameFontSizeSp
    val subtitleFontSizeSp = CarStageTypography.subtitleFontSizeSp
    val nameLineHeightSp = nameFontSizeSp * 1.235f
    val selectedProfile = remember(state.selectedCarId) { FmodBankProfiles.find(state.selectedCarId) }
    val selectedCarSubtitle = remember(selectedProfile.id) {
        CarSubtitleCatalog.forProfileId(selectedProfile.id)
    }
    val context = LocalContext.current
    var loadingTimedOut by remember(state.selectedCarId) { mutableStateOf(false) }
    LaunchedEffect(state.selectedCarId, state.carAudioReady) {
        if (state.carAudioReady) {
            loadingTimedOut = false
            return@LaunchedEffect
        }

        loadingTimedOut = false
        delay(CAR_ENGINE_AUDIO_LOAD_TIMEOUT_MS)
        loadingTimedOut = true
    }
    val showCarAudioLoading = !state.carAudioReady && !loadingTimedOut
    val audioResolver = remember(context) { FmodBankResolver(context.applicationContext) }
    val installedPreviewPath = audioResolver.previewFile(selectedProfile)?.path
    val preview = remember(state.selectedCarId, installedPreviewPath) {
        runCatching {
            audioResolver.openCarPreviewInput(selectedProfile)?.use { input ->
                requireNotNull(BitmapFactory.decodeStream(input)).asImageBitmap()
            }
        }.getOrNull()
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = CarDisplayNameFormatter.format(state.selectedCarName),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.apex_v10_car),
                    contentDescription = CarDisplayNameFormatter.format(state.selectedCarName),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CarStageTapDefaults.sideStripWidth)
                    .clickable { carPickerExpanded = true },
            )

            CarPreviewLoadingOverlay(
                visible = showCarAudioLoading,
                onOpenCarPicker = { carPickerExpanded = true },
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = DashboardLayoutDefaults.classicContentStartPadding,
                        top = 26.dp,
                    ),
            ) {
                Text(
                    text = CarDisplayNameFormatter.format(state.selectedCarName).uppercase(),
                    color = OnSurface,
                    fontSize = nameFontSizeSp.sp,
                    lineHeight = nameLineHeightSp.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    selectedCarSubtitle.horsepower?.let { horsepower ->
                        Text(
                            text = "$horsepower HP",
                            color = CarSubtitleCatalog.horsepowerColor(horsepower),
                            fontSize = subtitleFontSizeSp.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.1.sp,
                        )

                        if (selectedCarSubtitle.details.isNotBlank()) {
                            Text(
                                text = " · ",
                                color = AccentSoft,
                                fontSize = subtitleFontSizeSp.sp,
                            )
                        }
                    }

                    if (selectedCarSubtitle.details.isNotBlank()) {
                        Text(
                            text = selectedCarSubtitle.details,
                            color = AccentSoft,
                            fontSize = subtitleFontSizeSp.sp,
                            letterSpacing = 1.1.sp,
                        )
                    }
                }
            }

            CarSelectorSideTapZone(
                label = "‹",
                contentDescription = "Previous car",
                onClick = onPreviousCar,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CarSelectorSideIconZone(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Shuffle car",
                    onClick = onShuffleCar,
                )
                CarSelectorSideTapZone(
                    label = "›",
                    contentDescription = "Next car",
                    onClick = onNextCar,
                )
            }
            CarFavoriteStarButton(
                isFavorite = state.selectedCarId in state.favoriteCarIds,
                onToggle = { onToggleCarFavorite(state.selectedCarId) },
                scale = 2f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }

    if (carPickerExpanded) {
        CarGridSelectionDialog(
            selectedCarId = state.selectedCarId,
            favoriteCarIds = state.favoriteCarIds,
            onSelectCar = onSelectCar,
            onToggleFavorite = onToggleCarFavorite,
            onDismiss = { carPickerExpanded = false },
        )
    }
}

@Composable
internal fun TransmissionShifter(
    position: TransmissionPosition,
    lockedToVehicle: Boolean = false,
    onPositionSelected: ((TransmissionPosition) -> Unit)? = null,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width((58f * scale).dp)
            .height((202f * scale).dp)
            .clip(hardwareShape((16f * scale).dp))
            .background(
                Brush.verticalGradient(HardwareGradient),
            )
            .border((2f * scale).dp, HardwareBorder, hardwareShape((16f * scale).dp))
            .padding((8f * scale).dp),
        verticalArrangement = Arrangement.spacedBy((6f * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransmissionPosition.entries.forEach { option ->
            val selected = option == position
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((52f * scale).dp)
                    .clip(hardwareShape((10f * scale).dp))
                    .background(
                        if (selected) {
                            Accent.copy(alpha = 0.22f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .border(
                        width = if (selected) (2f * scale).dp else (1f * scale).dp,
                        color = if (selected) Accent else HardwareSlotBorder,
                        shape = hardwareShape((10f * scale).dp),
                    )
                    .clickable(
                        enabled = onPositionSelected != null,
                        onClick = { onPositionSelected?.invoke(option) },
                    ),

                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.displayName,
                    color = if (selected) Accent else Muted,
                    fontSize = (22f * scale).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.0.sp,
                )
            }
        }
    }
}

@Composable
private fun CarSelectorSideIconZone(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(CarStageTapDefaults.sideStripWidth)
            .height(CarStageTapDefaults.navigationButtonHeight)
            .clip(CircleShape)
            .background(HardwareBackdrop.copy(alpha = 0.92f))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = OnSurface,
            modifier = Modifier.size(CarStageTapDefaults.navigationIconSize),
        )
    }
}

@Composable
private fun CarSelectorSideTapZone(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(CarStageTapDefaults.sideStripWidth)
            .height(CarStageTapDefaults.navigationButtonHeight)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CarSelectorArrowGlyph(label = label)
    }
}

@Composable
private fun CarSelectorArrowGlyph(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(CarStageTapDefaults.sideStripWidth)
            .height(CarStageTapDefaults.navigationButtonHeight)
            .clip(CircleShape)
            .background(HardwareBackdrop.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = OnSurface,
            fontSize = CarStageTapDefaults.navigationArrowFontSize,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun PedalControl(
    label: String,
    value: Double,
    accent: Color,
    width: Dp,
    height: Dp,
    onValue: (Double) -> Unit,
    contentScale: Float = 1f,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(hardwareShape((16f * contentScale).dp))
            .background(
                Brush.verticalGradient(HardwareGradient),
            )
            .border((2f * contentScale).dp, if (value > 0.01) accent else HardwareBorder, hardwareShape((16f * contentScale).dp))
            .pointerInput(onValue) {
                awaitEachGesture {
                    try {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun updateAt(y: Float) {
                            onValue((1.0 - y / size.height.toDouble()).coerceIn(0.0, 1.0))
                        }
                        updateAt(down.position.y)
                        var pointer = down
                        do {
                            val event = awaitPointerEvent()
                            pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            updateAt(pointer.position.y)
                            pointer.consume()
                        } while (pointer.pressed)
                    } finally {
                        onValue(0.0)
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(value.toFloat().coerceIn(0f, 1f))
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0.45f)))),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (12f * contentScale).dp, vertical = (14f * contentScale).dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((6f * contentScale).dp)
                        .clip(StadiumShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                )
            }
            Text(
                text = "${(value * 100).roundToInt()}%",
                color = if (value > 0.01) accent else OnSurface,
                fontSize = (15f * contentScale).sp,
                fontWeight = FontWeight.Black,
            )
            Text(label, color = Muted, fontSize = (9f * contentScale).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        }
    }
}

@Composable
private fun DismissableUserMessageBanner(
    message: UserVisibleMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (bodyColor, borderColor) = when (message.severity) {
        UserVisibleMessageSeverity.INFO -> InfoBannerBody to Success
        UserVisibleMessageSeverity.ERROR -> ErrorBannerBody to Danger
    }
    Row(
        modifier = modifier
            .clip(skinShape(14.dp))
            .background(bodyColor.copy(alpha = 0.94f))
            .border(1.dp, borderColor.copy(alpha = 0.55f), skinShape(14.dp))
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message.title,
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message.detail,
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss message",
                tint = OnSurface,
            )
        }
    }
}

@Composable
private fun Tachometer(
    drivetrain: DrivetrainState,
    transmissionPosition: TransmissionPosition,
    manualShiftModeEnabled: Boolean,
    cruisingLogicEnabled: Boolean,
    cruisingShiftRangeOverlayEnabled: Boolean,
    maxRpm: Double,
    redlineRpm: Double,
    modifier: Modifier = Modifier,
) {
    when (LocalDashboardSkin.current.theme) {
        DashboardTheme.Classic -> TachometerGauge(
            drivetrain = drivetrain,
            transmissionPosition = transmissionPosition,
            manualShiftModeEnabled = manualShiftModeEnabled,
            cruisingLogicEnabled = cruisingLogicEnabled,
            cruisingShiftRangeOverlayEnabled = cruisingShiftRangeOverlayEnabled,
            maxRpm = maxRpm,
            redlineRpm = redlineRpm,
            modifier = modifier,
        )
        DashboardTheme.AudioLab -> AudioLabTachometer(
            drivetrain = drivetrain,
            transmissionPosition = transmissionPosition,
            manualShiftModeEnabled = manualShiftModeEnabled,
            cruisingLogicEnabled = cruisingLogicEnabled,
            cruisingShiftRangeOverlayEnabled = cruisingShiftRangeOverlayEnabled,
            maxRpm = maxRpm,
            redlineRpm = redlineRpm,
            modifier = modifier,
        )
    }
}

@Composable
private fun AutomaticTransmissionModeLabel(
    mode: AutomaticTransmissionMode,
    preparingCruising: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
) {
    Text(
        text = AutomaticTransmissionModeCaption.label(mode, preparingCruising),
        color = AutomaticTransmissionModeCaption.color(mode, preparingCruising, LocalDashboardSkin.current),
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
    )
}

@Composable
private fun TachometerGauge(
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

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val gaugeSize = if (maxWidth < maxHeight) maxWidth else maxHeight
        val gaugeMaxRpm = ceil(maxRpm.coerceAtLeast(1_000.0) / 1_000.0) * 1_000.0
        val majorIntervals = (gaugeMaxRpm / 1_000.0).roundToInt().coerceAtLeast(1)
        // Canvas draw lambdas are not composable, so every skin color is resolved before the block.
        val skin = LocalDashboardSkin.current
        val accent = skin.accent
        val accentSoft = skin.accentSoft
        val danger = skin.danger
        val success = skin.success
        val warning = skin.warning
        val faceGradient = listOf(skin.gaugeFaceOuter, skin.gaugeFaceInner, Color.Black)
        val gaugeTrack = skin.gaugeTrack
        val gaugeHub = skin.gaugeHub
        val labelColor = skin.onSurface.toArgb()
        val labelRedlineColor = skin.danger.toArgb()
        Box(modifier = Modifier.size(gaugeSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.455f
                val stroke = radius * 0.012f
                val startAngle = 135f
                val sweepAngle = 270f
                val rpmFraction = (drivetrain.rpm / gaugeMaxRpm).toFloat().coerceIn(0f, 1f)

                drawCircle(
                    brush = Brush.radialGradient(
                        faceGradient,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
                drawCircle(accent.copy(alpha = 0.16f), radius = radius * 1.015f, center = center, style = Stroke(radius * 0.035f))
                drawArc(
                    color = gaugeTrack,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(stroke * 1.35f, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(accent, accent, success, warning, danger, danger), center),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * rpmFraction,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(stroke * 1.8f, cap = StrokeCap.Butt),
                )

                val zoneBandStroke = radius * 0.024f
                val zoneBandRadius = radius * 0.96f
                val zoneBandTopLeft = androidx.compose.ui.geometry.Offset(
                    center.x - zoneBandRadius,
                    center.y - zoneBandRadius,
                )
                val zoneBandSize = androidx.compose.ui.geometry.Size(zoneBandRadius * 2f, zoneBandRadius * 2f)
                val zoneBandStyle = Stroke(zoneBandStroke, cap = StrokeCap.Butt)

                drawArc(
                    color = danger,
                    startAngle = startAngle + sweepAngle * (redlineRpm / gaugeMaxRpm).toFloat().coerceIn(0f, 1f),
                    sweepAngle = sweepAngle * ((gaugeMaxRpm - redlineRpm) / gaugeMaxRpm).toFloat().coerceAtLeast(0f),
                    useCenter = false,
                    topLeft = zoneBandTopLeft,
                    size = zoneBandSize,
                    style = zoneBandStyle,
                )

                if (
                    showAutomaticTransmissionMode &&
                    cruisingShiftRangeOverlayEnabled &&
                    cruisingLogicEnabled &&
                    drivetrain.automaticTransmissionMode == AutomaticTransmissionMode.CRUISING &&
                    drivetrain.gear > 1
                ) {
                    drawCruisingShiftRangeOverlay(
                        center = center,
                        radius = radius,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        gaugeMaxRpm = gaugeMaxRpm,
                        downshiftRpm = drivetrain.effectiveAutomaticDownshiftRpm,
                        upshiftRpm = drivetrain.effectiveAutomaticUpshiftRpm,
                        wedgeColor = accent,
                    )
                }

                val tickCount = majorIntervals * 5
                for (tick in 0..tickCount) {
                    val fraction = tick / tickCount.toFloat()
                    val angle = startAngle + sweepAngle * fraction
                    val major = tick % 5 == 0
                    val outer = polar(center, radius * 0.91f, angle)
                    val inner = polar(center, radius * if (major) 0.80f else 0.85f, angle)
                    val inRed = fraction * gaugeMaxRpm >= redlineRpm
                    drawLine(
                        color = if (inRed) danger else if (major) accent else accentSoft.copy(alpha = 0.60f),
                        start = inner,
                        end = outer,
                        strokeWidth = if (major) radius * 0.012f else radius * 0.005f,
                        cap = StrokeCap.Round,
                    )
                }

                drawIntoCanvas { canvas ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = labelColor
                        textAlign = Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(CONDENSED_FONT_FAMILY_NAME, android.graphics.Typeface.BOLD_ITALIC)
                        textSize = radius * 0.105f
                    }
                    for (number in 0..majorIntervals) {
                        val point = polar(center, radius * 0.69f, startAngle + sweepAngle * (number / majorIntervals.toFloat()))
                        paint.color = if (number * 1_000.0 >= redlineRpm) labelRedlineColor else labelColor
                        canvas.nativeCanvas.drawText(number.toString(), point.x, point.y + paint.textSize * 0.34f, paint)
                    }
                }

                val nominalNeedleAngle = startAngle + sweepAngle * rpmFraction
                val tipAngle = nominalNeedleAngle + redlineShake.needleTipAngleJitterDegrees
                val needleTip = polar(center, radius * 0.77f, tipAngle)
                val baseAngleRadians = Math.toRadians(nominalNeedleAngle.toDouble())
                val perpendicular = baseAngleRadians + PI / 2.0
                val baseHalfWidth = radius * 0.027f
                val baseA = androidx.compose.ui.geometry.Offset(
                    center.x + (cos(perpendicular) * baseHalfWidth).toFloat(),
                    center.y + (sin(perpendicular) * baseHalfWidth).toFloat(),
                )
                val baseB = androidx.compose.ui.geometry.Offset(
                    center.x - (cos(perpendicular) * baseHalfWidth).toFloat(),
                    center.y - (sin(perpendicular) * baseHalfWidth).toFloat(),
                )
                drawPath(
                    Path().apply {
                        moveTo(baseA.x, baseA.y)
                        lineTo(needleTip.x, needleTip.y)
                        lineTo(baseB.x, baseB.y)
                        close()
                    },
                    brush = Brush.linearGradient(listOf(warning, danger), start = center, end = needleTip),
                )
                drawCircle(gaugeHub, radius * 0.14f, center)
                drawCircle(accent, radius * 0.14f, center, style = Stroke(radius * 0.008f))
                if (drivetrain.isShifting) {
                    drawCircle(success.copy(alpha = 0.55f), radius * 0.985f, center, style = Stroke(radius * 0.018f))
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = (-2).dp),
            ) {
                Text(
                    text = if (transmissionPosition == TransmissionPosition.DRIVE) drivetrain.gear.toString() else transmissionPosition.displayName,
                    color = Accent,
                    fontSize = 48.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = gaugeSize * 0.12f),
            ) {
                Text(
                    text = formatWhole(drivetrain.realOrDocumentedRawSpeedKmh),
                    color = if (drivetrain.limiterActive) Danger else Accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 46.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                )
                Text("KM/H", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                if (showAutomaticTransmissionMode) {
                    Spacer(Modifier.height(4.dp))
                    AutomaticTransmissionModeLabel(
                        mode = drivetrain.automaticTransmissionMode,
                        preparingCruising = drivetrain.racingReturnArmed,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCruisingShiftRangeOverlay(
    center: androidx.compose.ui.geometry.Offset,
    radius: Float,
    startAngle: Float,
    sweepAngle: Float,
    gaugeMaxRpm: Double,
    downshiftRpm: Double,
    upshiftRpm: Double,
    wedgeColor: Color,
) {
    if (downshiftRpm <= 0.0 || upshiftRpm <= 0.0) {
        return
    }

    val downFraction = (downshiftRpm / gaugeMaxRpm).toFloat().coerceIn(0f, 1f)
    val downAngle = startAngle + sweepAngle * downFraction
    val outerRadius = radius * 0.92f
    val upFraction = (upshiftRpm / gaugeMaxRpm).toFloat().coerceIn(0f, 1f)
    val upAngle = startAngle + sweepAngle * upFraction
    val wedgeSweep = upAngle - downAngle

    if (wedgeSweep <= 0f) {
        return
    }

    val downPoint = polar(center, outerRadius, downAngle)
    val wedgePath = Path().apply {
        moveTo(center.x, center.y)
        lineTo(downPoint.x, downPoint.y)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                left = center.x - outerRadius,
                top = center.y - outerRadius,
                right = center.x + outerRadius,
                bottom = center.y + outerRadius,
            ),
            startAngleDegrees = downAngle,
            sweepAngleDegrees = wedgeSweep,
            forceMoveTo = false,
        )
        close()
    }

    drawPath(
        path = wedgePath,
        color = wedgeColor.copy(alpha = 0.22f),
    )
}

private fun polar(
    center: androidx.compose.ui.geometry.Offset,
    radius: Float,
    angleDegrees: Float,
): androidx.compose.ui.geometry.Offset {
    val radians = Math.toRadians(angleDegrees.toDouble())
    return androidx.compose.ui.geometry.Offset(
        center.x + (cos(radians) * radius).toFloat(),
        center.y + (sin(radians) * radius).toFloat(),
    )
}

private fun formatWhole(value: Double): String = value.roundToInt().toString()
