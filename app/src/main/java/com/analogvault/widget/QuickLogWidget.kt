package com.analogvault.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.analogvault.MainActivity
import com.analogvault.data.model.Shot
import com.analogvault.data.repo.VaultRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// Widget code runs outside any Hilt-injected component — pull the repo via an entry point.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repo(): VaultRepository
}

class QuickLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogWidget()
}

/**
 * Home-screen frame counter for the active roll with a one-tap +1 button.
 * The widget path deliberately skips GPS/weather so it's instant and
 * permission-free; details can be edited later in the app.
 */
class QuickLogWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (label, counter) = try {
            val repo = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repo()
            val rolls = repo.rolls.first()
            val films = repo.films.first()
            val active = rolls.firstOrNull { !it.finished && !it.developed }
            if (active != null) {
                val name = films.find { it.id == active.filmId }?.name ?: "Active roll"
                name to "${active.shots.size}/${active.totalShots}"
            } else "No roll in camera" to ""
        } catch (_: Exception) {
            "Analog Vault" to ""
        }

        provideContent {
            Row(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(Color(0xFF181512)))
                    .cornerRadius(16.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(label, style = TextStyle(
                        color = ColorProvider(Color(0xFFE8DDD0)),
                        fontSize = 14.sp, fontWeight = FontWeight.Medium))
                    if (counter.isNotEmpty()) {
                        Text(counter, style = TextStyle(
                            color = ColorProvider(Color(0xFFD4935A)), fontSize = 12.sp))
                    }
                }
                if (counter.isNotEmpty()) {
                    Box(
                        modifier = GlanceModifier.size(44.dp)
                            .background(ColorProvider(Color(0xFF7A5030)))
                            .cornerRadius(22.dp)
                            .clickable(actionRunCallback<QuickLogAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+1", style = TextStyle(
                            color = ColorProvider(Color(0xFFE8DDD0)),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

class QuickLogAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try {
            val repo = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repo()
            val roll = repo.rolls.first().firstOrNull { !it.finished && !it.developed } ?: return
            val last = roll.shots.lastOrNull()
            val film = repo.films.first().find { it.id == roll.filmId }
            val shot = Shot(
                id       = UUID.randomUUID().toString(),
                shutter  = last?.shutter ?: "",
                aperture = last?.aperture ?: "",
                iso      = last?.iso ?: roll.pushIso.ifBlank { film?.iso?.toString() ?: "" },
                lens     = last?.lens ?: "",
                date     = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            )
            repo.upsertRoll(roll.copy(shots = roll.shots + shot))
        } catch (_: Exception) {
            // never crash the launcher over a widget tap
        }
        QuickLogWidget().update(context, glanceId)
    }
}
