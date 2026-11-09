import { Provider } from '@angular/core';
import { MessageService } from 'primeng/api';
import { Observable, of } from 'rxjs';
import { AdminAuthService } from '../admin-auth.service';
import { ProcessDefinition, ProcessExecution, Scheduler } from '../api.models';
import { ScheduleApiService } from '../schedule-api.service';

export interface ApiMockState {
  createdProcessRequest?: object;
  updatedProcessRequest?: object;
  registeredScriptUpdate?: { id: number; request: object };
  updatedSchedulerStatus?: { id: number; status: string };
  schedulers: Scheduler[];
  processes: ProcessDefinition[];
  processHistory: Observable<ProcessExecution[]>;
}

export function newApiMockState(): ApiMockState {
  return { schedulers: [], processes: [], processHistory: of([]) };
}

export function makeProcess(overrides: Partial<ProcessDefinition> = {}): ProcessDefinition {
  return {
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
    ...overrides,
  };
}

export function makeScheduler(overrides: Partial<Scheduler> = {}): Scheduler {
  return {
    id: 1,
    name: 'Daily',
    description: null,
    cronExpression: '0 0 2 * * ?',
    status: 'ACTIVE',
    ownerProfileId: 1,
    groupId: null,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
    ...overrides,
  };
}

export function apiProviders(state: ApiMockState): Provider[] {
  return [
    MessageService,
    {
      provide: ScheduleApiService,
      useValue: {
        listSchedulers: () => of(state.schedulers),
        updateSchedulerStatus: (id: number, status: string) => {
          state.updatedSchedulerStatus = { id, status };
          return of({ id, status, updatedAt: '2026-01-02T00:00:00' });
        },
        listProfiles: () => of([]),
        listGroups: () => of([]),
        adminListShellCommands: () => of([]),
        listRegisteredScripts: () => of([]),
        getRegisteredScriptContent: () => of({ name: 'Daily report', fileName: 'daily-report.sh', type: 'SH', content: 'echo old' }),
        updateRegisteredScriptContent: (id: number, request: object) => {
          state.registeredScriptUpdate = { id, request };
          return of({});
        },
        listProcessTypes: () => of([
          { code: 'SQLDATABASE', description: 'Database SQL' },
          { code: 'SHELL_CMD', description: 'Shell command' },
          { code: 'SCRIPT', description: 'Script' },
        ]),
        listShellCommands: () => of([{ id: 1, name: 'Print status', type: 'REPORT', executable: 'echo', arguments: ['status'] }]),
        listShellScripts: () => of([{ id: 2, name: 'Daily report', fileName: 'daily-report.sh', type: 'SH' }]),
        listProcesses: () => of(state.processes),
        processHistory: () => state.processHistory,
        createProcess: (_schedulerId: number, request: object) => {
          state.createdProcessRequest = request;
          return of({});
        },
        updateProcess: (_schedulerId: number, _processId: number, request: object) => {
          state.updatedProcessRequest = request;
          return of({});
        },
      },
    },
    {
      provide: AdminAuthService,
      useValue: { authenticated: false, username: '', login: async () => {}, logout: () => {} },
    },
  ];
}
