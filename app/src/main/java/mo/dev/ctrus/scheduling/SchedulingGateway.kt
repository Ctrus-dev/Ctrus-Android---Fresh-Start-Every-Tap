package mo.dev.ctrus.scheduling

/**
 * Android equivalent of the one piece of Ctrus/Utils/DeviceActivityCenterUtil.swift that's
 * actually reachable through the app's UI: auto-resuming blocking when a break (Breaks section
 * in the profile form) elapses. iOS's other scheduled activities (strategy timers, pause
 * timers, soft-unblock grant expiry, recurring weekly schedule windows) all belong to strategies
 * or a Schedule feature that aren't user-selectable/reachable in the current app — see
 * [mo.dev.ctrus.strategy.StrategyIds] — so they weren't ported. Backed by AlarmManager (see
 * AlarmSchedulingGateway) since Android has no DeviceActivityCenter equivalent.
 */
interface SchedulingGateway {
    fun scheduleBreakExpiry(sessionId: String, profileId: String, triggerAtEpochMilli: Long)
    fun cancelBreakExpiry(sessionId: String)
}
