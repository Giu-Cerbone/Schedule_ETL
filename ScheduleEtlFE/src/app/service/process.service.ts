import { Injectable, OnDestroy, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  CreateProcess,
  ProcessDefinition,
  ProcessExecution,
  ProcessScheduleDayName,
} from './api.models';
import { ChangeNotifier } from './change-notifier.service';
import { formatItalianDate } from './date-format';
import { NotificationService } from './notification.service';
import { ScheduleApiService } from './schedule-api.service';
import { StateLabelsService } from './state-labels.service';
import { WorkspaceService } from './workspace.service';

export type ProcessAction = 'start' | 'pause' | 'resume' | 'stop';

@Injectable({ providedIn: 'root' })
export class ProcessService implements OnDestroy {
  private readonly api = inject(ScheduleApiService);
  private readonly workspace = inject(WorkspaceService);
  private readonly notifications = inject(NotificationService);
  private readonly notifier = inject(ChangeNotifier);
  private readonly labels = inject(StateLabelsService);

  readonly processActions: { id: ProcessAction; label: string; icon: string }[] = [
    { id: 'start', label: 'Avvia', icon: 'pi pi-play' },
    { id: 'pause', label: 'Pausa', icon: 'pi pi-pause' },
    { id: 'resume', label: 'Riprendi', icon: 'pi pi-refresh' },
    { id: 'stop', label: 'Ferma', icon: 'pi pi-stop' },
  ];
  readonly scheduleDayOptions = [
    { value: 'MONDAY', label: 'Lunedì' },
    { value: 'TUESDAY', label: 'Martedì' },
    { value: 'WEDNESDAY', label: 'Mercoledì' },
    { value: 'THURSDAY', label: 'Giovedì' },
    { value: 'FRIDAY', label: 'Venerdì' },
    { value: 'SATURDAY', label: 'Sabato' },
    { value: 'SUNDAY', label: 'Domenica' },
  ] as const;

  saving = false;
  editingProcessId: number | null = null;
  shellScriptName: number | null = null;
  shellExecutionMode: 'script' | 'command' = 'script';
  shellCommandId: number | null = null;
  processForm: CreateProcess = this.emptyForm();

  detailProcess: ProcessDefinition | null = null;
  detailExecutions: ProcessExecution[] = [];
  private detailPollingTimer: ReturnType<typeof setTimeout> | null = null;
  private detailRefreshed: (() => void) | null = null;

  private statusPollingInterval: ReturnType<typeof setInterval> | null = null;
  private statusRefreshInProgress = false;
  private readonly startDateCache = new Map<string, Date>();
  private static readonly weekdayNumbers: Record<string, number> = {
    SUNDAY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6,
  };

  ngOnDestroy(): void {
    this.stopStatusPolling();
    this.closeDetails();
  }

  get availableParentProcesses(): ProcessDefinition[] {
    const excludedIds = new Set<number>();
    if (this.editingProcessId !== null) {
      const collectDescendants = (parentId: number): void => {
        for (const child of this.workspace.processes.filter((process) => process.parentProcessId === parentId)) {
          if (excludedIds.has(child.id)) continue;
          excludedIds.add(child.id);
          collectDescendants(child.id);
        }
      };
      excludedIds.add(this.editingProcessId);
      collectDescendants(this.editingProcessId);
    }
    return this.workspace.orderedProcesses.filter((process) => !excludedIds.has(process.id));
  }

  // ---- Form del processo ----

  private emptyForm(): CreateProcess {
    return {
      name: '',
      type: 'SQLDATABASE',
      description: '',
      configuration: '{}',
      enabled: true,
      parentProcessId: null,
      sequenceOrder: 0,
      runIfParentFails: false,
      startSchedule: [],
    };
  }

  newProcess(): void {
    this.editingProcessId = null;
    this.shellScriptName = null;
    this.shellExecutionMode = 'script';
    this.shellCommandId = null;
    this.processForm = { ...this.emptyForm(), type: this.workspace.processTypes[0]?.code ?? 'SQLDATABASE' };
  }

