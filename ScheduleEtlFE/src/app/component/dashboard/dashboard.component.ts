import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ChartModule } from 'primeng/chart';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { Scheduler } from '../../service/api.models';
import { markOnChanges } from '../../service/change-notifier.service';
import { DashboardService } from '../../service/dashboard.service';
import { StateLabelsService } from '../../service/state-labels.service';
import { WorkspaceService } from '../../service/workspace.service';
import 'chart.js/auto';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [FormsModule, ButtonModule, ChartModule, SelectModule, TableModule, TagModule],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent {
  readonly workspace = inject(WorkspaceService);
  readonly dashboard = inject(DashboardService);
  readonly labels = inject(StateLabelsService);
  private readonly router = inject(Router);

  constructor() {
    markOnChanges();
  }

  trackById(_index: number, item: { id: number }): number {
    return item.id;
  }

  newScheduler(): void {
    void this.router.navigate(['/schedulers'], { queryParams: { new: 1 } });
  }

  openProcesses(scheduler?: Scheduler): void {
    if (scheduler) void this.workspace.chooseScheduler(scheduler.id);
    void this.router.navigate(['/processes']);
  }

  openSchedulers(): void {
    void this.router.navigate(['/schedulers']);
  }
}
