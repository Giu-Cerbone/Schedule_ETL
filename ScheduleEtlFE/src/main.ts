import { bootstrapApplication } from '@angular/platform-browser';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeuix/themes/aura';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, {
  ...appConfig,
  providers: [
    ...(appConfig.providers ?? []),
    providePrimeNG({
      theme: { preset: Aura, options: { darkModeSelector: '.app-dark' } },
      translation: {
        firstDayOfWeek: 1,
        dayNames: ['domenica', 'lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato'],
        dayNamesShort: ['dom', 'lun', 'mar', 'mer', 'gio', 'ven', 'sab'],
        dayNamesMin: ['D', 'L', 'M', 'M', 'G', 'V', 'S'],
        monthNames: ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno', 'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre'],
        monthNamesShort: ['gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug', 'ago', 'set', 'ott', 'nov', 'dic'],
        today: 'Oggi',
        clear: 'Pulisci',
        weekHeader: 'Sett.',
        dateFormat: 'dd/mm/yy',
      },
    }),
  ],
})
  .catch((err) => console.error(err));
