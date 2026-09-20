package dev.island.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import dev.island.core.logging.AndroidIslandLogger
import dev.island.core.logging.DebugLogStore
import dev.island.core.logging.IslandLogger
import dev.island.core.notifications.IslandNotifications
import dev.island.core.permissions.AndroidPermissionRepository
import dev.island.core.permissions.PermissionLauncher
import dev.island.core.platform.CutoutDetector
import dev.island.core.platform.DisplayInfoProvider
import dev.island.core.platform.SystemIslandClock
import dev.island.data.device.AlarmMonitor
import dev.island.data.device.BatteryMonitor
import dev.island.data.device.BluetoothMonitor
import dev.island.data.device.CallStateMonitor
import dev.island.data.device.DeviceStateRepositoryImpl
import dev.island.data.events.IslandEventFactories
import dev.island.data.history.FileEventHistoryRepository
import dev.island.data.media.ArtworkCache
import dev.island.data.media.MediaSessionRepositoryImpl
import dev.island.data.notifications.AppIconProvider
import dev.island.data.notifications.NotificationEventFactory
import dev.island.data.notifications.NotificationRepository
import dev.island.data.preferences.DataStoreSettingsRepository
import dev.island.data.timers.TimerEngine
import dev.island.domain.engine.IslandClock
import dev.island.domain.engine.IslandEngine
import dev.island.feature.island.ui.renderers.IslandRendererRegistry
import dev.island.feature.island.ui.renderers.defaultIslandRenderers
import dev.island.service.EventDispatcher
import dev.island.service.IslandActionRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Composition root.
 *
 * One object graph for the whole app, built once in [IslandApplication.onCreate]. Constructor
 * injection everywhere (no reflection, no codegen, no framework lock-in): every class declares its
 * dependencies, which is what keeps the domain layer pure JVM and the data/service/UI layers
 * independently testable — a unit test can build an [IslandEngine] with fakes and never touch this.
 *
 * Why an object and not a DI framework: the graph has ~20 nodes, is created once, and must be
 * reachable from non-Compose entry points ([dev.island.data.notifications.IslandNotificationListener],
 * [BootReceiver], [dev.island.service.TimerActionReceiver]) that Android instantiates itself and
 * therefore cannot receive constructor arguments.
 *
 * Everything here lives for the lifetime of the process; nothing here holds an Activity.
 */
// Every field here holds the *application* context for the lifetime of the process, which is
// exactly what a composition root is for; nothing in this graph can outlive the process or leak
// an Activity.
@SuppressLint("StaticFieldLeak")
object AppGraph {

    private const val TAG = "AppGraph"

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    /**
     * Process scope. `Main.immediate` because overlay layout, window updates and Compose all have
     * to happen on the main thread, and repositories hop to IO internally where they need to.
     * A [SupervisorJob] keeps one failing collector from tearing down the whole graph.
     */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val clock: IslandClock = SystemIslandClock

    /** In-memory ring buffer behind the developer "Event log" screen. Never persisted. */
    val logStore = DebugLogStore()

    val logger: IslandLogger = AndroidIslandLogger(logStore)

    // region repositories

    lateinit var settingsRepository: DataStoreSettingsRepository
        private set

    lateinit var permissionRepository: AndroidPermissionRepository
        private set

    lateinit var historyRepository: FileEventHistoryRepository
        private set

    lateinit var deviceStateRepository: DeviceStateRepositoryImpl
        private set

    lateinit var mediaRepository: MediaSessionRepositoryImpl
        private set

    lateinit var timerRepository: TimerEngine
        private set

    lateinit var notificationRepository: NotificationRepository
        private set

    // endregion

    // region device & platform

    lateinit var batteryMonitor: BatteryMonitor
        private set

    lateinit var bluetoothMonitor: BluetoothMonitor
        private set

    lateinit var callStateMonitor: CallStateMonitor
        private set

    lateinit var alarmMonitor: AlarmMonitor
        private set

    lateinit var artworkCache: ArtworkCache
        private set

    /** Loads source-app icons; exposed to the UI as the [appIconProvider] lambda below. */
    lateinit var iconProvider: AppIconProvider
        private set

    lateinit var cutoutDetector: CutoutDetector
        private set

    lateinit var displayInfoProvider: DisplayInfoProvider
        private set

    lateinit var permissionLauncher: PermissionLauncher
        private set

    // endregion

    // region engine & dispatch

    lateinit var eventFactories: IslandEventFactories
        private set

    lateinit var actionRunner: IslandActionRunner
        private set

    lateinit var engine: IslandEngine
        private set

