import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateGroup,
  CreateProcess,
  CreateProfile,
  CreateScheduler,
  ApprovedShellScript,
  ApprovedShellScriptRequest,
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

@Injectable({ providedIn: 'root' })
export class ScheduleApiService {
  private readonly baseUrl = '/api/v1';

  constructor(private readonly http: HttpClient) {}

  listSchedulers(): Observable<Scheduler[]> {
    return this.http.get<Scheduler[]>(`${this.baseUrl}/schedulers`);
  }

  getScheduler(id: number): Observable<Scheduler> {
    return this.http.get<Scheduler>(`${this.baseUrl}/schedulers/${id}`);
  }

  createScheduler(request: CreateScheduler): Observable<Scheduler> {
    return this.http.post<Scheduler>(`${this.baseUrl}/schedulers`, request);
  }

  assignSchedulerToGroup(schedulerId: number, groupId: number): Observable<Scheduler> {
    return this.http.put<Scheduler>(`${this.baseUrl}/schedulers/${schedulerId}/groups/${groupId}`, {});
  }

  deleteScheduler(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/schedulers/${id}`);
  }

  listProcesses(schedulerId: number): Observable<ProcessDefinition[]> {
    return this.http.get<ProcessDefinition[]>(`${this.baseUrl}/schedulers/${schedulerId}/processes`);
  }

  listProcessTypes(): Observable<ProcessType[]> {
    return this.http.get<ProcessType[]>(`${this.baseUrl}/process-types`);
  }

  listShellCommands(): Observable<ShellCommandOption[]> {
    return this.http.get<ShellCommandOption[]>(`${this.baseUrl}/shell-commands`);
  }

  listShellScripts(): Observable<ShellScriptOption[]> {
    return this.http.get<ShellScriptOption[]>(`${this.baseUrl}/shell-scripts`);
  }

  adminSession(): Observable<{ username: string }> {
    return this.http.get<{ username: string }>(`${this.baseUrl}/admin/session`);
  }

  adminListShellCommands(): Observable<AuthorizedShellCommand[]> {
    return this.http.get<AuthorizedShellCommand[]>(`${this.baseUrl}/admin/shell-commands`);
  }

  createShellCommand(command: AuthorizedShellCommand): Observable<AuthorizedShellCommand> {
    return this.http.post<AuthorizedShellCommand>(`${this.baseUrl}/admin/shell-commands`, command);
  }

  updateShellCommand(id: string, command: AuthorizedShellCommand): Observable<AuthorizedShellCommand> {
    return this.http.put<AuthorizedShellCommand>(`${this.baseUrl}/admin/shell-commands/${encodeURIComponent(id)}`, command);
  }

  deleteShellCommand(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/shell-commands/${encodeURIComponent(id)}`);
  }

  getApprovedScript(fileName: string): Observable<ApprovedShellScript> {
    return this.http.get<ApprovedShellScript>(
      `${this.baseUrl}/admin/shell-scripts/${encodeURIComponent(fileName)}`,
    );
  }

  createApprovedScript(script: ApprovedShellScriptRequest): Observable<ApprovedShellScript> {
    return this.http.post<ApprovedShellScript>(`${this.baseUrl}/admin/shell-scripts`, script);
  }

  updateApprovedScript(fileName: string, script: ApprovedShellScriptRequest): Observable<ApprovedShellScript> {
    return this.http.put<ApprovedShellScript>(
      `${this.baseUrl}/admin/shell-scripts/${encodeURIComponent(fileName)}`,
      script,
    );
  }

  deleteApprovedScript(fileName: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/shell-scripts/${encodeURIComponent(fileName)}`);
  }

  browseServerFiles(path: string): Observable<FileBrowseResponse> {
    return this.http.get<FileBrowseResponse>(`${this.baseUrl}/admin/filesystem`, { params: { path } });
  }

  listRegisteredScripts(): Observable<RegisteredShellScript[]> {
    return this.http.get<RegisteredShellScript[]>(`${this.baseUrl}/admin/registered-scripts`);
  }

  registerScript(id: string, path: string): Observable<RegisteredShellScript> {
    return this.http.post<RegisteredShellScript>(`${this.baseUrl}/admin/registered-scripts`, { id, path });
  }

  unregisterScript(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/registered-scripts/${encodeURIComponent(id)}`);
  }

  createProcess(schedulerId: number, request: CreateProcess): Observable<ProcessDefinition> {
    return this.http.post<ProcessDefinition>(`${this.baseUrl}/schedulers/${schedulerId}/processes`, request);
  }

  updateProcess(schedulerId: number, processId: number, request: CreateProcess): Observable<ProcessDefinition> {
    return this.http.put<ProcessDefinition>(`${this.baseUrl}/schedulers/${schedulerId}/processes/${processId}`, request);
  }

  startProcess(schedulerId: number, processId: number): Observable<ProcessExecution> {
    return this.processCommand(schedulerId, processId, 'start');
  }

  pauseProcess(schedulerId: number, processId: number): Observable<ProcessExecution> {
    return this.processCommand(schedulerId, processId, 'pause');
  }

  resumeProcess(schedulerId: number, processId: number): Observable<ProcessExecution> {
    return this.processCommand(schedulerId, processId, 'resume');
  }

  stopProcess(schedulerId: number, processId: number): Observable<ProcessExecution> {
    return this.processCommand(schedulerId, processId, 'stop');
  }

  processHistory(schedulerId: number, processId: number): Observable<ProcessExecution[]> {
    return this.http.get<ProcessExecution[]>(`${this.baseUrl}/schedulers/${schedulerId}/processes/${processId}/history`);
  }

  listProfiles(): Observable<UserProfile[]> {
    return this.http.get<UserProfile[]>(`${this.baseUrl}/profiles`);
  }

  getProfile(id: number): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.baseUrl}/profiles/${id}`);
  }

  createProfile(request: CreateProfile): Observable<UserProfile> {
    return this.http.post<UserProfile>(`${this.baseUrl}/profiles`, request);
  }

  listGroups(): Observable<UserGroup[]> {
    return this.http.get<UserGroup[]>(`${this.baseUrl}/groups`);
  }

  getGroup(id: number): Observable<UserGroup> {
    return this.http.get<UserGroup>(`${this.baseUrl}/groups/${id}`);
  }

  createGroup(request: CreateGroup): Observable<UserGroup> {
    return this.http.post<UserGroup>(`${this.baseUrl}/groups`, request);
  }

  private processCommand(schedulerId: number, processId: number, command: string): Observable<ProcessExecution> {
    return this.http.post<ProcessExecution>(
      `${this.baseUrl}/schedulers/${schedulerId}/processes/${processId}/${command}`,
      {},
    );
  }
}
