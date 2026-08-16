# Walkthrough - Automazione Ethernet Samsung One UI & Gestione Switch

Abbiamo implementato e verificato l'automazione completa e resiliente per dispositivi Samsung Galaxy con One UI, gestendo sia i dialoghi di configurazione IP sia la disattivazione/attivazione preventiva dello switch Ethernet.

## Modifiche Principali

### 1. Disattivazione preventiva e riattivazione post-save dello Switch Ethernet
- **Pre-config**: Se lo switch Ethernet è attivo (`switchNode.isChecked == true`), il servizio lo disabilita prima di aprire il dialogo "Configura dispositivo Ethernet" (necessario su Samsung per sbloccare la modifica dei parametri di rete).
- **Post-save**: Non appena le modifiche vengono salvate o confermate (`saveClicked == true`), lo switch Ethernet viene automaticamente riacceso, applicando i nuovi parametri a `eth0`.

### 2. Espansione a 2 Fasi per i Campi Statici (RadioGroup onCheckedChanged)
- Su Samsung One UI, se "IP statico" era già spuntato in precedenza, cliccare nuovamente sul radio button non scatena `onCheckedChanged` e i 4 campi `EditText` rimangono nascosti nel layout.
- L'automazione ora esegue una rapida sequenza **Step 1 (DHCP) $\rightarrow$ Step 2 (IP statico)** che forza l'espansione immediata dei 4 `EditText` (`ipaddr_edit`, `netmask_edit`, `eth_dns_edit`, `eth_gw_edit`).

### 3. Gestione e Chiusura Automatica della Tastiera Software
- Cliccando su "IP statico" Samsung apre automaticamente la tastiera a schermo sul primo campo.
- La funzione `closeKeyboardIfOpen()` rileva la finestra `TYPE_INPUT_METHOD` e la chiude con `GLOBAL_ACTION_BACK`, assicurando che il pulsante **Salva** (`android:id/button1`) sia sempre visibile e cliccabile.

### 4. Rilevamento Valori Già Attivi (Edge case Salva Disabilitato)
- Se i valori presenti nel dialogo corrispondono già esattamente al profilo selezionato, Samsung disabilita il tasto **Salva**. L'automazione rileva questa situazione e chiude il dialogo tramite **Scarta** (`android:id/button2`), evitando loop infiniti e completando l'operazione con successo.

---

## Stato dei Test Live

| Test | Dispositivo | Risultato |
|---|---|---|
| **DHCP (Auto)** | Samsung S23 Ultra | PASS (Switch disabilitato $\rightarrow$ DHCP selezionato $\rightarrow$ Salva $\rightarrow$ Switch riabilitato $\rightarrow$ Home) |
| **ONT SKY (192.168.100.10)** | Samsung S23 Ultra | PASS (Switch disabilitato $\rightarrow$ Espansione campi $\rightarrow$ Compilazione $\rightarrow$ Salva $\rightarrow$ Switch riabilitato $\rightarrow$ Home) |
| **ONT OF (192.168.1.10)** | Samsung S23 Ultra | PASS (Switch disabilitato $\rightarrow$ Espansione campi $\rightarrow$ Compilazione $\rightarrow$ Salva $\rightarrow$ Switch riabilitato $\rightarrow$ Home) |
| **Installazione APK** | Samsung Galaxy A17 | PASS (APK debug aggiornato e pronto per il test con adapter) |

L'APK aggiornato è installato su entrambi i dispositivi ([EthernetAutomationService.kt](file:///c:/Users/Rosti/Desktop/Ethernet%20controller/app/src/main/java/com/ethernet/controller/service/EthernetAutomationService.kt)).
