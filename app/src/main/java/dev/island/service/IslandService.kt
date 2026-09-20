package dev.island.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ServiceCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import dev.island.MainActivity
import dev.island.R
import dev.island.core.AppGraph
import dev.island.core.logging.IslandLogger
import dev.island.core.notifications.IslandNotifications
import dev.island.core.platform.CutoutInfo
import dev.island.core.platform.DisplayInfo
import dev.island.domain.model.DisabledReason
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandIconKey
import dev.island.domain.model.IslandSettings
import dev.island.feature.island.layout.IslandBounds
import dev.island.feature.island.ui.IslandGestureCallbacks
import dev.island.feature.island.ui.IslandGestureRouter
import dev.island.feature.island.ui.IslandOverlayRoot
import dev.island.core.ui.theme.IslandAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The island's host: one foreground service that owns the overlay window and the event pipeline.
 *
 * Deliberately thin. It does four things and delegates everything else:
 *
 * 1. Holds the `TYPE_APPLICATION_OVERLAY` window through [IslandWindowManager] — the only place in
 *    the app that touches `WindowManager`. The window is exactly the size of the island, so the app
 *    underneath keeps receiving every touch outside the pill.
 * 2. Hosts the Compose tree ([IslandOverlayRoot]) and feeds it live state. Geometry flows out
 *    (bounds → window), interaction flows in (gestures → engine).
 * 3. Keeps the pipeline alive: [dev.island.domain.engine.IslandEngine] + [EventDispatcher] start on
 *    create and stop on destroy, and monitors follow the user's settings.
 * 4. Owns the foreground notification, and stops itself the moment Island is switched off.
 *
 * Foreground-service policy (spec: never use an FGS blindly): the service runs **only** while the
 * user has Island turned on. A visible overlay drawn from the background is exactly the case Android
 * requires a foreground service for (API 34+: `specialUse` with a subtype declared in the manifest),
 * and there is precisely one such service in the app. Turn Island off and the service stops — no
 * background work, no wake locks, no periodic jobs.
 */
