package org.vibevoyager.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat

class NavigationService : Service() {

    companion object {
        private const val CHANNEL_ID = "voyagr_navigation"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()

        VoyagrLogger.log(
            this,
            "VOYAGR_SERVICE",
            "NavigationService created pid=${Process.myPid()}"
        )

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        VoyagrLogger.log(
            this,
            "VOYAGR_SERVICE",
            "NavigationService started pid=${Process.myPid()} " +
                    "flags=$flags startId=$startId"
        )

        val openAppIntent = Intent(
            this,
            MainActivity::class.java
        )

        VoyagrLogger.log(
            this,
            "VOYAGR_SERVICE",
            "Notification content intent " +
                    "action=${openAppIntent.action} " +
                    "flags=0x${Integer.toHexString(openAppIntent.flags)}"
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Voyagr navigation active")
            .setContentText("Location and navigation are running")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        VoyagrLogger.log(
            this,
            "VOYAGR_SERVICE",
            "NavigationService entered foreground"
        )

        return START_STICKY
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        VoyagrLogger.log(
            this,
            "VOYAGR_SERVICE",
            "NavigationService onTrimMemory level=$level pid=${Process.myPid()}"
        )
    }

    override fun onDestroy() {

        VoyagrLogger.log(
            this,
            "VOYAGR_SERVICE",
            "NavigationService destroyed pid=${Process.myPid()}"
        )

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voyagr Navigation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Voyagr navigation active"
            }

            val manager = getSystemService(
                NotificationManager::class.java
            )

            manager.createNotificationChannel(channel)

            VoyagrLogger.log(
                this,
                "VOYAGR_SERVICE",
                "Navigation notification channel ready"
            )
        }
    }
}