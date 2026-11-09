import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { CreateScheduler, Scheduler } from '../../service/api.models';
import { markOnChanges } from '../../service/change-notifier.service';
import { NotificationService } from '../../service/notification.service';
import { SchedulerService } from '../../service/scheduler.service';
import { StateLabelsService } from '../../service/state-labels.service';
import { WorkspaceService } from '../../service/workspace.service';
import { DetailsDialogComponent } from '../details-dialog/details-dialog.component';

@Component({
  selector: 'app-scheduler',
  standalone: true,
  imports: [
    FormsModule, ButtonModule, DialogModule, InputTextModule, SelectModule, TableModule, TagModule,
    TextareaModule, DetailsDialogComponent,
  ],
  templateUrl: './scheduler.component.html',
})
export class SchedulerComponent implements OnInit {
  readonly workspace = inject(WorkspaceService);
  readonly schedulers = inject(SchedulerService);
  readonly labels = inject(StateLabelsService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  saving = false;
  createOpen = false;
  assignOpen = false;
  detailsOpen = false;
  detailsTitle = '';
  detailsText = '';
  schedulerForm: CreateScheduler = this.schedulers.newSchedulerForm();
  assignGroupId: number | null = null;

  constructor() {
    markOnChanges();
  }

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.has('new')) {
      this.openCreate();
      void this.router.navigate([], { queryParams: {}, replaceUrl: true });
    }
  }

  trackById(_index: number, item: { id: number }): number {
    return item.id;
  }

  openCreate(): void {
    this.schedulerForm = this.schedulers.newSchedulerForm();
    this.createOpen = true;
  }

  async saveScheduler(): Promise<void> {
    await this.save(() => this.schedulers.create(this.schedulerForm), () => (this.createOpen = false));
  }

  openAssign(scheduler: Scheduler): void {
    void this.workspace.chooseScheduler(scheduler.id);
    this.assignGroupId = scheduler.groupId ?? null;
    this.assignOpen = true;
  }

  async saveAssign(): Promise<void> {
    await this.save(() => this.schedulers.assignToGroup(this.assignGroupId), () => (this.assignOpen = false));
  }

  openProcesses(scheduler: Scheduler): void {
    void this.workspace.chooseScheduler(scheduler.id);
    void this.router.navigate(['/processes']);
  }

  async showDetails(scheduler: Scheduler): Promise<void> {
    try {
      const details: unknown = await firstValueFrom(this.schedulers.details(scheduler));
      this.detailsTitle = `Scheduler · ${scheduler.name}`;
      this.detailsText = JSON.stringify(details, null, 2);
      this.detailsOpen = true;
    } catch (error) {
      this.notifications.reportError(error);
    }
  }

  private async save(action: () => Promise<void>, close: () => void): Promise<void> {
    this.saving = true;
    try {
      await action();
      close();
    } catch (error) {
      this.notifications.reportError(error);
    } finally {
      this.saving = false;
    }
  }
}
