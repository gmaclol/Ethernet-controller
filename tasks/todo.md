# Task List — Ethernet Controller

## Task Completati (2026-08-16 / 2026-08-17)

- [x] **1. Gestione Errori & Edge Cases Widget**:
  - [x] Alert Dialog Material 3 per Servizio di Accessibilità disattivato con reindirizzamento diretto alle impostazioni.
  - [x] Alert Dialog e Toast per Adattatore USB Ethernet non rilevato.
  - [x] Controllo preventivo profilo già attivo per evitare passaggi a vuoto (con Toast dedicato da MainLooper).
  - [x] Riconoscimento adattatore USB OTG anche ad interfaccia spenta tramite `UsbManager` e `/proc/net/dev`.
- [x] **2. Risoluzione Bug Critico Samsung One UI & S23 Ultra**:
  - [x] Implementata disattivazione sicura pre-editing e riattivazione obbligatoria post-editing.
  - [x] Risolto bug deadlock dove i profili già corrispondenti terminavano con switch OFF causando blocco persistente di `EthernetService`.
  - [x] Implementato safety watchdog con recovery automatico a 5 tentativi prima del timeout.
  - [x] Corretto sblocco del flag `switchReEnabled` per consentire chiusura immediata e ritorno alla Home.
- [x] **3. Grafica, Asset e UI**:
  - [x] Creata nuova Adaptive Icon cyber ad alta definizione (`mipmap-anydpi-v26/ic_launcher` e `ic_launcher_round`).
  - [x] Aggiornato `ic_ethernet.xml` in stile tech scuro con contatti ciano e LED di stato.
- [x] **4. Ritorno Automatico alla Home**:
  - [x] Esecuzione coordinata di `GLOBAL_ACTION_HOME` e Intent `CATEGORY_HOME` con chiusura immediata della coroutine.
- [x] **5. Ridenominazione Package & CI/CD**:
  - [x] Modificato `applicationId` e `namespace` in `com.ethernet.controller`.
  - [x] Aggiornato workflow `.github/workflows/release.yml` per supportare tag push e dispatch manuale con fallback APK.
  - [x] Creato `README.md` completo e dettagliato.
- [x] **6. Sistema di Aggiornamento Automatico via GitHub**:
  - [x] `AppUpdateManager` collegato alle GitHub Releases con `FileProvider` sicuro.
  - [x] Controllo aggiornamenti all'avvio e manuale da menu.