  newChildProcess(parent: ProcessDefinition): void {
    this.newProcess();
    this.processForm.parentProcessId = parent.id;
    this.processForm.sequenceOrder = this.nextTreeOrder(parent.id);
  }

  editProcess(process: ProcessDefinition): void {
    this.editingProcessId = process.id;
    this.shellScriptName = process.type === 'SHELL_CMD' ? this.readApprovedScriptName(process.configuration) : null;
    const configuredCommandId = process.type === 'SHELL_CMD' ? this.readApprovedCommandId(process.configuration) : null;
    this.shellExecutionMode = configuredCommandId === null ? 'script' : 'command';
    this.shellCommandId = configuredCommandId;
    this.processForm = {
      name: process.name,
      type: process.type,
      description: process.description ?? '',
      configuration: process.configuration ?? '',
      enabled: process.enabled,
      parentProcessId: process.parentProcessId,
      sequenceOrder: process.parentProcessId !== null && process.sequenceOrder < 1 ? 1 : process.sequenceOrder,
      runIfParentFails: process.runIfParentFails,
      startSchedule: (process.startSchedule ?? []).map((day) => ({
        dayOfWeek: day.dayOfWeek,
        times: [...day.times],
        startDate: day.startDate ?? null,
      })),
    };
  }

  onProcessTypeChange(type: string): void {
    this.processForm.type = type;
    if (type === 'SHELL_CMD') {
      this.shellScriptName = this.readApprovedScriptName(this.processForm.configuration);
      this.shellCommandId = this.readApprovedCommandId(this.processForm.configuration);
      this.shellExecutionMode = this.shellCommandId === null ? 'script' : 'command';
    }
  }

  onParentProcessChange(parentId: number | null): void {
    this.processForm.parentProcessId = parentId;
    if (parentId === null) {
      this.processForm.sequenceOrder = 0;
      this.processForm.runIfParentFails = false;
    } else if (this.editingProcessId === null || this.processForm.sequenceOrder < 1) {
      this.processForm.sequenceOrder = this.nextTreeOrder(parentId);
    }
  }

  onSequenceOrderChange(value: number | string | null): void {
    const order = Number(value);
    const isChild = this.processForm.parentProcessId !== null;
    this.processForm.sequenceOrder = !Number.isFinite(order) || value === null || value === ''
      ? (isChild ? 1 : 0)
      : isChild ? Math.max(1, Math.trunc(order)) : 0;
  }

  /** Salva il processo in modifica; restituisce true se il salvataggio è riuscito. */
  async save(): Promise<boolean> {
    this.saving = true;
    this.notifier.notify();
    try {
      const schedulerId = this.workspace.selectedSchedulerId;
      if (schedulerId === null) throw new Error('Seleziona prima uno scheduler.');
      this.validateSequenceOrder();
      const request = this.saveRequest();
      if (this.editingProcessId === null) {
        await firstValueFrom(this.api.createProcess(schedulerId, request));
      } else {
        await firstValueFrom(this.api.updateProcess(schedulerId, this.editingProcessId, request));
      }
      this.notifications.success('Salvato', 'Le modifiche sono state salvate.');
      await this.workspace.refresh();
      return true;
    } catch (error) {
      this.notifications.reportError(error);
      return false;
    } finally {
      this.saving = false;
      this.notifier.notify();
    }
  }

  private saveRequest(): CreateProcess {
    const request: CreateProcess = {
      ...this.processForm,
      startSchedule: this.processForm.startSchedule ?? [],
    };
    if (this.processForm.type !== 'SHELL_CMD') return request;
    if (this.shellExecutionMode === 'command') {
      if (!this.shellCommandId || !this.workspace.shellCommands.some((command) => command.id === this.shellCommandId)) {
        throw new Error('Seleziona un comando autorizzato configurato sul backend.');
      }
      return { ...request, configuration: JSON.stringify({ commandId: this.shellCommandId }) };
    }
    const scriptId = this.shellScriptName;
    if (scriptId === null || !this.workspace.shellScripts.some((script) => script.id === scriptId)) {
      throw new Error('Seleziona uno script presente nella directory approvata sul backend.');
    }
    return { ...request, configuration: JSON.stringify({ script: String(scriptId) }) };
  }

