package mo.dev.ctrus.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mo.dev.ctrus.CtrusApp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.data.ProfileRepository
import mo.dev.ctrus.data.SessionRepository
import mo.dev.ctrus.data.SessionTimeCalculator
import mo.dev.ctrus.data.isActive
import mo.dev.ctrus.data.isBreakActive
import mo.dev.ctrus.data.isBreakAvailable
import mo.dev.ctrus.data.remainingBreakAllowance
import mo.dev.ctrus.scheduling.SchedulingGateway
import mo.dev.ctrus.settings.AppPreferences
import mo.dev.ctrus.strategy.FocusMessages
import mo.dev.ctrus.strategy.StrategyCapabilities
import mo.dev.ctrus.strategy.StrategyInput
import mo.dev.ctrus.strategy.StrategyRegistry
import mo.dev.ctrus.strategy.StrategyRequirement
import mo.dev.ctrus.strategy.StrategyResult
import mo.dev.ctrus.util.UiText

sealed interface PendingRequirement {
    val requirement: StrategyRequirement
    val message: UiText?

    data class Start(
        val profile: BlockedProfileEntity,
        override val requirement: StrategyRequirement,
        val forceStart: Boolean,
        override val message: UiText? = null,
    ) : PendingRequirement

    data class Stop(
        val profile: BlockedProfileEntity,
        val session: BlockedProfileSessionEntity,
        override val requirement: StrategyRequirement,
        override val message: UiText? = null,
    ) : PendingRequirement
}

/**
 * Kotlin ViewModel equivalent of Ctrus/Utils/StrategyManager.swift: owns the active
 * session/profile, the once-a-second elapsed/displayed timer, the rotating focus message, and
 * start/stop/break/emergency-unblock orchestration through [StrategyRegistry].
 */
