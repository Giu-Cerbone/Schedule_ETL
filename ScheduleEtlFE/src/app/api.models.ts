export interface Scheduler {
  id: number;
  name: string;
  description: string | null;
  cronExpression: string;
  status: string;
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
  createdAt: string;
}

export interface ProcessType {
  code: string;
  description: string;
}

export interface ShellCommandOption {
  id: string;
  label: string;
  type: string;
}

export interface AuthorizedShellCommand {
  id: string;
  label: string;
  type: string;
  executable: string;
  arguments: string[];
}

export interface ApprovedShellScript {
  fileName: string;
  type: string;
  content: string;
}

export interface ApprovedShellScriptRequest {
  fileName: string;
  content: string;
}

export interface ShellScriptOption {
  id: string;
  fileName: string;
  type: string;
}

export interface RegisteredShellScript {
  id: string;
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
  status: string;
}

export interface CreateProcess {
  name: string;
  type: string;
  description: string;
  configuration: string;
  enabled: boolean;
  parentProcessId: number | null;
  sequenceOrder: number;
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
