package com.ethernet.controller

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ethernet.controller.adapter.ProfileAdapter
import com.ethernet.controller.data.ProfileRepository
import com.ethernet.controller.databinding.ActivityMainBinding
import com.ethernet.controller.databinding.DialogEditProfileBinding
import com.ethernet.controller.model.EthernetProfile
import com.ethernet.controller.service.EthernetAutomationService
import com.ethernet.controller.update.AppUpdateManager
import com.ethernet.controller.util.EthernetUtils
import com.ethernet.controller.widget.EthernetAppWidget
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ERROR_TYPE = "extra_error_type"
        const val ERROR_TYPE_ACCESSIBILITY = "error_accessibility"
        const val ERROR_TYPE_NO_ADAPTER = "error_no_adapter"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ProfileRepository
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProfileRepository(this)
        updateManager = AppUpdateManager(this)

        setupToolbar()
        setupRecyclerView()
        setupListeners()

        handleIncomingErrorIntent(intent)

        // Silent check for updates on startup
        updateManager.checkForUpdates(isManualCheck = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingErrorIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
        refreshEthernetStatus()
        refreshProfiles()
    }

    private fun handleIncomingErrorIntent(intent: Intent?) {
        val errorType = intent?.getStringExtra(EXTRA_ERROR_TYPE) ?: return

        when (errorType) {
            ERROR_TYPE_ACCESSIBILITY -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("⚠️ Accessibilità Non Attiva")
                    .setMessage("Per permettere al widget sulla Home di cambiare i parametri di rete in automatico, è necessario attivare il servizio \"Automazione Configurazione Ethernet\".\n\nVuoi aprirlo adesso?")
                    .setPositiveButton("Apri Impostazioni") { _, _ ->
                        openAccessibilitySettings()
                    }
                    .setNegativeButton("Chiudi", null)
                    .show()
            }
            ERROR_TYPE_NO_ADAPTER -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("⚠️ Adattatore Non Rilevato")
                    .setMessage("Nessun adattatore Ethernet USB è attualmente collegato al telefono.\n\nCollega l'adattatore OTG/USB Ethernet prima di cambiare profilo.")
                    .setPositiveButton("Ho Capito", null)
                    .show()
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    refreshEthernetStatus()
                    refreshAccessibilityStatus()
                    refreshProfiles()
                    Toast.makeText(this, "Stato aggiornato", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_check_updates -> {
                    updateManager.checkForUpdates(isManualCheck = true)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        profileAdapter = ProfileAdapter(
            profiles = emptyList(),
            onApplyClick = { profile -> applyProfile(profile) },
            onEditClick = { profile -> showEditProfileDialog(profile) },
            onDeleteClick = { profile -> confirmDeleteProfile(profile) }
        )
        binding.rvProfiles.layoutManager = LinearLayoutManager(this)
        binding.rvProfiles.adapter = profileAdapter
    }

    private fun setupListeners() {
        binding.btnEnableAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnAddProfile.setOnClickListener {
            showEditProfileDialog(null)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (EthernetAutomationService.isServiceRunning()) return true

        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName &&
                service.resolveInfo.serviceInfo.name == EthernetAutomationService::class.java.name
            ) {
                return true
            }
        }

        val prefString = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return prefString.contains(packageName)
    }

    private fun refreshAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled()

        if (isEnabled) {
            binding.cardAccessibilityStatus.setCardBackgroundColor(getColor(R.color.surface))
            binding.cardAccessibilityStatus.strokeColor = getColor(R.color.success)
            binding.ivAccIcon.setImageResource(R.drawable.ic_check)
            binding.ivAccIcon.setColorFilter(getColor(R.color.success))
            binding.tvAccTitle.text = "✓ Servizio Accessibilità Attivo"
            binding.tvAccTitle.setTextColor(getColor(R.color.success))
            binding.tvAccDescription.text = "Il widget Home e l'app sono pronti a commutare i parametri Ethernet automaticamente."
            binding.btnEnableAccessibility.visibility = View.GONE
        } else {
            binding.cardAccessibilityStatus.setCardBackgroundColor(getColor(R.color.surface))
            binding.cardAccessibilityStatus.strokeColor = getColor(R.color.warning)
            binding.ivAccIcon.setImageResource(R.drawable.ic_settings)
            binding.ivAccIcon.setColorFilter(getColor(R.color.warning))
            binding.tvAccTitle.text = "Servizio Accessibilità Richiesto"
            binding.tvAccTitle.setTextColor(getColor(R.color.warning))
            binding.tvAccDescription.text = "Per permettere al widget sulla Home di cambiare i parametri Ethernet in automatico, abilita il servizio."
            binding.btnEnableAccessibility.visibility = View.VISIBLE
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        Toast.makeText(
            this,
            "Cerca \"Automazione Configurazione Ethernet\" e attivalo",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun refreshEthernetStatus() {
        val info = EthernetUtils.getEthernetInfo(this)

        if (info.isConnected && info.isUp) {
            binding.tvLiveStatus.text = "Connesso"
            binding.tvLiveStatus.setTextColor(getColor(R.color.success))
            binding.tvLiveIp.text = info.ip
            binding.tvLiveMac.text = info.mac
        } else if (info.isConnected) {
            binding.tvLiveStatus.text = "Disattivato"
            binding.tvLiveStatus.setTextColor(getColor(R.color.warning))
            binding.tvLiveIp.text = "Adattatore presente (interfaccia spenta)"
            binding.tvLiveMac.text = info.mac
        } else {
            binding.tvLiveStatus.text = "Non rilevato"
            binding.tvLiveStatus.setTextColor(getColor(R.color.text_muted))
            binding.tvLiveIp.text = "Nessun adattatore USB collegato"
            binding.tvLiveMac.text = "—"
        }
    }

    private fun refreshProfiles() {
        val profiles = repository.getProfiles()
        profileAdapter.updateData(profiles)
        EthernetAppWidget.updateAllWidgets(this)
    }

    private fun applyProfile(profile: EthernetProfile) {
        if (!isAccessibilityServiceEnabled()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Abilita Accessibilità")
                .setMessage("Per cambiare i parametri Ethernet in automatico è necessario abilitare il servizio di accessibilità.\n\nVuoi aprirlo ora?")
                .setPositiveButton("Apri Impostazioni") { _, _ ->
                    openAccessibilitySettings()
                }
                .setNegativeButton("Annulla", null)
                .show()
            return
        }

        if (!EthernetUtils.isAdapterConnected(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Adattatore Non Rilevato")
                .setMessage("Nessun adattatore Ethernet USB è collegato. Inserisci l'adattatore prima di continuare.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val activeId = repository.getActiveProfileId()
        val ethInfo = EthernetUtils.getEthernetInfo(this)
        val isSameProfileSelected = (activeId == profile.id)
        val isSameStaticIpActive = (!profile.isDhcp && ethInfo.ip == profile.ip && ethInfo.isConnected)

        if (isSameProfileSelected || isSameStaticIpActive) {
            Toast.makeText(this, "✓ Profilo \"${profile.name}\" già attivo!", Toast.LENGTH_SHORT).show()
            repository.setActiveProfileId(profile.id)
            refreshProfiles()
            return
        }

        EthernetAutomationService.startAutomation(this, profile)
    }

    private fun showEditProfileDialog(profileToEdit: EthernetProfile?) {
        val dialogBinding = DialogEditProfileBinding.inflate(LayoutInflater.from(this))
        val isNew = profileToEdit == null

        if (profileToEdit != null) {
            dialogBinding.etProfileName.setText(profileToEdit.name)
            dialogBinding.switchIsDhcp.isChecked = profileToEdit.isDhcp
            dialogBinding.etProfileIp.setText(profileToEdit.ip)
            dialogBinding.etProfileNetmask.setText(profileToEdit.netmask)
            dialogBinding.etProfileGateway.setText(profileToEdit.gateway)
            dialogBinding.etProfileDns.setText(profileToEdit.dns)

            if (profileToEdit.isDhcp) {
                dialogBinding.layoutStaticInputs.visibility = View.GONE
            }
        }

        dialogBinding.switchIsDhcp.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.layoutStaticInputs.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isNew) "Nuovo Profilo Ethernet" else "Modifica Profilo")
            .setView(dialogBinding.root)
            .setPositiveButton("Salva") { _, _ ->
                val name = dialogBinding.etProfileName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Il nome del profilo è obbligatorio", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val isDhcp = dialogBinding.switchIsDhcp.isChecked
                val ip = dialogBinding.etProfileIp.text.toString().trim()
                val netmask = dialogBinding.etProfileNetmask.text.toString().trim()
                val gateway = dialogBinding.etProfileGateway.text.toString().trim()
                val dns = dialogBinding.etProfileDns.text.toString().trim()

                val newProfile = EthernetProfile(
                    id = profileToEdit?.id ?: ("custom_" + UUID.randomUUID().toString()),
                    name = name,
                    isDhcp = isDhcp,
                    ip = ip,
                    netmask = if (netmask.isEmpty()) "255.255.255.0" else netmask,
                    gateway = gateway,
                    dns = if (dns.isEmpty()) "8.8.8.8" else dns,
                    isDefault = profileToEdit?.isDefault ?: false
                )

                repository.addOrUpdateProfile(newProfile)
                refreshProfiles()
                Toast.makeText(this, "Profilo salvato!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun confirmDeleteProfile(profile: EthernetProfile) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Elimina Profilo")
            .setMessage("Sei sicuro di voler eliminare il profilo \"${profile.name}\"?")
            .setPositiveButton("Elimina") { _, _ ->
                repository.deleteProfile(profile.id)
                refreshProfiles()
                Toast.makeText(this, "Profilo eliminato", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
