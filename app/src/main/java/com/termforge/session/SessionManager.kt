package com.termforge.session

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.termforge.agent.AgentInstance
import com.termforge.agent.AgentInstanceRegistry
import com.termforge.agent.AgentRouter
import com.termforge.data.db.ForwardType
import com.termforge.data.db.entities.HostEntity
import com.termforge.data.prefs.AppPreferences
import com.termforge.data.repo.HostRepository
import com.termforge.data.repo.IdentityRepository
import com.termforge.data.repo.PortForwardRepository
import com.termforge.transport.AuthMethod
import com.termforge.transport.ConnectionConfig
import com.termforge.transport.ForwardKind
import com.termforge.transport.HostKeyDecision
import com.termforge.transport.HostKeyRequest
import com.termforge.transport.KbPrompt
import com.termforge.transport.PortForwardSpec
import com.termforge.transport.TerminalSize
import com.termforge.transport.TransportCallbacks
import com.termforge.transport.TransportFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Bridges a single session's interactive prompts to the [PromptHub]. */
private class PromptingCallbacks(
    private val sessionId: String,
    private val hub: PromptHub,
) : TransportCallbacks {
    override suspend fun verifyHostKey(request: HostKeyRequest): HostKeyDecision =
        hub.requestHostKey(sessionId, request)

    override suspend fun answerKeyboardInteractive(
        name: String, instruction: String, prompts: List<KbPrompt>,
    ): List<String>? = hub.requestKeyboardInteractive(sessionId, name, instruction, prompts)
}

