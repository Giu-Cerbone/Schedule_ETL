import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
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
  ApprovedShellScript,
  AuthorizedShellCommand,
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
export class App implements OnInit {
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
  loading = true;
  saving = false;
  error = '';
  dialogOpen = false;
  dialogMode: DialogMode = null;
  editingProcessId: number | null = null;
  shellScriptName = '';
  shellExecutionMode: 'script' | 'command' = 'script';
  shellCommandId: string | null = null;
  adminUsername = '';
  adminPassword = '';
  editingCommandId: string | null = null;
  commandEditorOpen = false;
  commandForm: AuthorizedShellCommand = {
    id: '',
    label: '',
    type: '',
    executable: '',
    arguments: [],
  };
  commandArgumentsText = '';
  editingScriptName: string | null = null;
  scriptEditorOpen = false;
  scriptForm: ApprovedShellScript = { fileName: '', type: '', content: '' };
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
      this.processTypes = await firstValueFrom(this.api.listProcessTypes());
      [this.shellCommands, this.shellScripts] = await Promise.all([
        firstValueFrom(this.api.listShellCommands()),
        firstValueFrom(this.api.listShellScripts()),
      ]);
      this.schedulers = schedulers;
      this.profiles = profiles;
      this.groups = groups;
      if (!this.schedulers.some((item) => item.id === this.selectedSchedulerId)) {
        this.selectedSchedulerId = this.schedulers[0]?.id ?? null;
      }
      await this.loadProcesses();
    } catch (error) {
      this.reportError(error);
    } finally {
      this.loading = false;
    }
  }

  async loadProcesses(): Promise<void> {
    if (this.selectedSchedulerId === null) {
      this.processes = [];
      return;
    }
    this.processes = await firstValueFrom(this.api.listProcesses(this.selectedSchedulerId));
    const histories = await Promise.all(
      this.processes.map((process) => firstValueFrom(this.api.processHistory(process.schedulerId, process.id))),
    );
    this.executions = histories.flat().sort((left, right) => right.id - left.id);
  }

  async refreshShellCatalogs(): Promise<void> {
    try {
      [this.shellCommands, this.shellScripts] = await Promise.all([
        firstValueFrom(this.api.listShellCommands()),
        firstValueFrom(this.api.listShellScripts()),
      ]);
      if (this.adminAuth.authenticated) {
        this.adminShellCommands = await firstValueFrom(this.api.adminListShellCommands());
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
    this.cancelScriptEdit();
    this.cancelCommandEdit();
  }

  newApprovedScript(): void {
    this.editingScriptName = null;
    this.scriptEditorOpen = true;
    this.scriptForm = { fileName: '', type: '', content: '' };
  }

  async editApprovedScript(fileName: string): Promise<void> {
    try {
      this.scriptForm = await firstValueFrom(this.api.getApprovedScript(fileName));
      this.editingScriptName = fileName;
      this.scriptEditorOpen = true;
    } catch (error) {
      this.reportError(error);
    }
  }

  cancelScriptEdit(): void {
    this.editingScriptName = null;
    this.scriptEditorOpen = false;
    this.scriptForm = { fileName: '', type: '', content: '' };
  }

  async saveApprovedScript(): Promise<void> {
    this.saving = true;
    try {
      if (this.editingScriptName === null) {
        await firstValueFrom(this.api.createApprovedScript({
          fileName: this.scriptForm.fileName,
          content: this.scriptForm.content,
        }));
      } else {
        await firstValueFrom(this.api.updateApprovedScript(this.editingScriptName, {
          fileName: this.scriptForm.fileName,
          content: this.scriptForm.content,
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

  async deleteApprovedScript(fileName: string): Promise<void> {
    if (!window.confirm(`Eliminare lo script autorizzato "${fileName}"?`)) return;
    try {
      await firstValueFrom(this.api.deleteApprovedScript(fileName));
      if (this.editingScriptName === fileName) this.cancelScriptEdit();
      await this.refreshShellCatalogs();
      this.messages.add({ severity: 'success', summary: 'Script eliminato', detail: fileName });
    } catch (error) {
      this.reportError(error);
    }
  }

  newAuthorizedCommand(): void {
    this.editingCommandId = null;
    this.commandEditorOpen = true;
    this.commandForm = { id: '', label: '', type: '', executable: '', arguments: [] };
    this.commandArgumentsText = '';
  }

  editAuthorizedCommand(command: AuthorizedShellCommand): void {
    this.editingCommandId = command.id;
    this.commandEditorOpen = true;
    this.commandForm = { ...command, arguments: [...command.arguments] };
    this.commandArgumentsText = command.arguments.join('\n');
  }

  cancelCommandEdit(): void {
    this.editingCommandId = null;
    this.commandEditorOpen = false;
    this.commandForm = { id: '', label: '', type: '', executable: '', arguments: [] };
    this.commandArgumentsText = '';
  }

  async saveAuthorizedCommand(): Promise<void> {
    this.saving = true;
    try {
      const request: AuthorizedShellCommand = {
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
    if (!window.confirm(`Eliminare il comando autorizzato "${command.label}"?`)) return;
    try {
      await firstValueFrom(this.api.deleteShellCommand(command.id));
      if (this.editingCommandId === command.id) this.cancelCommandEdit();
      await this.refreshShellCatalogs();
      this.messages.add({ severity: 'success', summary: 'Comando eliminato', detail: command.label });
    } catch (error) {
      this.reportError(error);
    }
  }

  editAuthorizedCommandById(id: string): void {
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
      await this.loadProcesses();
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
      this.view = 'history';
    } catch (error) {
      this.reportError(error);
    }
  }

  async openProcessDetails(process: ProcessDefinition): Promise<void> {
    try {
      this.processDetailExecutions = (await firstValueFrom(
        this.api.processHistory(process.schedulerId, process.id),
      )).sort((left, right) => right.id - left.id);
      this.selectedProcessDetails = process;
      this.detailsTitle = `Dettaglio processo · ${process.name}`;
      this.dialogMode = null;
      this.dialogOpen = true;
    } catch (error) {
      this.reportError(error);
    }
  }

  async runProcess(action: ProcessAction, process: ProcessDefinition): Promise<void> {
    const requests: Record<ProcessAction, () => ReturnType<ScheduleApiService['startProcess']>> = {
      start: () => this.api.startProcess(process.schedulerId, process.id),
      pause: () => this.api.pauseProcess(process.schedulerId, process.id),
      resume: () => this.api.resumeProcess(process.schedulerId, process.id),
      stop: () => this.api.stopProcess(process.schedulerId, process.id),
    };
    try {
      const execution = await firstValueFrom(requests[action]());
      this.messages.add({ severity: 'success', summary: 'Comando inviato', detail: `${process.name}: ${this.stateLabel(execution.state)}` });
      try {
        await this.loadProcesses();
      } catch (error) {
        this.reportError(error);
      }
    } catch (error) {
      this.reportError(error);
    }
  }

  openDialog(mode: Exclude<DialogMode, null>): void {
    this.dialogMode = mode;
    this.dialogOpen = true;
    if (mode === 'scheduler') {
      this.schedulerForm = { name: '', description: '', cronExpression: '0 0 2 * * ?', ownerProfileId: this.profiles[0]?.id ?? null, groupId: null, status: 'ACTIVE' };
    } else if (mode === 'process') {
      this.editingProcessId = null;
      this.shellScriptName = '';
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
      : '';
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
      sequenceOrder: process.sequenceOrder,
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
    const siblingOrders = this.processes
      .filter((process) => process.parentProcessId === parent.id)
      .map((process) => process.sequenceOrder);
    this.processForm.sequenceOrder = siblingOrders.length ? Math.max(...siblingOrders) + 1 : 0;
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
      STOPPED: 'Fermato', COMPLETED: 'Completato', FAILED: 'Fallito', CANCELLED: 'Annullato',
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
    return this.executions.find((execution) => execution.processDefinitionId === processId)?.state ?? 'CREATED';
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
    const scriptName = this.shellScriptName.trim();
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(scriptName) || scriptName === '.' || scriptName === '..') {
      throw new Error('Per SHELL_CMD indica il solo nome di uno script approvato, senza percorso.');
    }
    if (!this.shellScripts.some((script) => script.fileName === scriptName)) {
      throw new Error('Seleziona uno script presente nella directory approvata sul backend.');
    }
    return { ...this.processForm, configuration: JSON.stringify({ script: scriptName }) };
  }

  private readApprovedScriptName(configuration: string | null): string {
    if (!configuration) return '';
    try {
      const parsed: unknown = JSON.parse(configuration);
      if (typeof parsed === 'object' && parsed !== null && 'script' in parsed
        && typeof parsed.script === 'string') {
        return parsed.script;
      }
    } catch {
      return '';
    }
    return '';
  }

  private readApprovedCommandId(configuration: string | null): string | null {
    if (!configuration) return null;
    try {
      const parsed: unknown = JSON.parse(configuration);
      if (typeof parsed === 'object' && parsed !== null && 'commandId' in parsed
        && typeof parsed.commandId === 'string') {
        return parsed.commandId;
      }
    } catch {
      return null;
    }
    return null;
  }

  shellTargetDescription(configuration: string | null): string {
    const scriptName = this.readApprovedScriptName(configuration);
    if (scriptName) {
      const scriptType = this.shellScripts.find((script) => script.fileName === scriptName)?.type;
      return `Script approvato: ${scriptName}${scriptType ? ` · ${scriptType}` : ''}`;
    }
    const commandId = this.readApprovedCommandId(configuration);
    const command = this.shellCommands.find((item) => item.id === commandId);
    return command
      ? `${command.label} · ${command.type}`
      : commandId ? `Comando autorizzato: ${commandId}` : '—';
  }

  selectedShellScriptType(): string {
    return this.shellScripts.find((script) => script.fileName === this.shellScriptName)?.type ?? '—';
  }

  selectedShellCommandType(): string {
    return this.shellCommands.find((command) => command.id === this.shellCommandId)?.type ?? '—';
  }
}
