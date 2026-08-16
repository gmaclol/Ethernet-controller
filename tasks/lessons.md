# Lessons Learned & Technical Constraints

## ⚠️ BUG CRITICO SAMSUNG ONE UI: Desync dello Switch Ethernet e Rischio Blocco Rete
1. **Dinamica del Guasto su S23 Ultra / One UI**:
   - Se l'automazione disattiva lo switch Ethernet per entrare nel dialogo, verifica che i valori IP corrispondono già e chiude il popup ("Scarta"), **NON DEVE MAI terminare senza riaccendere lo switch**.
   - Se l'automazione termina lasciando lo switch su OFF o se lo switch viene cliccato mentre `com.android.settings` è in transizione, il servizio di sistema `EthernetService` (`dumpsys ethernet`) entra in uno stato di deadlock permanente:
     - L'interfaccia `eth0` finisce in stato `disabled` (`state DOWN`, `ipClient is null`).
     - La UI delle Impostazioni mostra erroneamente lo switch come *"Attivato"*, ma premendo lo switch questo torna istantaneamente su spento o non risponde più.
     - L'adattatore USB Ethernet smette di funzionare e il problema **sopravvive sia al riavvio del telefono che allo scollegamento fisico del dongle OTG**.
     - L'unico ripristino possibile senza permessi di root è il *Ripristino impostazioni di rete* da Android (`Impostazioni -> Gestione generale -> Ripristina -> Ripristina impostazioni di rete`).

2. **Soluzione e Triplo Livello di Sicurezza (Airtight Safety Guard)**:
   - **Regola Rigida di Fine Sequenza**: Dopo il dialogo, l'automazione torna sulla schermata Ethernet e verifica categoricamente se lo switch è su ON (`Attivato`). Se è su OFF, lo clicca, aspetta 600ms per la sincronizzazione PHY/UI e non procede alla chiusura finché lo stato ON non è confermato.
   - **Guard in `finishAutomation`**: Rifiuta categoricamente di terminare se `switchDisabledBeforeEdit` è `true` e `switchReEnabled` è ancora `false`.
   - **Safety Watchdog Recovery**: In caso di timeout o errore durante il flusso, prima di terminare la coroutine tenta un ciclo automatico di recupero in 5 tentativi per riaccendere forzatamente lo switch.
   - **Sblocco del Flag `switchReEnabled`**: Quando lo switch viene confermato acceso, il flag `switchReEnabled` deve essere impostato a `true` prima di chiamare `finishAutomation`, consentendo la chiusura e il ritorno istantaneo alla Home.

---

## Samsung One UI Ethernet Dialog Quirks
1. **Disabilitazione preventiva Switch Ethernet**:
   - Su Samsung One UI, per modificare i parametri Ethernet statici (o passare a DHCP) lo switch Ethernet deve essere **DISATTIVATO** prima di aprire "Configura dispositivo Ethernet".
   - Dopo il click su "Salva", lo switch Ethernet deve essere **RIATTIVATO** per applicare effettivamente i nuovi parametri di rete all'interfaccia `eth0`.

2. **Espansione campi EditText nel RadioGroup**:
   - Se "IP statico" è già selezionato da una sessione precedente, un click su "IP statico" non genera l'evento `onCheckedChanged`, lasciando il container dei campi EditText nascosto (`enterprise_wrapper`).
   - La soluzione affidabile al 100% è eseguire la sequenza di transizione **DHCP $\rightarrow$ IP statico**, che forza l'espansione dei campi.

3. **Chiusura della Tastiera Software**:
   - All'espansione dei campi o al click su "IP statico", Samsung focalizza il primo EditText aprendo la tastiera software (`TYPE_INPUT_METHOD`).
   - Chiudere la tastiera con `GLOBAL_ACTION_BACK` (verificando prima la presenza della finestra tastiera con `closeKeyboardIfOpen()`) permette di non coprire il pulsante **Salva**.

4. **Tasto "Salva" disabilitato se i valori non cambiano**:
   - Se i campi contengono già l'IP/Gateway/Mask del profilo bersaglio, Samsung imposta `isEnabled = false` sul tasto "Salva".
   - L'automazione rileva che i campi corrispondono già al profilo e chiude il dialogo premendo **Scarta**, evitando deadlock.

5. **Pacing e Timing di Transizione**:
   - Richiesto un delay di 180-250ms tra i passaggi UI e 500-600ms dopo l'accensione dello switch per dare tempo all'hardware PHY USB di agganciare il link elettrico.

---

## RemoteViews & Widget Restrictions
- **No `<View>` o `<ripple>`**: Non usare mai `<View>` o `<ripple>` nei layout XML di `RemoteViews` per evitare crash silenziosi dell'app widget.
- **Toast da BroadcastReceiver**: Per mostrare Toast affidabili su Android 13/14+ da un `BroadcastReceiver` (senza finestre in foreground), eseguire sempre il dispatch sul Main Looper (`Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, ...).show() }`).
- **Check Profilo Già Attivo**: Prima di lanciare l'automazione di accessibilità dal widget o dall'app, confrontare l'ID del profilo attivo o l'IP configurato per evitare cicli di automazione superflui.