class IslandService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val logger: IslandLogger get() = AppGraph.logger

    private var composeOwners: OverlayComposeOwners? = null
    private var composeView: ComposeView? = null
    private var overlay: IslandWindowManager? = null
    private lateinit var displayState: MutableStateFlow<DisplayInfo>

    private val cutoutState = MutableStateFlow(CutoutInfo())
    private val reduceMotionState = MutableStateFlow(false)

    private var currentSettings: IslandSettings = IslandSettings.Default
    private var pipelineStarted = false
    private var foregroundStarted = false
    private var lastStatusLine: String? = null
    private var settingsJob: Job? = null
    private var statusJob: Job? = null

    // region lifecycle

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(applicationContext)

        displayState = MutableStateFlow(AppGraph.displayInfoProvider.current())
        reduceMotionState.value = systemAnimationsDisabled()

        composeOwners = OverlayComposeOwners(lifecycle).also { it.restore() }

        promoteToForeground()
        attachOverlay()
        startPipeline()

        AppGraph.onServiceStateChanged(true)
        logger.i(TAG, "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TURN_OFF, ACTION_STOP -> {
                turnOff(userInitiated = intent.action == ACTION_TURN_OFF)
                return START_NOT_STICKY
            }

            ACTION_TOGGLE -> toggleIsland()
            else -> if (!pipelineStarted) startPipeline()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation, fold/unfold, split screen and font-scale changes all land here.
        refreshGeometry()
    }

    override fun onDestroy() {
        logger.i(TAG, "destroying")
        settingsJob?.cancel()
        statusJob?.cancel()
        runCatching { AppGraph.dispatcher.stop() }
        runCatching { AppGraph.engine.stop() }
        runCatching { AppGraph.notificationRepository.stopObserving() }
        runCatching { overlay?.detach() }
        AppGraph.onOverlayStateChanged(false)
        AppGraph.onServiceStateChanged(false)
        composeView?.disposeComposition()
        composeView = null
        composeOwners?.clear()
        composeOwners = null
        overlay = null
        serviceScope.cancel()
        super.onDestroy()
    }

    // endregion

    // region foreground notification

    private fun promoteToForeground() {
        val notification = IslandNotifications.serviceNotification(
            this,
            getString(R.string.service_notification_idle),
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                // Below API 34 the type is not part of the contract; passing an unknown flag to the
                // framework's validation would throw, so the plain call is used on purpose.
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        }.onFailure {
            foregroundStarted = false
            logger.e(TAG, "startForeground refused", it)
            IslandNotifications.post(
                this,
                RECOVERY_NOTIFICATION_ID,
                IslandNotifications.recoveryNotification(
                    context = this,
                    title = getString(R.string.error_service_stopped),
                    body = getString(R.string.permission_recovery_open),
                    fixIntent = Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ),
            )
            stopSelf()
        }
    }

    /** Keeps the ongoing notification honest without spamming the NotificationManager. */
    private fun observeStatusLine() {
        statusJob?.cancel()
        statusJob = serviceScope.launch {
            AppGraph.engine.uiState.collectLatest { state ->
                val count = state.activeEvents.size
                val line = if (count == 0) {
                    getString(R.string.service_notification_idle)
                } else {
                    resources.getQuantityString(R.plurals.island_active_events, count, count)
                }
                if (line != lastStatusLine && foregroundStarted) {
                    lastStatusLine = line
                    IslandNotifications.post(
                        this@IslandService,
                        NOTIFICATION_ID,
                        IslandNotifications.serviceNotification(this@IslandService, line),
                    )
                }
            }
        }
    }

    // endregion

    // region overlay window

    private fun attachOverlay() {
        if (overlay == null) overlay = IslandWindowManager(this, logger)
        val window = overlay ?: return

        val view = ComposeView(this).apply {
            // Compose in a non-Activity window needs all three owners in the view tree.
            @Suppress("DEPRECATION")
            setViewTreeLifecycleOwner(this@IslandService)
            @Suppress("DEPRECATION")
            setViewTreeViewModelStoreOwner(composeOwners)
            @Suppress("DEPRECATION")
            setViewTreeSavedStateRegistryOwner(composeOwners)

            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                applyInsets(insets)
                insets
            }
            setContent { IslandHost() }
        }

        composeView = view

        // Start at 1x1 and invisible: the Compose tree reports real bounds on its first frame, so
        // the window is resized before anything is drawn. No flash, no wrong-size first frame.
        val attached = window.attach(view, WindowBoundsPx(widthPx = 1, heightPx = 1, xPx = 0, yPx = 0))
        window.setVisible(false)
        AppGraph.onOverlayStateChanged(attached)
        if (!attached) {
            logger.w(TAG, "overlay not attached: ${window.lastError}")
        }
        refreshGeometry()
        // Insets can arrive after the first layout pass; re-read once they have settled.
        view.post { refreshGeometry() }
        view.postDelayed({ refreshGeometry() }, INSET_SETTLE_DELAY_MS)
    }

    private fun applyInsets(insets: androidx.core.view.WindowInsetsCompat) {
        val display = displayState.value
        val detected = AppGraph.cutoutDetector.fromInsets(insets, display.widthPx, display.heightPx)
        if (detected != cutoutState.value) {
            cutoutState.value = detected
            logger.d(TAG, "insets updated: cutout=${detected.kind} safeTop=${detected.safeTopPx}px")
        }
    }

    private fun refreshGeometry() {
        val display = AppGraph.displayInfoProvider.current()
        if (display != displayState.value) displayState.value = display
        reduceMotionState.value = systemAnimationsDisabled()

        val view = composeView ?: return
        val rootInsets = ViewCompat.getRootWindowInsets(view)
        if (rootInsets != null) {
            applyInsets(rootInsets)
        } else {
            // No insets yet (or a platform that does not dispatch them to overlays): fall back to
            // the status-bar height from resources so the island is never under the status bar.
            val fallback = AppGraph.cutoutDetector.fromInsets(null, display.widthPx, display.heightPx)
            if (fallback != cutoutState.value) cutoutState.value = fallback
        }
    }

    /** dp → px with the display's own density; nothing here assumes a resolution. */
    private fun toPx(bounds: IslandBounds): WindowBoundsPx {
        val density = displayState.value.density.takeIf { it > 0f } ?: 1f
        return WindowBoundsPx(
            widthPx = (bounds.widthDp * density).roundToInt().coerceAtLeast(1),
            heightPx = (bounds.heightDp * density).roundToInt().coerceAtLeast(1),
            xPx = (bounds.xDp * density).roundToInt().coerceAtLeast(0),
            yPx = (bounds.yDp * density).roundToInt().coerceAtLeast(0),
        )
    }

    private fun onBoundsChanged(bounds: IslandBounds) {
        val window = overlay ?: return
        if (!window.isAttached) {
            val view = composeView ?: return
            window.attach(view, toPx(bounds))
            AppGraph.onOverlayStateChanged(window.isAttached)
        }
        window.updateBounds(toPx(bounds))
        window.setVisible(bounds.visible)
    }

    // endregion

    // region pipeline

    private fun startPipeline() {
        if (pipelineStarted) return
        pipelineStarted = true
        AppGraph.engine.start()
        AppGraph.dispatcher.start()
        AppGraph.notificationRepository.observeSettings()
        observeSettings()
        observeStatusLine()
        logger.i(TAG, "pipeline started")
    }

    /**
     * Settings drive everything: whether the island is enabled at all, and whether the required
     * capability is still granted. Losing the overlay permission degrades gracefully — the engine is
     * told to disable itself with a reason the dashboard can explain, and the user gets a recovery
     * notification instead of a silent dead overlay.
     */
    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            AppGraph.settingsRepository.settings.collect { settings ->
                val previous = currentSettings
                currentSettings = settings

                if (!settings.islandEnabled) {
                    AppGraph.engine.setEnabled(false, DisabledReason.USER_DISABLED)
                    overlay?.setVisible(false)
                    if (previous.islandEnabled) stopSelf()
                    return@collect
                }

                val canDraw = overlay?.canDrawOverlays() ?: false
                if (!canDraw) {
                    AppGraph.engine.setEnabled(false, DisabledReason.OVERLAY_PERMISSION_MISSING)
                    notifyOverlayPermissionLost()
                    return@collect
                }

                AppGraph.engine.setEnabled(true, reason = null)
                AppGraph.dispatcher.applySettings(previous, settings)
                AppGraph.permissionRepository.refresh()
            }
        }
    }

    private var permissionNoticeShownAtMs = 0L

    private fun notifyOverlayPermissionLost() {
        val now = System.currentTimeMillis()
        if (now - permissionNoticeShownAtMs < PERMISSION_NOTICE_THROTTLE_MS) return
        permissionNoticeShownAtMs = now
        IslandNotifications.post(
            this,
            RECOVERY_NOTIFICATION_ID,
            IslandNotifications.recoveryNotification(
                context = this,
                title = getString(R.string.error_overlay_denied),
                body = getString(R.string.permission_recovery_overlay),
                fixIntent = dev.island.core.permissions.PermissionIntents.overlaySettings(this)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ),
        )
    }

    private fun toggleIsland() {
        serviceScope.launch {
            AppGraph.settingsRepository.update { it.copy(islandEnabled = !it.islandEnabled) }
        }
    }

    private fun turnOff(userInitiated: Boolean) {
        logger.i(TAG, "turning off (user=$userInitiated)")
        serviceScope.launch {
            AppGraph.settingsRepository.update { it.copy(islandEnabled = false) }
            stopSelf()
        }
    }

    // endregion

    // region compose host

    @Composable
    private fun IslandHost() {
        val uiState by AppGraph.engine.uiState.collectAsState()
        val settings by AppGraph.settingsRepository.settings.collectAsState(initial = IslandSettings.Default)
        val display by displayState.collectAsState()
        val cutout by cutoutState.collectAsState()
        val reduceMotion by reduceMotionState.collectAsState()

        val router = remember {
            IslandGestureRouter(
                engine = AppGraph.engine,
                settingsProvider = { currentSettings },
                onOpenIslandSettings = { openIslandApp() },
                onOpenSourceApp = {
                    AppGraph.engine.performAction(
                        IslandAction(
                            id = "gesture-open-source",
                            kind = IslandActionKind.OPEN_SOURCE_APP,
                            iconKey = IslandIconKey.APP,
                        ),
                    )
                },
                onHaptic = { performIslandHaptic() },
                logger = logger,
            )
        }
        val gestures: IslandGestureCallbacks = remember(settings.behavior) { router.callbacks() }

        IslandAppTheme(appearance = settings.appearance, reduceMotion = reduceMotion) {
            IslandOverlayRoot(
                uiState = uiState,
                settings = settings,
                cutout = cutout,
                display = display,
                registry = AppGraph.rendererRegistry,
                systemReduceMotion = reduceMotion,
                artworkProvider = AppGraph.artworkProvider,
                appIconProvider = AppGraph.appIconProvider,
                gestures = gestures,
                onAction = { action -> AppGraph.engine.performAction(action) },
                onBoundsChanged = { bounds -> onBoundsChanged(bounds) },
                onAnimationSettled = { AppGraph.engine.onAnimationCompleted() },
            )
        }
    }

    /** Haptics through the overlay view: no VIBRATE permission needed for view haptics. */
    private fun performIslandHaptic() {
        if (!currentSettings.behavior.hapticFeedback) return
        runCatching {
            composeView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun openIslandApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true),
            )
        }.onFailure { logger.w(TAG, "opening Island failed", it) }
    }

    /** "Remove animations" / animator scale 0 is honoured app-wide, on top of Island's own switch. */
    private fun systemAnimationsDisabled(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    // endregion

    companion object {
        private const val TAG = "IslandService"

        const val ACTION_START = "dev.island.action.START"
        const val ACTION_STOP = "dev.island.action.STOP"
        const val ACTION_TOGGLE = "dev.island.action.TOGGLE"
        const val ACTION_TURN_OFF = "dev.island.action.TURN_OFF"

        private const val NOTIFICATION_ID = 1001
        private const val RECOVERY_NOTIFICATION_ID = 1002
        private const val INSET_SETTLE_DELAY_MS = 350L
        private const val PERMISSION_NOTICE_THROTTLE_MS = 60_000L

        /**
         * Starts (or re-starts) the island. Callers must be in a state where a foreground service may
         * be started: an Activity in the foreground, `BOOT_COMPLETED`, or the notification listener.
         */
        fun start(context: Context) {
            val intent = Intent(context, IslandService::class.java).setAction(ACTION_START)
            runCatching { ServiceCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, IslandService::class.java).setAction(ACTION_STOP))
            }
        }

        fun toggle(context: Context) {
            runCatching {
                context.startService(Intent(context, IslandService::class.java).setAction(ACTION_TOGGLE))
            }
        }
    }
}
