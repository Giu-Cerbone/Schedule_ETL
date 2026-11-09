import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  ApprovedShellScript,
  AuthorizedShellCommand,
  AuthorizedShellCommandRequest,
  FileBrowseResponse,
  RegisteredShellScript,
} from './api.models';
import { AdminAuthService } from './admin-auth.service';
import { ChangeNotifier } from './change-notifier.service';
import { NotificationService } from './notification.service';
import { ScheduleApiService } from './schedule-api.service';
import { WorkspaceService } from './workspace.service';

/** Catalogo di script e comandi autorizzati, con login amministratore e file browser del server. */
@Injectable({ providedIn: 'root' })
export class ShellCatalogService {
  private readonly api = inject(ScheduleApiService);
  private readonly workspace = inject(WorkspaceService);
  private readonly notifications = inject(NotificationService);
  private readonly notifier = inject(ChangeNotifier);
  readonly adminAuth = inject(AdminAuthService);

  saving = false;
  adminUsername = '';
  adminPassword = '';
  adminShellCommands: AuthorizedShellCommand[] = [];
  registeredScripts: RegisteredShellScript[] = [];

  editingCommandId: number | null = null;
  commandEditorOpen = false;
  commandForm: AuthorizedShellCommandRequest = { name: '', type: '', executable: '', arguments: [] };
  commandArgumentsText = '';

  editingScriptName: number | null = null;
  scriptEditorOpen = false;
  scriptForm: ApprovedShellScript = { name: '', fileName: '', type: '', content: '' };
  selectedScriptPath = '';
  selectedScriptName = '';

  browserOpen = false;
  browserLoading = false;
  browser: FileBrowseResponse = { path: '', parent: null, entries: [] };

  async refresh(): Promise<void> {
    try {
      [this.workspace.shellCommands, this.workspace.shellScripts] = await Promise.all([
        firstValueFrom(this.api.listShellCommands()),
        firstValueFrom(this.api.listShellScripts()),
      ]);
      if (this.adminAuth.authenticated) {
        this.adminShellCommands = await firstValueFrom(this.api.adminListShellCommands());
        this.registeredScripts = await firstValueFrom(this.api.listRegisteredScripts());
      }
    } catch (error) {
      this.notifications.reportError(error);
    }
    this.notifier.notify();
  }

  async login(): Promise<void> {
    try {
      await this.adminAuth.login(this.adminUsername, this.adminPassword);
      this.adminPassword = '';
      await this.refresh();
      this.notifications.success('Accesso amministratore', 'Autenticazione completata.');
    } catch (error) {
      this.notifications.reportError(error);
    }
  }

  logout(): void {
    this.adminAuth.logout();
    this.adminShellCommands = [];
    this.registeredScripts = [];
    this.cancelScriptEdit();
    this.cancelCommandEdit();
  }

  newScript(): void {
    this.editingScriptName = null;
    this.scriptEditorOpen = true;
    this.scriptForm = { name: '', fileName: '', type: '', content: '' };
  }

  async editScript(id: number): Promise<void> {
    const script = this.registeredScript(id);
    if (!script) {
      this.notifications.reportError(new Error('Script autorizzato non trovato nel catalogo.'));
      return;
    }
    this.scriptForm = { name: script.name, fileName: script.fileName, type: script.type, content: '' };
    this.selectedScriptPath = script.path;
    this.selectedScriptName = script.name;
    this.editingScriptName = id;
    this.scriptEditorOpen = true;
  }

  registeredScript(id: number): RegisteredShellScript | undefined {
    return this.registeredScripts.find((script) => script.id === id);
  }

  selectBrowserFile(path: string): void {
    this.selectedScriptPath = path;
    const fileName = path.split(/[\\/]/).pop() ?? '';
    if (this.editingScriptName !== null) {
      this.scriptForm.fileName = fileName;
    } else if (!this.selectedScriptName) {
      this.selectedScriptName = fileName;
    }
  }

  async openFileBrowser(): Promise<void> {
    this.browserOpen = true;
    await this.browseTo(this.browser.path || this.selectedScriptPath.replace(/[\\/][^\\/]*$/, ''));
  }

  async browseTo(path: string): Promise<void> {
    this.browserLoading = true;
    this.notifier.notify();
    try {
      this.browser = await firstValueFrom(this.api.browseServerFiles(path));
    } catch (error) {
      this.notifications.reportError(error);
      if (path) await this.browseTo('');
    } finally {
      this.browserLoading = false;
      this.notifier.notify();
    }
  }

