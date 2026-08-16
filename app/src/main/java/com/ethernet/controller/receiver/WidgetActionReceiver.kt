package com.ethernet.controller.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.ethernet.controller.MainActivity
import com.ethernet.controller.data.ProfileRepository
import com.ethernet.controller.service.EthernetAutomationService
import com.ethernet.controller.util.EthernetUtils
import com.ethernet.controller.widget.EthernetAppWidget

class WidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_APPLY_PROFILE = "com.ethernet.controller.APPLY_PROFILE"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_APPLY_PROFILE) {
            val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
            val repo = ProfileRepository(context)
            val profile = repo.getProfileById(profileId) ?: return

            // Edge Case 1: Check Accessibility Service
            if (!EthernetAutomationService.isServiceRunning()) {
                Toast.makeText(
                    context,
                    "⚠️ Servizio di Accessibilità non attivo!",
                    Toast.LENGTH_LONG
                ).show()

                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_ERROR_TYPE, MainActivity.ERROR_TYPE_ACCESSIBILITY)
                }
                context.startActivity(appIntent)
                return
            }

            // Edge Case 2: Check if requested profile is ALREADY active
            val activeId = repo.getActiveProfileId()
            val ethInfo = EthernetUtils.getEthernetInfo(context)

            val isSameProfileSelected = (activeId == profile.id)
            val isSameStaticIpActive = (!profile.isDhcp && ethInfo.ip == profile.ip && ethInfo.isConnected)

            if (isSameProfileSelected || isSameStaticIpActive) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        context.applicationContext,
                        "✓ Profilo \"${profile.name}\" già attivo!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                repo.setActiveProfileId(profile.id)
                EthernetAppWidget.updateAllWidgets(context)
                return
            }

            // Start Automation
            EthernetAutomationService.startAutomation(context, profile)
        }
    }
}
