package com.hussh.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import com.hussh.MainActivity
import com.hussh.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that owns the live connections' process lifetime. It does not hold the sessions
 * itself (the singleton [SessionManager] does) — its job is to keep the process alive while sockets
 * are open and to surface the persistent notification with a "Disconnect all" control. It stops
 * itself once no sessions remain.
 */
@AndroidEntryPoint
class SessionService : LifecycleService() {

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundWith(0)
        lifecycleScope.launch {
            sessionManager.sessions.collect { sessions ->
                if (sessions.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(sessions.size))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            sessionManager.disconnectAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWith(sessionManager.sessions.value.size)
        // START_STICKY so the OS recreates us; on relaunch the app offers to reconnect prior sessions.
        return START_STICKY
    }

    private fun startForegroundWith(count: Int) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(count), type)
    }

    private fun buildNotification(count: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1, Intent(this, SessionService::class.java).setAction(ACTION_DISCONNECT_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (count == 1) "1 active session" else "$count active sessions"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hussh")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Disconnect all", disconnectIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.session_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.session_notification_channel_desc) }
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun notificationManager() =
        getSystemService(NotificationManager::class.java)

    companion object {
        const val CHANNEL_ID = "hussh_sessions"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISCONNECT_ALL = "com.hussh.action.DISCONNECT_ALL"
    }
}
