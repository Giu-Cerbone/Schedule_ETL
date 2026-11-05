import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { App } from './app';
import { ScheduleApiService } from './schedule-api.service';
import { AdminAuthService } from './admin-auth.service';

describe('App', () => {
  let createdProcessRequest: object | undefined;

  beforeEach(async () => {
    createdProcessRequest = undefined;
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [{
        provide: ScheduleApiService,
        useValue: {
          listSchedulers: () => of([]),
          listProfiles: () => of([]),
          listGroups: () => of([]),
          listProcessTypes: () => of([
            { code: 'SQLDATABASE', description: 'Database SQL' },
            { code: 'SHELL_CMD', description: 'Shell command' },
            { code: 'SCRIPT', description: 'Script' },
          ]),
          listShellCommands: () => of([
            { id: 'print-status', label: 'Print status', type: 'REPORT' },
          ]),
          listShellScripts: () => of([
            { id: 'daily-report.sh', fileName: 'daily-report.sh', type: 'SH' },
          ]),
          listProcesses: () => of([]),
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

  it('should provide a menu page for authorized commands and approved scripts', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.setView('shell-commands');
    app.shellCommands = [{ id: 'print-status', label: 'Print status', type: 'REPORT' }];
    app.shellScripts = [{ id: 'daily-report.sh', fileName: 'daily-report.sh', type: 'SH' }];

    expect(app.view).toBe('shell-commands');
    expect(app.navigation.some((item) => item.id === 'shell-commands')).toBe(true);
    expect(app.shellCommands[0].type).toBe('REPORT');
    expect(app.shellScripts[0].type).toBe('SH');
  });

  it('should show the approved script catalog and its detected type for SHELL_CMD', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.processTypes = [{ code: 'SHELL_CMD', description: 'Shell command' }];
    app.shellScripts = [{ id: 'daily-report.sh', fileName: 'daily-report.sh', type: 'SH' }];
    app.openDialog('process');
    app.processForm.configuration = '{"script":"daily-report.sh"}';
    app.onProcessTypeChange('SHELL_CMD');
    fixture.detectChanges();

    expect(app.shellScriptName).toBe('daily-report.sh');
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
    app.shellScripts = [{ id: 'daily-report.sh', fileName: 'daily-report.sh', type: 'SH' }];
    app.shellScriptName = 'daily-report.sh';

    await app.saveDialog();

    expect(createdProcessRequest).toMatchObject({
      type: 'SHELL_CMD',
      configuration: '{"script":"daily-report.sh"}',
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
    app.shellScriptName = '../outside.sh';

    await app.saveDialog();

    expect(createdProcessRequest).toBeUndefined();
    expect(app.error).toContain('senza percorso');
  });

  it('should save a selected server-authorized command by ID, not as shell text', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.selectedSchedulerId = 1;
    app.dialogMode = 'process';
    app.processForm.type = 'SHELL_CMD';
    app.shellCommands = [{ id: 'print-status', label: 'Print status', type: 'REPORT' }];
    app.shellExecutionMode = 'command';
    app.shellCommandId = 'print-status';

    await app.saveDialog();

    expect(createdProcessRequest).toMatchObject({
      type: 'SHELL_CMD',
      configuration: '{"commandId":"print-status"}',
    });
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
});
