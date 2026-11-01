package mo.dev.ctrus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mo.dev.ctrus.alarm.WakeAlarmManager
import mo.dev.ctrus.blocking.AdultDomainList
import mo.dev.ctrus.childmode.ChildModeStore
import mo.dev.ctrus.childmode.ChildSessionLimit
import mo.dev.ctrus.childmode.ParentCredentials
import mo.dev.ctrus.blocking.BlockingHealthMonitor
import mo.dev.ctrus.blocking.BlockingStateHolder
import mo.dev.ctrus.data.CtrusDatabase
import mo.dev.ctrus.data.ProfileRepository
import mo.dev.ctrus.data.SessionRepository
import mo.dev.ctrus.model3d.ObjModelCache
import mo.dev.ctrus.network.RecoveryCodeClient
import mo.dev.ctrus.scheduling.AlarmSchedulingGateway
import mo.dev.ctrus.scheduling.ScheduleAlarmScheduler
import mo.dev.ctrus.scheduling.SchedulingGateway
import mo.dev.ctrus.settings.AppPreferences
import mo.dev.ctrus.strategy.StrategyRegistry

/**
 * Simple manual-DI application container (no Hilt/Dagger, matching the rest of the scaffold's
 * "keep it plain" approach) — exposes the singletons every screen/service needs.
 */
class CtrusApp : Application() {
    lateinit var database: CtrusDatabase
        private set
    lateinit var profileRepository: ProfileRepository
        private set
    lateinit var sessionRepository: SessionRepository
        private set
    lateinit var schedulingGateway: SchedulingGateway
        private set
    lateinit var strategyRegistry: StrategyRegistry
        private set
    lateinit var scheduleAlarmScheduler: ScheduleAlarmScheduler
        private set
    lateinit var preferences: AppPreferences
        private set
    val recoveryCodeClient = RecoveryCodeClient()

    /** Lives for the process lifetime — backs [BlockingStateHolder] and scheduling callbacks. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ChildModeStore.init(this)
        ParentCredentials.init(this)
        database = CtrusDatabase.getInstance(this)
        profileRepository = ProfileRepository(database.blockedProfileDao())
        schedulingGateway = AlarmSchedulingGateway(this)
        sessionRepository = SessionRepository(database.sessionDao(), schedulingGateway)
        strategyRegistry = StrategyRegistry(sessionRepository)
        preferences = AppPreferences(this)
        scheduleAlarmScheduler = ScheduleAlarmScheduler(this)

        WakeAlarmManager.init(this)
        BlockingStateHolder.start(applicationScope, sessionRepository, profileRepository)
        AdultDomainList.init(this)

        // Keeps every "Schedule + Ctrus NFC" profile's alarms in step with Room: creating,
        // editing, switching mode, duplicating, or deleting a profile all re-arm/cancel here —
        // the equivalent of iOS calling DeviceActivityCenterUtil.scheduleTimerActivity on save
        // and removeScheduleTimerActivities on delete.
        applicationScope.launch {
            var previousIds = emptySet<String>()
            profileRepository.observeAll().collect { profiles ->
                val ids = profiles.map { it.id }.toSet()
                scheduleAlarmScheduler.syncAll(profiles, removedProfileIds = previousIds - ids)
                previousIds = ids
                // Alarm mode's alarm follows every save, delete and skip too (spec D4).
                WakeAlarmManager.refreshAll(this@CtrusApp)
            }
        }
        // Arms the automatic stop for fixed-duration schedule sessions however they started
        // (on schedule or from the bubble). ScheduleReceiver checks the session is still running.
        applicationScope.launch {
            sessionRepository.observeMostRecentActive().collect { session ->
                BlockingHealthMonitor.sync(this@CtrusApp, session?.id)
                val profile = session?.let { profileRepository.find(it.profileId) } ?: return@collect
                scheduleAlarmScheduler.syncSessionEnd(session, profile)
            }
        }

        // Mirrors StrategyManager's handleSessionStarted/handleSessionEnded checking both unlock
        // cycles, however the session started or ended (app, schedule, NFC, emergency).
        applicationScope.launch {
            sessionRepository.observeMostRecentActive().map { it?.id }.distinctUntilChanged().drop(1).collect {
                preferences.checkUnlockCycles()
            }
        }
        // Alarm mode: a session starting or ending moves its profile's alarm (spec D4), and an
        // ending one silences it, however it ended (D6.5).
        applicationScope.launch {
            var previous: mo.dev.ctrus.data.BlockedProfileSessionEntity? = null
            sessionRepository.observeMostRecentActive().collect { session ->
                val ended = previous?.takeIf { it.id != session?.id }
                previous = session
                ended?.let { WakeAlarmManager.onSessionEnded(this@CtrusApp, it.profileId) }
                session?.let { WakeAlarmManager.refresh(this@CtrusApp, it.profileId) }
            }
        }
        // Child Mode's 24-hour (or shorter) automatic end, armed for each new session and dropped
        // when it ends, however it started or ended.
        applicationScope.launch {
            sessionRepository.observeMostRecentActive().map { it?.id }.distinctUntilChanged().collect {
                ChildSessionLimit.sync(this@CtrusApp)
            }
        }

        // Warms the 3D mascot's parsed-mesh cache before Home is ever shown, so the model doesn't
        // visibly stall on the very first appearance either — see ObjModelCache's kdoc.
        applicationScope.launch { ObjModelCache.get(this@CtrusApp) }

        createNotificationChannels()
    }

    /**
     * Backs the "break almost over" notification posted by ExpiryReceiver's warning alarm, and
     * ScheduleReceiver's "starts in 5 minutes" / "couldn't start" notifications, and
 * BlockingHealthMonitor's "Blocking stopped" warning.
     */
    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val breakChannel = NotificationChannel(
            BREAK_WARNING_CHANNEL_ID,
            getString(R.string.break_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.break_notification_channel_description) }
        val scheduleChannel = NotificationChannel(
            SCHEDULE_CHANNEL_ID,
            getString(R.string.schedule_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.schedule_notification_channel_description) }
        val blockingStatusChannel = NotificationChannel(
            BLOCKING_STATUS_CHANNEL_ID,
            getString(R.string.blocking_status_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = getString(R.string.blocking_status_channel_description) }
        // The alarm's own sound and vibration come from AlarmService, so the channel stays quiet;
        // it's high importance for the full-screen intent and the heads-up.
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannels(listOf(breakChannel, scheduleChannel, blockingStatusChannel, alarmChannel))
    }

    companion object {
        const val BREAK_WARNING_CHANNEL_ID = "break_warning"
        const val SCHEDULE_CHANNEL_ID = "schedule"
        /** BlockingHealthMonitor's "Blocking stopped" warning. */
        const val BLOCKING_STATUS_CHANNEL_ID = "blocking_status"
        /** Alarm mode's ringing alarm (AlarmService). */
        const val ALARM_CHANNEL_ID = "alarm"

        fun from(context: android.content.Context): CtrusApp = context.applicationContext as CtrusApp
    }
}
