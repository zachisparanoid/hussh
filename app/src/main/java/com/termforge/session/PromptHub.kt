package com.termforge.session

import com.termforge.transport.HostKeyDecision
import com.termforge.transport.HostKeyRequest
import com.termforge.transport.KbPrompt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interactive prompts a connection raises mid-handshake (host-key TOFU, keyboard-interactive,
 * ask-each-time password, key passphrase). Each carries a [CompletableDeferred] the UI completes.
 */
sealed interface SessionPrompt {
    val sessionId: String

    data class HostKey(
        override val sessionId: String,
        val request: HostKeyRequest,
        val response: CompletableDeferred<HostKeyDecision>,
    ) : SessionPrompt

    data class KeyboardInteractive(
        override val sessionId: String,
        val name: String,
        val instruction: String,
        val prompts: List<KbPrompt>,
        val response: CompletableDeferred<List<String>?>,
    ) : SessionPrompt

    data class Password(
        override val sessionId: String,
        val label: String,
        val response: CompletableDeferred<String?>,
    ) : SessionPrompt

    data class Passphrase(
        override val sessionId: String,
        val keyName: String,
        val response: CompletableDeferred<String?>,
    ) : SessionPrompt
}

/**
 * Decouples the background transport layer from the UI: connections call the `request…` suspend
 * functions, which emit a [SessionPrompt] and await the user's answer. The active screen collects
 * [prompts] and completes the deferred.
 */
@Singleton
class PromptHub @Inject constructor() {
    private val _prompts = MutableSharedFlow<SessionPrompt>(extraBufferCapacity = 16)
    val prompts = _prompts.asSharedFlow()

    suspend fun requestHostKey(sessionId: String, request: HostKeyRequest): HostKeyDecision {
        val d = CompletableDeferred<HostKeyDecision>()
        _prompts.emit(SessionPrompt.HostKey(sessionId, request, d))
        return d.await()
    }

    suspend fun requestKeyboardInteractive(
        sessionId: String, name: String, instruction: String, prompts: List<KbPrompt>,
    ): List<String>? {
        val d = CompletableDeferred<List<String>?>()
        _prompts.emit(SessionPrompt.KeyboardInteractive(sessionId, name, instruction, prompts, d))
        return d.await()
    }

    suspend fun requestPassword(sessionId: String, label: String): String? {
        val d = CompletableDeferred<String?>()
        _prompts.emit(SessionPrompt.Password(sessionId, label, d))
        return d.await()
    }

    suspend fun requestPassphrase(sessionId: String, keyName: String): String? {
        val d = CompletableDeferred<String?>()
        _prompts.emit(SessionPrompt.Passphrase(sessionId, keyName, d))
        return d.await()
    }
}
