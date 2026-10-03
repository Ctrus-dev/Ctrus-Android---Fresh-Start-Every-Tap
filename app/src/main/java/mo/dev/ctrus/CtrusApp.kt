package mo.dev.ctrus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        database = CtrusDatabase.getInstance(this)
        profileRepository = ProfileRepository(database.blockedProfileDao())
        schedulingGateway = AlarmSchedulingGateway(this)
        sessionRepository = SessionRepository(database.sessionDao(), schedulingGateway)
        strategyRegistry = StrategyRegistry(sessionRepository)
        preferences = AppPreferences(this)
        scheduleAlarmScheduler = ScheduleAlarmScheduler(this)

        BlockingStateHolder.start(applicationScope, sessionRepository, profileRepository)

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
            }
        }
        // Arms the automatic stop for fixed-duration schedule sessions however they started
        // (on schedule or from the bubble). ScheduleReceiver checks the session is still running.
        applicationScope.launch {
            sessionRepository.observeMostRecentActive().collect { session ->
                val profile = session?.let { profileRepository.find(it.profileId) } ?: return@collect
                scheduleAlarmScheduler.syncSessionEnd(session, profile)
            }
        }

        // Warms the 3D mascot's parsed-mesh cache before Home is ever shown, so the model doesn't
        // visibly stall on the very first appearance either — see ObjModelCache's kdoc.
        applicationScope.launch { ObjModelCache.get(this@CtrusApp) }

        createNotificationChannels()
    }

    /**
     * Backs the "break almost over" notification posted by ExpiryReceiver's warning alarm, and
     * ScheduleReceiver's "starts in 5 minutes" / "couldn't start" notifications.
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
        manager.createNotificationChannels(listOf(breakChannel, scheduleChannel))
    }

    companion object {
        const val BREAK_WARNING_CHANNEL_ID = "break_warning"
        const val SCHEDULE_CHANNEL_ID = "schedule"

        fun from(context: android.content.Context): CtrusApp = context.applicationContext as CtrusApp
    }
}