  async selectBrowserEntry(entry: { path: string; directory: boolean }): Promise<void> {
    if (entry.directory) {
      await this.browseTo(entry.path);
      return;
    }
    this.selectBrowserFile(entry.path);
    this.browserOpen = false;
  }

  cancelScriptEdit(): void {
    this.editingScriptName = null;
    this.scriptEditorOpen = false;
    this.selectedScriptPath = '';
    this.selectedScriptName = '';
    this.scriptForm = { name: '', fileName: '', type: '', content: '' };
  }

  async saveScript(): Promise<void> {
    this.saving = true;
    try {
      if (this.editingScriptName === null) {
        if (!this.selectedScriptName.trim()) throw new Error('Inserisci il nome dello script.');
        await firstValueFrom(this.api.registerScript(this.selectedScriptName.trim(), this.selectedScriptPath));
      } else {
        if (!this.selectedScriptPath) throw new Error('Seleziona il percorso dello script.');
        await firstValueFrom(this.api.updateRegisteredScriptContent(this.editingScriptName, {
          name: this.scriptForm.name,
          fileName: this.scriptForm.fileName,
          path: this.selectedScriptPath,
        }));
      }
      this.cancelScriptEdit();
      await this.refresh();
      this.notifications.success('Script salvato', 'Catalogo script aggiornato.');
    } catch (error) {
      this.notifications.reportError(error);
    } finally {
      this.saving = false;
      this.notifier.notify();
    }
  }

  async deleteScript(id: number): Promise<void> {
    const script = this.workspace.shellScripts.find((item) => item.id === id);
    if (!window.confirm(`Eliminare lo script autorizzato "${script?.name ?? id}"?`)) return;
    try {
      await firstValueFrom(this.api.unregisterScript(id));
      if (this.editingScriptName === id) this.cancelScriptEdit();
      await this.refresh();
      this.notifications.success('Script eliminato', script?.name ?? String(id));
    } catch (error) {
      this.notifications.reportError(error);
    }
  }

  newCommand(): void {
    this.editingCommandId = null;
    this.commandEditorOpen = true;
    this.commandForm = { name: '', type: '', executable: '', arguments: [] };
    this.commandArgumentsText = '';
  }

  editCommand(command: AuthorizedShellCommand): void {
    this.editingCommandId = command.id;
    this.commandEditorOpen = true;
    this.commandForm = {
      name: command.name, type: command.type, executable: command.executable, arguments: [...command.arguments],
    };
    this.commandArgumentsText = command.arguments.join('\n');
  }

  editCommandById(id: number): void {
    const command = this.adminShellCommands.find((item) => item.id === id);
    if (!command) {
      this.notifications.reportError(new Error('Il comando autorizzato non è più disponibile. Aggiorna il catalogo.'));
      return;
    }
    this.editCommand(command);
  }

  cancelCommandEdit(): void {
    this.editingCommandId = null;
    this.commandEditorOpen = false;
    this.commandForm = { name: '', type: '', executable: '', arguments: [] };
    this.commandArgumentsText = '';
  }

  async saveCommand(): Promise<void> {
    this.saving = true;
    try {
      const request: AuthorizedShellCommandRequest = {
        ...this.commandForm,
        arguments: this.commandArgumentsText.split(/\r?\n/).filter((argument) => argument.length > 0),
      };
      if (this.editingCommandId === null) {
        await firstValueFrom(this.api.createShellCommand(request));
      } else {
        await firstValueFrom(this.api.updateShellCommand(this.editingCommandId, request));
      }
      this.cancelCommandEdit();
      await this.refresh();
      this.notifications.success('Comando salvato', 'Catalogo comandi aggiornato.');
    } catch (error) {
      this.notifications.reportError(error);
    } finally {
      this.saving = false;
      this.notifier.notify();
    }
  }

  async deleteCommand(command: AuthorizedShellCommand): Promise<void> {
    if (!window.confirm(`Eliminare il comando autorizzato "${command.name}"?`)) return;
    try {
      await firstValueFrom(this.api.deleteShellCommand(command.id));
      if (this.editingCommandId === command.id) this.cancelCommandEdit();
      await this.refresh();
      this.notifications.success('Comando eliminato', command.name);
    } catch (error) {
      this.notifications.reportError(error);
    }
  }
}
