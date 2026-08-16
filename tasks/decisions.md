# Registro Decisioni — Ethernet Controller

## Stack e Vincoli

### Stack Tecnologico:
- **Piattaforma**: Android Native (Kotlin 2.0.21, Target/Compile SDK 35, Min SDK 26)
- **UI**: Material 3 + ViewBinding (AndroidX AppCompat, ConstraintLayout, RecyclerView, CardView)
- **Widget**: Android AppWidgetProvider (RemoteViews compatibile con One UI 6 & 7, pure shape drawables)
- **Automazione**: Android AccessibilityService (Controlled Coroutine Loop con Pacing 180ms e Switch Safety Guard)
- **Persistenza**: SharedPreferences + Gson per profili di rete
- **Aggiornamento Automatico**: GitHub Releases API (con download APK e installazione sicura tramite FileProvider)
- **CI/CD**: GitHub Actions per build e release automatica firmata con Keystore

### Vincoli:
- Nessuna dipendenza a pagamento o server proprietario (tutto su GitHub Releases open/free).
- Compatibilità con Samsung One UI / Android 13, 14, 15.
- Funzionamento senza Root, senza PC e senza Shizuku sui telefoni aziendali dei colleghi.

---

## 2026-08-17 — Architettura di Sicurezza Ethernet & Pacing Pacing Loop
- Abbandonati i tight-loop a 35ms a favore di un `startControlledLoop` con pacing di 180ms e 600ms post-switch per garantire stabilità al chip PHY dell'adattatore USB.
- Implementata regola tassativa di fine sequenza: prima del ritorno alla Home, l'automazione verifica e garantisce che lo switch Ethernet sia su ON (`Attivato`), prevenendo il blocco hardware persistente rilevato su Galaxy S23 Ultra.
- Aggiunto recovery automatico watchdog a 5 tentativi prima del timeout per ripristinare lo switch Ethernet in ogni scenario anomalo.

## 2026-08-17 — Rilevamento Hardware USB & Multi-Source
- Diagnostica adattatore basata su `UsbManager` (dispositivi OTG collegati), `/proc/net/dev` (interfaccia eth presente nel kernel) e `NetworkInterface`.
- Permette di mostrare lo stato "Disattivato / Adattatore presente" anche quando l'utente o il sistema ha spento lo switch Ethernet.

## 2026-08-16 — Package Name: `com.ethernet.controller`
- Ridenominazione package da `com.azienda.ethernetcontroller` a `com.ethernet.controller`.
- Aggiornamento struttura cartelle in `app/src/main/java/com/ethernet/controller/`.

## 2026-08-16 — Sistema Auto-Update via GitHub Releases
- Repository di rilascio: `https://github.com/Rosti90/Ethernet-controller`
- Controllo periodico e manuale della versione `vX.Y` tramite endpoint `https://api.github.com/repos/Rosti90/Ethernet-controller/releases/latest`.
- Download APK in cache e avvio Intent `ACTION_VIEW` con `FLAG_GRANT_READ_URI_PERMISSION` tramite `androidx.core.content.FileProvider`.

## 2026-08-16 — Firma APK per GitHub Actions CI/CD
- Generazione Keystore di rilascio (`release.keystore` / `keystore.jks`).
- Alias: `ethernet_controller`
- Configurazione GitHub Secrets (`KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`, `KEYSTORE_BASE64`) documentata in `KEYS.md`.
