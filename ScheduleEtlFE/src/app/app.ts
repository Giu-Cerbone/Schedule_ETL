import { CommonModule } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  QueryList,
  ViewChildren,
  inject,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { ChartModule } from 'primeng/chart';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { firstValueFrom, Observable } from 'rxjs';
import { TooltipModule } from 'primeng/tooltip';
import {
  CreateGroup,
  CreateProcess,
  CreateProfile,
  CreateScheduler,
  EditableSchedulerStatus,
  ApprovedShellScript,
  AuthorizedShellCommandRequest,
  AuthorizedShellCommand,
  FileBrowseResponse,
  RegisteredShellScript,
  ProcessDefinition,
  ProcessExecution,
  ProcessType,
  ShellCommandOption,
  ShellScriptOption,
  Scheduler,
  UserGroup,
  UserProfile,
} from './api.models';
import { ScheduleApiService } from './schedule-api.service';
import { AdminAuthService } from './admin-auth.service';
import 'chart.js/auto';

type ViewName = 'overview' | 'schedulers' | 'processes' | 'ownership' | 'shell-commands' | 'history';
type DialogMode = 'scheduler' | 'process' | 'profile' | 'group' | 'assign' | null;
type ProcessAction = 'start' | 'pause' | 'resume' | 'stop';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    ChartModule,
    DialogModule,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
    TextareaModule,
    ToastModule,
    TooltipModule,
  ],
  providers: [MessageService],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnDestroy, OnInit {
  view: ViewName = 'overview';
  schedulers: Scheduler[] = [];
  profiles: UserProfile[] = [];
  groups: UserGroup[] = [];
  processes: ProcessDefinition[] = [];
  processTypes: ProcessType[] = [];
  shellCommands: ShellCommandOption[] = [];
  shellScripts: ShellScriptOption[] = [];
  adminShellCommands: AuthorizedShellCommand[] = [];
  executions: ProcessExecution[] = [];
  selectedSchedulerId: number | null = null;
  selectedProcess: ProcessDefinition | null = null;
  selectedProcessDetails: ProcessDefinition | null = null;
  processDetailExecutions: ProcessExecution[] = [];
  private processDetailPollingTimer: ReturnType<typeof setTimeout> | null = null;
  @ViewChildren('consoleOutput') private consoleOutputs!: QueryList<ElementRef<HTMLElement>>;
  loading = true;
  saving = false;
  readonly schedulerStatusOptions: { label: string; value: EditableSchedulerStatus }[] = [
    { label: 'Attivo', value: 'ACTIVE' },
    { label: 'Inattivo', value: 'INACTIVE' },
    { label: 'In pausa', value: 'PAUSED' },
  ];
  readonly updatingSchedulerIds = new Set<number>();
  error = '';
  dialogOpen = false;
  dialogMode: DialogMode = null;
  editingProcessId: number | null = null;
  shellScriptName: number | null = null;
  shellExecutionMode: 'script' | 'command' = 'script';
  shellCommandId: number | null = null;
  adminUsername = '';
  adminPassword = '';
  editingCommandId: number | null = null;
  commandEditorOpen = false;
  commandForm: AuthorizedShellCommandRequest = {
    name: '',
    type: '',
    executable: '',
    arguments: [],
  };
  commandArgumentsText = '';
  editingScriptName: number | null = null;
  scriptEditorOpen = false;
  scriptForm: ApprovedShellScript = { name: '', fileName: '', type: '', content: '' };
  registeredScripts: RegisteredShellScript[] = [];
  selectedScriptPath = '';
  selectedScriptName = '';
  browserOpen = false;
  browserLoading = false;
  browser: FileBrowseResponse = { path: '', parent: null, entries: [] };
  detailsTitle = '';
  detailsText = '';

  schedulerForm: CreateScheduler = {
    name: '',
    description: '',
    cronExpression: '0 0 2 * * ?',
    ownerProfileId: null,
    groupId: null,
    status: 'ACTIVE',
  };
  processForm: CreateProcess = {
    name: '',
    type: 'SQLDATABASE',
    description: '',
    configuration: '{}',
    enabled: true,
    parentProcessId: null,
    sequenceOrder: 0,
    runIfParentFails: false,
  };
  profileForm: CreateProfile = { username: '', fullName: '', email: '' };
  groupForm: CreateGroup = { name: '', description: '' };
  assignGroupId: number | null = null;

  readonly navigation: { id: ViewName; label: string; icon: string }[] = [
    { id: 'overview', label: 'Dashboard', icon: 'pi pi-th-large' },
    { id: 'schedulers', label: 'Scheduler', icon: 'pi pi-calendar' },
    { id: 'processes', label: 'Processi', icon: 'pi pi-sitemap' },
    { id: 'shell-commands', label: 'Comandi autorizzati', icon: 'pi pi-terminal' },
    { id: 'ownership', label: 'Utenti e gruppi', icon: 'pi pi-users' },
  ];
  readonly processActions: { id: ProcessAction; label: string; icon: string }[] = [
    { id: 'start', label: 'Avvia', icon: 'pi pi-play' },
    { id: 'pause', label: 'Pausa', icon: 'pi pi-pause' },
    { id: 'resume', label: 'Riprendi', icon: 'pi pi-refresh' },
    { id: 'stop', label: 'Ferma', icon: 'pi pi-stop' },
  ];
  readonly stateChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom' as const, labels: { usePointStyle: true, padding: 18 } } },
    cutout: '72%',
  };
  readonly activityChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: { x: { grid: { display: false } }, y: { beginAtZero: true, grid: { color: '#edf0f5' } } },
  };

  constructor(
    private readonly api: ScheduleApiService,
    private readonly messages: MessageService,
    readonly adminAuth: AdminAuthService,
  ) {}

  ngOnInit(): void {
    void this.refresh();
  }

  ngOnDestroy(): void {
    this.stopProcessStatusPolling();
    this.stopProcessDetailPolling();
  }

  get selectedScheduler(): Scheduler | undefined {
    return this.schedulers.find((item) => item.id === this.selectedSchedulerId);
  }

  get enabledProcesses(): number {
    return this.processes.filter((process) => process.enabled).length;
  }

  get activeSchedulerCount(): number {
    return this.schedulers.filter((scheduler) => scheduler.status === 'ACTIVE').length;
  }

  get runningProcesses(): number {
    return this.processes.filter((process) => this.latestState(process.id) === 'RUNNING').length;
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

  get availableParentProcesses(): ProcessDefinition[] {
    const excludedIds = new Set<number>();
    if (this.editingProcessId !== null) {
      const collectDescendants = (parentId: number): void => {
        for (const child of this.processes.filter((process) => process.parentProcessId === parentId)) {
          if (excludedIds.has(child.id)) continue;
          excludedIds.add(child.id);
          collectDescendants(child.id);
        }
      };
      excludedIds.add(this.editingProcessId);
      collectDescendants(this.editingProcessId);
    }
    return this.orderedProcesses.filter((process) => !excludedIds.has(process.id));
  }

  get stateChartData(): object {
    const counts = ['RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'STOPPED'].map(
      (state) => this.processes.filter((process) => this.latestState(process.id) === state).length,
    );
    return {
      labels: ['In esecuzione', 'In pausa', 'Completati', 'Falliti', 'Fermati'],
      datasets: [{ data: counts, backgroundColor: ['#536dfe', '#f5a623', '#25b887', '#ed6575', '#c7ced9'], borderWidth: 0 }],
    };
  }

  get activityChartData(): object {
    const days = Array.from({ length: 7 }, (_, index) => {
      const date = new Date();
      date.setDate(date.getDate() - 6 + index);
      return date;
    });
    return {
      labels: days.map((date) => date.toLocaleDateString('it-IT', { weekday: 'short' })),
      datasets: [{
        label: 'Esecuzioni',
        data: days.map((date) => this.executions.filter(
          (execution) => execution.startedAt && new Date(execution.startedAt).toDateString() === date.toDateString(),
        ).length),
        borderColor: '#536dfe',
        backgroundColor: 'rgba(83,109,254,.12)',
        fill: true,
        tension: 0.4,
        pointRadius: 3,
      }],
    };
  }

  async refresh(): Promise<void> {
    this.loading = true;
    this.error = '';
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
      this.reportError(error);
    } finally {
      this.loading = false;
      this.changeDetector.markForCheck();
    }
    void this.loadSupportingData();
  }

  async loadProcesses(waitForHistory = true): Promise<void> {
    if (this.selectedSchedulerId === null) {
      this.processes = [];
      this.executions = [];
      this.changeDetector.markForCheck();
      return;
    }
    const schedulerId = this.selectedSchedulerId;
    this.processes = await firstValueFrom(this.api.listProcesses(schedulerId));
    this.executions = [];
    this.changeDetector.markForCheck();
    if (waitForHistory) {
      await this.loadProcessHistory(schedulerId, this.processes);
    } else {
      void this.loadProcessHistory(schedulerId, this.processes).catch((error: unknown) => this.reportError(error));
    }
  }

  private async loadProcessHistory(
    schedulerId: number,
    processes: ProcessDefinition[],
    activeOnly = false,
  ): Promise<void> {
    const processesToLoad = activeOnly
      ? processes.filter((process) => this.isExecutionActive(this.latestState(process.id)))
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
      this.changeDetector.markForCheck();
    }
  }

  private async loadSupportingData(): Promise<void> {
    try {
      const [processTypes, shellCommands, shellScripts] = await Promise.all([
        firstValueFrom(this.api.listProcessTypes()),
        firstValueFrom(this.api.listShellCommands()),
        firstValueFrom(this.api.listShellScripts()),
      ]);
      this.processTypes = processTypes;
      this.shellCommands = shellCommands;
      this.shellScripts = shellScripts;
    } catch (error) {
      this.reportError(error);
    }
  }

  async refreshShellCatalogs(): Promise<void> {
    try {
      [this.shellCommands, this.shellScripts] = await Promise.all([
        firstValueFrom(this.api.listShellCommands()),
        firstValueFrom(this.api.listShellScripts()),
      ]);
      if (this.adminAuth.authenticated) {
        this.adminShellCommands = await firstValueFrom(this.api.adminListShellCommands());
        this.registeredScripts = await firstValueFrom(this.api.listRegisteredScripts());
      }
    } catch (error) {
      this.reportError(error);
    }
  }

  async loginAdmin(): Promise<void> {
    try {
      await this.adminAuth.login(this.adminUsername, this.adminPassword);
      this.adminPassword = '';
      await this.refreshShellCatalogs();
      this.messages.add({ severity: 'success', summary: 'Accesso amministratore', detail: 'Autenticazione completata.' });
    } catch (error) {
      this.reportError(error);
    }
  }

  logoutAdmin(): void {
    this.adminAuth.logout();
    this.adminShellCommands = [];
    this.registeredScripts = [];
    this.cancelScriptEdit();
    this.cancelCommandEdit();
  }

  newApprovedScript(): void {
    this.editingScriptName = null;
    this.scriptEditorOpen = true;
    this.scriptForm = { name: '', fileName: '', type: '', content: '' };
  }

  async editApprovedScript(id: number): Promise<void> {
    const script = this.registeredScript(id);
    if (!script) {
      this.reportError(new Error('Script autorizzato non trovato nel catalogo.'));
      return;
    }
    this.scriptForm = {
      name: script.name,
      fileName: script.fileName,
      type: script.type,
      content: '',
    };
    this.selectedScriptPath = script.path;
    this.selectedScriptName = script.name;
    this.editingScriptName = id;
    this.scriptEditorOpen = true;
  }

  registeredScript(id: number): RegisteredShellScript | undefined {
    return this.registeredScripts.find((script) => script.id === id);
  }

  selectBrowserFile(path: string): void {
    this.selectedScriptPath = path;
    const fileName = path.split(/[\\/]/).pop() ?? '';
    if (this.editingScriptName !== null) {
      this.scriptForm.fileName = fileName;
    } else if (!this.selectedScriptName) {
      this.selectedScriptName = fileName;
    }
  }

  async openFileBrowser(): Promise<void> {
    this.browserOpen = true;
    await this.browseTo(this.browser.path || this.selectedScriptPath.replace(/[\\/][^\\/]*$/, ''));
  }

  async browseTo(path: string): Promise<void> {
    this.browserLoading = true;
    try {
      this.browser = await firstValueFrom(this.api.browseServerFiles(path));
    } catch (error) {
      this.reportError(error);
      if (path) await this.browseTo('');
    } finally {
      this.browserLoading = false;
    }
  }

  async selectBrowserEntry(entry: { path: string; directory: boolean }): Promise<void> {
    if (entry.directory) {
      await this.browseTo(entry.path);
      return;
    }
    this.selectBrowserFile(entry.path);
    this.browserOpen = false;
  }

  cancelScriptEdit(): void {
    this.editingScriptName = null;
    this.scriptEditorOpen = false;
    this.selectedScriptPath = '';
    this.selectedScriptName = '';
    this.scriptForm = { name: '', fileName: '', type: '', content: '' };
  }

  async saveApprovedScript(): Promise<void> {
    this.saving = true;
    try {
      if (this.editingScriptName === null) {
        if (!this.selectedScriptName.trim()) throw new Error('Inserisci il nome dello script.');
        await firstValueFrom(this.api.registerScript(this.selectedScriptName.trim(), this.selectedScriptPath));
      } else {
        if (!this.selectedScriptPath) throw new Error('Seleziona il percorso dello script.');
        await firstValueFrom(this.api.updateRegisteredScriptContent(this.editingScriptName, {
          name: this.scriptForm.name,
          fileName: this.scriptForm.fileName,
          path: this.selectedScriptPath,
        }));
      }
      this.cancelScriptEdit();
      await this.refreshShellCatalogs();
      this.messages.add({ severity: 'success', summary: 'Script salvato', detail: 'Catalogo script aggiornato.' });
    } catch (error) {
      this.reportError(error);
    } finally {
      this.saving = false;
    }
  }

  async deleteApprovedScript(id: number): Promise<void> {
    const script = this.shellScripts.find((item) => item.id === id);
    if (!window.confirm(`Eliminare lo script autorizzato "${script?.name ?? id}"?`)) return;
    try {
      await firstValueFrom(this.api.unregisterScript(id));
      if (this.editingScriptName === id) this.cancelScriptEdit();
      await this.refreshShellCatalogs();
      this.messages.add({ severity: 'success', summary: 'Script eliminato', detail: script?.name ?? String(id) });
    } catch (error) {
      this.reportError(error);
    }
  }

  newAuthorizedCommand(): void {
    this.editingCommandId = null;
    this.commandEditorOpen = true;
    this.commandForm = { name: '', type: '', executable: '', arguments: [] };
    this.commandArgumentsText = '';
  }

  editAuthorizedCommand(command: AuthorizedShellCommand): void {
    this.editingCommandId = command.id;
    this.commandEditorOpen = true;
    this.commandForm = { name: command.name, type: command.type, executable: command.executable,
      arguments: [...command.arguments] };
    this.commandArgumentsText = command.arguments.join('\n');
  }

  cancelCommandEdit(): void {
    this.editingCommandId = null;
    this.commandEditorOpen = false;
    this.commandForm = { name: '', type: '', executable: '', arguments: [] };
    this.commandArgumentsText = '';
  }

  async saveAuthorizedCommand(): Promise<void> {
    this.saving = true;
    try {
      const request: AuthorizedShellCommandRequest = {
        ...this.commandForm,
        arguments: this.commandArgumentsText.split(/\r?\n/).filter((argument) => argument.length > 0),
      };
      if (this.editingCommandId === null) {
        await firstValueFrom(this.api.createShellCommand(request));
      } else {
        await firstValueFrom(this.api.updateShellCommand(this.editingCommandId, request));
      }
      this.cancelCommandEdit();
      await this.refreshShellCatalogs();
      this.messages.add({ severity: 'success', summary: 'Comando salvato', detail: 'Catalogo comandi aggiornato.' });
    } catch (error) {
      this.reportError(error);
    } finally {
      this.saving = false;
    }
  }

  async deleteAuthorizedCommand(command: AuthorizedShellCommand): Promise<void> {
    if (!window.confirm(`Eliminare il comando autorizzato "${command.name}"?`)) return;
    try {
      await firstValueFrom(this.api.deleteShellCommand(command.id));
      if (this.editingCommandId === command.id) this.cancelCommandEdit();
      await this.refreshShellCatalogs();
      this.messages.add({ severity: 'success', summary: 'Comando eliminato', detail: command.name });
    } catch (error) {
      this.reportError(error);
    }
  }

  editAuthorizedCommandById(id: number): void {
    const command = this.adminShellCommands.find((item) => item.id === id);
    if (!command) {
      this.reportError(new Error('Il comando autorizzato non è più disponibile. Aggiorna il catalogo.'));
      return;
    }
    this.editAuthorizedCommand(command);
  }

  async chooseScheduler(id: number): Promise<void> {
    this.selectedSchedulerId = id;
    try {
      await this.loadProcesses(false);
    } catch (error) {
      this.reportError(error);
    }
  }

  async openHistory(process: ProcessDefinition): Promise<void> {
    this.selectedProcess = process;
    this.selectedProcessDetails = null;
    try {
      this.executions = (await firstValueFrom(this.api.processHistory(process.schedulerId, process.id)))
        .sort((left, right) => right.id - left.id);
      this.setView('history');
    } catch (error) {
      this.reportError(error);
    }
  }

  async openProcessDetails(process: ProcessDefinition): Promise<void> {
    this.stopProcessDetailPolling();
    try {
      this.processDetailExecutions = (await firstValueFrom(
        this.api.processHistory(process.schedulerId, process.id),
      )).sort((left, right) => right.id - left.id);
      this.selectedProcessDetails = process;
      this.detailsTitle = `Dettaglio processo · ${process.name}`;
      this.dialogMode = null;
      this.dialogOpen = true;
      this.scheduleProcessDetailPolling(process);
    } catch (error) {
      this.reportError(error);
    }
  }

  onDialogVisibilityChange(visible: boolean): void {
    if (!visible) this.stopProcessDetailPolling();
  }

  private scheduleProcessDetailPolling(process: ProcessDefinition): void {
    if (!this.dialogOpen || this.selectedProcessDetails?.id !== process.id
      || !this.isExecutionActive(this.latestExecutionState(this.processDetailExecutions))) {
      return;
    }
    this.processDetailPollingTimer = setTimeout(() => void this.pollProcessDetails(process), 500);
  }

  private async pollProcessDetails(process: ProcessDefinition): Promise<void> {
    this.processDetailPollingTimer = null;
    if (!this.dialogOpen || this.selectedProcessDetails?.id !== process.id) return;
    try {
      const executions = await firstValueFrom(this.api.processHistory(process.schedulerId, process.id));
      if (!this.dialogOpen || this.selectedProcessDetails?.id !== process.id) return;
      this.processDetailExecutions = executions.sort((left, right) => right.id - left.id);
      this.changeDetector.detectChanges();
      for (const output of this.consoleOutputs) {
        output.nativeElement.scrollTop = output.nativeElement.scrollHeight;
      }
      this.scheduleProcessDetailPolling(process);
    } catch (error) {
      this.reportError(error);
    }
  }

  private stopProcessDetailPolling(): void {
    if (this.processDetailPollingTimer !== null) {
      clearTimeout(this.processDetailPollingTimer);
      this.processDetailPollingTimer = null;
    }
  }

  private isExecutionActive(state: string): boolean {
    return ['QUEUED', 'RUNNING', 'PAUSED', 'STOPPING'].includes(state);
  }

  private latestExecutionState(executions: ProcessExecution[]): string {
    return executions.reduce<ProcessExecution | null>(
      (latest, execution) => latest === null || execution.id > latest.id ? execution : latest,
      null,
    )?.state ?? 'CREATED';
  }

  async runProcess(action: ProcessAction, process: ProcessDefinition): Promise<void> {
    const requests: Record<ProcessAction, () => ReturnType<ScheduleApiService['startProcess']>> = {
      start: () => this.api.startProcess(process.schedulerId, process.id),
      pause: () => this.api.pauseProcess(process.schedulerId, process.id),
      resume: () => this.api.resumeProcess(process.schedulerId, process.id),
      stop: () => this.api.stopProcess(process.schedulerId, process.id),
    };
    if (action === 'start') {
      // Mostra subito lo stato "In coda" su padre e figli, poi esegue l'avvio.
      [process.id, ...this.descendantIds(process.id)].forEach((id) => this.pendingStates.set(id, 'QUEUED'));
      this.changeDetector.detectChanges();
      await new Promise((resolve) => setTimeout(resolve, 400));
    }
    try {
      const execution = await firstValueFrom(requests[action]());
      const detail = action === 'start'
        ? `${process.name}: esecuzione accodata in background. I processi indipendenti possono procedere in parallelo.`
        : `${process.name}: ${this.stateLabel(execution.state)}`;
      this.messages.add({
        severity: 'success',
        summary: action === 'start' ? 'Esecuzione accodata' : 'Comando inviato',
        detail,
      });
      try {
        await this.loadProcesses();
      } catch (error) {
        this.reportError(error);
      }
    } catch (error) {
      this.reportError(error);
    } finally {
      this.pendingStates.clear();
      this.changeDetector.detectChanges();
    }
  }

  async updateSchedulerStatus(scheduler: Scheduler, status: EditableSchedulerStatus): Promise<void> {
    if (scheduler.status === status || this.updatingSchedulerIds.has(scheduler.id)) return;
    this.updatingSchedulerIds.add(scheduler.id);
    try {
      const updated = await firstValueFrom(this.api.updateSchedulerStatus(scheduler.id, status));
      scheduler.status = updated.status;
      scheduler.updatedAt = updated.updatedAt;
      this.messages.add({
        severity: 'success',
        summary: 'Stato scheduler aggiornato',
        detail: `${scheduler.name}: ${this.stateLabel(updated.status)}`,
      });
    } catch (error) {
      this.reportError(error);
    } finally {
      this.updatingSchedulerIds.delete(scheduler.id);
    }
  }

  openDialog(mode: Exclude<DialogMode, null>): void {
    this.dialogMode = mode;
    this.dialogOpen = true;
    if (mode === 'scheduler') {
      this.schedulerForm = { name: '', description: '', cronExpression: '0 0 2 * * ?', ownerProfileId: this.profiles[0]?.id ?? null, groupId: null, status: 'ACTIVE' };
    } else if (mode === 'process') {
      this.editingProcessId = null;
      this.shellScriptName = null;
      this.shellExecutionMode = 'script';
      this.shellCommandId = null;
      this.processForm = {
        name: '',
        type: this.processTypes[0]?.code ?? 'SQLDATABASE',
        description: '',
        configuration: '{}',
        enabled: true,
        parentProcessId: null,
        sequenceOrder: 0,
        runIfParentFails: false,
      };
    } else if (mode === 'profile') {
      this.profileForm = { username: '', fullName: '', email: '' };
    } else if (mode === 'group') {
      this.groupForm = { name: '', description: '' };
    } else {
      this.assignGroupId = this.selectedScheduler?.groupId ?? null;
    }
  }

  editProcess(process: ProcessDefinition): void {
    this.dialogMode = 'process';
    this.dialogOpen = true;
    this.editingProcessId = process.id;
    this.shellScriptName = process.type === 'SHELL_CMD'
      ? this.readApprovedScriptName(process.configuration)
      : null;
    const configuredCommandId = process.type === 'SHELL_CMD'
      ? this.readApprovedCommandId(process.configuration)
      : null;
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

  addChildProcess(parent: ProcessDefinition): void {
    this.openDialog('process');
    this.processForm.parentProcessId = parent.id;
    this.processForm.sequenceOrder = this.nextTreeOrder(parent.id);
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
    const rootId = this.rootIdOf(this.processes.find((item) => item.id === parentId)!);
    const occupied = this.processes.find((item) =>
      item.id !== this.editingProcessId && item.id !== rootId
      && this.rootIdOf(item) === rootId && item.sequenceOrder === order);
    if (occupied) {
      throw new Error(`L'ordine ${order} è già occupato da "${occupied.name}". Primo ordine libero: ${this.nextFreeOrder(rootId)}.`);
    }
  }

  private nextFreeOrder(rootId: number): number {
    const used = new Set(this.processes
      .filter((item) => item.id !== this.editingProcessId && item.id !== rootId && this.rootIdOf(item) === rootId)
      .map((item) => item.sequenceOrder));
    let order = 1;
    while (used.has(order)) order += 1;
    return order;
  }

  onSequenceOrderChange(value: number | string | null): void {
    const order = Number(value);
    const isChild = this.processForm.parentProcessId !== null;
    this.processForm.sequenceOrder = !Number.isFinite(order) || value === null || value === ''
      ? (isChild ? 1 : 0)
      : isChild ? Math.max(1, Math.trunc(order)) : 0;
  }

  private nextTreeOrder(parentId: number): number {
    let root = this.processes.find((item) => item.id === parentId);
    const visited = new Set<number>();
    while (root && root.parentProcessId !== null && !visited.has(root.id)) {
      visited.add(root.id);
      root = this.processes.find((item) => item.id === root!.parentProcessId);
    }
    if (!root) return 1;
    return this.nextFreeOrder(root.id);
  }

  private rootIdOf(process: ProcessDefinition): number {
    let current = process;
    const visited = new Set<number>();
    while (current.parentProcessId !== null && !visited.has(current.id)) {
      visited.add(current.id);
      const parent = this.processes.find((item) => item.id === current.parentProcessId);
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
      const parent = this.processes.find((item) => item.id === parentId);
      if (!parent) break;
      depth += 1;
      parentId = parent.parentProcessId;
    }
    return depth;
  }

  parentProcessName(parentProcessId: number): string {
    return this.processes.find((process) => process.id === parentProcessId)?.name ?? `#${parentProcessId}`;
  }

  async saveDialog(): Promise<void> {
    this.saving = true;
    try {
      switch (this.dialogMode) {
        case 'scheduler': {
          const created = await firstValueFrom(this.api.createScheduler(this.schedulerForm));
          this.selectedSchedulerId = created.id;
          break;
        }
        case 'process': {
          if (this.selectedSchedulerId === null) throw new Error('Seleziona prima uno scheduler.');
          this.validateSequenceOrder();
          const processRequest = this.processSaveRequest();
          if (this.editingProcessId === null) {
            await firstValueFrom(this.api.createProcess(this.selectedSchedulerId, processRequest));
          } else {
            await firstValueFrom(this.api.updateProcess(this.selectedSchedulerId, this.editingProcessId, processRequest));
          }
          break;
        }
        case 'profile':
          await firstValueFrom(this.api.createProfile(this.profileForm));
          break;
        case 'group':
          await firstValueFrom(this.api.createGroup(this.groupForm));
          break;
        case 'assign':
          if (this.selectedSchedulerId === null || this.assignGroupId === null) throw new Error('Seleziona uno scheduler e un gruppo.');
          await firstValueFrom(this.api.assignSchedulerToGroup(this.selectedSchedulerId, this.assignGroupId));
          break;
        default:
          return;
      }
      this.dialogOpen = false;
      this.messages.add({ severity: 'success', summary: 'Salvato', detail: 'Le modifiche sono state salvate.' });
      await this.refresh();
    } catch (error) {
      this.reportError(error);
    } finally {
      this.saving = false;
    }
  }

  async deleteScheduler(scheduler: Scheduler): Promise<void> {
    if (!window.confirm(`Eliminare lo scheduler "${scheduler.name}"?`)) return;
    try {
      await firstValueFrom(this.api.deleteScheduler(scheduler.id));
      this.messages.add({ severity: 'success', summary: 'Scheduler eliminato', detail: scheduler.name });
      await this.refresh();
    } catch (error) {
      this.reportError(error);
    }
  }

  async showSchedulerDetails(scheduler: Scheduler): Promise<void> {
    await this.showDetails(`Scheduler · ${scheduler.name}`, () => this.api.getScheduler(scheduler.id));
  }

  async showProfileDetails(profile: UserProfile): Promise<void> {
    await this.showDetails(`Profilo · ${profile.fullName}`, () => this.api.getProfile(profile.id));
  }

  async showGroupDetails(group: UserGroup): Promise<void> {
    await this.showDetails(`Gruppo · ${group.name}`, () => this.api.getGroup(group.id));
  }

  async showDetails(title: string, request: () => Observable<unknown>): Promise<void> {
    try {
      const details: unknown = await firstValueFrom(request());
      this.detailsTitle = title;
      this.detailsText = JSON.stringify(details, null, 2);
      this.selectedProcessDetails = null;
      this.dialogMode = null;
      this.dialogOpen = true;
    } catch (error) {
      this.reportError(error);
    }
  }

  stateLabel(state: string): string {
    const labels: Record<string, string> = {
      ACTIVE: 'Attivo', INACTIVE: 'Inattivo', PAUSED: 'In pausa', DELETED: 'Eliminato',
      CREATED: 'Creato', QUEUED: 'In coda', RUNNING: 'In esecuzione', STOPPING: 'Arresto',
      STOPPED: 'Fermato', COMPLETED: 'Completato', FAILED: 'Fallito (fermato)', CANCELLED: 'Annullato',
    };
    return labels[state] ?? state;
  }

  stateSeverity(state: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    if (['ACTIVE', 'RUNNING', 'COMPLETED'].includes(state)) return 'success';
    if (['PAUSED', 'QUEUED', 'STOPPING'].includes(state)) return 'warn';
    if (['FAILED', 'DELETED'].includes(state)) return 'danger';
    if (['INACTIVE', 'STOPPED', 'CANCELLED'].includes(state)) return 'secondary';
    return 'info';
  }

  latestState(processId: number): string {
    const latestExecution = this.executions.reduce<ProcessExecution | null>((latest, execution) => {
      if (execution.processDefinitionId !== processId) return latest;
      return latest === null || execution.id > latest.id ? execution : latest;
    }, null);
    return this.pendingStates.get(processId) ?? latestExecution?.state ?? 'CREATED';
  }

  private pendingStates = new Map<number, string>();
  private processStatusPollingInterval: ReturnType<typeof setInterval> | null = null;
  private processStatusRefreshInProgress = false;
  private readonly changeDetector = inject(ChangeDetectorRef);

  private startProcessStatusPolling(): void {
    if (this.processStatusPollingInterval !== null) return;
    this.processStatusPollingInterval = setInterval(() => {
      if (this.processStatusRefreshInProgress || this.selectedSchedulerId === null || this.processes.length === 0) return;
      this.processStatusRefreshInProgress = true;
      void this.loadProcessHistory(this.selectedSchedulerId, this.processes, true)
        .catch((error: unknown) => this.reportError(error))
        .finally(() => {
          this.processStatusRefreshInProgress = false;
        });
    }, 2000);
  }

  private stopProcessStatusPolling(): void {
    if (this.processStatusPollingInterval === null) return;
    clearInterval(this.processStatusPollingInterval);
    this.processStatusPollingInterval = null;
  }

  private descendantIds(processId: number): number[] {
    return this.processes
      .filter((item) => item.parentProcessId === processId && item.enabled)
      .flatMap((item) => [item.id, ...this.descendantIds(item.id)]);
  }

  ownerName(id: number): string {
    return this.profiles.find((profile) => profile.id === id)?.fullName ?? `Profilo #${id}`;
  }

  groupName(id: number | null): string {
    return this.groups.find((group) => group.id === id)?.name ?? '—';
  }

  setView(view: ViewName): void {
    this.view = view;
    if (view === 'processes' && this.selectedSchedulerId !== null) void this.chooseScheduler(this.selectedSchedulerId);
    if (view === 'processes') this.startProcessStatusPolling();
    else this.stopProcessStatusPolling();
    if (view === 'shell-commands') void this.refreshShellCatalogs();
  }

  trackById(_index: number, item: { id: number }): number {
    return item.id;
  }

  private reportError(error: unknown): void {
    let message: string;
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        message = 'Backend non raggiungibile. Verifica che Spring Boot sia avviato su http://localhost:8080 e che il proxy frontend sia attivo.';
      } else if (error.status === 401) {
        message = 'Autenticazione richiesta o credenziali non valide. Per l’accesso admin configura SCHEDULETL_ADMIN_PASSWORD e riavvia il backend.';
      } else if (error.status === 403) {
        message = 'Operazione non autorizzata. Accedi con un account amministratore.';
      } else {
        const responseMessage = this.errorResponseMessage(error.error);
        message = responseMessage
          ? `Errore backend (${error.status}): ${responseMessage}`
          : `Errore backend (${error.status} ${error.statusText || 'HTTP'}).`;
      }
    } else {
      message = error instanceof Error ? error.message : 'Errore durante la comunicazione con il backend.';
    }
    this.error = message;
    this.messages.add({ severity: 'error', summary: 'Operazione non riuscita', detail: message, life: 6000 });
  }

  private errorResponseMessage(response: unknown): string {
    if (typeof response === 'string') return response;
    if (typeof response === 'object' && response !== null && 'message' in response
      && typeof response.message === 'string') {
      return response.message;
    }
    if (typeof response === 'object' && response !== null && 'detail' in response
      && typeof response.detail === 'string') {
      return response.detail;
    }
    return '';
  }

  private processSaveRequest(): CreateProcess {
    if (this.processForm.type !== 'SHELL_CMD') return this.processForm;
    if (this.shellExecutionMode === 'command') {
      if (!this.shellCommandId || !this.shellCommands.some((command) => command.id === this.shellCommandId)) {
        throw new Error('Seleziona un comando autorizzato configurato sul backend.');
      }
      return { ...this.processForm, configuration: JSON.stringify({ commandId: this.shellCommandId }) };
    }
    const scriptId = this.shellScriptName;
    if (scriptId === null || !this.shellScripts.some((script) => script.id === scriptId)) {
      throw new Error('Seleziona uno script presente nella directory approvata sul backend.');
    }
    return { ...this.processForm, configuration: JSON.stringify({ script: String(scriptId) }) };
  }

  private readApprovedScriptName(configuration: string | null): number | null {
    if (!configuration) return null;
    try {
      const parsed: unknown = JSON.parse(configuration);
      if (typeof parsed === 'object' && parsed !== null && 'script' in parsed) {
        const script = parsed.script;
        if (typeof script === 'number' && Number.isInteger(script)) return script;
        if (typeof script === 'string') {
          const legacyScript = this.shellScripts.find((item) => item.fileName === script);
          const id = Number(script);
          return legacyScript?.id ?? (Number.isInteger(id) && id > 0 ? id : null);
        }
      }
    } catch {
      return null;
    }
    return null;
  }

  private readApprovedCommandId(configuration: string | null): number | null {
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
      const script = this.shellScripts.find((item) => item.id === scriptId);
      return script ? script.fileName : `Script approvato: #${scriptId}`;
    }
    const commandId = this.readApprovedCommandId(configuration);
    const command = this.shellCommands.find((item) => item.id === commandId);
    return command
      ? command.executable
      : commandId ? `Comando autorizzato: ${commandId}` : '—';
  }

  selectedShellScriptType(): string {
    return this.shellScripts.find((script) => script.id === this.shellScriptName)?.type ?? '—';
  }

  selectedShellCommandType(): string {
    return this.shellCommands.find((command) => command.id === this.shellCommandId)?.type ?? '—';
  }
}
