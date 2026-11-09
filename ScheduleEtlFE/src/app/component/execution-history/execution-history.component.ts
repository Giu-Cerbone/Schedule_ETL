import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { markOnChanges } from '../../service/change-notifier.service';
import { ExecutionHistoryService } from '../../service/execution-history.service';
import { StateLabelsService } from '../../service/state-labels.service';

@Component({
  selector: 'app-execution-history',
  standalone: true,
  imports: [DatePipe, ButtonModule, TableModule, TagModule],
  templateUrl: './execution-history.component.html',
})
export class ExecutionHistoryComponent {
  readonly history = inject(ExecutionHistoryService);
  readonly labels = inject(StateLabelsService);
  private readonly router = inject(Router);

  constructor() {
    markOnChanges();
  }

  trackById(_index: number, item: { id: number }): number {
    return item.id;
  }

  backToProcesses(): void {
    void this.router.navigate(['/processes']);
  }
}