  // ---- Gerarchia ----

  private validateSequenceOrder(): void {
    const parentId = this.processForm.parentProcessId;
    if (parentId === null) {
      this.processForm.sequenceOrder = 0;
      return;
    }
    const order = Number(this.processForm.sequenceOrder);
    if (!Number.isInteger(order) || order < 1) {
      throw new Error('L\'ordine di un figlio deve partire da 1: lo 0 è occupato dal padre.');
    }
    const processes = this.workspace.processes;
    const rootId = this.rootIdOf(processes.find((item) => item.id === parentId)!);
    const occupied = processes.find((item) =>
      item.id !== this.editingProcessId && item.id !== rootId
      && this.rootIdOf(item) === rootId && item.sequenceOrder === order);
    if (occupied) {
      throw new Error(`L'ordine ${order} è già occupato da "${occupied.name}". Primo ordine libero: ${this.nextFreeOrder(rootId)}.`);
    }
  }

  private nextFreeOrder(rootId: number): number {
    const used = new Set(this.workspace.processes
      .filter((item) => item.id !== this.editingProcessId && item.id !== rootId && this.rootIdOf(item) === rootId)
      .map((item) => item.sequenceOrder));
    let order = 1;
    while (used.has(order)) order += 1;
    return order;
  }

  private nextTreeOrder(parentId: number): number {
    const processes = this.workspace.processes;
    let root = processes.find((item) => item.id === parentId);
    const visited = new Set<number>();
    while (root && root.parentProcessId !== null && !visited.has(root.id)) {
      visited.add(root.id);
      root = processes.find((item) => item.id === root!.parentProcessId);
    }
    if (!root) return 1;
    return this.nextFreeOrder(root.id);
  }

  private rootIdOf(process: ProcessDefinition): number {
    let current = process;
    const visited = new Set<number>();
    while (current.parentProcessId !== null && !visited.has(current.id)) {
      visited.add(current.id);
      const parent = this.workspace.processes.find((item) => item.id === current.parentProcessId);
      if (!parent) break;
      current = parent;
    }
    return current.id;
  }

  processDepth(process: ProcessDefinition): number {
    let depth = 0;
    let parentId = process.parentProcessId;
    const visited = new Set<number>([process.id]);
    while (parentId !== null && !visited.has(parentId)) {
      visited.add(parentId);
      const parent = this.workspace.processes.find((item) => item.id === parentId);
      if (!parent) break;
      depth += 1;
      parentId = parent.parentProcessId;
    }
    return depth;
  }

  parentProcessName(parentProcessId: number): string {
    return this.workspace.processes.find((process) => process.id === parentProcessId)?.name ?? `#${parentProcessId}`;
  }

  private descendantIds(processId: number): number[] {
    return this.workspace.processes
      .filter((item) => item.parentProcessId === processId && item.enabled)
      .flatMap((item) => [item.id, ...this.descendantIds(item.id)]);
  }

  // ---- Pianificazione ----

  // Giorni (0=domenica..6=sabato) non selezionabili: tutti tranne quello della pianificazione.
  disabledWeekdays(dayOfWeek: string): number[] {
    const allowed = ProcessService.weekdayNumbers[dayOfWeek];
    return [0, 1, 2, 3, 4, 5, 6].filter((weekday) => weekday !== allowed);
  }

  // Il modello resta ISO (aaaa-mm-gg); il datepicker lavora con Date mostrate come gg/mm/aaaa.
  startDateValue(value?: string | null): Date | null {
    if (!value) {
      return null;
    }
    let date = this.startDateCache.get(value);
    if (!date) {
      const [year, month, day] = value.split('-').map(Number);
      date = new Date(year, month - 1, day);
      this.startDateCache.set(value, date);
    }
    return date;
  }

