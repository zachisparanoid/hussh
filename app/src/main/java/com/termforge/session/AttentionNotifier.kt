package com.termforge.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.termforge.MainActivity
import com.termforge.R
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

    private fun ensureChannel() {
        if (channelReady || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Agent attention", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Alerts when Claude Code on a connected host finishes or needs you." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        channelReady = true
    }

    companion object {
        const val CHANNEL_ID = "termforge_agent_attention"
        const val EXTRA_SESSION_ID = "com.termforge.extra.SESSION_ID"
    }
}
