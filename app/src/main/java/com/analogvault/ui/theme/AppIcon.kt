package com.analogvault.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.analogvault.R

/**
 * The launcher icons, one per palette: the same film strip, its three frames
 * in that palette's live, action and waiting colours on its void.
 *
 * Each is an `<activity-alias>` in the manifest pointing at VaultActivity.
 * [alias] is its class name there — fixed strings, since a launcher keeps
 * shortcuts by component name. An icon is offered once its palette is
 * unlocked, so a secret's icon stays as hidden as its palette.
 */
enum class AppIcon(
    val palette: Palette,
    val alias: String,
    @DrawableRes val foreground: Int,
) {
    // The old activity name, kept by the default alias: shortcuts made before
    // alternate icons still point at it.
    DYE_LAYER(Palette.DYE_LAYER, "com.analogvault.MainActivity", R.drawable.ic_launcher_foreground),
    LEGACY_AMBER(Palette.LEGACY_AMBER, "com.analogvault.IconLegacyAmber", R.drawable.ic_launcher_foreground_legacy_amber),
    EXPIRED(Palette.EXPIRED, "com.analogvault.IconExpired", R.drawable.ic_launcher_foreground_expired),
    CYANOTYPE(Palette.CYANOTYPE, "com.analogvault.IconCyanotype", R.drawable.ic_launcher_foreground_cyanotype),
    SLIDE(Palette.SLIDE, "com.analogvault.IconSlide", R.drawable.ic_launcher_foreground_slide),
    AEROCHROME(Palette.AEROCHROME, "com.analogvault.IconAerochrome", R.drawable.ic_launcher_foreground_aerochrome),
    ;

    val label: String get() = palette.label

    companion object {
        val DEFAULT = DYE_LAYER
    }
}

/**
 * Which alias the launcher shows. The package manager is the only record of
 * it: a setting could disagree with what is on the home screen.
 *
 * A choice is not applied on the spot. Disabling the alias the app was
 * launched through makes the system close the app's task, so the user would
 * be thrown out of Settings mid-tap. The choice waits in [pending] until the
 * user leaves the app (VaultActivity.onStop), when closing the task
 * costs nothing they can see. If the process dies before that, the choice is
 * lost and the picker shows the icon the launcher still has.
 */
object AppIconSwitcher {

    @Volatile private var pending: AppIcon? = null

    /** The icon the launcher will show once the user leaves the app. */
    fun shown(context: Context): AppIcon = pending ?: current(context)

    fun choose(context: Context, icon: AppIcon) {
        pending = icon.takeIf { it != current(context) }
    }

    /** Applies a waiting choice. Call only when the user is leaving the app. */
    fun applyPending(context: Context) {
        val icon = pending ?: return
        pending = null
        apply(context, icon)
    }

    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(ComponentName(context, icon.alias))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                // Not set yet: the manifest decides, and only the default is on.
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.DEFAULT
                else -> false
            }
        } ?: AppIcon.DEFAULT
    }

    /**
     * Shows [icon] in the launcher and hides the rest. The new alias is
     * enabled before the old is disabled, so there is never a moment with no
     * way back into the app. Going back to the default restores the manifest's
     * own state rather than pinning it, as if nothing had been changed.
     */
    private fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        val flags = PackageManager.DONT_KILL_APP
        fun set(i: AppIcon, state: Int) =
            pm.setComponentEnabledSetting(ComponentName(context, i.alias), state, flags)

        if (icon == AppIcon.DEFAULT) {
            AppIcon.entries.forEach { set(it, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) }
            return
        }
        set(icon, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        AppIcon.entries.filter { it != icon }
            .forEach { set(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }
    }
}
