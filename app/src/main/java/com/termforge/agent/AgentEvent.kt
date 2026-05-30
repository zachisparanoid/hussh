package com.termforge.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire shape of one feed/list line (kotlinx.serialization; pure-JVM so it unit-tests off-device). */
@Serializable
private data class AgentEventDto(
    val reason: String? = null,
    val instance: String? = null,
    val socket: String? = null,
    val target: String? = null,
    val session: String? = null,
    @SerialName("window_name") val windowName: String? = null,
    val cwd: String? = null,
    val cmd: String? = null,
    val prompt: List<String> = emptyList(),
    val ts: Long = 0L,
)

/** One agent feed event (from the hook) or list entry (from `tf-route.sh list`). */
data class AgentEvent(
    val reason: String,            // done | input | perm | running
    val instance: String?,         // tmux pane id; null = not tmux = not routable
    val socket: String?,
    val target: String?,
    val session: String?,
    val windowName: String?,
    val cwd: String?,
    val cmd: String?,
    val prompt: List<String>,
    val tsSec: Long,
)

/** Parses one JSONL line into an [AgentEvent]; returns null for malformed/partial/non-JSON lines. */
class AgentEventParser {
    private val json = Json {
        ignoreUnknownKeys = true   // tolerate future fields (v, event, window, …)
        isLenient = true
        coerceInputValues = true   // a null prompt coerces to []
    }

    fun parse(line: String): AgentEvent? {
        val t = line.trim()
        if (t.isEmpty() || !t.startsWith("{")) return null
        return runCatching {
            val dto = json.decodeFromString(AgentEventDto.serializer(), t)
            val reason = dto.reason?.takeIf { it.isNotEmpty() } ?: return null
            AgentEvent(
                reason = reason,
                instance = dto.instance,
                socket = dto.socket,
                target = dto.target,
                session = dto.session,
                windowName = dto.windowName,
                cwd = dto.cwd,
                cmd = dto.cmd,
                prompt = dto.prompt,
                tsSec = dto.ts,
            )
        }.getOrNull()
    }
}
