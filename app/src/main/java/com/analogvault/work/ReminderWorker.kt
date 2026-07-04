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
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.analogvault.MainActivity
import com.analogvault.R
import com.analogvault.data.repo.VaultRepository
import com.analogvault.util.daysUntilDate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// ── Scheduling ────────────────────────────────────────────────────────────────

object Reminders {
    const val WORK_NAME = "analogvault_reminders"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(4, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

// ── Worker ────────────────────────────────────────────────────────────────────

/**
 * Daily housekeeping check. Notifies about:
 *  - film stock expiring within [EXPIRY_WINDOW_DAYS] (or already expired)
 *  - finished rolls sitting undeveloped for more than [UNDEVELOPED_DAYS]
 *    (latent image degrades over time)
 *  - chemistry mixed more than [CHEMICAL_AGE_DAYS] ago (age kills mixed
 *    chemistry independently of the roll-count cap tracked in-app)
 *
 * Anti-nag: last-notified dates per item are stored in the settings table;
 * an item is re-notified at most every [RENOTIFY_DAYS].
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: VaultRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            if (repo.getSetting("reminders_enabled") != "true") return Result.success()

            if (repo.getSetting("remind_expiry") != "false") checkExpiry()
            if (repo.getSetting("remind_undeveloped") != "false") checkUndeveloped()
            if (repo.getSetting("remind_chemicals") != "false") checkChemicals()
        } catch (_: Exception) {
            // best-effort housekeeping — never fail/retry-storm over it
        }
        return Result.success()
    }

    private suspend fun checkExpiry() {
        val films = repo.films.first().filter { it.quantity > 0 && it.expiryDate.isNotBlank() }
        val due = films.filter { film ->
            val days = daysUntilDate(film.expiryDate) ?: return@filter false
            days <= EXPIRY_WINDOW_DAYS && shouldNotify("expiry", film.id)
        }
        if (due.isEmpty()) return
        val expired = due.filter { (daysUntilDate(it.expiryDate) ?: 0) < 0 }
        val text = due.joinToString("; ") { f ->
            val d = daysUntilDate(f.expiryDate) ?: 0
            "${f.name}${if (f.quantity > 1) " ×${f.quantity}" else ""} " +
                if (d < 0) "(expired)" else "(in ${d}d)"
        }
        notify(
            NOTIF_EXPIRY,
            if (expired.isNotEmpty()) "Film in your stash has expired" else "Film expiring soon",
            text
        )
        due.forEach { markNotified("expiry", it.id) }
    }

    private suspend fun checkUndeveloped() {
        val rolls = repo.rolls.first().filter { it.finished && !it.developed && it.startDate.isNotBlank() }
        val films = repo.films.first()
        val due = rolls.filter { roll ->
            val age = -(daysUntilDate(roll.startDate) ?: 0)
            age >= UNDEVELOPED_DAYS && shouldNotify("undev", roll.id)
        }
        if (due.isEmpty()) return
        val text = due.joinToString("; ") { roll ->
            val name = films.find { it.id == roll.filmId }?.name ?: "Unknown film"
            "$name (loaded ${roll.startDate})"
        }
        notify(
            NOTIF_UNDEV,
            "${due.size} finished roll${if (due.size != 1) "s" else ""} awaiting development",
            "$text — the latent image fades over time"
        )
        due.forEach { markNotified("undev", it.id) }
    }

    private suspend fun checkChemicals() {
        val due = repo.chemicals.first().filter { chem ->
            chem.mixDate.isNotBlank() &&
                -(daysUntilDate(chem.mixDate) ?: 0) >= CHEMICAL_AGE_DAYS &&
                shouldNotify("chem", chem.id)
        }
        if (due.isEmpty()) return
        val text = due.joinToString("; ") { "${it.name} (mixed ${it.mixDate})" }
        notify(NOTIF_CHEM, "Ageing chemistry", "$text — mixed chemistry degrades with age")
        due.forEach { markNotified("chem", it.id) }
    }

    // ── Anti-nag bookkeeping (settings table: type → {id: epochDay}) ─────────

    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, Long>>() {}.type

    private suspend fun notifiedMap(type: String): MutableMap<String, Long> =
        repo.getSetting("notified_$type")?.let {
            try { gson.fromJson<MutableMap<String, Long>>(it, mapType) } catch (_: Exception) { null }
        } ?: mutableMapOf()

    private fun epochDay(): Long = System.currentTimeMillis() / 86_400_000L

    private suspend fun shouldNotify(type: String, id: String): Boolean {
        val last = notifiedMap(type)[id] ?: return true
        return epochDay() - last >= RENOTIFY_DAYS
    }

    private suspend fun markNotified(type: String, id: String) {
        val map = notifiedMap(type)
        map[id] = epochDay()
        repo.setSetting("notified_$type", gson.toJson(map))
    }

    // ── Notification plumbing ────────────────────────────────────────────────

    private fun notify(id: Int, title: String, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Film expiry, undeveloped rolls and chemistry age"
                }
            )
        }
        val contentIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, notification)
    }

    private companion object {
        const val CHANNEL_ID = "reminders"
        const val NOTIF_EXPIRY = 1001
        const val NOTIF_UNDEV  = 1002
        const val NOTIF_CHEM   = 1003
        const val EXPIRY_WINDOW_DAYS = 60
        const val UNDEVELOPED_DAYS   = 21
        const val CHEMICAL_AGE_DAYS  = 60
        const val RENOTIFY_DAYS      = 14L
    }
}
