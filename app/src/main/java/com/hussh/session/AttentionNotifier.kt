package com.hussh.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hussh.MainActivity
import com.hussh.R
import com.hussh.agent.AgentInstance
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the opt-in "Claude needs you" notification and routes a tap back to the originating session
 * (via an [EXTRA_SESSION_ID] deep link into [MainActivity]). One notification per session id, so two
 * sessions both needing attention show two distinct, individually-dismissable notifications.
 *
 * Separate from [SessionService]'s ongoing foreground notification — this is a transient, high-
 * priority alert on its own channel so the user can silence it independently.
 */
@Singleton
class AttentionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var channelReady = false

    /** @param title the session/host label shown as the notification body. */
    fun notifyAttention(sessionId: String, title: String) {
        ensureChannel()
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(
            context, sessionId.hashCode(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Claude needs you")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        // notify() throws SecurityException if POST_NOTIFICATIONS isn't granted (API 33+). MainActivity
        // requests it; guard so a denial never crashes a background terminal coroutine.
        runCatching { NotificationManagerCompat.from(context).notify(sessionId.hashCode(), notification) }
    }

    fun cancel(sessionId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(sessionId.hashCode()) }
    }

    /** Per-instance "Claude needs you" naming the specific tmux session/pane; tap deep-links to it. */
    fun notifyInstance(inst: AgentInstance) {
        ensureChannel()
        val id = inst.key.hashCode()
        val tap = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_INSTANCE_KEY, inst.key)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${inst.sessionName ?: "claude"} · ${phrase(inst.reason)}")
            .setContentText(inst.cwd ?: "")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
        if (inst.reason == "perm" && inst.routable) {
            builder.addAction(0, "Approve", actionIntent(AgentActionReceiver.ACTION_APPROVE, inst.key, id * 31 + 1))
            builder.addAction(0, "Deny", actionIntent(AgentActionReceiver.ACTION_DENY, inst.key, id * 31 + 2))
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    fun cancelInstance(inst: AgentInstance) {
        runCatching { NotificationManagerCompat.from(context).cancel(inst.key.hashCode()) }
    }

    private fun actionIntent(action: String, instanceKey: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AgentActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_INSTANCE_KEY, instanceKey)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun phrase(reason: String): String = when (reason) {
        "perm" -> "needs permission"
        "input" -> "waiting for input"
        "done" -> "finished"
        else -> "needs you"
    }

    private fun ensureChannel() {
        if (channelReady || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Agent attention", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Alerts when Claude Code on a connected host finishes or needs you." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        channelReady = true
    }

    companion object {
        const val CHANNEL_ID = "hussh_agent_attention"
        const val EXTRA_SESSION_ID = "com.hussh.extra.SESSION_ID"
        const val EXTRA_INSTANCE_KEY = "com.hussh.extra.INSTANCE_KEY"
    }
}
