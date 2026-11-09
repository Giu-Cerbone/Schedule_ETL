import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ProcessEventLog } from './api.models';
import { toIsoDate } from './date-format';
import { ChangeNotifier } from './change-notifier.service';
import { NotificationService } from './notification.service';
import { ScheduleApiService } from './schedule-api.service';

export interface ProcessLogFilter {
  text: string;
  level: string;
  eventType: string;
  triggerType: string;
  schedulerId: number | null;
  from: Date | null;
  to: Date | null;
}

@Injectable({ providedIn: 'root' })
export class ProcessLogService {
  private readonly api = inject(ScheduleApiService);
  private readonly notifications = inject(NotificationService);
  private readonly notifier = inject(ChangeNotifier);

  readonly levelOptions = ['INFO', 'WARN', 'ERROR'];
  readonly eventOptions = ['QUEUED', 'STARTED', 'STEP', 'COMPLETED', 'FAILED', 'CANCELLED', 'PAUSE', 'RESUME', 'STOP'];
  readonly triggerOptions = ['MANUAL', 'SCHEDULED'];
  readonly pageSize = 10;
  logs: ProcessEventLog[] = [];
  total = 0;
  page = 0;
  loading = false;
  expandedIds = new Set<number>();
  filter: ProcessLogFilter = this.emptyFilter();
  private static readonly errorTextPattern = /\b(error|errors|exception|failed|failure|fatal|timed out|timeout|errore|fallit\w*)\b/i;

  get lastPage(): number {
    return Math.max(Math.ceil(this.total / this.pageSize) - 1, 0);
  }

  async load(page = 0): Promise<void> {
    this.loading = true;
    this.notifier.notify();
    try {
      const filter = this.filter;
      const result = await firstValueFrom(this.api.searchProcessLogs({
        text: filter.text,
        level: filter.level,
        eventType: filter.eventType,
        triggerType: filter.triggerType,
        schedulerId: filter.schedulerId,
        from: filter.from ? `${toIsoDate(filter.from)}T00:00:00` : undefined,
        to: filter.to ? `${toIsoDate(filter.to)}T23:59:59` : undefined,
        page,
        size: this.pageSize,
      }));
      this.logs = result.items;
      this.total = result.totalElements;
      this.page = result.page;
      this.expandedIds.clear();
    } catch (error) {
      this.notifications.reportError(error);
    } finally {
      this.loading = false;
      this.notifier.notify();
    }
  }

  resetFilter(): void {
    this.filter = this.emptyFilter();
    void this.load();
  }

  toggleDetails(log: ProcessEventLog): void {
    if (!this.expandedIds.delete(log.id)) this.expandedIds.add(log.id);
  }

  isError(log: ProcessEventLog): boolean {
    return log.level === 'ERROR' || log.state === 'FAILED'
      || ProcessLogService.errorTextPattern.test(log.message)
      || (!!log.details && ProcessLogService.errorTextPattern.test(log.details));
  }

  private emptyFilter(): ProcessLogFilter {
    return { text: '', level: '', eventType: '', triggerType: '', schedulerId: null, from: null, to: null };
  }
}
