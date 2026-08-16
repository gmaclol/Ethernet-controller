# 🌐 Ethernet Controller (Android)

<div align="center">
  <img src="app/src/main/res/drawable/ic_ethernet.xml" width="128" height="128" alt="Ethernet Controller Logo" />
  <h3>Commutatore Istantaneo di Profili Ethernet & IP Statico per Android & Samsung One UI</h3>
  <p><b>Cambia la configurazione di rete della scheda Ethernet USB con un solo tocco da Widget o App</b></p>
</div>

---

## 🚀 Panoramica
**Ethernet Controller** è un'applicazione Android specializzata e ad alte prestazioni progettata per tecnici di rete, installatori di fibra ottica (FTTH) e power-user. Permette di memorizzare profili di rete e applicarli automaticamente all'adattatore USB Ethernet Type-C senza dover digitare ogni volta IP, Netmask, Gateway e DNS nelle impostazioni di sistema.

L'applicazione include un **Widget per la Schermata Home** personalizzato con pulsanti di commutazione one-touch e un motore di automazione basato su **Accessibility Service** ottimizzato per superare i vincoli e i bug noti di **Samsung One UI**.

---

## ✨ Funzionalità Principali

- ⚡ **Commutazione con 1 Tap da Widget**: Seleziona al volo tra **ONT Open Fiber (192.168.1.10)**, **ONT Sky Wi-Fi (192.168.100.10)** o **DHCP Automatico** direttamente dalla schermata Home.
- 📱 **Automazione UI Resiliente**: Navigazione, compilazione dei campi e salvataggio automatico tramite `AccessibilityService` con pacing a prova di collisione.
- 🛡️ **Samsung One UI Safe Guard & Auto-Recovery**:
  - Disattivazione temporanea dello switch Ethernet per consentire l'editing dei parametri.
  - **Verifica tassativa post-salvataggio**: Lo switch Ethernet viene categoricamente riacceso e verificato prima di tornare alla Home.
  - Recovery watchdog automatico in caso di timeout.
- 🔍 **Rilevamento Hardware Multi-Livello**: Riconosce la presenza dell'adattatore USB anche a interfaccia spenta tramite `UsbManager`, `/proc/net/dev` e `NetworkInterface`.
- ⚡ **Rilevamento Profilo Già Attivo**: Se un profilo è già in uso, l'app evita aperture inutili delle impostazioni e mostra una conferma immediata.
- 🔄 **Auto-Update Integrato**: Controllo e download automatico degli aggiornamenti APK direttamente da GitHub Releases con installazione in-app sicura via `FileProvider`.
- 🎨 **Interfaccia Cyberpunk / Tech Modern**: Tema scuro rifinito in Material Design 3 con badge di stato in tempo reale e Adaptive Icon vettoriale.

---

## 📋 Profili Predefiniti

| Profilo | Tipo | Indirizzo IP | Maschera Sottorete | Gateway | DNS Primario |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ONT OF** | Statico | `192.168.1.10` | `255.255.255.0` | `192.168.1.1` | `8.8.8.8` |
| **ONT SKY** | Statico | `192.168.100.10` | `255.255.255.0` | `192.168.100.1` | `8.8.8.8` |
| **DHCP** | Dinamico | Assegnato da router | — | — | — |

*È possibile aggiungere, modificare o eliminare profili personalizzati dall'app.*

---

## 🛠️ Architettura e Dettagli Tecnici

```
app/src/main/java/com/ethernet/controller/
├── MainActivity.kt               # Dashboard principale con stato live e gestione profili
├── adapter/
│   └── ProfileAdapter.kt         # RecyclerView Adapter per la lista profili
├── data/
│   └── ProfileRepository.kt      # Persistenza JSON SharedPreferences e profili di default
├── model/
│   └── EthernetProfile.kt        # Data class del profilo di rete (IP, Mask, GW, DNS, DHCP)
├── receiver/
│   └── WidgetActionReceiver.kt   # Gestione click dal widget e check profilo già attivo
├── service/
│   └── EthernetAutomationService.kt # Motore di automazione AccessibilityService per One UI
├── update/
│   └── AppUpdateManager.kt       # Gestore download e installazione aggiornamenti GitHub
├── util/
│   └── EthernetUtils.kt          # Rilevamento diagnostico interfaccia eth0 e USB OTG
└── widget/
    └── EthernetAppWidget.kt      # AppWidgetProvider con RemoteViews e badge stato
```

---

## 📥 Installazione & Download

1. Scarica l'ultimo file `EthernetController.apk` dalla sezione [Releases](https://github.com/Rosti90/Ethernet-controller/releases).
2. Installa l'APK sul tuo dispositivo Android (abilita *"Installa app da origini sconosciute"* se richiesto).
3. Apri l'applicazione e concedi il permesso al **Servizio di Accessibilità** ("*Automazione Configurazione Ethernet*").
4. Aggiungi il **Widget Ethernet Quick Switch** alla tua Home per la commutazione istantanea.

---

## ⚠️ Note Tecniche & Compatibilità Samsung One UI

Su Samsung One UI (Android 13/14/15 su serie Galaxy S e Galaxy A), il framework di sistema applica restrizioni specifiche:
1. **Editing con Switch ON**: One UI non consente la modifica o il salvataggio dei parametri IP statici se lo switch Ethernet è attivo. L'automazione disattiva lo switch, compila i dati e lo riaccende automaticamente.
2. **Post-Save Re-Enablement**: L'app include un triplo livello di sicurezza per garantire che l'adattatore non rimanga mai in stato `disabled`, prevenendo blocchi del sottosistema di rete del telefono.

---

## 📄 Licenza
Rilasciato sotto licenza MIT.
