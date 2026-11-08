package com.analogvault.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.analogvault.MainActivity
import com.analogvault.R
import com.analogvault.util.AchCategory
import com.analogvault.util.Achievement

/**
 * Whether the app is on screen. Set by MainActivity as it starts and stops,
 * and read when a badge is earned: on screen it gets a banner, off screen a
 * notification, never both.
 */
object AppVisibility {
    @Volatile var foreground: Boolean = false
}

/**
 * Posts "achievement earned" notifications, on a channel of their own so
 * they can be silenced without silencing the reminders.
 *
 * Only for badges earned while the app is in the background, which in
 * practice is a darkroom timer finishing with the phone in a pocket. Every
 * other badge is earned by doing something in the app, and gets the banner.
 */
object AchievementNotifier {
    const val CHANNEL_ID = "achievements"
    private const val NOTIFICATION_ID = 2001
    /** Intent extra asking MainActivity to open Stats → Badges. */
    const val EXTRA_OPEN_BADGES = "open_badges"

    fun post(context: Context, earned: List<Achievement>) {
        if (earned.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Achievements", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Badges earned while the app is in the background"
                }
            )
        }
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_BADGES, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val (title, text) = describe(earned)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Title and body; a secret is named once it is earned, but called a secret. */
    fun describe(earned: List<Achievement>): Pair<String, String> {
        val one = earned.singleOrNull()
        return when {
            one == null -> "${earned.size} achievements earned" to earned.joinToString(", ") { it.title }
            one.category == AchCategory.SECRET -> "Secret found: ${one.title}" to one.description
            else -> "Achievement earned: ${one.title}" to one.description
        }
    }
}