  toggleScheduleDay(dayOfWeek: ProcessScheduleDayName, selected: boolean): void {
    const schedule = this.processForm.startSchedule ?? [];
    const existing = schedule.find((day) => day.dayOfWeek === dayOfWeek);
    if (selected && !existing) {
      this.processForm.startSchedule = [...schedule, { dayOfWeek, times: ['09:00'], startDate: null }];
    } else if (!selected && existing) {
      this.processForm.startSchedule = schedule.filter((day) => day.dayOfWeek !== dayOfWeek);
    }
  }

  addScheduleTime(dayOfWeek: string): void {
    const day = this.processForm.startSchedule?.find((item) => item.dayOfWeek === dayOfWeek);
    day?.times.push('09:00');
  }

  removeScheduleTime(dayOfWeek: string, index: number): void {
    const day = this.processForm.startSchedule?.find((item) => item.dayOfWeek === dayOfWeek);
    day?.times.splice(index, 1);
    if (day?.times.length === 0) {
      this.toggleScheduleDay(day.dayOfWeek, false);
    }
  }

  processScheduleSummary(process: ProcessDefinition): string {
    const days = (process.startSchedule ?? [])
      .map((day) => `${this.scheduleDayOptions.find((option) => option.value === day.dayOfWeek)?.label ?? day.dayOfWeek}: ${day.times.join(', ')}${day.startDate ? ` dal ${formatItalianDate(day.startDate)}` : ''}`)
      .join(' · ');
    return days || 'Avvio manuale';
  }

  isProcessScheduled(process: ProcessDefinition): boolean {
    return (process.startSchedule ?? []).some((day) => day.times.length > 0);
  }

  // ---- Controlli di esecuzione ----

  async run(action: ProcessAction, process: ProcessDefinition): Promise<void> {
    const requests: Record<ProcessAction, () => ReturnType<ScheduleApiService['startProcess']>> = {
      start: () => this.api.startProcess(process.schedulerId, process.id),
      pause: () => this.api.pauseProcess(process.schedulerId, process.id),
      resume: () => this.api.resumeProcess(process.schedulerId, process.id),
      stop: () => this.api.stopProcess(process.schedulerId, process.id),
    };
    if (action === 'start') {
      // Mostra subito lo stato "In coda" su padre e figli, poi esegue l'avvio.
      [process.id, ...this.descendantIds(process.id)].forEach((id) => this.workspace.pendingStates.set(id, 'QUEUED'));
      this.notifier.notify();
      await new Promise((resolve) => setTimeout(resolve, 400));
    }
    try {
      const execution = await firstValueFrom(requests[action]());
      const detail = action === 'start'
        ? `${process.name}: esecuzione accodata in background. I processi indipendenti possono procedere in parallelo.`
        : `${process.name}: ${this.labels.stateLabel(execution.state)}`;
      this.notifications.success(action === 'start' ? 'Esecuzione accodata' : 'Comando inviato', detail);
      try {
        await this.workspace.loadProcesses();
      } catch (error) {
        this.notifications.reportError(error);
      }
    } catch (error) {
      this.notifications.reportError(error);
    } finally {
      this.workspace.pendingStates.clear();
      this.notifier.notify();
    }
  }

  startStatusPolling(): void {
    if (this.statusPollingInterval !== null) return;
    this.statusPollingInterval = setInterval(() => {
      const schedulerId = this.workspace.selectedSchedulerId;
      if (this.statusRefreshInProgress || schedulerId === null || this.workspace.processes.length === 0) return;
      this.statusRefreshInProgress = true;
      void this.workspace.loadProcessHistory(schedulerId, this.workspace.processes, true)
        .catch((error: unknown) => this.notifications.reportError(error))
        .finally(() => {
          this.statusRefreshInProgress = false;
        });
    }, 2000);
  }

  stopStatusPolling(): void {
    if (this.statusPollingInterval === null) return;
    clearInterval(this.statusPollingInterval);
    this.statusPollingInterval = null;
  }

  // ---- Dettaglio processo e console ----

