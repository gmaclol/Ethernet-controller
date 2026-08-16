# Mappa del Progetto — Ethernet Controller

## Struttura Moduli

```
app/src/main/
├── AndroidManifest.xml
├── java/com/ethernet/controller/
│   ├── MainActivity.kt                 # UI Principale con live monitor eth0 e profili
│   ├── adapter/
│   │   └── ProfileAdapter.kt          # Adapter RecyclerView profili
│   ├── data/
│   │   └── ProfileRepository.kt       # Gestione SharedPreferences + Gson profili
│   ├── model/
│   │   └── EthernetProfile.kt         # Data model profilo Ethernet (IP, Mask, GW, DNS, DHCP)
│   ├── receiver/
│   │   └── WidgetActionReceiver.kt    # Ricevitore click widget con gestione edge-case, check profilo attivo e toast
│   ├── service/
│   │   └── EthernetAutomationService.kt # Servizio di accessibilità con Pacing e Switch Safety Guard
│   ├── update/
│   │   └── AppUpdateManager.kt        # Controllo release GitHub, download APK e installazione
│   ├── util/
│   │   └── EthernetUtils.kt           # Diagnostica adapter USB OTG, /proc/net/dev e stato di rete
│   └── widget/
│       └── EthernetAppWidget.kt       # Provider widget Home con RemoteViews e stato attivo
└── res/
    ├── drawable/                      # Icone vettoriali (ic_ethernet, ic_launcher_bg/fg, shape drawables)
    ├── layout/                        # Layout UI app e widget
    ├── mipmap-anydpi-v26/             # Adaptive Icons per Android (ic_launcher, ic_launcher_round)
    ├── values/                        # Colori, stringhe, temi
    └── xml/
        ├── accessibility_service_config.xml # Configurazione accessibilità
        ├── provider_paths.xml         # Percorsi FileProvider per auto-update
        └── widget_info.xml            # Metadati AppWidgetProvider Android 12+
```
