import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProcessComponent } from './process.component';
import { ProcessService } from '../../service/process.service';
import { WorkspaceService } from '../../service/workspace.service';
import { ApiMockState, apiProviders, makeProcess, newApiMockState } from '../../service/testing/api-mocks';

describe('ProcessComponent', () => {
  let state: ApiMockState;

  beforeEach(async () => {
    state = newApiMockState();
    await TestBed.configureTestingModule({
      imports: [ProcessComponent],
      providers: [...apiProviders(state), provideRouter([])],
    }).compileComponents();
    const workspace = TestBed.inject(WorkspaceService);
    workspace.loading = false;
    workspace.selectedSchedulerId = 1;
    workspace.shellCommands = [{ id: 1, name: 'Print status', type: 'REPORT', executable: 'echo', arguments: ['status'] }];
    workspace.shellScripts = [{ id: 2, name: 'Daily report', fileName: 'daily-report.sh', type: 'SH' }];
  });

  it('shows the script content field only for type SCRIPT', () => {
    const scriptFixture = TestBed.createComponent(ProcessComponent);
    scriptFixture.componentInstance.openNew();
    TestBed.inject(ProcessService).processForm.type = 'SCRIPT';
    scriptFixture.detectChanges();
    expect(document.body.textContent).toContain('Contenuto script');
    scriptFixture.destroy();

    const sqlFixture = TestBed.createComponent(ProcessComponent);
    sqlFixture.componentInstance.openNew();
    TestBed.inject(ProcessService).processForm.type = 'SQLDATABASE';
    sqlFixture.detectChanges();
    expect(document.body.textContent).not.toContain('Contenuto script');
    sqlFixture.destroy();
  });

  it('shows the continue-after-failure option only for child processes', () => {
    const label = 'Esegui comunque se il processo padre fallisce';
    const rootFixture = TestBed.createComponent(ProcessComponent);
    rootFixture.componentInstance.openNew();
    rootFixture.detectChanges();
    expect(document.body.textContent).not.toContain(label);
    rootFixture.destroy();

    const childFixture = TestBed.createComponent(ProcessComponent);
    childFixture.componentInstance.openNew();
    TestBed.inject(ProcessService).processForm.parentProcessId = 1;
    childFixture.detectChanges();
    expect(document.body.textContent).toContain(label);
    childFixture.destroy();
  });

  it('displays the executable or script filename in every process row', () => {
    const workspace = TestBed.inject(WorkspaceService);
    workspace.processes = [
      makeProcess({ id: 1, name: 'Command process', type: 'SHELL_CMD', configuration: '{"commandId":1}' }),
      makeProcess({ id: 2, name: 'Child', type: 'SHELL_CMD', configuration: '{"script":"2"}', parentProcessId: 1, sequenceOrder: 1 }),
    ];
    const fixture = TestBed.createComponent(ProcessComponent);
    fixture.detectChanges();

    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('.table-panel tbody tr');
    expect(rows).toHaveLength(2);
    for (const row of rows) expect(row.querySelectorAll('td')).toHaveLength(9);
    expect(rows[0].querySelector('td:nth-child(3)')?.textContent).toContain('echo');
    expect(rows[1].querySelector('td:nth-child(3)')?.textContent).toContain('daily-report.sh');
    fixture.destroy();
  });

  it('shows whether each child runs when its parent fails', () => {
    const workspace = TestBed.inject(WorkspaceService);
    workspace.processes = [
      makeProcess({ id: 1, name: 'Parent' }),
      makeProcess({ id: 2, name: 'Child', parentProcessId: 1, sequenceOrder: 1, runIfParentFails: true }),
    ];
    const fixture = TestBed.createComponent(ProcessComponent);
    fixture.detectChanges();

    const table = fixture.nativeElement as HTMLElement;
    expect(table.querySelector('th:nth-child(4)')?.textContent).toContain('Continua Se Padre Fallisce');
    expect(table.querySelectorAll('tbody tr')[0].querySelector('td:nth-child(4)')?.textContent?.trim()).toBe('—');
    expect(table.querySelectorAll('tbody tr')[1].querySelector('td:nth-child(4)')?.textContent?.trim()).toBe('Sì');
    fixture.destroy();
  });

  it('displays shell execution output in the detail console', () => {
    const fixture = TestBed.createComponent(ProcessComponent);
    const processes = TestBed.inject(ProcessService);
    fixture.componentInstance.detailsOpen = true;
    processes.detailProcess = makeProcess({ type: 'SHELL_CMD', configuration: '{"script":"daily-report.sh"}' });
    processes.detailExecutions = [{
      id: 2, schedulerId: 1, processDefinitionId: 1, state: 'COMPLETED', startedAt: '2026-01-01T00:00:00',
      finishedAt: '2026-01-01T00:00:01', exitCode: 0, statusMessage: 'ok', logs: 'report generated',
    }];
    fixture.detectChanges();

    expect(document.body.querySelector('.console-output')?.textContent).toContain('report generated');
    fixture.destroy();
  });
});
