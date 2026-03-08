package com.example.codexmobile.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.codexmobile.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TerminalSessionService : Service() {
    @Inject
    lateinit var terminalSessionManager: TerminalSessionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("Terminal session active"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val command = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
            ?: CommandGate.DEFAULT_PROFILE

        serviceScope.launch {
            when (action) {
                ACTION_OPEN -> terminalSessionManager.open(sessionId, profileId)
                ACTION_EXECUTE -> terminalSessionManager.execute(sessionId, command)
                ACTION_CLOSE -> {
                    terminalSessionManager.close(sessionId)
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(contentText: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Codex Terminal")
            .setContentText(contentText)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Terminal session",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_OPEN = "com.example.codexmobile.runtime.OPEN"
        const val ACTION_EXECUTE = "com.example.codexmobile.runtime.EXECUTE"
        const val ACTION_CLOSE = "com.example.codexmobile.runtime.CLOSE"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_COMMAND = "extra_command"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        private const val CHANNEL_ID = "terminal_session"
        private const val NOTIFICATION_ID = 1101
    }
}
