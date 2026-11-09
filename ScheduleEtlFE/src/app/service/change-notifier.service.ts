import { ChangeDetectorRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';

/** Avvisa i componenti quando lo stato condiviso dei servizi cambia (l'app è zoneless). */
@Injectable({ providedIn: 'root' })
export class ChangeNotifier {
  readonly changes = new Subject<void>();

  notify(): void {
    this.changes.next();
  }
}

/** Da chiamare nel costruttore di un componente: ridisegna il componente a ogni notifica. */
export function markOnChanges(): void {
  const changeDetector = inject(ChangeDetectorRef);
  inject(ChangeNotifier).changes
    .pipe(takeUntilDestroyed())
    .subscribe(() => changeDetector.markForCheck());
}
