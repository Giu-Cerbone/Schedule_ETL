# ScheduleEtlFE

Frontend Angular 21 per la piattaforma di gestione e monitoraggio degli scheduler SchedulETL. Usa PrimeNG 21, PrimeIcons e Chart.js.

## Avvio locale

Avvia prima il backend Spring Boot sulla porta `8080`, poi dalla cartella `ScheduleEtlFE` esegui:

```bash
npm install
npm start
```

Apri `http://localhost:4200`. Il dev server inoltra `/api/*` a `http://localhost:8080` tramite `proxy.conf.json`.

## Funzionalità

- Dashboard con contatori, grafici di esecuzione e diagramma del flusso dei processi.
- Gestione degli scheduler: creazione, dettagli, assegnazione a gruppo ed eliminazione.
- Gestione dei processi: creazione, stato, avvio, pausa, ripresa, arresto, configurazione e cronologia/log.
- Gestione e consultazione dei profili utente e dei gruppi.
- Errori delle API mostrati nell'interfaccia; dati letti dalle API del backend, senza fixture locali.

Le API sono definite in `src/app/schedule-api.service.ts`; il backend documenta ulteriori dettagli su Swagger in `http://localhost:8080/swagger-ui.html`.

## Build e test

```bash
npm run build
npm test -- --watch=false
```
