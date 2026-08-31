package mo.dev.ctrus.blocking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.data.ProfileRepository
import mo.dev.ctrus.data.SessionRepository
import mo.dev.ctrus.data.isActive
import mo.dev.ctrus.data.isBreakActive
import mo.dev.ctrus.strategy.StrategyCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
object BlockingStateHolder {
    private val _state = MutableStateFlow(BlockingState())
    val state: StateFlow<BlockingState> = _state

    /** Call once from Application.onCreate; keeps [state] in sync with Room for the app's lifetime. */
    fun start(
        scope: CoroutineScope,
        sessionRepository: SessionRepository,
        profileRepository: ProfileRepository,
    ) {
        sessionRepository.observeMostRecentActive()
            .distinctUntilChanged()
            .flatMapLatest { session ->
                if (session == null || !session.isActive) {
                    flowOf(BlockingState())
                } else {
                    profileRepository.observe(session.profileId).flatMapLatest { profile ->
                        if (profile == null) flowOf(BlockingState()) else flowOf(buildState(session, profile))
                    }
                }
            }
            .onEach { _state.value = it }
            .launchIn(scope)
    }

    private fun buildState(session: BlockedProfileSessionEntity, profile: BlockedProfileEntity): BlockingState {
        val allowsBreaks = StrategyCapabilities.allowsTimedBreaks(profile.blockingStrategyId)
        return BlockingState(
            profileId = profile.id,
            profileName = profile.name,
            blockingStrategyId = profile.blockingStrategyId,
            sessionId = session.id,
            blockedPackages = profile.selectedPackages.toSet(),
            allowMode = profile.enableAllowMode,
            domains = profile.domains.orEmpty().toSet(),
            allowModeDomains = profile.enableAllowModeDomains,
            enableBrowserBlocking = profile.enableBrowserBlocking,
            enableAdultContentBlocking = profile.enableAdultContentBlocking,
            isBreakActive = session.isBreakActive(profile, allowsBreaks),
            enableStrictMode = profile.enableStrictMode,
            enableBlockAppInstallation = profile.enableBlockAppInstallation,
        )
    }
}
