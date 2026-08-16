# Convenzioni di Codice — Ethernet Controller

## Naming & Struttura
- **Package base**: `com.ethernet.controller`
- **Architettura**:
  - `model/`: Data class e modelli di dominio (`EthernetProfile`)
  - `data/`: Gestori persistenza e repository (`ProfileRepository`)
  - `service/`: Servizi di sistema (`EthernetAutomationService`)
  - `widget/`: Provider e logica per i widget Home (`EthernetAppWidget`)
  - `receiver/`: Ricevitori di broadcast per azioni asincrone (`WidgetActionReceiver`)
  - `update/`: Gestione controllo, download e installazione aggiornamenti GitHub (`AppUpdateManager`)
  - `util/`: Funzioni di supporto e diagnostica di rete (`EthernetUtils`)

## Gestione Errori ed Edge Cases
- Notifiche all'utente tramite Dialog con spiegazione chiara o Toast immediati se in background.
- Verifica preliminare dell'adattatore hardware (`eth0`) prima di avviare qualsiasi automazione.
- Verifica del servizio di accessibilità attivo prima di inviare broadcast di automazione.
