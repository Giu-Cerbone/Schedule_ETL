import { TestBed } from '@angular/core/testing';
import { NEVER } from 'rxjs';
import { SchedulerService } from './scheduler.service';
import { ShellCatalogService } from './shell-catalog.service';
import { WorkspaceService } from './workspace.service';
import { apiProviders, makeProcess, makeScheduler, newApiMockState, ApiMockState } from './testing/api-mocks';

describe('services of scheduler, shell commands and dashboard', () => {
  let state: ApiMockState;

  beforeEach(() => {
    state = newApiMockState();
    TestBed.configureTestingModule({ providers: apiProviders(state) });
  });

  it('saves a scheduler status and applies the backend response', async () => {
    const scheduler = makeScheduler();
    TestBed.inject(WorkspaceService).schedulers = [scheduler];

    await TestBed.inject(SchedulerService).updateStatus(scheduler, 'PAUSED');

    expect(state.updatedSchedulerStatus).toEqual({ id: 1, status: 'PAUSED' });
    expect(scheduler.status).toBe('PAUSED');
    expect(scheduler.updatedAt).toBe('2026-01-02T00:00:00');
  });

  it('loads the workspace without waiting for process history', async () => {
    state.schedulers = [makeScheduler()];
    state.processes = [makeProcess()];
    state.processHistory = NEVER;
    const workspace = TestBed.inject(WorkspaceService);

    await workspace.refresh();

    expect(workspace.loading).toBe(false);
    expect(workspace.processes).toHaveLength(1);
  });

  it('loads authorized commands and approved scripts', async () => {
    const workspace = TestBed.inject(WorkspaceService);

    await TestBed.inject(ShellCatalogService).refresh();

    expect(workspace.shellCommands[0].type).toBe('REPORT');
    expect(workspace.shellScripts[0].type).toBe('SH');
  });

  it('edits a registered script through its admin API', async () => {
    const catalog = TestBed.inject(ShellCatalogService);
    catalog.registeredScripts = [{ id: 2, name: 'Daily report', fileName: 'daily-report.sh', type: 'SH', path: 'C:\\scripts\\daily-report.sh' }];

    await catalog.editScript(2);
    expect(catalog.scriptForm.content).toBe('');
    catalog.selectBrowserFile('C:\\scripts\\daily-report-renamed.sh');
    await catalog.saveScript();

    expect(state.registeredScriptUpdate).toEqual({
      id: 2,
      request: { name: 'Daily report', fileName: 'daily-report-renamed.sh', path: 'C:\\scripts\\daily-report-renamed.sh' },
    });
  });
});
