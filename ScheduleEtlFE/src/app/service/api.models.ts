export type SchedulerStatus = 'ACTIVE' | 'INACTIVE' | 'PAUSED' | 'DELETED';
export type EditableSchedulerStatus = Exclude<SchedulerStatus, 'DELETED'>;

export interface Scheduler {
  id: number;
  name: string;
  description: string | null;
  cronExpression: string;
  status: SchedulerStatus;
  ownerProfileId: number;
  groupId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProcessDefinition {
  id: number;
  name: string;
  type: string;
  description: string | null;
  configuration: string | null;
  enabled: boolean;
  schedulerId: number;
  parentProcessId: number | null;
  sequenceOrder: number;
  runIfParentFails: boolean;
  startSchedule?: ProcessScheduleDay[];
  createdAt: string;
}

export type ProcessScheduleDayName =
  | 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface ProcessScheduleDay {
  dayOfWeek: ProcessScheduleDayName;
  times: string[];
  startDate?: string | null;
}

export interface ProcessType {
  code: string;
  description: string;
}

export interface ShellCommandOption {
  id: number;
  name: string;
  type: string;
  executable: string;
  arguments: string[];
}

export interface AuthorizedShellCommand {
  id: number;
  name: string;
  type: string;
  executable: string;
  arguments: string[];
}

export type AuthorizedShellCommandRequest = Omit<AuthorizedShellCommand, 'id'>;

export interface ApprovedShellScript {
  name: string;
  fileName: string;
  type: string;
  content: string;
}

export interface ApprovedShellScriptRequest {
  name: string;
  fileName: string;
  path?: string;
  content?: string;
}

export interface ShellScriptOption {
  id: number;
  name: string;
  fileName: string;
  type: string;
}

export interface RegisteredShellScript {
  id: number;
  name: string;
  fileName: string;
  type: string;
  path: string;
}

export interface FileBrowseEntry {
  name: string;
  path: string;
  directory: boolean;
}

export interface FileBrowseResponse {
  path: string;
  parent: string | null;
  entries: FileBrowseEntry[];
}

export interface ProcessExecution {
  id: number;
  schedulerId: number;
  processDefinitionId: number;
  state: string;
  startedAt: string | null;
  finishedAt: string | null;
  exitCode: number | null;
  statusMessage: string | null;
  logs: string | null;
}

export interface UserProfile {
  id: number;
  username: string;
  fullName: string;
  email: string;
}

export interface UserGroup {
  id: number;
  name: string;
  description: string | null;
}

export interface CreateScheduler {
  name: string;
  description: string;
  cronExpression: string;
  ownerProfileId: number | null;
  groupId: number | null;
  status: EditableSchedulerStatus;
}

export interface CreateProcess {
  name: string;
  type: string;
  description: string;
  configuration: string;
  enabled: boolean;
  parentProcessId: number | null;
  sequenceOrder: number;
  runIfParentFails: boolean;
  startSchedule?: ProcessScheduleDay[];
}

export interface CreateProfile {
  username: string;
  fullName: string;
  email: string;
}

export interface CreateGroup {
  name: string;
  description: string;
}

export interface ProcessEventLog {
  id: number;
  loggedAt: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  eventType: string;
  stepNumber: number | null;
  stepName: string | null;
  message: string;
  details: string | null;
  executionId: number | null;
  processDefinitionId: number | null;
  processName: string | null;
  processType: string | null;
  parentProcessId: number | null;
  sequenceOrder: number | null;
  schedulerId: number | null;
  schedulerName: string | null;
  triggerType: string | null;
  scheduleInfo: string | null;
  state: string | null;
  exitCode: number | null;
}

export interface ProcessEventLogPage {
  items: ProcessEventLog[];
  totalElements: number;
  page: number;
  size: number;
}

export interface ProcessEventLogFilter {
  text?: string;
  level?: string;
  eventType?: string;
  triggerType?: string;
  schedulerId?: number | null;
  from?: string;
  to?: string;
  page: number;
  size: number;
}
