import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ScheduleApiService } from './schedule-api.service';
import { ProcessService } from './process.service';
import { WorkspaceService } from './workspace.service';
import { StateLabelsService } from './state-labels.service';
import { ApiMockState, apiProviders, makeProcess, newApiMockState } from './testing/api-mocks';

describe('ProcessService', () => {
  let state: ApiMockState;
  let service: ProcessService;
  let workspace: WorkspaceService;

  beforeEach(() => {
    state = newApiMockState();
    TestBed.configureTestingModule({ providers: apiProviders(state) });
    service = TestBed.inject(ProcessService);
    workspace = TestBed.inject(WorkspaceService);
    workspace.selectedSchedulerId = 1;
    workspace.processTypes = [{ code: 'SHELL_CMD', description: 'Shell command' }];
    workspace.shellCommands = [{ id: 1, name: 'Print status', type: 'REPORT', executable: 'echo', arguments: ['status'] }];
    workspace.shellScripts = [{ id: 2, name: 'Daily report', fileName: 'daily-report.sh', type: 'SH' }];
  });

  afterEach(() => service.ngOnDestroy());

  it('shows the approved script and its type for SHELL_CMD', () => {
    service.newProcess();
    service.processForm.configuration = '{"script":"daily-report.sh"}';
    service.onProcessTypeChange('SHELL_CMD');

    expect(service.shellScriptName).toBe(2);
    expect(service.selectedShellScriptType()).toBe('SH');
  });

  it('submits SHELL_CMD as an approved script configuration', async () => {
    service.newProcess();
    service.processForm = { ...service.processForm, name: 'Report', type: 'SHELL_CMD', configuration: '{}' };
    service.shellScriptName = 2;

    await service.save();

    expect(state.createdProcessRequest).toMatchObject({ type: 'SHELL_CMD', configuration: '{"script":"2"}' });
  });

  it('submits multiple weekly start times for each selected day', async () => {
    service.newProcess();
    service.processForm.type = 'SQLDATABASE';
    service.processForm.startSchedule = [
      { dayOfWeek: 'MONDAY', times: ['08:30', '12:00'], startDate: '2026-12-07' },
      { dayOfWeek: 'FRIDAY', times: ['09:15'], startDate: '2026-12-04' },
    ];

    await service.save();

    expect(state.createdProcessRequest).toMatchObject({
      startSchedule: [
        { dayOfWeek: 'MONDAY', times: ['08:30', '12:00'], startDate: '2026-12-07' },
        { dayOfWeek: 'FRIDAY', times: ['09:15'], startDate: '2026-12-04' },
      ],
    });
  });

  it('keeps the weekly start schedule when editing', async () => {
    service.editProcess(makeProcess({
      id: 2,
      type: 'SQLDATABASE',
      startSchedule: [{ dayOfWeek: 'TUESDAY', times: ['08:30', '12:00'], startDate: '2026-12-01' }],
    }));

    await service.save();

    expect(state.updatedProcessRequest).toMatchObject({
      startSchedule: [{ dayOfWeek: 'TUESDAY', times: ['08:30', '12:00'], startDate: '2026-12-01' }],
    });
  });

  it('submits the child setting to run after parent failure', async () => {
    workspace.processes = [makeProcess({ id: 1, name: 'Parent', type: 'SQLDATABASE' })];
    service.newProcess();
    service.processForm = {
      ...service.processForm,
      name: 'Child',
      type: 'SQLDATABASE',
      configuration: '{}',
      parentProcessId: 1,
      sequenceOrder: 1,
      runIfParentFails: true,
    };

    await service.save();

    expect(state.createdProcessRequest).toMatchObject({ runIfParentFails: true });
  });

  it('rejects an unknown approved script', async () => {
    service.newProcess();
    service.processForm.type = 'SHELL_CMD';
    service.shellScriptName = 999;

    await service.save();

    expect(state.createdProcessRequest).toBeUndefined();
    expect(TestBed.inject(WorkspaceService)).toBeTruthy();
    expect(service['notifications'].error).toContain('Seleziona uno script');
  });

  it('saves a server-authorized command by ID', async () => {
    service.newProcess();
    service.processForm.type = 'SHELL_CMD';
    service.shellExecutionMode = 'command';
    service.shellCommandId = 1;

    await service.save();

    expect(state.createdProcessRequest).toMatchObject({ type: 'SHELL_CMD', configuration: '{"commandId":1}' });
  });

  it('restricts the datepicker to the weekday of the selected day', () => {
    expect(service.disabledWeekdays('MONDAY')).toEqual([0, 2, 3, 4, 5, 6]);
  });

  it('refreshes a running console until its execution completes', async () => {
    const running = {
      id: 2, schedulerId: 1, processDefinitionId: 1, state: 'RUNNING', startedAt: '2026-01-01T00:00:00',
      finishedAt: null, exitCode: null, statusMessage: null, logs: 'first line',
    };
    state.processHistory = of([running]);
    vi.useFakeTimers();
    try {
      await service.openDetails(makeProcess({ type: 'SHELL_CMD' }));
      state.processHistory = of([{ ...running, state: 'COMPLETED', logs: 'first line\nsecond line' }]);
      await vi.advanceTimersByTimeAsync(500);
      expect(service.detailExecutions[0].logs).toContain('second line');
    } finally {
      service.closeDetails();
      vi.useRealTimers();
    }
  });

  it('does not poll a terminal execution again', async () => {
    const historyRequest = vi.spyOn(TestBed.inject(ScheduleApiService), 'processHistory').mockReturnValue(of([{
      id: 3, schedulerId: 1, processDefinitionId: 1, state: 'FAILED', startedAt: '2026-01-01T00:00:00',
      finishedAt: '2026-01-01T00:00:01', exitCode: 1, statusMessage: 'Script failed', logs: 'failed',
    }]));
    vi.useFakeTimers();
    try {
      await service.openDetails(makeProcess({ type: 'SHELL_CMD' }));
      await vi.advanceTimersByTimeAsync(5000);
      expect(historyRequest).toHaveBeenCalledTimes(1);
    } finally {
      service.closeDetails();
      vi.useRealTimers();
    }
  });

  it('shows a failed process as stopped without touching siblings', () => {
    const labels = TestBed.inject(StateLabelsService);
    const base = { schedulerId: 1, startedAt: null, finishedAt: null, statusMessage: null, logs: null };
    workspace.executions = [
      { ...base, id: 8, processDefinitionId: 3, state: 'RUNNING', exitCode: null },
      { ...base, id: 7, processDefinitionId: 2, state: 'FAILED', exitCode: 1 },
      { ...base, id: 6, processDefinitionId: 1, state: 'COMPLETED', exitCode: 0 },
    ];

    expect(workspace.latestState(1)).toBe('COMPLETED');
    expect(workspace.latestState(2)).toBe('FAILED');
    expect(labels.stateLabel(workspace.latestState(2))).toBe('Fallito (fermato)');
    expect(workspace.latestState(3)).toBe('RUNNING');
  });
});