  /** Apre il dettaglio; `afterRefresh` viene chiamato a ogni aggiornamento della console. */
  async openDetails(process: ProcessDefinition, afterRefresh?: () => void): Promise<boolean> {
    this.closeDetails();
    try {
      this.detailExecutions = (await firstValueFrom(this.api.processHistory(process.schedulerId, process.id)))
        .sort((left, right) => right.id - left.id);
      this.detailProcess = process;
      this.detailRefreshed = afterRefresh ?? null;
      this.scheduleDetailPolling(process);
      this.notifier.notify();
      return true;
    } catch (error) {
      this.notifications.reportError(error);
      return false;
    }
  }

  closeDetails(): void {
    if (this.detailPollingTimer !== null) {
      clearTimeout(this.detailPollingTimer);
      this.detailPollingTimer = null;
    }
    this.detailProcess = null;
    this.detailRefreshed = null;
  }

  private scheduleDetailPolling(process: ProcessDefinition): void {
    if (this.detailProcess?.id !== process.id
      || !this.labels.isExecutionActive(this.latestExecutionState(this.detailExecutions))) {
      return;
    }
    this.detailPollingTimer = setTimeout(() => void this.pollDetails(process), 500);
  }

  private async pollDetails(process: ProcessDefinition): Promise<void> {
    this.detailPollingTimer = null;
    if (this.detailProcess?.id !== process.id) return;
    try {
      const executions = await firstValueFrom(this.api.processHistory(process.schedulerId, process.id));
      if (this.detailProcess?.id !== process.id) return;
      this.detailExecutions = executions.sort((left, right) => right.id - left.id);
      this.notifier.notify();
      this.detailRefreshed?.();
      this.scheduleDetailPolling(process);
    } catch (error) {
      this.notifications.reportError(error);
    }
  }

  private latestExecutionState(executions: ProcessExecution[]): string {
    return executions.reduce<ProcessExecution | null>(
      (latest, execution) => latest === null || execution.id > latest.id ? execution : latest,
      null,
    )?.state ?? 'CREATED';
  }

  // ---- Target SHELL_CMD ----

  readApprovedScriptName(configuration: string | null): number | null {
    if (!configuration) return null;
    try {
      const parsed: unknown = JSON.parse(configuration);
      if (typeof parsed === 'object' && parsed !== null && 'script' in parsed) {
        const script = parsed.script;
        if (typeof script === 'number' && Number.isInteger(script)) return script;
        if (typeof script === 'string') {
          const legacyScript = this.workspace.shellScripts.find((item) => item.fileName === script);
          const id = Number(script);
          return legacyScript?.id ?? (Number.isInteger(id) && id > 0 ? id : null);
        }
      }
    } catch {
      return null;
    }
    return null;
  }

  readApprovedCommandId(configuration: string | null): number | null {
    if (!configuration) return null;
    try {
      const parsed: unknown = JSON.parse(configuration);
      if (typeof parsed === 'object' && parsed !== null && 'commandId' in parsed) {
        const id = Number(parsed.commandId);
        return Number.isInteger(id) && id > 0 ? id : null;
      }
    } catch {
      return null;
    }
    return null;
  }

  shellTargetDescription(configuration: string | null): string {
    const scriptId = this.readApprovedScriptName(configuration);
    if (scriptId !== null) {
      const script = this.workspace.shellScripts.find((item) => item.id === scriptId);
      return script ? script.fileName : `Script approvato: #${scriptId}`;
    }
    const commandId = this.readApprovedCommandId(configuration);
    const command = this.workspace.shellCommands.find((item) => item.id === commandId);
    return command
      ? command.executable
      : commandId ? `Comando autorizzato: ${commandId}` : '—';
  }

  selectedShellScriptType(): string {
    return this.workspace.shellScripts.find((script) => script.id === this.shellScriptName)?.type ?? '—';
  }

  selectedShellCommandType(): string {
    return this.workspace.shellCommands.find((command) => command.id === this.shellCommandId)?.type ?? '—';
  }
}
