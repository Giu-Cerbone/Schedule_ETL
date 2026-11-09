import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ProcessDefinition, ProcessExecution } from './api.models';
import { ChangeNotifier } from './change-notifier.service';
import { NotificationService } from './notification.service';
import { ScheduleApiService } from './schedule-api.service';

@Injectable({ providedIn: 'root' })
export class ExecutionHistoryService {
  private readonly api = inject(ScheduleApiService);
  private readonly notifications = inject(NotificationService);
  private readonly notifier = inject(ChangeNotifier);

  selectedProcess: ProcessDefinition | null = null;
  executions: ProcessExecution[] = [];

  /** Carica la cronologia del processo; restituisce false se la richiesta fallisce. */
  async load(process: ProcessDefinition): Promise<boolean> {
    try {
      this.executions = (await firstValueFrom(this.api.processHistory(process.schedulerId, process.id)))
        .sort((left, right) => right.id - left.id);
      this.selectedProcess = process;
      this.notifier.notify();
      return true;
    } catch (error) {
      this.notifications.reportError(error);
      return false;
    }
  }
}