/**
 * Owns every live [TerminalConnection] for the whole process (a Hilt @Singleton, so it outlives any
 * screen). Builds connection configs by resolving stored credentials and raising prompts where
 * needed, then keeps sessions alive in the background via the foreground [SessionService].
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hostRepo: HostRepository,
    private val identityRepo: IdentityRepository,
    private val portForwardRepo: PortForwardRepository,
    private val appPrefs: AppPreferences,
    private val transportFactory: TransportFactory,
    private val promptHub: PromptHub,
    private val attentionNotifier: AttentionNotifier,
) {
    private val _sessions = MutableStateFlow<List<TerminalConnection>>(emptyList())
    val sessions = _sessions.asStateFlow()

    // --- Agent attention notifications ---
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Hot-path snapshot of the opt-in flag, read once per inbound chunk; kept live by the collector
     *  below instead of re-reading DataStore each time (which would stutter the terminal). */
    @Volatile private var agentAttentionEnabled = false
    @Volatile private var mutedAgents: Set<String> = emptySet()
    /** The session the user is actively viewing (foreground); its own attention pings are suppressed. */
    @Volatile var visibleSessionId: String? = null
        private set
    private val lastAttentionAtMs = ConcurrentHashMap<String, Long>()

    /** One feed-follower per connection; discovers Claude instances and fires per-instance attention. */
    private val registries = ConcurrentHashMap<String, AgentInstanceRegistry>()
    private val _instances = MutableStateFlow<List<AgentInstance>>(emptyList())
    /** Every live agent instance across all connected hosts (the Agents dashboard / watch source). */
    val instances = _instances.asStateFlow()

    init {
        managerScope.launch {
            appPrefs.settings.collect {
                agentAttentionEnabled = it.agentAttentionEnabled
                mutedAgents = it.mutedAgentSessions
            }
        }
    }

    fun get(sessionId: String): TerminalConnection? = _sessions.value.firstOrNull { it.sessionId == sessionId }

    /** Called by the terminal screen on RESUME — suppresses pings for and clears any stale ping of it. */
    fun markVisible(sessionId: String) {
        visibleSessionId = sessionId
        attentionNotifier.cancel(sessionId)
    }

    /** Called on PAUSE. Guarded so a screen leaving doesn't clear the incoming screen's visibility. */
    fun markHidden(sessionId: String) {
        if (visibleSessionId == sessionId) visibleSessionId = null
    }

    fun newSessionId(): String = UUID.randomUUID().toString()

    /** Build a connection config for a host (resolving auth), e.g. for the SFTP browser. */
    suspend fun buildConfig(hostId: Long, sessionId: String): ConnectionConfig {
        val host = hostRepo.getById(hostId) ?: throw IllegalArgumentException("Host $hostId not found")
        val settings = appPrefs.settings.first()
        return ConnectionConfig(
            protocol = host.protocol,
            host = host.address,
            port = host.port,
            username = host.username,
            termType = host.termType,
            charset = host.charset,
            keepAliveSec = if (host.keepAliveSec > 0) host.keepAliveSec else settings.defaultKeepAliveSec,
            authMethods = buildAuth(host, sessionId),
            portForwards = loadForwards(hostId),
        )
    }

    /** Generate an id and connect (for non-UI callers). */
    suspend fun connect(hostId: Long): String {
        val id = newSessionId()
        startConnection(id, hostId)
        return id
    }

    /**
     * Connect using a caller-supplied [sessionId] (so the UI can navigate to the terminal screen
     * first, then have prompts appear there). No-op if that session already exists.
     */
    suspend fun startConnection(sessionId: String, hostId: Long) {
        if (get(sessionId) != null) return
        val host = hostRepo.getById(hostId)
            ?: throw IllegalArgumentException("Host $hostId not found")
        val callbacks = PromptingCallbacks(sessionId, promptHub)
        val settings = appPrefs.settings.first()

        val config = ConnectionConfig(
            protocol = host.protocol,
            host = host.address,
            port = host.port,
            username = host.username,
            termType = host.termType,
            charset = host.charset,
            startupCommand = host.startupCommand,
            keepAliveSec = if (host.keepAliveSec > 0) host.keepAliveSec else settings.defaultKeepAliveSec,
            authMethods = buildAuth(host, sessionId),
            portForwards = loadForwards(hostId),
            initialSize = TerminalSize(80, 24),
        )

        val connection = TerminalConnection(
            sessionId = sessionId,
            hostId = hostId,
            title = host.nickname.ifBlank { "${host.username}@${host.address}" },
            protocol = host.protocol,
            baseConfig = config,
            scrollbackLines = settings.scrollbackLines,
            transportFactory = transportFactory,
            callbacks = callbacks,
            autoReconnect = host.autoReconnect,
            onTerminated = ::onConnectionTerminated,
            onAttention = ::onConnectionAttention,
            attentionEnabled = { agentAttentionEnabled },
        )
        _sessions.update { it + connection }
        connection.start()
        if (agentAttentionEnabled) startRegistry(sessionId, hostId, connection)
        hostRepo.touchLastConnected(hostId, System.currentTimeMillis())
        startService()
    }

    private suspend fun buildAuth(host: HostEntity, sessionId: String): List<AuthMethod> {
        val methods = mutableListOf<AuthMethod>()
        host.identityId?.let { id ->
            identityRepo.getById(id)?.let { identity ->
                val pem = identityRepo.resolvePrivateKeyPem(identity)
                val passphrase = identityRepo.resolvePassphrase(identity)
                    ?: promptHub.requestPassphrase(sessionId, identity.name)
                methods.add(AuthMethod.PublicKey(pem, passphrase))
            }
        }
        when {
            host.savePassword && host.inlinePasswordEnc != null ->
                hostRepo.resolvePassword(host)?.let { methods.add(AuthMethod.Password(it)) }
            host.askEachTime ->
                promptHub.requestPassword(sessionId, "${host.username}@${host.address}")
                    ?.let { methods.add(AuthMethod.Password(it)) }
        }
        if (host.useKeyboardInteractive) methods.add(AuthMethod.KeyboardInteractive)
        return methods
    }

    private suspend fun loadForwards(hostId: Long): List<PortForwardSpec> =
        portForwardRepo.getForHost(hostId).filter { it.enabled }.map { f ->
            PortForwardSpec(
                kind = when (f.type) {
                    ForwardType.LOCAL -> ForwardKind.LOCAL
                    ForwardType.REMOTE -> ForwardKind.REMOTE
                    ForwardType.DYNAMIC -> ForwardKind.DYNAMIC
                },
                bindAddr = f.bindAddr, bindPort = f.bindPort,
                destHost = f.destHost, destPort = f.destPort,
            )
        }

    fun disconnect(sessionId: String) {
        _sessions.value.firstOrNull { it.sessionId == sessionId }?.disconnect()
        _sessions.update { it.filterNot { c -> c.sessionId == sessionId } }
        attentionNotifier.cancel(sessionId)
        registries.remove(sessionId)?.stop()
        recomputeInstances()
        if (_sessions.value.isEmpty()) stopService()
    }

    /**
     * A [TerminalConnection] saw the agent-attention marker in its stream. Notify — unless the user is
     * already looking at that session — debounced so the Stop + Notification hook double-fire (and rapid
     * re-prompts) collapse into a single ping.
     */
    private fun onConnectionAttention(sessionId: String) {
        if (visibleSessionId == sessionId) return
        val conn = _sessions.value.firstOrNull { it.sessionId == sessionId } ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - (lastAttentionAtMs[sessionId] ?: 0L) < ATTENTION_DEBOUNCE_MS) return
        lastAttentionAtMs[sessionId] = now
        attentionNotifier.notifyAttention(sessionId, conn.title)
    }

    // --- Multi-instance (agent feed) wiring ---

    private fun startRegistry(sessionId: String, hostId: Long, connection: TerminalConnection) {
        if (registries.containsKey(sessionId)) return
        val reg = AgentInstanceRegistry(
            hostId = hostId,
            scope = managerScope,
            nowMs = { SystemClock.elapsedRealtime() },
            openExec = { cmd -> connection.openExec(cmd) },
            onAttention = ::onInstanceAttention,
        )
        registries[sessionId] = reg
        managerScope.launch { reg.instances.collect { recomputeInstances() } }
        reg.start()
    }

    private fun recomputeInstances() {
        _instances.value = registries.values
            .flatMap { it.instances.value.values }
            .sortedBy { (it.sessionName ?: "") + "/" + (it.windowName ?: "") }
    }

    /** A specific Claude instance saw a needs-you transition. Debounced per instance. */
    private fun onInstanceAttention(inst: AgentInstance) {
        if (inst.enableKey in mutedAgents) return // this session is muted
        val now = SystemClock.elapsedRealtime()
        if (now - (lastAttentionAtMs[inst.key] ?: 0L) < ATTENTION_DEBOUNCE_MS) return
        lastAttentionAtMs[inst.key] = now
        attentionNotifier.notifyInstance(inst)
    }

    fun instanceByKey(key: String): AgentInstance? = _instances.value.firstOrNull { it.key == key }

    private fun connectionForHost(hostId: Long): TerminalConnection? =
        _sessions.value.firstOrNull { it.hostId == hostId }

    /** sessionId of a live connection to that instance's host, for navigating the terminal to it. */
    fun sessionIdForHost(hostId: Long): String? = connectionForHost(hostId)?.sessionId

    private fun routerForHost(hostId: Long): AgentRouter? =
        connectionForHost(hostId)?.let { c -> AgentRouter { cmd -> c.openExec(cmd) } }

    fun focusInstance(inst: AgentInstance) {
        managerScope.launch { runCatching { routerForHost(inst.hostId)?.focus(inst) } }
    }

    fun routeApprove(inst: AgentInstance) {
        managerScope.launch { runCatching { routerForHost(inst.hostId)?.approve(inst) }; attentionNotifier.cancelInstance(inst) }
    }

    fun routeDeny(inst: AgentInstance) {
        managerScope.launch { runCatching { routerForHost(inst.hostId)?.deny(inst) }; attentionNotifier.cancelInstance(inst) }
    }

    fun routeContinue(inst: AgentInstance) {
        managerScope.launch { runCatching { routerForHost(inst.hostId)?.cont(inst) } }
    }

    fun routeText(inst: AgentInstance, text: String) {
        managerScope.launch { runCatching { routerForHost(inst.hostId)?.sendText(inst, text) } }
    }

    /**
     * Called by a [TerminalConnection] when it ends on its own (clean `exit`, or a drop with no
     * reconnect). Prunes it so the active-sessions list/count stays accurate. Idempotent.
     */
    private fun onConnectionTerminated(sessionId: String) {
        if (_sessions.value.none { it.sessionId == sessionId }) return
        _sessions.update { it.filterNot { c -> c.sessionId == sessionId } }
        attentionNotifier.cancel(sessionId)
        registries.remove(sessionId)?.stop()
        recomputeInstances()
        if (_sessions.value.isEmpty()) stopService()
    }

    fun disconnectAll() {
        _sessions.value.forEach { it.disconnect() }
        _sessions.value = emptyList()
        stopService()
    }

    private fun startService() {
        val intent = Intent(context, SessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopService() {
        context.stopService(Intent(context, SessionService::class.java))
    }

    private companion object {
        const val ATTENTION_DEBOUNCE_MS = 2000L
    }
}
