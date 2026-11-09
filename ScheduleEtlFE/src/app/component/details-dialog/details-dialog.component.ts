import { Component, input, model } from '@angular/core';
import { DialogModule } from 'primeng/dialog';

/** Dialog di sola lettura che mostra il JSON di dettaglio di un elemento. */
@Component({
  selector: 'app-details-dialog',
  standalone: true,
  imports: [DialogModule],
  template: `
    <p-dialog [header]="title()" [(visible)]="visible" [modal]="true" [draggable]="false" [resizable]="false" [style]="{ width: 'min(540px, 94vw)' }">
      <pre class="details-json">{{ text() }}</pre>
    </p-dialog>
  `,
})
export class DetailsDialogComponent {
  readonly title = input('');
  readonly text = input('');
  readonly visible = model(false);
}
