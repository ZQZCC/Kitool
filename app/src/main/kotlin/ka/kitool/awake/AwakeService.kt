package ka.kitool.awake

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import ka.kitool.R

class AwakeService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                stopKeepingAwake()
            }
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification =
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_awake)
                .setContentTitle(getString(R.string.action_keep_awake))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enabled = true
        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isInteractive) {
            stopKeepingAwake()
            return START_NOT_STICKY
        }
        acquireWakeLock(powerManager)
        return START_STICKY
    }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun acquireWakeLock(powerManager: PowerManager) {
        if (wakeLock?.isHeld == true) return
        wakeLock =
            powerManager
                .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply {
                    acquire()
                }
    }

    private fun stopKeepingAwake() {
        enabled = false
        stopSelf()
    }

    override fun onDestroy() {
        enabled = false
        unregisterReceiver(screenOffReceiver)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.action_keep_awake),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "keep_awake"
        private const val NOTIFICATION_ID = 1002
        private const val WAKE_LOCK_TAG = "ka.kitool:keep-awake"

        private var enabled = false

        internal val isEnabled: Boolean
            get() = enabled

        internal fun toggle(context: Context): Boolean = setEnabled(context, !enabled)

        internal fun setEnabled(context: Context, value: Boolean): Boolean {
            enabled = value
            val intent = Intent(context, AwakeService::class.java)
            if (value) {
                try {
                    context.startForegroundService(intent)
                } catch (_: RuntimeException) {
                    enabled = false
                }
            } else {
                context.stopService(intent)
            }
            return enabled
        }
    }
}
