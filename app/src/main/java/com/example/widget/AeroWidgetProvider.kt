package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.example.R
import com.example.MainActivity
import com.example.service.AudioService

/**
 * AeroPlayer Home Screen Widget Provider.
 * Connects the player background services to a stunning Frutiger-Aero glassmorphic widget.
 */
class AeroWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val TAG = "AeroWidgetProvider"

        /**
         * Triggers a widget frame refresh on all active instances around the device launcher.
         */
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, AeroWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                for (widgetId in allWidgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing widgets", e)
            }
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("aero_player_prefs", Context.MODE_PRIVATE)
            val title = prefs.getString("widget_title", "Ninguna canción") ?: "Ninguna canción"
            val artist = prefs.getString("widget_artist", "Presione Reproducir") ?: "Presione Reproducir"
            val coverPath = prefs.getString("widget_cover_path", null)
            val isPlaying = prefs.getBoolean("widget_is_playing", false)
            val currentTheme = prefs.getString("selected_theme", "azul") ?: "azul"

            // Construct RemoteViews template
            val views = RemoteViews(context.packageName, R.layout.aero_widget)

            // 1. Assign Song Descriptors
            views.setTextViewText(R.id.widget_track_title, title)
            views.setTextViewText(R.id.widget_track_artist, artist)

            // 2. Dynamic Aero Theming (Syncs with in-app appearance setting!)
            val bgRes = when (currentTheme) {
                "claro" -> R.drawable.bg_aero_widget_claro
                "verde" -> R.drawable.bg_aero_widget_verde
                "gris_oscuro" -> R.drawable.bg_aero_widget_gris_oscuro
                else -> R.drawable.bg_aero_widget_azul
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

            // Apply high-contrast text color combinations matching the theme styles
            val titleColor = if (currentTheme == "claro") 0xFF003C71.toInt() else 0xFFFFFFFF.toInt()
            val artistColor = when (currentTheme) {
                "claro" -> 0xFF005DA3.toInt()
                "verde" -> 0xFF05B26F.toInt()
                "gris_oscuro" -> 0xFFC0D5E8.toInt()
                else -> 0xFF8AD4FF.toInt()
            }
            views.setTextColor(R.id.widget_track_title, titleColor)
            views.setTextColor(R.id.widget_track_artist, artistColor)

            // 3. Decode Album Art or fall back to high-gloss app logo
            if (!coverPath.isNullOrEmpty()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(coverPath)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_cover, bitmap)
                    } else {
                        views.setImageViewResource(R.id.widget_cover, R.drawable.ic_aero_logo)
                    }
                } catch (e: Exception) {
                    views.setImageViewResource(R.id.widget_cover, R.drawable.ic_aero_logo)
                }
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.ic_aero_logo)
            }

            // 4. Update Play / Pause Button Frame
            val playPauseIcon = if (isPlaying) R.drawable.ic_aero_pause else R.drawable.ic_aero_play
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

            // Apply button tint overlays to prevent contrast loss
            val btnTint = if (currentTheme == "claro") 0xFF005DA3.toInt() else 0xFFFFFFFF.toInt()
            views.setInt(R.id.widget_btn_prev, "setColorFilter", btnTint)
            views.setInt(R.id.widget_btn_play_pause, "setColorFilter", btnTint)
            views.setInt(R.id.widget_btn_next, "setColorFilter", btnTint)

            // 5. Connect Clicks to Audio Controls (Uses pending intents mapped directly to background service intents)
            val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // Click play-pause -> launches service command
            val intentPlay = Intent(context, AudioService::class.java).setAction(AudioService.ACTION_PLAY_PAUSE)
            val piPlay = PendingIntent.getService(context, 201, intentPlay, piFlags)
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, piPlay)

            // Click previous -> launches service command
            val intentPrev = Intent(context, AudioService::class.java).setAction(AudioService.ACTION_PREVIOUS)
            val piPrev = PendingIntent.getService(context, 202, intentPrev, piFlags)
            views.setOnClickPendingIntent(R.id.widget_btn_prev, piPrev)

            // Click next -> launches service command
            val intentNext = Intent(context, AudioService::class.java).setAction(AudioService.ACTION_NEXT)
            val piNext = PendingIntent.getService(context, 203, intentNext, piFlags)
            views.setOnClickPendingIntent(R.id.widget_btn_next, piNext)

            // Click cover art/descriptions -> open application screen!
            val intentOpen = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val piOpen = PendingIntent.getActivity(context, 204, intentOpen, piFlags)
            views.setOnClickPendingIntent(R.id.widget_cover, piOpen)
            views.setOnClickPendingIntent(R.id.widget_track_title, piOpen)
            views.setOnClickPendingIntent(R.id.widget_track_artist, piOpen)

            // Commit modifications to system panel
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
