package com.scoot.transit.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.scoot.transit.MainActivity
import com.scoot.transit.R
import com.scoot.transit.data.db.PresetDao
import com.scoot.transit.data.db.PresetEntity
import com.scoot.transit.ui.ACTION_OPEN_PRESET
import com.scoot.transit.ui.EXTRA_PRESET_ID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Mirrors [PresetEntity]s as Android dynamic launcher shortcuts so the user can long-press the
 * launcher icon and tap "Home -> Work" for one-tap planning.
 */
@Singleton
class PresetShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presets: PresetDao,
) {
    suspend fun rebuildFromCurrentPresets() {
        val list = presets.observe().first().take(MAX_DYNAMIC_SHORTCUTS)
        val shortcuts = list.map { it.toShortcut(context) }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun PresetEntity.toShortcut(ctx: Context): ShortcutInfoCompat {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = ACTION_OPEN_PRESET
            putExtra(EXTRA_PRESET_ID, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return ShortcutInfoCompat.Builder(ctx, "preset-$id")
            .setShortLabel(label.take(10))
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_launcher_foreground))
            .setIntent(intent)
            .build()
    }

    companion object {
        private val MAX_DYNAMIC_SHORTCUTS get() = if (Build.VERSION.SDK_INT >= 25) 4 else 0
    }
}
