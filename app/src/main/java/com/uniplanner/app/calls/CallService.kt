package com.uniplanner.app.calls

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.uniplanner.app.MainActivity
import com.uniplanner.app.R

/**
 * Keeps a call going when the student leaves the app, with the ongoing-call notification Android
 * asks for. It declares the microphone, the camera when it is on, and screen capture when shared.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        val screen = intent?.getBooleanExtra(EXTRA_SCREEN, false) == true
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val name = CallManager.call.value?.title.orEmpty()
        val notification = NotificationCompat.Builder(this, CallManager.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name.ifBlank { getString(R.string.app_name) })
            .setContentText(getString(R.string.call_ongoing))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        var types = 0
        if (Build.VERSION.SDK_INT >= 30) {
            if (granted(Manifest.permission.RECORD_AUDIO)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (video && granted(Manifest.permission.CAMERA)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (Build.VERSION.SDK_INT >= 29 && screen) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        runCatching { ServiceCompat.startForeground(this, ID, notification, types) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val ID = 77_002
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_SCREEN = "screen"

        fun start(context: Context, video: Boolean, screen: Boolean = false) {
            val i = Intent(context, CallService::class.java).putExtra(EXTRA_VIDEO, video).putExtra(EXTRA_SCREEN, screen)
            runCatching { ContextCompat.startForegroundService(context, i) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
