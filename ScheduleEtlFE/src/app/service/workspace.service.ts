import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  ProcessDefinition,
  ProcessExecution,
  ProcessType,
  Scheduler,
  ShellCommandOption,
  ShellScriptOption,
  UserGroup,
  UserProfile,
} from './api.models';
import { ChangeNotifier } from './change-notifier.service';
import { NotificationService } from './notification.service';
import { ScheduleApiService } from './schedule-api.service';
import { StateLabelsService } from './state-labels.service';

/** Stato condiviso tra le pagine: elenchi di base, scheduler selezionato, processi ed esecuzioni. */
@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly api = inject(ScheduleApiService);
  private readonly notifier = inject(ChangeNotifier);
  private readonly notifications = inject(NotificationService);
  private readonly labels = inject(StateLabelsService);

  schedulers: Scheduler[] = [];
  profiles: UserProfile[] = [];
  groups: UserGroup[] = [];
  processes: ProcessDefinition[] = [];
  processTypes: ProcessType[] = [];
  shellCommands: ShellCommandOption[] = [];
  shellScripts: ShellScriptOption[] = [];
  executions: ProcessExecution[] = [];
  selectedSchedulerId: number | null = null;
  loading = true;
  readonly pendingStates = new Map<number, string>();

  get selectedScheduler(): Scheduler | undefined {
    return this.schedulers.find((item) => item.id === this.selectedSchedulerId);
  }

  get orderedProcesses(): ProcessDefinition[] {
    const processIds = new Set(this.processes.map((process) => process.id));
    const childrenByParent = new Map<number | null, ProcessDefinition[]>();
    for (const process of this.processes) {
      const parentId = process.parentProcessId !== null && processIds.has(process.parentProcessId)
        ? process.parentProcessId
        : null;
      const siblings = childrenByParent.get(parentId) ?? [];
      siblings.push(process);
      childrenByParent.set(parentId, siblings);
    }

    const ordered: ProcessDefinition[] = [];
    const visited = new Set<number>();
    const appendChildren = (parentId: number | null): void => {
      const children = (childrenByParent.get(parentId) ?? [])
        .sort((left, right) => left.sequenceOrder - right.sequenceOrder || left.id - right.id);
      for (const child of children) {
        if (visited.has(child.id)) continue;
        visited.add(child.id);
        ordered.push(child);
        appendChildren(child.id);
      }
    };
    appendChildren(null);
    for (const process of this.processes) {
      if (!visited.has(process.id)) ordered.push(process);
    }
    return ordered;
  }

  async refresh(): Promise<void> {
    this.loading = true;
    this.notifications.clearError();
    try {
      const [schedulers, profiles, groups] = await Promise.all([
        firstValueFrom(this.api.listSchedulers()),
        firstValueFrom(this.api.listProfiles()),
        firstValueFrom(this.api.listGroups()),
      ]);
      this.schedulers = schedulers;
      this.profiles = profiles;
      this.groups = groups;
      if (!this.schedulers.some((item) => item.id === this.selectedSchedulerId)) {
        this.selectedSchedulerId = this.schedulers[0]?.id ?? null;
      }
      await this.loadProcesses(false);
    } catch (error) {
      this.notifications.reportError(error);
    } finally {
      this.loading = false;
      this.notifier.notify();
    }
    void this.loadSupportingData();
  }

  async loadProcesses(waitForHistory = true): Promise<void> {
    if (this.selectedSchedulerId === null) {
      this.processes = [];
      this.executions = [];
      this.notifier.notify();
      return;
    }
    const schedulerId = this.selectedSchedulerId;
    this.processes = await firstValueFrom(this.api.listProcesses(schedulerId));
    this.executions = [];
    this.notifier.notify();
    if (waitForHistory) {
      await this.loadProcessHistory(schedulerId, this.processes);
    } else {
      void this.loadProcessHistory(schedulerId, this.processes)
        .catch((error: unknown) => this.notifications.reportError(error));
    }
  }

  async loadProcessHistory(
    schedulerId: number,
    processes: ProcessDefinition[],
    activeOnly = false,
  ): Promise<void> {
    const processesToLoad = activeOnly
      ? processes.filter((process) => this.labels.isExecutionActive(this.latestState(process.id)))
      : processes;
    if (processesToLoad.length === 0) return;
    const histories = await Promise.all(
      processesToLoad.map((process) => firstValueFrom(this.api.processHistory(process.schedulerId, process.id))),
    );
    if (this.selectedSchedulerId === schedulerId
      && this.processes.map((process) => process.id).join(',') === processes.map((process) => process.id).join(',')) {
      if (activeOnly) {
        const refreshedIds = new Set(processesToLoad.map((process) => process.id));
        this.executions = [
          ...this.executions.filter((execution) => !refreshedIds.has(execution.processDefinitionId)),
          ...histories.flat(),
        ].sort((left, right) => right.id - left.id);
      } else {
        this.executions = histories.flat().sort((left, right) => right.id - left.id);
      }
      this.notifier.notify();
    }
  }

  async loadSupportingData(): Promise<void> {
    try {
      const [processTypes, shellCommands, shellScripts] = await Promise.all([
        firstValueFrom(this.api.listProcessTypes()),
        firstValueFrom(this.api.listShellCommands()),
        firstValueFrom(this.api.listShellScripts()),
      ]);
      this.processTypes = processTypes;
      this.shellCommands = shellCommands;
      this.shellScripts = shellScripts;
      this.notifier.notify();
    } catch (error) {
      this.notifications.reportError(error);
    }
  }

  async chooseScheduler(id: number): Promise<void> {
    this.selectedSchedulerId = id;
    try {
      await this.loadProcesses(false);
    } catch (error) {
      this.notifications.reportError(error);
    }
  }

  latestState(processId: number): string {
    const latestExecution = this.executions.reduce<ProcessExecution | null>((latest, execution) => {
      if (execution.processDefinitionId !== processId) return latest;
      return latest === null || execution.id > latest.id ? execution : latest;
    }, null);
    return this.pendingStates.get(processId) ?? latestExecution?.state ?? 'CREATED';
  }

  ownerName(id: number): string {
    return this.profiles.find((profile) => profile.id === id)?.fullName ?? `Profilo #${id}`;
  }

  groupName(id: number | null): string {
    return this.groups.find((group) => group.id === id)?.name ?? '—';
  }
}