    lateinit var dispatcher: EventDispatcher
        private set

    /** UI-side registry: which renderer draws which event family. */
    val rendererRegistry: IslandRendererRegistry by lazy { IslandRendererRegistry(defaultIslandRenderers()) }

    // endregion

    // region observable app state (dashboard / diagnostics)

    private val _notificationAccess = MutableStateFlow(false)
    val notificationAccess: StateFlow<Boolean> = _notificationAccess.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _overlayAttached = MutableStateFlow(false)
    val overlayAttached: StateFlow<Boolean> = _overlayAttached.asStateFlow()

    // endregion

    /** Album art for a media event token, or null (the renderer then draws a glyph). */
    val artworkProvider: (String?) -> Bitmap? = { token ->
        runCatching { if (initialized) mediaRepository.artworkFor(token) else null }.getOrNull()
    }

    /** Source-app icon for a notification event, or null (the renderer then draws a glyph). */
    val appIconProvider: (String?) -> Drawable? = { packageName ->
        runCatching { if (initialized) iconProvider.iconFor(packageName) else null }.getOrNull()
    }

    /**
     * Builds the graph. Idempotent and safe to call from [IslandApplication.onCreate]; Android
     * guarantees the Application is created before any other component in the same process.
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        IslandNotifications.ensureChannels(appContext)

        settingsRepository = DataStoreSettingsRepository(appContext, scope, logger)
        permissionRepository = AndroidPermissionRepository(appContext, logger)
        permissionLauncher = PermissionLauncher(logger)
        artworkCache = ArtworkCache(logger)
        iconProvider = AppIconProvider(appContext)
        cutoutDetector = CutoutDetector(logger)
        displayInfoProvider = DisplayInfoProvider(appContext)

        batteryMonitor = BatteryMonitor(appContext, logger)
        bluetoothMonitor = BluetoothMonitor(appContext, logger)
        callStateMonitor = CallStateMonitor(appContext, logger, clock)
        alarmMonitor = AlarmMonitor(appContext, logger, clock)

        deviceStateRepository = DeviceStateRepositoryImpl(appContext, scope, logger, batteryMonitor)
        mediaRepository = MediaSessionRepositoryImpl(appContext, scope, logger, artworkCache, settingsRepository)
        timerRepository = TimerEngine(appContext, scope, clock, logger)
        historyRepository = FileEventHistoryRepository(appContext, scope, logger, settingsRepository)

        val notificationFactory = NotificationEventFactory(appContext, logger)
        notificationRepository =
            NotificationRepository(appContext, scope, logger, notificationFactory, settingsRepository)

        eventFactories = IslandEventFactories(appContext, clock)
        actionRunner = IslandActionRunner(
            context = appContext,
            scope = scope,
            logger = logger,
            mediaRepository = mediaRepository,
            timerRepository = timerRepository,
            notificationRepository = notificationRepository,
            callStateMonitor = callStateMonitor,
            demoMode = { runCatching { engine.currentSettings.advanced.demoMode }.getOrDefault(false) },
        )

        engine = IslandEngine(
            scope = scope,
            settingsRepository = settingsRepository,
            deviceStateRepository = deviceStateRepository,
            clock = clock,
            logger = logger,
            historyRepository = historyRepository,
            actionHandler = actionRunner,
        )

        dispatcher = EventDispatcher(
            scope = scope,
            logger = logger,
            clock = clock,
            engine = engine,
            settingsRepository = settingsRepository,
            deviceStateRepository = deviceStateRepository,
            mediaRepository = mediaRepository,
            timerRepository = timerRepository,
            batteryMonitor = batteryMonitor,
            bluetoothMonitor = bluetoothMonitor,
            callStateMonitor = callStateMonitor,
            alarmMonitor = alarmMonitor,
            notificationRepository = notificationRepository,
            factories = eventFactories,
        )

        permissionRepository.refresh()
        initialized = true
        logger.i(TAG, "graph ready")
    }

    /** Called by [dev.island.data.notifications.IslandNotificationListener] on connect/disconnect. */
    fun onNotificationAccessChanged(granted: Boolean) {
        _notificationAccess.value = granted
        if (initialized) permissionRepository.refresh()
    }

    fun onServiceStateChanged(running: Boolean) {
        _serviceRunning.value = running
    }

    fun onOverlayStateChanged(attached: Boolean) {
        _overlayAttached.value = attached
    }

    /** Application context for the rare component that needs one and cannot get it injected. */
    fun context(): Context {
        check(initialized) { "AppGraph.init() must be called from Application.onCreate()" }
        return appContext
    }

    fun isReady(): Boolean = initialized
}
