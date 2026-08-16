package com.ethernet.controller.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ethernet.controller.MainActivity
import com.ethernet.controller.R
import com.ethernet.controller.data.ProfileRepository
import com.ethernet.controller.receiver.WidgetActionReceiver

class EthernetAppWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_ethernet)
            val repo = ProfileRepository(context)

            // Open Main App Intent
            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, appPendingIntent)

            // Active Profile Badge
            val activeId = repo.getActiveProfileId()
            val activeProfile = activeId?.let { repo.getProfileById(it) }
            if (activeProfile != null) {
                views.setTextViewText(R.id.widget_status_text, "Attivo: ${activeProfile.name}")
            } else {
                views.setTextViewText(R.id.widget_status_text, "Pronto")
            }

            // Bind ONT OF Button
            val ofIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_APPLY_PROFILE
                putExtra(WidgetActionReceiver.EXTRA_PROFILE_ID, ProfileRepository.DEFAULT_PROFILE_OF.id)
            }
            val ofPendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                ofIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_of, ofPendingIntent)

            // Bind ONT SKY Button
            val skyIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_APPLY_PROFILE
                putExtra(WidgetActionReceiver.EXTRA_PROFILE_ID, ProfileRepository.DEFAULT_PROFILE_SKY.id)
            }
            val skyPendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                skyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_sky, skyPendingIntent)

            // Bind DHCP Button
            val dhcpIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_APPLY_PROFILE
                putExtra(WidgetActionReceiver.EXTRA_PROFILE_ID, ProfileRepository.DEFAULT_PROFILE_DHCP.id)
            }
            val dhcpPendingIntent = PendingIntent.getBroadcast(
                context,
                103,
                dhcpIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_dhcp, dhcpPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, EthernetAppWidget::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                for (widgetId in allWidgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
