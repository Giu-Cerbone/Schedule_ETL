import { TestBed } from '@angular/core/testing';
import { NEVER, Observable, of } from 'rxjs';
import { App } from './app';
import { ScheduleApiService } from './schedule-api.service';
import { AdminAuthService } from './admin-auth.service';
import { ProcessDefinition, ProcessExecution, Scheduler } from './api.models';
import { vi } from 'vitest';

describe('App', () => {
  let createdProcessRequest: object | undefined;
  let registeredScriptUpdate: { id: number; request: object } | undefined;
  let updatedSchedulerStatus: { id: number; status: string } | undefined;
  let schedulersResponse: Scheduler[];
  let processesResponse: ProcessDefinition[];
  let processHistoryResponse: Observable<ProcessExecution[]>;

  beforeEach(async () => {
    createdProcessRequest = undefined;
    registeredScriptUpdate = undefined;
    updatedSchedulerStatus = undefined;
    schedulersResponse = [];
    processesResponse = [];
    processHistoryResponse = of([]);
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [{
        provide: ScheduleApiService,
        useValue: {
          listSchedulers: () => of(schedulersResponse),
          updateSchedulerStatus: (id: number, status: string) => {
            updatedSchedulerStatus = { id, status };
            return of({ id, status, updatedAt: '2026-01-02T00:00:00' });
          },
          listProfiles: () => of([]),
          listGroups: () => of([]),
          adminListShellCommands: () => of([]),
          listRegisteredScripts: () => of([]),
          getRegisteredScriptContent: () => of({
            name: 'Daily report',
            fileName: 'daily-report.sh',
            type: 'SH',
            content: 'echo old',
          }),
          updateRegisteredScriptContent: (id: number, request: object) => {
            registeredScriptUpdate = { id, request };
            return of({});
          },
          listProcessTypes: () => of([
            { code: 'SQLDATABASE', description: 'Database SQL' },
            { code: 'SHELL_CMD', description: 'Shell command' },
            { code: 'SCRIPT', description: 'Script' },
          ]),
          listShellCommands: () => of([
            { id: 1, name: 'Print status', type: 'REPORT', executable: 'echo', arguments: ['status'] },
          ]),
          listShellScripts: () => of([
            { id: 2, name: 'Daily report', fileName: 'daily-report.sh', type: 'SH' },
          ]),
          listProcesses: () => of(processesResponse),
          processHistory: () => processHistoryResponse,
          createProcess: (_schedulerId: number, request: object) => {
            createdProcessRequest = request;
            return of({});
          },
        },
      }, {
        provide: AdminAuthService,
        useValue: {
          authenticated: false,
          username: '',
          login: async () => {},
          logout: () => {},
        },
      }],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the application shell', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand')?.textContent).toContain('SchedulETL');
  });

  it('saves a selected scheduler status and applies the backend response', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const app = fixture.componentInstance;
    const scheduler: Scheduler = {
      id: 1,
      name: 'Daily',
      description: null,
      cronExpression: '0 0 2 * * ?',
      status: 'ACTIVE',
      ownerProfileId: 1,
      groupId: null,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    };
    app.schedulers = [scheduler];

    await app.updateSchedulerStatus(scheduler, 'PAUSED');

    expect(updatedSchedulerStatus).toEqual({ id: 1, status: 'PAUSED' });
    expect(scheduler.status).toBe('PAUSED');
    expect(scheduler.updatedAt).toBe('2026-01-02T00:00:00');
    fixture.destroy();
  });

  it('keeps the dashboard visible while backend data is loading', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    fixture.componentInstance.loading = true;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.activity-panel')).not.toBeNull();
    expect(compiled.querySelector('.loading-state')).toBeNull();
  });

  it('shows the dashboard without waiting for process history', async () => {
    schedulersResponse = [{
      id: 1,
      name: 'Daily',
      description: null,
      cronExpression: '0 0 2 * * ?',
      status: 'ACTIVE',
      ownerProfileId: 1,
      groupId: null,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    }];
    processesResponse = [{
      id: 1,
      name: 'Report',
      type: 'SCRIPT',
      description: null,
      configuration: null,
      enabled: true,
      schedulerId: 1,
      parentProcessId: null,
      sequenceOrder: 0,
      runIfParentFails: false,
      createdAt: '2026-01-01T00:00:00',
    }];
    processHistoryResponse = NEVER;
    const app = TestBed.createComponent(App).componentInstance;

    await app.refresh();

    expect(app.loading).toBe(false);
    expect(app.processes).toHaveLength(1);
  });

  it('should provide a menu page for authorized commands and approved scripts', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.setView('shell-commands');
    app.shellCommands = [{ id: 1, name: 'Print status', type: 'REPORT', executable: 'echo', arguments: ['status'] }];
    app.shellScripts = [{ id: 2, name: 'Daily report', fileName: 'daily-report.sh', type: 'SH' }];

    expect(app.view).toBe('shell-commands');
    expect(app.navigation.some((item) => item.id === 'shell-commands')).toBe(true);
    expect(app.shellCommands[0].type).toBe('REPORT');
    expect(app.shellScripts[0].type).toBe('SH');
  });

  it('should edit a registered script through its admin API', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.registeredScripts = [{
      id: 2,
      name: 'Daily report',
      fileName: 'daily-report.sh',
      type: 'SH',
      path: 'C:\\scripts\\daily-report.sh',
    }];

    await app.editApprovedScript(2);
    expect(app.scriptForm.content).toBe('');
    app.selectBrowserFile('C:\\scripts\\daily-report-renamed.sh');

    await app.saveApprovedScript();

    expect(registeredScriptUpdate).toEqual({
      id: 2,
      request: {
        name: 'Daily report',
        fileName: 'daily-report-renamed.sh',
        path: 'C:\\scripts\\daily-report-renamed.sh',
      },
    });
  });

  it('shows the script content field only for process type SCRIPT', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.dialogOpen = true;
    app.dialogMode = 'process';
    app.processForm.type = 'SCRIPT';
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Contenuto script');

    const sqlFixture = TestBed.createComponent(App);
    const sqlApp = sqlFixture.componentInstance;
    sqlApp.dialogOpen = true;
    sqlApp.dialogMode = 'process';
    sqlApp.processForm.type = 'SQLDATABASE';
    sqlFixture.detectChanges();

    expect((sqlFixture.nativeElement as HTMLElement).textContent).not.toContain('Contenuto script');
  });

  it('should show the approved script catalog and its detected type for SHELL_CMD', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.processTypes = [{ code: 'SHELL_CMD', description: 'Shell command' }];
    app.shellScripts = [{ id: 2, name: 'Daily report', fileName: 'daily-report.sh', type: 'SH' }];
    app.openDialog('process');
    app.processForm.configuration = '{"script":"daily-report.sh"}';
    app.onProcessTypeChange('SHELL_CMD');
    fixture.detectChanges();

    expect(app.shellScriptName).toBe(2);
    expect(app.selectedShellScriptType()).toBe('SH');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('directory approvata configurata sul backend');
  });

  it('should show the continue-after-failure option only for child processes', () => {
    const rootFixture = TestBed.createComponent(App);
    rootFixture.componentInstance.openDialog('process');
    rootFixture.detectChanges();

    const rootOptions = Array.from((rootFixture.nativeElement as HTMLElement).querySelectorAll('.checkbox-label'));
    expect(rootOptions.some((label) => label.textContent?.includes('Esegui comunque se il processo padre fallisce'))).toBe(false);

    const childFixture = TestBed.createComponent(App);
    childFixture.componentInstance.openDialog('process');
    childFixture.componentInstance.processForm.parentProcessId = 1;
    childFixture.detectChanges();

    const childOptions = Array.from((childFixture.nativeElement as HTMLElement).querySelectorAll('.checkbox-label'));
    expect(childOptions.some((label) => label.textContent?.includes('Esegui comunque se il processo padre fallisce'))).toBe(true);
  });

  it('should submit SHELL_CMD as an approved script filename configuration', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.selectedSchedulerId = 1;
    app.dialogMode = 'process';
    app.processForm = {
      name: 'Report',
      type: 'SHELL_CMD',
      description: '',
      configuration: '{}',
      enabled: true,
      parentProcessId: null,
      sequenceOrder: 0,
      runIfParentFails: false,
    };
    app.shellScripts = [{ id: 2, name: 'Daily report', fileName: 'daily-report.sh', type: 'SH' }];
    app.shellScriptName = 2;

    await app.saveDialog();

    expect(createdProcessRequest).toMatchObject({
      type: 'SHELL_CMD',
      configuration: '{"script":"2"}',
    });
  });

  it('should submit the child setting to run after parent failure', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.selectedSchedulerId = 1;
    app.dialogMode = 'process';
    app.processForm = {
      name: 'Child',
      type: 'SQLDATABASE',
      description: '',
      configuration: '{}',
      enabled: true,
      parentProcessId: 1,
      sequenceOrder: 1,
      runIfParentFails: true,
    };
    app.processes = [{
      id: 1,
      name: 'Parent',
      type: 'SQLDATABASE',
      description: null,
      configuration: null,
      enabled: true,
      schedulerId: 1,
      parentProcessId: null,
      sequenceOrder: 0,
      runIfParentFails: false,
      createdAt: '2026-01-01T00:00:00',
    }];

    await app.saveDialog();

    expect(createdProcessRequest).toMatchObject({ runIfParentFails: true });
  });

  it('should reject a path instead of an approved script filename', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.selectedSchedulerId = 1;
    app.dialogMode = 'process';
    app.processForm.type = 'SHELL_CMD';
    app.shellScriptName = 999;

    await app.saveDialog();

    expect(createdProcessRequest).toBeUndefined();
    expect(app.error).toContain('Seleziona uno script');
  });

  it('should save a selected server-authorized command by ID, not as shell text', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.selectedSchedulerId = 1;
    app.dialogMode = 'process';
    app.processForm.type = 'SHELL_CMD';
    app.shellCommands = [{ id: 1, name: 'Print status', type: 'REPORT', executable: 'echo', arguments: ['status'] }];
    app.shellExecutionMode = 'command';
    app.shellCommandId = 1;

    await app.saveDialog();

    expect(createdProcessRequest).toMatchObject({
      type: 'SHELL_CMD',
      configuration: '{"commandId":1}',
    });
  });

  it('should display an executable or script filename in every process row', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const app = fixture.componentInstance;
    app.view = 'processes';
    app.loading = false;
    app.selectedSchedulerId = 1;
    app.shellCommands = [{ id: 1, name: 'Print status', type: 'REPORT', executable: 'echo', arguments: ['status'] }];
    app.shellScripts = [{ id: 2, name: 'Daily report', fileName: 'daily-report.sh', type: 'SH' }];
    app.processes = [
      {
        id: 1,
        name: 'Command process',
        type: 'SHELL_CMD',
        description: null,
        configuration: '{"commandId":1}',
        enabled: true,
        schedulerId: 1,
        parentProcessId: null,
        sequenceOrder: 0,
        runIfParentFails: false,
        createdAt: '2026-01-01T00:00:00',
      },
      {
        id: 2,
        name: 'Child script process',
        type: 'SHELL_CMD',
        description: null,
        configuration: '{"script":"2"}',
        enabled: true,
        schedulerId: 1,
        parentProcessId: 1,
        sequenceOrder: 1,
        runIfParentFails: false,
        createdAt: '2026-01-01T00:00:00',
      },
    ];
    fixture.detectChanges();

    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('.table-panel tbody tr');
    expect(rows).toHaveLength(2);
    for (const row of rows) expect(row.querySelectorAll('td')).toHaveLength(8);
    expect(rows[0].querySelector('td:nth-child(3)')?.textContent).toContain('echo');
    expect(rows[1].querySelector('td:nth-child(3)')?.textContent).toContain('daily-report.sh');
  });

  it('should show a failed process as stopped without changing its parent or sibling states', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.executions = [
      { id: 8, schedulerId: 1, processDefinitionId: 3, state: 'RUNNING', startedAt: null, finishedAt: null, exitCode: null, statusMessage: null, logs: null },
      { id: 7, schedulerId: 1, processDefinitionId: 2, state: 'FAILED', startedAt: null, finishedAt: null, exitCode: 1, statusMessage: null, logs: null },
      { id: 6, schedulerId: 1, processDefinitionId: 1, state: 'COMPLETED', startedAt: null, finishedAt: null, exitCode: 0, statusMessage: null, logs: null },
    ];

    expect(app.latestState(1)).toBe('COMPLETED');
    expect(app.latestState(2)).toBe('FAILED');
    expect(app.stateLabel(app.latestState(2))).toBe('Fallito (fermato)');
    expect(app.latestState(3)).toBe('RUNNING');
  });

  it('should show whether each child runs when its parent fails', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const app = fixture.componentInstance;
    app.view = 'processes';
    app.loading = false;
    app.selectedSchedulerId = 1;
    app.processes = [
      {
        id: 1, name: 'Parent', type: 'SCRIPT', description: null, configuration: null,
        enabled: true, schedulerId: 1, parentProcessId: null, sequenceOrder: 0,
        runIfParentFails: false, createdAt: '2026-01-01T00:00:00',
      },
      {
        id: 2, name: 'Child', type: 'SCRIPT', description: null, configuration: null,
        enabled: true, schedulerId: 1, parentProcessId: 1, sequenceOrder: 1,
        runIfParentFails: true, createdAt: '2026-01-01T00:00:00',
      },
    ];
    fixture.detectChanges();

    const table = fixture.nativeElement as HTMLElement;
    expect(table.querySelector('th:nth-child(4)')?.textContent).toContain('Continua Se Padre Fallisce');
    expect(table.querySelectorAll('tbody tr')[0].querySelector('td:nth-child(4)')?.textContent?.trim()).toBe('—');
    expect(table.querySelectorAll('tbody tr')[1].querySelector('td:nth-child(4)')?.textContent?.trim()).toBe('Sì');
  });

  it('should display shell execution output in the process detail console', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.dialogOpen = true;
    app.selectedProcessDetails = {
      id: 1,
      name: 'Report',
      type: 'SHELL_CMD',
      description: null,
      configuration: '{"script":"daily-report.sh"}',
      enabled: true,
      schedulerId: 1,
      parentProcessId: null,
      sequenceOrder: 0,
      runIfParentFails: false,
      createdAt: '2026-01-01T00:00:00',
    };
    app.processDetailExecutions = [{
      id: 2,
      schedulerId: 1,
      processDefinitionId: 1,
      state: 'COMPLETED',
      startedAt: '2026-01-01T00:00:00',
      finishedAt: '2026-01-01T00:00:01',
      exitCode: 0,
      statusMessage: 'Script completed successfully',
      logs: 'report generated',
    }];
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.console-output')?.textContent)
      .toContain('report generated');
  });

  it('should refresh a running process console until its execution completes', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const app = fixture.componentInstance;
    const process: ProcessDefinition = {
      id: 1,
      name: 'Report',
      type: 'SHELL_CMD',
      description: null,
      configuration: '{"script":"daily-report.sh"}',
      enabled: true,
      schedulerId: 1,
      parentProcessId: null,
      sequenceOrder: 0,
      runIfParentFails: false,
      createdAt: '2026-01-01T00:00:00',
    };
    const runningExecution: ProcessExecution = {
      id: 2,
      schedulerId: 1,
      processDefinitionId: 1,
      state: 'RUNNING',
      startedAt: '2026-01-01T00:00:00',
      finishedAt: null,
      exitCode: null,
      statusMessage: null,
      logs: 'first line',
    };
    processHistoryResponse = of([runningExecution]);
    vi.useFakeTimers();
    try {
      await app.openProcessDetails(process);
      processHistoryResponse = of([{ ...runningExecution, state: 'COMPLETED', logs: 'first line\nsecond line' }]);
      await vi.advanceTimersByTimeAsync(500);
      expect(app.processDetailExecutions[0].logs).toContain('second line');
    } finally {
      fixture.destroy();
      vi.useRealTimers();
    }
  });

  it('should not poll a terminal execution detail again', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const app = fixture.componentInstance;
    const process: ProcessDefinition = {
      id: 1,
      name: 'Report',
      type: 'SHELL_CMD',
      description: null,
      configuration: '{"script":"daily-report.sh"}',
      enabled: true,
      schedulerId: 1,
      parentProcessId: null,
      sequenceOrder: 0,
      runIfParentFails: false,
      createdAt: '2026-01-01T00:00:00',
    };
    const api = TestBed.inject(ScheduleApiService);
    const historyRequest = vi.spyOn(api, 'processHistory').mockReturnValue(of([{
      id: 3,
      schedulerId: 1,
      processDefinitionId: 1,
      state: 'FAILED',
      startedAt: '2026-01-01T00:00:00',
      finishedAt: '2026-01-01T00:00:01',
      exitCode: 1,
      statusMessage: 'Script failed',
      logs: 'failed',
    }]));
    vi.useFakeTimers();
    try {
      await app.openProcessDetails(process);
      await vi.advanceTimersByTimeAsync(5000);
      expect(historyRequest).toHaveBeenCalledTimes(1);
    } finally {
      fixture.destroy();
      vi.useRealTimers();
    }
  });
});