class SessionOrchestrator(
    private val profiles: ProfileRepository,
    private val sessions: SessionRepository,
    private val strategies: StrategyRegistry,
    private val scheduling: SchedulingGateway,
    private val preferences: AppPreferences,
) : ViewModel() {

    val activeSession: StateFlow<BlockedProfileSessionEntity?> =
        sessions.observeMostRecentActive().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _activeProfile = MutableStateFlow<BlockedProfileEntity?>(null)
    val activeProfile: StateFlow<BlockedProfileEntity?> = _activeProfile

    private val _elapsedSeconds = MutableStateFlow(0.0)
    val elapsedSeconds: StateFlow<Double> = _elapsedSeconds

    private val _displayedSeconds = MutableStateFlow(0.0)
    val displayedSeconds: StateFlow<Double> = _displayedSeconds

    private val _focusMessage = MutableStateFlow(FocusMessages.randomIndex())
    val focusMessage: StateFlow<Int> = _focusMessage

    private val _errorMessage = MutableStateFlow<UiText?>(null)
    val errorMessage: StateFlow<UiText?> = _errorMessage

    private val _pendingRequirement = MutableStateFlow<PendingRequirement?>(null)
    val pendingRequirement: StateFlow<PendingRequirement?> = _pendingRequirement

    private var tickerJob: Job? = null
    private var messageJob: Job? = null

    init {
        viewModelScope.launch {
            var lastSessionId: String? = null
            activeSession.collect { session ->
                if (session == null || !session.isActive) {
                    lastSessionId = null
                    stopTicking()
                    _activeProfile.value = null
                    return@collect
                }
                if (session.id == lastSessionId) return@collect
                lastSessionId = session.id
                _activeProfile.value = profiles.find(session.profileId)
                startTicking(session.id, session.profileId)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun toggleBlocking(profile: BlockedProfileEntity?) {
        if (activeSession.value?.isActive == true) requestStop() else profile?.let(::requestStart)
    }

    fun requestStart(profile: BlockedProfileEntity, forceStart: Boolean = false) {
        viewModelScope.launch {
            val strategy = strategies.get(profile.blockingStrategyId)
            val requirement = strategy.startRequirement(profile)
            if (requirement == StrategyRequirement.None) {
                runStart(profile, StrategyInput.None, forceStart)
            } else {
                _pendingRequirement.value = PendingRequirement.Start(profile, requirement, forceStart)
            }
        }
    }

    fun provideStartInput(input: StrategyInput) {
        val pending = _pendingRequirement.value as? PendingRequirement.Start ?: return
        viewModelScope.launch { runStart(pending.profile, input, pending.forceStart) }
    }

    private suspend fun runStart(profile: BlockedProfileEntity, input: StrategyInput, forceStart: Boolean) {
        val strategy = strategies.get(profile.blockingStrategyId)
        when (val result = strategy.startBlocking(profile, input, forceStart)) {
            is StrategyResult.Started -> {
                _pendingRequirement.value = null
                _errorMessage.value = null
            }
            is StrategyResult.NeedsInput ->
                _pendingRequirement.value = PendingRequirement.Start(profile, result.requirement, forceStart, result.message)
            is StrategyResult.Error -> {
                _pendingRequirement.value = null
                _errorMessage.value = result.message
            }
            else -> Unit
        }
    }

    fun requestStop() {
        val session = activeSession.value ?: return
        val profile = _activeProfile.value ?: return
        if (profile.disableBackgroundStops) {
            _errorMessage.value = UiText(R.string.error_background_stops_disabled, profile.name)
            return
        }
        viewModelScope.launch {
            val strategy = strategies.get(profile.blockingStrategyId)
            val requirement = strategy.stopRequirement(profile, session)
            if (requirement == StrategyRequirement.None) {
                runStop(profile, session, StrategyInput.None)
            } else {
                _pendingRequirement.value = PendingRequirement.Stop(profile, session, requirement)
            }
        }
    }

    fun provideStopInput(input: StrategyInput) {
        val pending = _pendingRequirement.value as? PendingRequirement.Stop ?: return
        viewModelScope.launch { runStop(pending.profile, pending.session, input) }
    }

    private suspend fun runStop(profile: BlockedProfileEntity, session: BlockedProfileSessionEntity, input: StrategyInput) {
        val strategy = strategies.get(profile.blockingStrategyId)
        when (val result = strategy.stopBlocking(profile, session, input)) {
            is StrategyResult.Ended -> _pendingRequirement.value = null
            is StrategyResult.NeedsInput ->
                _pendingRequirement.value = PendingRequirement.Stop(profile, session, result.requirement, result.message)
            is StrategyResult.Error -> {
                _pendingRequirement.value = null
                _errorMessage.value = result.message
            }
            else -> Unit
        }
    }

    fun cancelPendingRequirement() {
        _pendingRequirement.value = null
    }

    fun toggleBreak() {
        val session = activeSession.value ?: return
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            val allowsBreaks = StrategyCapabilities.allowsTimedBreaks(profile.blockingStrategyId)
            when {
                session.isBreakActive(profile, allowsBreaks) -> {
                    sessions.endBreak(session, profile)
                    scheduling.cancelBreakExpiry(session.id)
                }
                session.isBreakAvailable(profile, allowsBreaks) -> {
                    val updated = sessions.startBreak(session, profile.allowMultipleBreaks)
                    val remaining = updated.remainingBreakAllowance(profile)
                    scheduling.scheduleBreakExpiry(session.id, profile.id, System.currentTimeMillis() + (remaining * 1000).toLong())
                }
            }
        }
    }

    /** Mirrors StrategyManager.emergencyUnblock: bypasses the active strategy entirely. */
    fun emergencyUnblock() {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            if (!preferences.consumeEmergencyUnblock()) return@launch
            scheduling.cancelBreakExpiry(session.id)
            sessions.endSession(session)
        }
    }

    private fun startTicking(sessionId: String, profileId: String) {
        stopTicking()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                val session = sessions.find(sessionId) ?: break
                if (!session.isActive) break
                val profile = profiles.find(profileId) ?: break
                _activeProfile.value = profile
                val elapsed = SessionTimeCalculator.elapsedFocusTimeSeconds(session, profile)
                _elapsedSeconds.value = elapsed
                _displayedSeconds.value = SessionTimeCalculator.displayedTimeSeconds(session, profile, elapsed)
                delay(1000)
            }
        }
        messageJob = viewModelScope.launch {
            while (isActive) {
                _focusMessage.value = FocusMessages.randomIndex()
                delay(10_000)
            }
        }
    }

    private fun stopTicking() {
        tickerJob?.cancel()
        tickerJob = null
        messageJob?.cancel()
        messageJob = null
        _elapsedSeconds.value = 0.0
        _displayedSeconds.value = 0.0
    }

    class Factory(private val app: CtrusApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SessionOrchestrator(
                app.profileRepository,
                app.sessionRepository,
                app.strategyRegistry,
                app.schedulingGateway,
                app.preferences,
            ) as T
    }
}
