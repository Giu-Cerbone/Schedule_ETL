import { Injectable, inject } from '@angular/core';
import { ChangeNotifier } from './change-notifier.service';

/** Tiene traccia della raggiungibilità del backend; alimenta il pannello modale di warning. */
@Injectable({ providedIn: 'root' })
export class BackendStatusService {
  private readonly notifier = inject(ChangeNotifier);
  unavailable = false;
  retrying = false;

  setUnavailable(value: boolean): void {
    if (this.unavailable === value) return;
    this.unavailable = value;
    this.notifier.notify();
  }
}
