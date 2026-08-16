package com.ethernet.controller.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.ethernet.controller.data.ProfileRepository
import com.ethernet.controller.model.EthernetProfile
import com.ethernet.controller.widget.EthernetAppWidget
import kotlinx.coroutines.*

class EthernetAutomationService : AccessibilityService() {

    companion object {
        private const val TAG = "EthAuto"
        var instance: EthernetAutomationService? = null
        var targetProfile: EthernetProfile? = null
        var isAutomating = false

        fun isServiceRunning(): Boolean = instance != null

        fun stopAutomation() {
            if (isAutomating) {
                Log.d(TAG, "Automation explicitly stopped")
                isAutomating = false
                instance?.cancelLoop("Manuale")
            }
        }

        fun startAutomation(context: Context, profile: EthernetProfile) {
            Log.d(TAG, "=== startAutomation: ${profile.name} (IP: ${profile.ip}, DHCP: ${profile.isDhcp}) ===")
            targetProfile = profile
            isAutomating = true

            val openIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            if (instance != null) {
                instance?.startActivity(openIntent)
                instance?.startControlledLoop(profile)
            } else {
                context.startActivity(openIntent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var loopJob: Job? = null

    // State Tracking
    private var switchDisabledBeforeEdit = false
    private var dialogOpened = false
    private var staticExpanded = false
    private var formPopulated = false
    private var saveExecuted = false
    private var switchReEnabled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "onServiceConnected: Ready")
        if (isAutomating && targetProfile != null) {
            startControlledLoop(targetProfile!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "onDestroy")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We rely on the controlled coroutine loop for safe and steady timing
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
        cancelLoop("Interrupted")
    }

    fun cancelLoop(reason: String) {
        isAutomating = false
        loopJob?.cancel()
        Log.d(TAG, "Automation cancelled: $reason")
    }

    fun startControlledLoop(profile: EthernetProfile) {
        Log.d(TAG, ">>> startControlledLoop initiated for ${profile.name}")
        switchDisabledBeforeEdit = false
        dialogOpened = false
        staticExpanded = false
        formPopulated = false
        saveExecuted = false
        switchReEnabled = false

        loopJob?.cancel()
        loopJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            val timeoutMs = 15000L

            while (isActive && isAutomating && (System.currentTimeMillis() - startTime < timeoutMs)) {
                val root = getTopActiveRoot()
                if (root != null) {
                    handleScreen(root, profile)
                }
                if (!isAutomating) break
                delay(180) // Pacing: 180ms between ticks for hardware & UI stability
            }

            if (isAutomating) {
                Log.w(TAG, "Automation watchdog finished (timeout)")
                isAutomating = false
                // SAFETY: If we disabled the switch and never re-enabled it, try to recover
                if (switchDisabledBeforeEdit && !switchReEnabled) {
                    Log.e(TAG, "!!! SAFETY RECOVERY: Switch was disabled but never re-enabled! Attempting recovery...")
                    recoverEthernetSwitch()
                }
            }
        }
    }

    /**
     * SAFETY NET: If automation fails or times out, ensure the Ethernet switch is NOT left disabled.
     * This prevents bricking the Ethernet adapter on Samsung devices.
     */
    private suspend fun recoverEthernetSwitch() {
        for (attempt in 1..5) {
            val root = getTopActiveRoot() ?: continue
            val allTexts = mutableListOf<String>()
            collectTexts(root, allTexts)

            val isEthernetScreen = allTexts.any { it.contains("Configura dispositivo Ethernet", ignoreCase = true) }
            if (isEthernetScreen) {
                val switchNode = findSwitchNode(root)
                if (switchNode != null && !switchNode.isChecked) {
                    Log.d(TAG, "SAFETY RECOVERY: Re-enabling Ethernet switch (attempt $attempt)")
                    clickNode(switchNode)
                    switchReEnabled = true
                    delay(300)
                    return
                } else if (switchNode != null && switchNode.isChecked) {
                    Log.d(TAG, "SAFETY RECOVERY: Switch already ON, no recovery needed")
                    return
                }
            }
            delay(300)
        }
        Log.e(TAG, "SAFETY RECOVERY: Could not find switch to re-enable! User may need to manually toggle.")
    }

    private fun getTopActiveRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return it }
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                w.root?.let { return it }
            }
        }
        return null
    }

    private suspend fun handleScreen(node: AccessibilityNodeInfo, profile: EthernetProfile) {
        var root = node
        while (root.parent != null) root = root.parent

        val allTexts = mutableListOf<String>()
        collectTexts(root, allTexts)

        // ========================================================
        // 1. DIALOG "Configura dispositivo Ethernet"
        // ========================================================
        val isConfigDialog = findNodeById(root, "com.android.settings:id/outerlayout") != null ||
                findSaveButton(root) != null ||
                allTexts.any { it.contains("Seleziona dispositivo Ethernet", ignoreCase = true) }

        if (isConfigDialog) {
            if (profile.isDhcp) {
                val dhcpNode = findNodeById(root, "com.android.settings:id/dhcp_radio")
                    ?: findNodeContainingText(root, "DHCP")

                if (dhcpNode != null && dhcpNode.isChecked) {
                    Log.d(TAG, "SceneAware: DHCP already checked, closing via Scarta...")
                    val cancelBtn = findCancelButton(root)
                    if (cancelBtn != null) clickNode(cancelBtn) else performGlobalAction(GLOBAL_ACTION_BACK)
                    saveExecuted = true
                    delay(150)
                    return
                }

                if (dhcpNode != null) {
                    Log.d(TAG, "SceneAware: Selecting DHCP Radio...")
                    clickNode(dhcpNode)
                    dhcpNode.performAction(AccessibilityNodeInfo.ACTION_SELECT)
                    delay(150)
                }

                val saveBtn = findSaveButton(root)
                if (saveBtn != null) {
                    Log.d(TAG, "SceneAware: Saving DHCP configuration...")
                    clickNode(saveBtn)
                    saveExecuted = true
                    delay(200)
                }
                return
            } else {
                // STATIC IP
                val editTexts = mutableListOf<AccessibilityNodeInfo>()
                collectEditTexts(root, editTexts)

                val ipNode = findNodeById(root, "com.android.settings:id/ipaddr_edit") ?: editTexts.getOrNull(0)
                val maskNode = findNodeById(root, "com.android.settings:id/netmask_edit") ?: editTexts.getOrNull(1)
                val dnsNode = findNodeById(root, "com.android.settings:id/eth_dns_edit") ?: editTexts.getOrNull(2)
                val gwNode = findNodeById(root, "com.android.settings:id/eth_gw_edit") ?: editTexts.getOrNull(3)

                // Step A: Expand fields if not yet visible
                if (ipNode == null) {
                    val dhcpNode = findNodeById(root, "com.android.settings:id/dhcp_radio")
                        ?: findNodeContainingText(root, "DHCP")
                    val manualNode = findNodeById(root, "com.android.settings:id/manual_radio")
                        ?: findNodeContainingText(root, "IP statico")
                        ?: findNodeContainingText(root, "Static IP")

                    if (!staticExpanded) {
                        if (dhcpNode != null) {
                            clickNode(dhcpNode)
                            dhcpNode.performAction(AccessibilityNodeInfo.ACTION_SELECT)
                            delay(120)
                        }
                        if (manualNode != null) {
                            clickNode(manualNode)
                            manualNode.performAction(AccessibilityNodeInfo.ACTION_SELECT)
                            delay(120)
                        }
                        staticExpanded = true
                        closeKeyboardIfOpen()
                        delay(100)
                    }
                    return
                }

                // Step B: Check if already matching
                val currentIp = ipNode.text?.toString()?.trim()
                val currentMask = maskNode?.text?.toString()?.trim()
                val currentDns = dnsNode?.text?.toString()?.trim()
                val currentGw = gwNode?.text?.toString()?.trim()

                if (currentIp == profile.ip && currentMask == profile.netmask &&
                    currentDns == profile.dns && currentGw == profile.gateway) {
                    Log.d(TAG, "SceneAware: Profile values already active ($currentIp), closing dialog...")
                    val cancelBtn = findCancelButton(root)
                    if (cancelBtn != null) clickNode(cancelBtn) else performGlobalAction(GLOBAL_ACTION_BACK)
                    // Mark save as done so we proceed to re-enable switch on Ethernet screen
                    saveExecuted = true
                    delay(300) // Wait for dialog close animation
                    return
                }

                // Step C: Populate fields
                if (!formPopulated) {
                    if (currentIp != profile.ip) setText(ipNode, profile.ip)
                    if (maskNode != null && currentMask != profile.netmask) setText(maskNode, profile.netmask)
                    if (dnsNode != null && currentDns != profile.dns) setText(dnsNode, profile.dns)
                    if (gwNode != null && currentGw != profile.gateway) setText(gwNode, profile.gateway)

                    closeKeyboardIfOpen()
                    formPopulated = true
                    Log.d(TAG, "SceneAware: Populated fields for IP=${profile.ip}")
                    delay(150)
                }

                // Step D: Save
                val saveBtn = findSaveButton(root)
                if (saveBtn != null) {
                    Log.d(TAG, "SceneAware: Clicking Save for IP=${profile.ip}...")
                    clickNode(saveBtn)
                    saveExecuted = true
                    delay(200)
                }
            }
            return
        }

        // ========================================================
        // 2. ETHERNET SETTINGS SCREEN
        // ========================================================
        val isEthernetScreen = allTexts.any { it.contains("Configura dispositivo Ethernet", ignoreCase = true) }

        if (isEthernetScreen) {
            val switchNode = findSwitchNode(root)
            val isSummaryActive = allTexts.any { it.equals("Attivato", ignoreCase = true) || it.equals("Enabled", ignoreCase = true) || it.equals("On", ignoreCase = true) }
            val isSummaryDisabled = allTexts.any { it.equals("Disattivato", ignoreCase = true) || it.equals("Disabled", ignoreCase = true) || it.equals("Off", ignoreCase = true) }
            val isSwitchCurrentlyOn = (switchNode != null && switchNode.isChecked) || isSummaryActive

            if (saveExecuted) {
                // ========================================================
                // MANDATORY END-OF-SEQUENCE RULE: ETHERNET MUST BE ON!
                // ========================================================
                if (!isSwitchCurrentlyOn || (switchNode != null && !switchNode.isChecked) || isSummaryDisabled) {
                    Log.d(TAG, "SceneAware: [END CHECK] Ethernet is OFF! Turning switch ON now...")
                    if (switchNode != null) {
                        clickNode(switchNode)
                    } else {
                        // Fallback click on the Ethernet row
                        val ethItem = findNodeContainingText(root, "Ethernet")
                        if (ethItem != null) clickNode(ethItem)
                    }
                    delay(600) // Give hardware PHY time to bind and update UI
                    return // Loop again to strictly verify that switch is now ON
                }

                // If we reach here, Ethernet switch is CONFIRMED ON!
                switchReEnabled = true
                Log.d(TAG, "SceneAware: [END CHECK] ✓ Verified Ethernet is ON. Completing sequence.")
                finishAutomation(profile)
                return
            } else {
                // Pre-edit: Turn Ethernet switch OFF if currently enabled (required for editing on One UI)
                if (isSwitchCurrentlyOn && !switchDisabledBeforeEdit) {
                    Log.d(TAG, "SceneAware: Disabling Ethernet switch before configuring...")
                    if (switchNode != null) clickNode(switchNode) else findNodeContainingText(root, "Ethernet")?.let { clickNode(it) }
                    switchDisabledBeforeEdit = true
                    delay(300) // Allow adapter PHY to unbind cleanly
                    return
                }

                // Click "Configura dispositivo Ethernet"
                if (!dialogOpened) {
                    val configItem = findNodeContainingText(root, "Configura dispositivo Ethernet")
                        ?: findNodeContainingText(root, "Configure Ethernet")

                    if (configItem != null) {
                        Log.d(TAG, "SceneAware: Opening configuration dialog...")
                        clickNode(configItem)
                        dialogOpened = true
                        delay(250)
                        return
                    }
                }
            }
            return
        }

        // ========================================================
        // 3. "Altre impostazioni di rete" SCREEN
        // ========================================================
        val isMoreSettingsScreen = allTexts.any { it.equals("Altre impostazioni di rete", ignoreCase = true) } &&
                allTexts.any { it.contains("VPN", ignoreCase = true) || it.contains("DNS privato", ignoreCase = true) }

        if (isMoreSettingsScreen) {
            val ethRow = findNodeExactText(root, "Ethernet")
            if (ethRow != null) {
                Log.d(TAG, "SceneAware: Clicking Ethernet in More Settings...")
                clickNode(ethRow)
                delay(200)
                return
            }
        }

        // ========================================================
        // 4. MAIN "Connessioni" SCREEN
        // ========================================================
        val moreSettingsNode = findNodeContainingText(root, "Altre impostazioni di rete")
            ?: findNodeContainingText(root, "More connection settings")

        if (moreSettingsNode != null) {
            Log.d(TAG, "SceneAware: Clicking More connection settings in Connections...")
            clickNode(moreSettingsNode)
            delay(200)
            return
        }
    }

    private fun finishAutomation(profile: EthernetProfile) {
        // FINAL SAFETY CHECK: Never finish with switch left disabled
        if (switchDisabledBeforeEdit && !switchReEnabled) {
            Log.e(TAG, "!!! finishAutomation called but switch still disabled! Aborting finish to prevent bricking.")
            // Don't finish - let the loop continue to try re-enabling
            return
        }

        Log.d(TAG, "=== FINISH AUTOMATION: SUCCESS FOR ${profile.name} ===")
        isAutomating = false
        loopJob?.cancel()

        val repo = ProfileRepository(applicationContext)
        repo.setActiveProfileId(profile.id)
        EthernetAppWidget.updateAllWidgets(applicationContext)

        Toast.makeText(
            applicationContext,
            "✓ Profilo \"${profile.name}\" attivato!",
            Toast.LENGTH_SHORT
        ).show()

        // Guaranteed return to Home screen
        val homeSuccess = performGlobalAction(GLOBAL_ACTION_HOME)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
        } catch (_: Exception) {}
    }

    private fun collectEditTexts(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            collectEditTexts(node.getChild(i), list)
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) list.add(text)
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc != text) list.add(desc)
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), list)
        }
    }

    private fun findSaveButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = root.findAccessibilityNodeInfosByViewId("android:id/button1")
        if (!byId.isNullOrEmpty()) return byId[0]
        return findNodeExactText(root, "Salva")
            ?: findNodeExactText(root, "Save")
            ?: findNodeContainingText(root, "Salva")
            ?: findNodeContainingText(root, "Save")
    }

    private fun findCancelButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = root.findAccessibilityNodeInfosByViewId("android:id/button2")
        if (!byId.isNullOrEmpty()) return byId[0]
        return findNodeExactText(root, "Scarta")
            ?: findNodeExactText(root, "Annulla")
            ?: findNodeExactText(root, "Cancel")
            ?: findNodeContainingText(root, "Scarta")
            ?: findNodeContainingText(root, "Annulla")
    }

    private fun findNodeExactText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val nodeText = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (nodeText.equals(text, ignoreCase = true) || desc.equals(text, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val res = findNodeExactText(node.getChild(i), text)
            if (res != null) return res
        }
        return null
    }

    private fun findNodeContainingText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val nodeText = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if ((nodeText != null && nodeText.contains(text, ignoreCase = true)) ||
            (desc != null && desc.contains(text, ignoreCase = true))) {
            return node
        }
        for (i in 0 until node.childCount) {
            val res = findNodeContainingText(node.getChild(i), text)
            if (res != null) return res
        }
        return null
    }

    private fun findNodeById(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val list = root.findAccessibilityNodeInfosByViewId(viewId)
        return list?.firstOrNull()
    }

    private fun findSwitchNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = root.findAccessibilityNodeInfosByViewId("android:id/switch_widget")
        if (!byId.isNullOrEmpty()) return byId[0]

        val bySettingsId = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/switch_widget")
        if (!bySettingsId.isNullOrEmpty()) return bySettingsId[0]

        return findFirstSwitchNode(root)
    }

    private fun findFirstSwitchNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = root.className?.toString() ?: ""
        if (className.contains("Switch", ignoreCase = true) || root.isCheckable) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findFirstSwitchNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun setText(node: AccessibilityNodeInfo?, text: String) {
        if (node == null) return
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun closeKeyboardIfOpen() {
        for (w in windows) {
            if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                Log.d(TAG, "SceneAware: Soft keyboard detected, dismissing...")
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }
    }
}
