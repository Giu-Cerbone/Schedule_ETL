import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { CreateScheduler, EditableSchedulerStatus, Scheduler } from './api.models';
import { NotificationService } from './notification.service';
import { ScheduleApiService } from './schedule-api.service';
import { StateLabelsService } from './state-labels.service';
import { WorkspaceService } from './workspace.service';

@Injectable({ providedIn: 'root' })
export class SchedulerService {
  private readonly api = inject(ScheduleApiService);
  private readonly workspace = inject(WorkspaceService);
  private readonly notifications = inject(NotificationService);
  private readonly labels = inject(StateLabelsService);

  readonly statusOptions: { label: string; value: EditableSchedulerStatus }[] = [
    { label: 'Attivo', value: 'ACTIVE' },
    { label: 'Inattivo', value: 'INACTIVE' },
    { label: 'In pausa', value: 'PAUSED' },
  ];
  readonly updatingIds = new Set<number>();

  newSchedulerForm(): CreateScheduler {
    return {
      name: '',
      description: '',
      cronExpression: '0 0 2 * * ?',
      ownerProfileId: this.workspace.profiles[0]?.id ?? null,
      groupId: null,
      status: 'ACTIVE',
    };
  }

  async create(form: CreateScheduler): Promise<void> {
    const created = await firstValueFrom(this.api.createScheduler(form));
    this.workspace.selectedSchedulerId = created.id;
    await this.afterSave();
  }

  async assignToGroup(groupId: number | null): Promise<void> {
    if (this.workspace.selectedSchedulerId === null || groupId === null) {
      throw new Error('Seleziona uno scheduler e un gruppo.');
    }
    await firstValueFrom(this.api.assignSchedulerToGroup(this.workspace.selectedSchedulerId, groupId));
    await this.afterSave();
  }

  async remove(scheduler: Scheduler): Promise<void> {
    if (!window.confirm(`Eliminare lo scheduler "${scheduler.name}"?`)) return;
    try {
      await firstValueFrom(this.api.deleteScheduler(scheduler.id));
      this.notifications.success('Scheduler eliminato', scheduler.name);
      await this.workspace.refresh();
    } catch (error) {
      this.notifications.reportError(error);
    }
  }

  async updateStatus(scheduler: Scheduler, status: EditableSchedulerStatus): Promise<void> {
    if (scheduler.status === status || this.updatingIds.has(scheduler.id)) return;
    this.updatingIds.add(scheduler.id);
    try {
      const updated = await firstValueFrom(this.api.updateSchedulerStatus(scheduler.id, status));
      scheduler.status = updated.status;
      scheduler.updatedAt = updated.updatedAt;
      this.notifications.success('Stato scheduler aggiornato', `${scheduler.name}: ${this.labels.stateLabel(updated.status)}`);
    } catch (error) {
      this.notifications.reportError(error);
    } finally {
      this.updatingIds.delete(scheduler.id);
    }
  }

  details(scheduler: Scheduler): Observable<unknown> {
    return this.api.getScheduler(scheduler.id);
  }

  private async afterSave(): Promise<void> {
    this.notifications.success('Salvato', 'Le modifiche sono state salvate.');
    await this.workspace.refresh();
  }
}
